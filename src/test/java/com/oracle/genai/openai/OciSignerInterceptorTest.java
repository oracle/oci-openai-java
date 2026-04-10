/**
 * Copyright (c) 2025 Oracle and/or its affiliates.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */
package com.oracle.genai.openai;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;

import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSink;

class OciSignerInterceptorTest {
    @Test
    void interceptBuffersOneShotBodyBeforeSigning() throws Exception {
        byte[] payload = "multipart-payload".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Request request = new Request.Builder()
                .url("https://example.com/v1/files")
                .post(new OneShotRequestBody(payload, "multipart/form-data; boundary=test-boundary"))
                .build();

        CapturingChain chain = new CapturingChain(request);
        OciSignerInterceptor interceptor = new OciSignerInterceptor(testAuthProvider());

        Response response = interceptor.intercept(chain);
        response.close();

        Request forwardedRequest = chain.forwardedRequest;
        assertNotNull(forwardedRequest);
        assertEquals("multipart/form-data; boundary=test-boundary", forwardedRequest.body().contentType().toString());
        assertEquals(payload.length, forwardedRequest.body().contentLength());
        assertArrayEquals(payload, bodyBytes(forwardedRequest.body()));
    }

    private static SimpleAuthenticationDetailsProvider testAuthProvider() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        byte[] privateKey = pemEncodedPrivateKey(keyPair);
        return SimpleAuthenticationDetailsProvider.builder()
                .tenantId("ocid1.tenancy.oc1..test")
                .userId("ocid1.user.oc1..test")
                .fingerprint("11:22:33:44")
                .privateKeySupplier(() -> new ByteArrayInputStream(privateKey))
                .build();
    }

    private static byte[] pemEncodedPrivateKey(KeyPair keyPair) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
                .encodeToString(keyPair.getPrivate().getEncoded());
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + base64
                + "\n-----END PRIVATE KEY-----\n";
        return pem.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }

    private static byte[] bodyBytes(RequestBody body) throws IOException {
        Buffer buffer = new Buffer();
        body.writeTo(buffer);
        return buffer.readByteArray();
    }

    private static final class OneShotRequestBody extends RequestBody {
        private final InputStream inputStream;
        private final MediaType contentType;

        private OneShotRequestBody(byte[] payload, String contentType) {
            this.inputStream = new ByteArrayInputStream(payload);
            this.contentType = MediaType.parse(contentType);
        }

        @Override
        public MediaType contentType() {
            return contentType;
        }

        @Override
        public boolean isOneShot() {
            return true;
        }

        @Override
        public long contentLength() {
            return -1L;
        }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {
            sink.writeAll(okio.Okio.source(inputStream));
        }
    }

    private static final class CapturingChain implements Interceptor.Chain {
        private final Request request;
        private Request forwardedRequest;

        private CapturingChain(Request request) {
            this.request = request;
        }

        @Override
        public Request request() {
            return request;
        }

        @Override
        public Response proceed(Request request) {
            this.forwardedRequest = request;
            return new Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(ResponseBody.create("{}", MediaType.parse("application/json")))
                    .build();
        }

        @Override
        public Connection connection() {
            return null;
        }

        @Override
        public Call call() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int connectTimeoutMillis() {
            return 0;
        }

        @Override
        public Interceptor.Chain withConnectTimeout(int timeout, TimeUnit unit) {
            return this;
        }

        @Override
        public int readTimeoutMillis() {
            return 0;
        }

        @Override
        public Interceptor.Chain withReadTimeout(int timeout, TimeUnit unit) {
            return this;
        }

        @Override
        public int writeTimeoutMillis() {
            return 0;
        }

        @Override
        public Interceptor.Chain withWriteTimeout(int timeout, TimeUnit unit) {
            return this;
        }
    }
}
