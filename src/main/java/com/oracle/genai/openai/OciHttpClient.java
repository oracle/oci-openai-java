/**
 * Copyright (c) 2025 Oracle and/or its affiliates.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */
package com.oracle.genai.openai;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import com.openai.core.RequestOptions;
import com.openai.core.Timeout;
import com.openai.core.http.Headers;
import com.openai.core.http.HttpClient;
import com.openai.core.http.HttpMethod;
import com.openai.core.http.HttpRequest;
import com.openai.core.http.HttpRequestBody;
import com.openai.core.http.HttpResponse;
import com.openai.errors.OpenAIIoException;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Dispatcher;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import okio.BufferedSink;


/**
 * Custom HttpClient that signs requests with OCI credentials while complying with the OpenAI SDK contract.
 */
public final class OciHttpClient implements HttpClient {
    private static final RequestBody EMPTY_REQUEST_BODY = new RequestBody() {
        @Override
        public MediaType contentType() {
            return null;
        }

        @Override
        public long contentLength() {
            return 0L;
        }

        @Override
        public void writeTo(BufferedSink sink) {
            // no-op for empty body
        }
    };

    private final OkHttpClient httpClient;
    private final String placeholderBearerToken;

    /**
     * Creates an OCI-signing HTTP client wrapper around the provided OkHttp client.
     *
     * @param httpClient the underlying OkHttp client used to issue requests
     * @param placeholderBearerToken optional placeholder token used to filter Authorization headers
     */
    private OciHttpClient(OkHttpClient httpClient, String placeholderBearerToken) {
        this.httpClient = httpClient;
        this.placeholderBearerToken = placeholderBearerToken;
    }

    /**
     * Executes the request synchronously using the configured OCI signer.
     *
     * @param request the OpenAI SDK request to execute
     * @param requestOptions optional per-request overrides such as timeouts
     * @return the OpenAI-compatible {@link HttpResponse}
     * @throws OpenAIIoException if an I/O problem occurs while calling the service
     */
    @Override
    public HttpResponse execute(HttpRequest request, RequestOptions requestOptions) {
        Objects.requireNonNull(request, "request");
        RequestOptions options = requestOptions != null ? requestOptions : RequestOptions.none();
        Call call = newCall(request, options);
        try {
            Response response = call.execute();
            return toResponse(response);
        } catch (IOException e) {
            throw new OpenAIIoException("Request failed", e);
        } finally {
            closeQuietly(request.body());
        }
    }

