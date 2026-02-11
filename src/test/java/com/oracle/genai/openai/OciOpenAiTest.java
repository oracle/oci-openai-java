/**
 * Copyright (c) 2025 Oracle and/or its affiliates.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */
package com.oracle.genai.openai;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import com.openai.core.http.Headers;
import com.openai.core.http.QueryParams;
import com.openai.credential.BearerTokenCredential;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;

class OciOpenAITest {

    private static final String TEST_PRIVATE_KEY = """
            -----BEGIN RSA PRIVATE KEY-----
            MIICXQIBAAKBgQDSiMfxC/IeyDllIxptcJZQML6zkdNsb2j6YTGbP23FsHn4TrvH
            YQxfagpN8JxdGo3h4lcs6LxROUlFpq9mLYXjahOiM4T2xyHKsNmOtaPj5UjnW5MK
            ReNjYlPmSAFrsT8TOckYJeOpPrFfyvbLTYD1rELEihAr9QEhVKtBc9pFbwIDAQAB
            AoGBAJB/yuNZtJuGB0awOjJFJRXy7uCmxPrW2LGIxhhtB3W4824G9AEbTfeq+1mV
            PGz2jc0soXK0ZpRFAlJo3lf+BLbbaeM3WU5WqY9pyOAbndEeuYZE7UJadnVuqvT+
            exKJ+OhJaD4fAKUEv5FzZs7uZ6WuvwkyaT+UAljr0kJuXgZ5AkEA9WWGiC8zpJbg
            oHp5GHkEKYfqAzmGVbfTenZH5i735U8iiaPQHRDsRTYlwmRzCROPY1SjsZj9F/6P
            Vn2UKvf4fQJBANuhn51mVkoGEVfhgEErDUiO9pTPmjcoobf/7X6IxdMw7XWVvQGV
            0BKArBB7KBVnEIyMzrqkAfr2tzlvZI7MhVsCQALm43Ni04KDwj5DlIEElVcEY3EM
            UhlbZiXRlkITlhzhFbB4/nIJjDG5VTL6Sx31XEG5c4IbJAsPmJRWQdVMP2UCQQDJ
            3cejGAh/iQwvxefn/fX7lss1A4su332kbOqQvo11Cyd2R+asqlHQb8u2ajvxUAV5
            6YGpMk1PqavGcofuaDS5AkBCX4r7FYEVaW8J8z5LKyQ8XccLdJEZ+1AbMiBBAOx0
            C/ynXWW8SPumCaKTsxnkXo1FscAM9danZJthIQ50ILC6
            -----END RSA PRIVATE KEY-----
            """; // Dummy RSA private key

    private static final String TEST_TENANCY_OCID =
            "ocid1.tenancy.oc1..aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String TEST_USER_OCID =
            "ocid1.user.oc1..aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String TEST_COMPARTMENT_OCID =
            "ocid1.compartment.oc1..aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String TEST_CONVERSATION_STORE_OCID =
            "ocid1.genai.cs.oc1..aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String TEST_FINGERPRINT = "20:3b:13:79:aa:bb:cc:dd:ee:ff:11:22:33:44:55:66";

    private static final BasicAuthenticationDetailsProvider AUTH_PROVIDER =
            SimpleAuthenticationDetailsProvider.builder()
                    .tenantId(TEST_TENANCY_OCID)
                    .userId(TEST_USER_OCID)
                    .fingerprint(TEST_FINGERPRINT)
                    .privateKeySupplier(() -> new ByteArrayInputStream(TEST_PRIVATE_KEY.getBytes(StandardCharsets.UTF_8)))
                    .build();

