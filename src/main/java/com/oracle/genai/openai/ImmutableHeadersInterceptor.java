/**
 * Copyright (c) 2025 Oracle and/or its affiliates.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */
package com.oracle.genai.openai;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Adds immutable headers to every request unless they are already present.
 */
public final class ImmutableHeadersInterceptor implements Interceptor {
    private final Map<String, String> headers;

    /**
     * Creates an interceptor that injects the provided headers into downstream requests.
     *
     * @param headers header name/value pairs to add when the request does not already contain them
     */
    public ImmutableHeadersInterceptor(Map<String, String> headers) {
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
    }

    /**
     * Adds the configured headers to the outgoing request before continuing the chain.
     *
     * @param chain the OkHttp call chain for this request
     * @return the downstream {@link Response}
     * @throws IOException if an I/O error occurs while proceeding with the chain
     */
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request original = chain.request();
        Request.Builder builder = original.newBuilder();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (original.header(entry.getKey()) == null) {
                builder.header(entry.getKey(), entry.getValue());
            }
        }
        return chain.proceed(builder.build());
    }
}