    /**
     * Executes the request asynchronously using the configured OCI signer.
     *
     * @param request the OpenAI SDK request to execute
     * @param requestOptions optional per-request overrides such as timeouts
     * @return a {@link CompletableFuture} that completes with the response or an exception
     */
    @Override
    public CompletableFuture<HttpResponse> executeAsync(HttpRequest request, RequestOptions requestOptions) {
        Objects.requireNonNull(request, "request");
        RequestOptions options = requestOptions != null ? requestOptions : RequestOptions.none();
        CompletableFuture<HttpResponse> future = new CompletableFuture<>();
        HttpRequestBody requestBody = request.body();
        if (requestBody != null) {
            future.whenComplete((ignored, throwable) -> closeQuietly(requestBody));
        }

        newCall(request, options).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                future.completeExceptionally(new OpenAIIoException("Request failed", e));
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    future.complete(toResponse(response));
                } catch (RuntimeException ex) {
                    response.close();
                    future.completeExceptionally(ex);
                }
            }
        });

        return future;
    }

    /**
     * Releases resources held by the underlying OkHttp client, including running calls and caches.
     */
    @Override
    public void close() {
        Dispatcher dispatcher = httpClient.dispatcher();
        dispatcher.executorService().shutdown();
        dispatcher.cancelAll();
        httpClient.connectionPool().evictAll();
        okhttp3.Cache cache = httpClient.cache();
        if (cache != null) {
            try {
                cache.close();
            } catch (IOException ignored) {
                // ignore
            }
        }
    }

    /**
     * Builds a new OkHttp {@link Call} for the supplied request, applying any timeout overrides.
     *
     * @param request the OpenAI request to execute
     * @param requestOptions per-request options that may override client timeouts
     * @return a configured OkHttp {@link Call}
     */
    private Call newCall(HttpRequest request, RequestOptions requestOptions) {
        OkHttpClient.Builder builder = httpClient.newBuilder();
        Timeout timeout = requestOptions.getTimeout();
        if (timeout != null) {
            builder.connectTimeout(timeout.connect());
            builder.readTimeout(timeout.read());
            builder.writeTimeout(timeout.write());
            builder.callTimeout(timeout.request());
        }

        OkHttpClient client = builder.build();
        Request okRequest = toOkHttpRequest(request, client);
        return client.newCall(okRequest);
    }

    /**
     * Translates the OpenAI SDK request into an OkHttp {@link Request}, preserving headers and body semantics.
     *
     * @param request the OpenAI SDK request to convert
     * @param client the OkHttp client whose configuration (e.g. timeouts) may influence headers
     * @return a fully populated OkHttp request
     */
    private Request toOkHttpRequest(HttpRequest request, OkHttpClient client) {
        RequestBody body = null;
        HttpRequestBody requestBody = request.body();
        if (requestBody != null) {
            body = toRequestBody(requestBody);
        }
        if (body == null && requiresBody(request.method())) {
            body = EMPTY_REQUEST_BODY;
        }

        Request.Builder builder = new Request.Builder()
                .url(toHttpUrl(request))
                .method(request.method().name(), body);

        Headers headers = request.headers();
        for (String name : headers.names()) {
            for (String value : headers.values(name)) {
                if (isPlaceholderAuthorization(name, value)) {
                    continue;
                }
                builder.header(name, value);
            }
        }

        if (!headers.names().contains("X-Stainless-Read-Timeout") && client.readTimeoutMillis() != 0) {
            long readTimeoutSeconds = Duration.ofMillis(client.readTimeoutMillis()).getSeconds();
            builder.header("X-Stainless-Read-Timeout", Long.toString(readTimeoutSeconds));
        }
        if (!headers.names().contains("X-Stainless-Timeout") && client.callTimeoutMillis() != 0) {
            long callTimeoutSeconds = Duration.ofMillis(client.callTimeoutMillis()).getSeconds();
            builder.header("X-Stainless-Timeout", Long.toString(callTimeoutSeconds));
        }

        return builder.build();
    }

    /**
     * Determines whether a header matches the placeholder Authorization value that should be suppressed.
     *
     * @param name the header name
     * @param value the header value
     * @return {@code true} when the header corresponds to the placeholder bearer token
     */
    private boolean isPlaceholderAuthorization(String name, String value) {
        if (placeholderBearerToken == null) {
            return false;
        }
        return "Authorization".equalsIgnoreCase(name)
                && ("Bearer " + placeholderBearerToken).equals(value);
    }

    /**
     * Builds the target {@link HttpUrl} by combining the request's base URL, path segments, and query parameters.
     *
     * @param request the OpenAI request containing path and query information
     * @return the resulting OkHttp {@link HttpUrl}
     * @throws IllegalArgumentException if the base URL is invalid
     */
    private HttpUrl toHttpUrl(HttpRequest request) {
        HttpUrl base = HttpUrl.parse(request.baseUrl());
        if (base == null) {
            throw new IllegalArgumentException("Invalid base URL: " + request.baseUrl());
        }
        HttpUrl.Builder builder = base.newBuilder();
        for (String segment : request.pathSegments()) {
            builder.addPathSegment(segment);
        }
        for (String key : request.queryParams().keys()) {
            for (String value : request.queryParams().values(key)) {
                builder.addQueryParameter(key, value);
            }
        }
        return builder.build();
    }

    /**
     * Indicates whether the HTTP method requires a request body to satisfy OkHttp validation.
     *
     * @param method the HTTP verb
     * @return {@code true} when OkHttp mandates a body for the method
     */
    private boolean requiresBody(HttpMethod method) {
        return method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH;
    }

    /**
     * Converts the OpenAI {@link HttpRequestBody} into an OkHttp {@link RequestBody}.
     *
     * @param requestBody the OpenAI request body wrapper
     * @return an OkHttp body that streams data to the network
     */
    private RequestBody toRequestBody(HttpRequestBody requestBody) {
        MediaType mediaType = null;
        String contentType = requestBody.contentType();
        if (contentType != null && !contentType.isBlank()) {
            mediaType = MediaType.parse(contentType);
        }
        MediaType finalMediaType = mediaType;
        return new RequestBody() {
            @Override
            public MediaType contentType() {
                return finalMediaType;
            }

            @Override
            public long contentLength() {
                return requestBody.contentLength();
            }

            @Override
            public boolean isOneShot() {
                return !requestBody.repeatable();
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                requestBody.writeTo(sink.outputStream());
            }
        };
    }

    /**
     * Wraps an OkHttp {@link Response} in the OpenAI SDK {@link HttpResponse} abstraction.
     *
     * @param response the OkHttp response received from the service
     * @return a view compatible with the OpenAI SDK
     */
    private HttpResponse toResponse(Response response) {
        okhttp3.Headers okHeaders = response.headers();
        Headers.Builder headersBuilder = Headers.builder();
        for (String name : okHeaders.names()) {
            headersBuilder.put(name, okHeaders.values(name));
        }
        Headers headers = headersBuilder.build();

        return new HttpResponse() {
            @Override
            public int statusCode() {
                return response.code();
            }

            @Override
            public Headers headers() {
                return headers;
            }

            @Override
            public InputStream body() {
                ResponseBody body = response.body();
                if (body == null) {
                    return InputStream.nullInputStream();
                }
                return body.byteStream();
            }

            @Override
            public void close() {
                response.close();
            }
        };
    }

    /**
     * Closes a request body without propagating exceptions to the caller.
     *
     * @param body the request body to close, possibly {@code null}
     */
    private void closeQuietly(HttpRequestBody body) {
        if (body == null) {
            return;
        }
        try {
            body.close();
        } catch (Exception ignored) {
            // Ignore close exceptions.
        }
    }

    /**
     * Creates a new {@link Builder} for configuring {@link OciHttpClient} instances.
     *
     * @return a builder seeded with default configuration
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<String, String> defaultHeaders = new LinkedHashMap<>();
        private BasicAuthenticationDetailsProvider authProvider;
        private Duration timeout;
        private String placeholderBearerToken;
        private HttpLoggingInterceptor.Level logRequestsAndResponsesLevel;
        private boolean requiredAuthProvider = true;

        /**
         * Creates a new builder with empty defaults.
         */
        private Builder() {
        }

        /**
         * Supplies the OCI authentication provider used to sign outgoing requests.
         *
         * @param authProvider the OCI auth provider
         * @return this builder for chaining
         */
        public Builder authProvider(BasicAuthenticationDetailsProvider authProvider) {
            this.authProvider = authProvider;
            return this;
        }


        /**
         * Specifies whether the OCI authentication provider is required for building the client.
         *
         * @param requiredAuthProvider {@code true} if the auth provider is mandatory; {@code false} otherwise
         * @return this builder for chaining
         */
        public Builder requiredAuthProvider(boolean requiredAuthProvider) {
            this.requiredAuthProvider = requiredAuthProvider;
            return this;
        }

        /**
         * Adds the provided headers to the default header set applied to every request.
         *
         * @param headers a map of header names and values; {@code null} entries are ignored
         * @return this builder for chaining
         */
        public Builder defaultHeaders(Map<String, String> headers) {
            if (headers != null) {
                headers.forEach(this::defaultHeader);
            }
            return this;
        }

        /**
         * Adds a single default header that should be included with every request.
         *
         * @param name the header name
         * @param value the header value
         * @return this builder for chaining
         */
        public Builder defaultHeader(String name, String value) {
            if (name != null && value != null && !name.isBlank() && !value.isBlank()) {
                this.defaultHeaders.put(name, value);
            }
            return this;
        }

        /**
         * Configures a uniform timeout that applies to connect, read, write, and call phases.
         *
         * @param timeout the timeout duration to enforce
         * @return this builder for chaining
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Sets the placeholder bearer token that signals Authorization headers to omit when signing.
         *
         * @param token the placeholder token value
         * @return this builder for chaining
         */
        public Builder placeholderBearerToken(String token) {
            this.placeholderBearerToken = token;
            return this;
        }

        /**
         * Controls whether each request/response pair is printed to the console using the provided verbosity.
         *
         * @param level string log level; accepts {@code "info"} for BASIC or {@code "debug"} for BODY
         * @return this builder for chaining
         */
        public Builder logRequestsAndResponses(String level) {
            this.logRequestsAndResponsesLevel = toLoggingLevel(level);
            return this;
        }

        /**
         * Controls whether each request/response pair is printed to the console using the provided verbosity.
         *
         * @param level okhttp logging level to use; {@code null} disables logging
         * @return this builder for chaining
         */
        public Builder logRequestsAndResponses(HttpLoggingInterceptor.Level level) {
            this.logRequestsAndResponsesLevel = level;
            return this;
        }

        /**
         * Builds a new {@link OciHttpClient} instance using the accumulated configuration.
         *
         * @return a configured {@link OciHttpClient}
         * @throws IllegalStateException if required inputs, such as the auth provider, are missing
         */
        public OciHttpClient build() {
            if (requiredAuthProvider && authProvider == null) {
                throw new IllegalStateException("Auth provider must be provided for OCI signing.");
            }

            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            if (timeout != null) {
                builder.connectTimeout(timeout)
                        .readTimeout(timeout)
                        .writeTimeout(timeout)
                        .callTimeout(timeout);
            }

            if (!defaultHeaders.isEmpty()) {
                builder.addInterceptor(new ImmutableHeadersInterceptor(Collections.unmodifiableMap(new LinkedHashMap<>(defaultHeaders))));
            }

            if (authProvider != null) {
                builder.addInterceptor(new OciSignerInterceptor(authProvider));
            }
            if (logRequestsAndResponsesLevel != null) {
                HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(
                        message -> System.out.println("OCI OpenAI HTTP: " + message));
                loggingInterceptor.redactHeader("Authorization");
                loggingInterceptor.redactHeader("Proxy-Authorization");
                loggingInterceptor.setLevel(logRequestsAndResponsesLevel);
                builder.addInterceptor(loggingInterceptor);
            }

            OkHttpClient client = builder.build();
            Dispatcher dispatcher = client.dispatcher();
            dispatcher.setMaxRequestsPerHost(dispatcher.getMaxRequests());
            return new OciHttpClient(client, placeholderBearerToken);
        }

        private static HttpLoggingInterceptor.Level toLoggingLevel(String level) {
            if (level == null || level.isBlank()) {
                return null;
            }
            String normalizedLevel = level.trim().toLowerCase();
            return switch (normalizedLevel) {
                case "info" -> HttpLoggingInterceptor.Level.BASIC;
                case "debug" -> HttpLoggingInterceptor.Level.BODY;
                default -> throw new IllegalArgumentException(
                        "Unsupported log level '" + level + "'. Use \"info\" or \"debug\".");
            };
        }
    }
}
