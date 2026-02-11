/**
 * Copyright (c) 2025 Oracle and/or its affiliates.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */
package com.examples.demo;

import java.time.Duration;
import java.util.Objects;

import com.openai.client.OpenAIClient;
import com.openai.errors.BadRequestException;
import com.openai.errors.NotFoundException;
import com.openai.errors.UnauthorizedException;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.oracle.genai.openai.OciOpenAI;

public final class ConversationStoreExample {

    private static final String COMPARTMENT_ID = "<YOUR COMPARTMENT_ID>";
    private static final String BASE_URL = "https://inference.generativeai.us-chicago-1.oci.oraclecloud.com/actions/v1";
    private static final String CONVERSATION_STORE_ID = "<YOUR CONVERSATION_STORE_ID>";

    private ConversationStoreExample() {
    }

    public static void main(String[] args) throws Exception {

        OpenAIClient client = OciOpenAI.builder()
                .authType("security_token")
                .profile("DEFAULT")
                .baseUrl(BASE_URL)
                .compartmentId(COMPARTMENT_ID)
                .conversationStoreId(CONVERSATION_STORE_ID)
                .timeout(Duration.ofMinutes(2))
                .build();

        try {
            String prompt = "Tell me a three sentence bedtime story about a unicorn.";
            String model = "openai.gpt-4.1";
            Response response = client.responses().create(ResponseCreateParams.builder()
                    .model(model)
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
