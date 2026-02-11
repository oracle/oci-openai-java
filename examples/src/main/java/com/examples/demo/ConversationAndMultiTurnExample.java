/**
 * Copyright (c) 2025 Oracle and/or its affiliates.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */
package com.examples.demo;

import java.time.Duration;
import java.util.Optional;

import com.openai.client.OpenAIClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.oracle.genai.openai.OciOpenAI;

public final class ConversationAndMultiTurnExample {
    private static final String COMPARTMENT_ID = "<YOUR COMPARTMENT_ID>";
    private static final String BASE_URL = "https://inference.generativeai.us-chicago-1.oci.oraclecloud.com/actions/v1";
    private static final String CONVERSATION_STORE_ID = "<YOUR CONVERSATION_STORE_ID>";

    private ConversationAndMultiTurnExample() {
    }

    public static void main(String[] args) throws Exception {
        OpenAIClient client = OciOpenAI.builder()
                .compartmentId(COMPARTMENT_ID)
                .conversationStoreId(CONVERSATION_STORE_ID)
                .authType("security_token")
                .baseUrl(BASE_URL)
                .profile("DEFAULT")
                .timeout(Duration.ofMinutes(2))
                .build();

        try {
            String model = "openai.gpt-4.1";

            Response response1 = client.responses().create(ResponseCreateParams.builder()
                    .model(model)
                    .input("Tell me a three sentence bedtime story about a unicorn.")
                    .previousResponseId(Optional.empty())
                    .store(true)
                    .build());
            System.out.println(response1.output());
            System.out.println("--------------------------------");

            Response resp = client.responses().retrieve(response1._id().asString().get());
            System.out.println(resp);

            Response response2 = client.responses().create(ResponseCreateParams.builder()
                    .model(model)
                    .input("Change the unicorn to a panda.")
                    .previousResponseId(response1._id().asString().get())
                    .store(true)
                    .build());
            System.out.println(response2.output());
            System.out.println("--------------------------------");

            Response response3 = client.responses().create(ResponseCreateParams.builder()
                    .model(model)
                    .input("Change the panda to a Goose")
                    .previousResponseId(response2._id().asString().get())
                    .store(true)
                    .build());
            System.out.println(response3.output());
            System.out.println("--------------------------------");
        } finally {
            client.close();
        }
    }
}
