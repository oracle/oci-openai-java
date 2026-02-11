/**
 * Copyright (c) 2025 Oracle and/or its affiliates.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */
package com.examples.demo;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;

import com.openai.client.OpenAIClient;
import com.openai.errors.BadRequestException;
import com.openai.errors.NotFoundException;
import com.openai.errors.UnauthorizedException;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.oracle.genai.openai.OciOpenAI;

public final class BasicExample {

    private static final String COMPARTMENT_ID = "<YOUR COMPARTMENT_ID>";
    private static final String BASE_URL = "https://inference.generativeai.us-chicago-1.oci.oraclecloud.com/actions/v1";
    private static final String SERVICE_ENDPOINT = "https://inference.generativeai.us-chicago-1.oci.oraclecloud.com";
    private static final String REGION = "us-chicago-1";

    private BasicExample() {
    }

    // Traditional OpenAI client initializer (would read API key from env/system
    // properties)
    // OpenAIClient client =
    // OpenAIOkHttpClient.builder().apiKey("your_api_key_here").build();
    //
    // Native OpenAI Client can take a customized endpoint by setting the base_url.
    // Oci OpenAI client also can accept the same arg as the same purpose. Usually
    // this is for the customer who has a dedicated endpoint service.

    // Build client by region; SDK derives the service endpoint automatically.
    @SuppressWarnings("unused")
    private static OpenAIClient initializerByRegion() throws IOException {
        return OciOpenAI.builder()
                .compartmentId(COMPARTMENT_ID)
                .authType("security_token") // local developer auth using OCI session token from config
                .profile("DEFAULT")          // Specify the profile name according with the profile in ~/.oci/config
                .region(REGION)
                .timeout(Duration.ofMinutes(2))
                .build();
    }

    // Build client using a full service endpoint override (SDK appends /openai/v1).
    @SuppressWarnings("unused")
    private static  OpenAIClient initializerByServiceEndpoint() throws IOException {
        return OciOpenAI.builder()
                .compartmentId(COMPARTMENT_ID)
                .authType("security_token") // local developer auth using OCI session token from config
                .profile("DEFAULT")          // Specify the profile name according with the profile in ~/.oci/config
                .serviceEndpoint(SERVICE_ENDPOINT)
                .timeout(Duration.ofMinutes(2))
                .build();
    }

    // Build client with an explicit base URL override; include request/response logging.
    @SuppressWarnings("unused")
    private static OpenAIClient initializerByBaseUrl() throws IOException {
        return OciOpenAI.builder()
                .compartmentId(COMPARTMENT_ID)
                .authType("security_token") // local developer auth using OCI session token from config
                .profile("DEFAULT")          // Specify the profile name according with the profile in ~/.oci/config
                .baseUrl(BASE_URL)
                .timeout(Duration.ofMinutes(2))
                .logRequestsAndResponses("debug")
                .build();
    }

    // Build client for workloads running with instance principal auth.
    @SuppressWarnings("unused")
    private static OpenAIClient initializerByBaseUrlWithOciInstancePrinciple() throws IOException {
        return OciOpenAI.builder()
                .compartmentId(COMPARTMENT_ID)
                .authType("instance_principal") // workload auth via instance principal (dynamic group policies)
                .baseUrl(BASE_URL)
                .timeout(Duration.ofMinutes(2))
                .logRequestsAndResponses("debug")
                .build();
    }

    // Build client for IAM user API key auth via oci_config profile.
    @SuppressWarnings("unused")
    private static OpenAIClient initializerByBaseUrlWithOciIAMUserAPIKey() throws IOException {
        return OciOpenAI.builder()
                .compartmentId(COMPARTMENT_ID)
                .authType("oci_config") // IAM user long-lived key from ~/.oci/config
                .profile("DEFAULT")      // Specify the profile name according with the user in ~/.oci/config
                .baseUrl(BASE_URL)
                .timeout(Duration.ofMinutes(2))
                .logRequestsAndResponses("debug")
                .build();
    }

    @SuppressWarnings("unused")
    private static OpenAIClient initializerByGenerativeAIAPIKey() throws IOException {
        return OciOpenAI.builder()
                .apiKey("<GENERATIVE_AI_API_KEY>")  // Provide the Generative AI Api key
                .region(REGION)
                .timeout(Duration.ofMinutes(2))
                .logRequestsAndResponses("debug")
                .build();
    }

    public static void main(String[] args) throws Exception {

        OpenAIClient client = initializerByBaseUrl();
        try {
            String prompt = "Tell me a three sentence bedtime story about a unicorn.";
            String model = "openai.gpt-4.1";
            Response response = client.responses().create(ResponseCreateParams.builder()
                    .model(model)
                    .store(false)
                    .input(Objects.requireNonNull(prompt, "Prompt must not be null"))
                    .build());

            System.out.println(response.output());
        } catch (NotFoundException e) {
            System.out.println(e.headers());
        } catch (UnauthorizedException e) {
            System.out.println(e.headers());
        } catch (BadRequestException e) {
            System.out.println(e.headers());
        } catch (Exception e) {
            System.out.println(e);
        } finally {
            client.close();
        }
    }
}