    @Test
    void buildFailsWhenRegionBaseUrlAndServiceEndpointMissing() {
        OciOpenAI.Builder builder = OciOpenAI.builder()
                .compartmentId(TEST_COMPARTMENT_OCID)
                .conversationStoreId(TEST_CONVERSATION_STORE_OCID)
                .authProvider(AUTH_PROVIDER);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, builder::build);
        assertTrue(
                ex.getMessage()
                        .contains("At least one of region, service_endpoint, or base_url constructor arg must be provided"));
    }

    @Test
    void buildFailsWhenCompartmentMissing() {
        OciOpenAI.Builder builder = OciOpenAI.builder()
                .region("us-ashburn-1")
                .conversationStoreId(TEST_CONVERSATION_STORE_OCID)
                .authProvider(AUTH_PROVIDER);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, builder::build);
        assertTrue(ex.getMessage().contains("Compartment ID must be provided"));
    }

    @Test
    void buildFailsWhenAuthMissing() {
        OciOpenAI.Builder builder = OciOpenAI.builder()
                .region("us-ashburn-1")
                .compartmentId(TEST_COMPARTMENT_OCID)
                .conversationStoreId(TEST_CONVERSATION_STORE_OCID);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, builder::build);
        assertTrue(ex.getMessage().contains("Auth provider or auth type must be provided"));
    }

    @Test
    void buildSuccessWhenAuthMissingButApiKeyProvided() throws IOException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        OpenAIClient client = OciOpenAI.builder()
                .serviceEndpoint("https://custom.example.com")
                .apiKey("TestApiKey")
                .build();
        try {
            ClientOptions options = extractClientOptions(client);
            assertEquals("https://custom.example.com/openai/v1", options.baseUrl());
        } finally {
            client.close();
        }
    }

    @Test
    void buildUsesbaseUrlAndHonorsDefaults() throws Exception {
        Map<String, String> defaultHeaders = new LinkedHashMap<>();
        defaultHeaders.put("X-Test", "123");
        defaultHeaders.put("X-Blank", "");

        Map<String, String> defaultQuery = Map.of("mode", "test");

        OpenAIClient client = OciOpenAI.builder()
                .baseUrl("https://custom.example.com/openai/v1")
                .compartmentId(TEST_COMPARTMENT_OCID)
                .conversationStoreId(TEST_CONVERSATION_STORE_OCID)
                .authProvider(AUTH_PROVIDER)
                .defaultHeaders(defaultHeaders)
                .defaultQueryParams(defaultQuery)
                .timeout(Duration.ofSeconds(5))
                .build();
        try {
            ClientOptions options = extractClientOptions(client);
            assertEquals("https://custom.example.com/openai/v1", options.baseUrl());

            Headers headers = options.headers();
            assertEquals(List.of(TEST_COMPARTMENT_OCID), headers.values("opc-compartment-id"));
            assertEquals(List.of(TEST_CONVERSATION_STORE_OCID), headers.values("opc-conversation-store-id"));
            assertEquals(List.of(TEST_CONVERSATION_STORE_OCID), headers.values("opc-conversation-store-id"));
            assertEquals(List.of("123"), headers.values("X-Test"));
            assertTrue(headers.values("X-Blank").isEmpty(), "Blank header entries should be removed");

            QueryParams params = options.queryParams();
            assertEquals(List.of("test"), params.values("mode"));

            BearerTokenCredential credential = assertInstanceOf(BearerTokenCredential.class, options.credential());
            assertEquals("<NOTUSED>", credential.token());
        } finally {
            client.close();
        }
    }

    @Test
    void buildDerivesRegionalEndpointAndAppliesPlaceholderApiKey() throws Exception {
        OpenAIClient client = OciOpenAI.builder()
                .region("us-ashburn-1")
                .compartmentId(TEST_COMPARTMENT_OCID)
                .conversationStoreId(TEST_CONVERSATION_STORE_OCID)
                .authProvider(AUTH_PROVIDER)
                .build();
        try {
            ClientOptions options = extractClientOptions(client);
            assertEquals(
                    "https://inference.generativeai.us-ashburn-1.oci.oraclecloud.com/openai/v1",
                    options.baseUrl());

            Headers headers = options.headers();
            assertEquals(List.of(TEST_COMPARTMENT_OCID), headers.values("opc-compartment-id"));

            BearerTokenCredential credential = assertInstanceOf(BearerTokenCredential.class, options.credential());
            assertEquals("<NOTUSED>", credential.token());
        } finally {
            client.close();
        }
    }

    @Test
    void buildUsesServiceEndpointWhenProvided() throws Exception {
        OpenAIClient client = OciOpenAI.builder()
                .serviceEndpoint("https://custom.example.com")
                .compartmentId(TEST_COMPARTMENT_OCID)
                .conversationStoreId(TEST_CONVERSATION_STORE_OCID)
                .authProvider(AUTH_PROVIDER)
                .build();
        try {
            ClientOptions options = extractClientOptions(client);
            assertEquals("https://custom.example.com/openai/v1", options.baseUrl());
        } finally {
            client.close();
        }
    }

    @Test
    void buildStripsApiPathFromServiceEndpoint() throws Exception {
        OpenAIClient client = OciOpenAI.builder()
                .serviceEndpoint("https://custom.example.com/openai/v1/")
                .compartmentId(TEST_COMPARTMENT_OCID)
                .conversationStoreId(TEST_CONVERSATION_STORE_OCID)
                .authProvider(AUTH_PROVIDER)
                .build();
        try {
            ClientOptions options = extractClientOptions(client);
            assertEquals("https://custom.example.com/openai/v1", options.baseUrl());
        } finally {
            client.close();
        }
    }

    @Test
    void buildFallsBackToRegionWhenServiceEndpointInvalid() throws Exception {
        OpenAIClient client = OciOpenAI.builder()
                .serviceEndpoint(" / ")
                .region("us-phoenix-1")
                .compartmentId(TEST_COMPARTMENT_OCID)
                .conversationStoreId(TEST_CONVERSATION_STORE_OCID)
                .authProvider(AUTH_PROVIDER)
                .build();
        try {
            ClientOptions options = extractClientOptions(client);
            assertEquals(
                    "https://inference.generativeai.us-phoenix-1.oci.oraclecloud.com/openai/v1",
                    options.baseUrl());
        } finally {
            client.close();
        }
    }

    private ClientOptions extractClientOptions(OpenAIClient client)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method accessor = OpenAIClientImpl.class.getDeclaredMethod("access$getClientOptions$p", OpenAIClientImpl.class);
        accessor.setAccessible(true);
        return (ClientOptions) accessor.invoke(null, client);
    }
}
