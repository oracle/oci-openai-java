/**
 * Copyright (c) 2025 Oracle and/or its affiliates.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */
package com.oracle.genai.openai;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import com.openai.core.http.Headers;
import com.openai.core.http.QueryParams;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import okhttp3.logging.HttpLoggingInterceptor;
import org.jetbrains.annotations.NotNull;

/**
 * Factory for creating {@link OpenAIClient} instances that talk to OCI's
 * Generative AI endpoints.
 */
public final class OciOpenAI {
    private static final String BASE_URL_TEMPLATE = "https://inference.generativeai.%s.oci.oraclecloud.com/%s";
    private static final String API_VERSION_PATH = "openai/v1";
    private static final String OPC_COMPARTMENT_ID_HEADER = "opc-compartment-id";
    private static final String COMPARTMENT_ID_HEADER = "CompartmentId";
    private static final String OCI_PLACEHOLDER_API_KEY = "<NOTUSED>";
    private static final String CONVERSATION_STORE_ID_HEADER = "opc-conversation-store-id";

    private OciOpenAI() {
    }

    /**
     * Creates a new {@link Builder} for constructing {@link OpenAIClient} instances.
     *
     * @return a builder initialized with defaults
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Resolves the service base URL using either the explicit overrides or the OCI region.
     *
     * @param region the OCI region short code
     * @param baseUrlOverride a fully-qualified base URL, if provided
     * @param serviceEndpointOverride an OCI service endpoint without the API path, if provided
     * @return the fully-qualified base URL
     * @throws IllegalStateException if none of the inputs provide routing information
     */
    private static String resolveBaseUrl(String region, String serviceEndpointOverride, String baseUrlOverride) {
        if (!isBlank(baseUrlOverride)) {
            return baseUrlOverride;
        }
        String normalizedServiceEndpoint = convertServiceEndpoinToBaseUrl(serviceEndpointOverride);
        if (normalizedServiceEndpoint != null) {
            return normalizedServiceEndpoint;
        }
        if (!isBlank(region)) {
            return String.format(BASE_URL_TEMPLATE, region, API_VERSION_PATH);
        }
        throw new IllegalStateException("Region, base_url, or service_endpoint must be provided one for OciOpenAI client.");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String convertServiceEndpoinToBaseUrl(String serviceEndpoint) {
        if (isBlank(serviceEndpoint)) {
            return null;
        }
        String trimmed = serviceEndpoint.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String sanitized = stripTrailingSlash(trimmed);
        String apiSuffix = "/" + API_VERSION_PATH;
        if (sanitized.endsWith(apiSuffix)) {
            sanitized = stripTrailingSlash(sanitized.substring(0, sanitized.length() - apiSuffix.length()));
        }
        if (sanitized.isEmpty()) {
            return null;
        }
        return sanitized + apiSuffix;
    }

    private static String stripTrailingSlash(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }

    public static final class Builder {
        private String region;
        private String authType;
        private String profile;
        private String compartmentId;
        private String conversationStoreId;
        private Duration timeout;
        private BasicAuthenticationDetailsProvider authProvider;
        private final Map<String, String> defaultHeaders = new LinkedHashMap<>();
        private final Map<String, String> defaultQueryParams = new LinkedHashMap<>();
        private String baseUrl = null;
        private String serviceEndpoint = null;
        private HttpLoggingInterceptor.Level logRequestsAndResponsesLevel;
        private String apiKey;
        /**
         * Constructs a builder seeded with environment defaults.
         */
        private Builder() {
        }

        /**
         * Sets the OCI region used to derive the Generative AI endpoint when no override is provided.
         *
         * @param region the OCI region identifier, for example {@code us-ashburn-1}
         * @return this builder for chaining
         */
        public Builder region(String region) {
            this.region = region;
            return this;
        }

        /**
         * Sets the OCI service endpoint (without the API path) that should back the client. The final base URL becomes
         * {@code serviceEndpoint + "/"} {@link #API_VERSION_PATH}.
         *
         * @param serviceEndpoint the OCI service endpoint host
         * @return this builder for chaining
         */
        public Builder serviceEndpoint(String serviceEndpoint) {
            this.serviceEndpoint = serviceEndpoint;
            return this;
        }

        /**
         * Sets the fully-qualified base URL that should be used for all requests.
         *
         * @param baseUrl the explicit base URL including the API version path
         * @return this builder for chaining
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Specifies the authentication type to use when creating an OCI auth provider.
         *
         * @param authType the auth type string understood by {@link AuthProviderFactory}
         * @return this builder for chaining
         */
        public Builder authType(String authType) {
            this.authType = authType;
            return this;
        }

        /**
         * Sets the OCI configuration profile name used when the auth type relies on config files.
         *
         * @param profile the profile name from the OCI config file
         * @return this builder for chaining
         */
        public Builder profile(String profile) {
            this.profile = profile;
            return this;
        }

        /**
         * Supplies a pre-built {@link BasicAuthenticationDetailsProvider}.
         *
         * @param authProvider the OCI auth provider
         * @return this builder for chaining
         */
        public Builder authProvider(BasicAuthenticationDetailsProvider authProvider) {
            this.authProvider = authProvider;
            return this;
        }

        /**
         * Sets the OCI compartment OCID used when invoking the Generative AI endpoint.
         *
         * @param compartmentId the compartment OCID
         * @return this builder for chaining
         */
        public Builder compartmentId(String compartmentId) {
            this.compartmentId = compartmentId;
            return this;
        }

        /**
         * Sets the OCI conversation store OCID that tags every request issued by the client.
         *
         * @param conversationStoreId the conversation store OCID
         * @return this builder for chaining
         */
        public Builder conversationStoreId(String conversationStoreId) {
            this.conversationStoreId = conversationStoreId;
            return this;
        }

        /**
         * Specifies the network timeout applied to outbound requests made by the client.
         *
         * @param timeout the timeout duration
         * @return this builder for chaining
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Adds a collection of default headers that are applied to every request.
         *
         * @param headers header name/value pairs to include by default
         * @return this builder for chaining
         */
        public Builder defaultHeaders(Map<String, String> headers) {
            if (headers != null) {
                headers.forEach(this::defaultHeader);
            }
            return this;
        }

        /**
         * Adds a single default header that should accompany every request.
         *
         * @param key the header name
         * @param value the header value
         * @return this builder for chaining
         */
        public Builder defaultHeader(String key, String value) {
            if (key != null && value != null) {
                this.defaultHeaders.put(key, value);
            }
            return this;
        }

        /**
         * Adds a set of default query parameters that are appended to every request.
         *
         * @param params parameter name/value pairs to include
         * @return this builder for chaining
         */
        public Builder defaultQueryParams(Map<String, String> params) {
            if (params != null) {
                params.forEach(this::defaultQueryParam);
            }
            return this;
        }

        /**
         * Adds a single default query parameter that should accompany every request.
         *
         * @param key the parameter name
         * @param value the parameter value
         * @return this builder for chaining
         */
        public Builder defaultQueryParam(String key, String value) {
            if (key != null && value != null) {
                this.defaultQueryParams.put(key, value);
            }
            return this;
        }

        /**
         * Enables request/response logging with the specified verbosity.
         *
         * @param level logging level; accepts {@code "info"} for BASIC or {@code "debug"} for BODY
         * @return this builder for chaining
         */
        public Builder logRequestsAndResponses(String level) {
            this.logRequestsAndResponsesLevel = toLoggingLevel(level);
            return this;
        }

        /**
         * Sets the OCI Generative AI API key used for authentication.
         * <p>
         * When an API key is provided, the client will use it for authentication instead of relying on an OCI authentication provider.
         *
         * @param apiKey the OCI Generative AI API key
         * @return this builder for chaining
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Builds an {@link OpenAIClient} configured to call the OCI Generative AI service.
         *
         * @return a ready-to-use {@link OpenAIClient}
         * @throws IOException if OCI configuration files cannot be loaded for the requested auth type
         * @throws IllegalArgumentException if required configuration, such as region or compartment ID, is missing
         */
        public OpenAIClient build() throws IOException {

            inputValidation();

            baseUrl = resolveBaseUrl(region, serviceEndpoint, baseUrl);

            Map<String, String> headers = new LinkedHashMap<>(defaultHeaders);
            headers.computeIfAbsent(COMPARTMENT_ID_HEADER, k -> compartmentId);
            headers.computeIfAbsent(OPC_COMPARTMENT_ID_HEADER, k -> compartmentId);
            headers.computeIfAbsent(CONVERSATION_STORE_ID_HEADER, k -> conversationStoreId);
            headers.entrySet()
                    .removeIf(
                            entry -> entry.getKey() == null || entry.getValue() == null || entry.getValue().isBlank());

            OciHttpClient httpClient = getOciHttpClient(headers);

            ClientOptions.Builder optionsBuilder = ClientOptions.builder()
                    .httpClient(httpClient)
                    .baseUrl(baseUrl);

            if (timeout != null) {
                optionsBuilder.timeout(timeout);
            }

            optionsBuilder.apiKey(isBlank(apiKey) ? OCI_PLACEHOLDER_API_KEY : apiKey);

            if (!headers.isEmpty()) {
                Headers.Builder headersBuilder = Headers.builder();
                headers.forEach(headersBuilder::put);
                optionsBuilder.putAllHeaders(headersBuilder.build());
            }

            if (!defaultQueryParams.isEmpty()) {
                QueryParams.Builder queryBuilder = QueryParams.builder();
                defaultQueryParams.forEach(queryBuilder::put);
                optionsBuilder.putAllQueryParams(queryBuilder.build());
            }

            ClientOptions clientOptions = optionsBuilder.build();
            return new OpenAIClientImpl(clientOptions);
        }

        private @NotNull OciHttpClient getOciHttpClient(Map<String, String> headers) throws IOException {
            OciHttpClient.Builder httpClientBuilder = OciHttpClient.builder()
                    .defaultHeaders(headers)
                    .logRequestsAndResponses(logRequestsAndResponsesLevel);
            if (timeout != null) {
                httpClientBuilder.timeout(timeout);
            }
            httpClientBuilder.placeholderBearerToken(OCI_PLACEHOLDER_API_KEY);
            if (!isBlank(apiKey)) {
                httpClientBuilder.requiredAuthProvider(false);
                return httpClientBuilder.build();
            }
            BasicAuthenticationDetailsProvider provider = authProvider != null
                    ? authProvider
                    : AuthProviderFactory.create(authType, profile);
            httpClientBuilder.authProvider(provider);
            return httpClientBuilder.build();
        }

        private void inputValidation() {
            boolean baseUrlMissing = isBlank(baseUrl);
            boolean serviceEndpointMissing = isBlank(serviceEndpoint);
            boolean regionMissing = isBlank(region);
            if (baseUrlMissing && serviceEndpointMissing && regionMissing) {
                throw new IllegalArgumentException(
                        "At least one of region, service_endpoint, or base_url constructor arg must be provided");
            }
            boolean apiKeyPresent = !isBlank(apiKey);
            if (apiKeyPresent) {
                return;
            }
            if (compartmentId == null || compartmentId.isBlank()) {
                throw new IllegalArgumentException("Compartment ID must be provided for OciOpenAI.");
            }

            if (authProvider == null && (authType == null || authType.isBlank())) {
                throw new IllegalArgumentException("Auth provider or auth type must be provided for OciOpenAI.");
            }
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
