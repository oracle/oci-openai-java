/**
 * Copyright (c) 2025 Oracle and/or its affiliates.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */
package com.oracle.genai.openai;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.http.client.io.DuplicatableInputStream;
import com.oracle.bmc.http.signing.DefaultRequestSigner;
import com.oracle.bmc.http.signing.RequestSigner;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

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

        DuplicatableInputStream bodyStream = null;
        if (originalRequest.body() != null) {
            okio.Buffer buffer = new okio.Buffer();
            originalRequest.body().writeTo(buffer);
            bodyStream = new CustomDuplicatableInputStream(buffer.readByteArray());
        }

        Map<String, String> signedHeaders = this.signer.signRequest(
                URI.create(originalRequest.url().toString()),
                originalRequest.method(),
                originalRequest.headers().toMultimap(),
                bodyStream
        );

        Request.Builder newRequestBuilder = originalRequest.newBuilder();
        for (Map.Entry<String, String> entry : signedHeaders.entrySet()) {
            newRequestBuilder.header(entry.getKey(), entry.getValue());
        }
        return chain.proceed(newRequestBuilder.build());
    }
}
