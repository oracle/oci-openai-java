/**
 * Copyright (c) 2025 Oracle and/or its affiliates.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */
package com.oracle.genai.openai;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.http.client.io.DuplicatableInputStream;
import com.oracle.bmc.http.signing.DefaultRequestSigner;
import com.oracle.bmc.http.signing.RequestSigner;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;

/**
 * OkHttp interceptor that applies OCI request signing to outbound calls made by the OpenAI client.
 */
public class OciSignerInterceptor implements Interceptor {
    private final RequestSigner signer;

    /**
     * Creates an interceptor that signs requests with the given OCI authentication provider.
     *
     * @param provider the OCI auth provider used to produce request signatures
     */
    public OciSignerInterceptor(BasicAuthenticationDetailsProvider provider) {
        this.signer = DefaultRequestSigner.createRequestSigner(provider);
    }

    /**
     * Signs the outbound request and forwards it through the OkHttp chain.
     *
     * @param chain the OkHttp chain that will execute the request
     * @return the service {@link Response}
     * @throws IOException if an I/O error occurs while reading or forwarding the request
     */
    @NotNull
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request originalRequest = chain.request();
        BufferedRequestBody bufferedBody = null;

        if (originalRequest.body() != null) {
            okio.Buffer buffer = new okio.Buffer();
            originalRequest.body().writeTo(buffer);
            bufferedBody = new BufferedRequestBody(
                    buffer.readByteArray(),
                    originalRequest.body().contentType()
            );
        }

        Map<String, List<String>> headersToSign = effectiveHeaders(originalRequest, bufferedBody);
        Map<String, String> signedHeaders = this.signer.signRequest(
                URI.create(originalRequest.url().toString()),
                originalRequest.method(),
                headersToSign,
                bufferedBody != null ? bufferedBody.duplicatableInputStream() : null
        );

        Request.Builder newRequestBuilder = originalRequest.newBuilder();
        if (bufferedBody != null) {
            newRequestBuilder.method(originalRequest.method(), bufferedBody);
            if (bufferedBody.contentType() != null) {
                newRequestBuilder.header("Content-Type", bufferedBody.contentType().toString());
            } else {
                newRequestBuilder.removeHeader("Content-Type");
            }
            newRequestBuilder.header("Content-Length", Long.toString(bufferedBody.contentLength()));
        }
        for (Map.Entry<String, String> entry : signedHeaders.entrySet()) {
            newRequestBuilder.header(entry.getKey(), entry.getValue());
        }
        return chain.proceed(newRequestBuilder.build());
    }

    private static Map<String, List<String>> effectiveHeaders(Request request, BufferedRequestBody bufferedBody) {
        Map<String, List<String>> headers = new LinkedHashMap<>(request.headers().toMultimap());
        if (bufferedBody == null) {
            return headers;
        }

        replaceHeader(headers, "content-length", Long.toString(bufferedBody.contentLength()));
        if (bufferedBody.contentType() != null) {
            replaceHeader(headers, "content-type", bufferedBody.contentType().toString());
        } else {
            removeHeader(headers, "content-type");
        }
        return headers;
    }

    private static void replaceHeader(Map<String, List<String>> headers, String name, String value) {
        removeHeader(headers, name);
        headers.put(name, new ArrayList<>(List.of(value)));
    }

    private static void removeHeader(Map<String, List<String>> headers, String name) {
        String matchedKey = null;
        for (String existing : headers.keySet()) {
            if (existing.equalsIgnoreCase(name)) {
                matchedKey = existing;
                break;
            }
        }
        if (matchedKey != null) {
            headers.remove(matchedKey);
        }
    }

    private static final class BufferedRequestBody extends RequestBody {
        private final byte[] payload;
        private final MediaType contentType;

        private BufferedRequestBody(byte[] payload, MediaType contentType) {
            this.payload = payload;
            this.contentType = contentType;
        }

        private DuplicatableInputStream duplicatableInputStream() {
            return new CustomDuplicatableInputStream(payload);
        }

        @Override
        public MediaType contentType() {
            return contentType;
        }

        @Override
        public long contentLength() {
            return payload.length;
        }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {
            sink.write(payload);
        }
    }
}
