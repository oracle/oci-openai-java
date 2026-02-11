/**
 * Copyright (c) 2025 Oracle and/or its affiliates.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */
package com.oracle.genai.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import kotlin.jvm.functions.Function0;
import kotlin.reflect.KClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Connection;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Timeout;

class ImmutableHeadersInterceptorTest {

    @Test
    void addsHeadersThatAreMissing() throws IOException {
        ImmutableHeadersInterceptor interceptor = new ImmutableHeadersInterceptor(Map.of("X-Test", "123"));
        Request request = new Request.Builder().url("https://example.com").build();
        RecordingChain chain = new RecordingChain(request);

        interceptor.intercept(chain);

        Request proceededRequest = chain.getProceededRequest();
        assertEquals("123", proceededRequest.header("X-Test"));
    }

    @Test
    void preservesHeadersThatAlreadyExist() throws IOException {
        ImmutableHeadersInterceptor interceptor = new ImmutableHeadersInterceptor(Map.of("X-Test", "123"));
        Request request = new Request.Builder()
                .url("https://example.com")
                .header("X-Test", "existing")
                .build();
        RecordingChain chain = new RecordingChain(request);

        interceptor.intercept(chain);

        Request proceededRequest = chain.getProceededRequest();
        assertEquals("existing", proceededRequest.header("X-Test"));
        assertNull(chain.getProceededRequest().header("Missing"));
    }

    private static final class RecordingChain implements Interceptor.Chain {
        private final Request original;
        private Request proceeded;

        private RecordingChain(Request original) {
            this.original = original;
        }

        @Override
        public Request request() {
            return original;
        }

        @Override
        public Response proceed(Request request) {
            this.proceeded = request;
            return new Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(ResponseBody.create("", MediaType.get("text/plain")))
                    .build();
        }

        @Override
        public Connection connection() {
            return null;
        }

        @Override
        public Call call() {
            return new Call() {
                @Override
                public @NotNull <T> T tag(@NotNull Class<T> aClass, @NotNull Function0<? extends T> function0) {
                    T existing = original.tag(aClass);
                    return (existing != null) ? existing : function0.invoke();
                }

                @Override
                public @NotNull <T> T tag(@NotNull KClass<T> kClass, @NotNull Function0<? extends T> function0) {
                    T existing = original.tag(kClass);
                    return (existing != null) ? existing : function0.invoke();
                }

                @Override
                public @Nullable <T> T tag(@NotNull Class<? extends T> aClass) {
                    return original.tag(aClass);
                }

                @Override
                public @Nullable <T> T tag(@NotNull KClass<T> kClass) {
                    return original.tag(kClass);
                }

                @Override
                public Request request() {
                    return original;
                }

                @Override
                public Response execute() {
                    throw new UnsupportedOperationException("Not used by test");
                }

                @Override
                public void enqueue(Callback responseCallback) {
                    throw new UnsupportedOperationException("Not used by test");
                }

                @Override
                public void cancel() {
                    // no-op
                }

                @Override
                public boolean isExecuted() {
                    return false;
                }

                @Override
                public boolean isCanceled() {
                    return false;
                }

                @Override
                public Timeout timeout() {
                    return new Timeout();
                }

                @Override
                public Call clone() {
                    return this;
                }
            };
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

        Request getProceededRequest() {
            return proceeded;
        }
    }
}
