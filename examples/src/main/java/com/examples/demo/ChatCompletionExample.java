/**
 * Copyright (c) 2025 Oracle and/or its affiliates.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */
package com.examples.demo;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseInputContent;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseInputText;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseOutputText;
import com.oracle.genai.openai.OciOpenAI;

public final class ChatCompletionExample {

    private static final String COMPARTMENT_ID = "<YOUR COMPARTMENT_ID>";
    private static final String BASE_URL = "https://inference.generativeai.us-chicago-1.oci.oraclecloud.com/openai/v1";
    private static final String CONVERSATION_STORE_ID = "<YOUR CONVERSATION_STORE_ID>";

    private ChatCompletionExample() {
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
        String model = "openai.gpt-4.1";

        try {
            chatCompletion(client, model);
            simplePrompt(client, model);
            personaPrompt(client, model);
            multiTurnChat(client, model);
        } finally {
            client.close();
        }
    }

    private static void chatCompletion(OpenAIClient client, String model) {
        ChatCompletion chat = client.chat().completions().create(ChatCompletionCreateParams.builder()
                .model(model)
                .messages(List.of(
                        ChatCompletionMessageParam.ofSystem(ChatCompletionSystemMessageParam.builder().content("You are a short, friendly assistant.").build()),
                        ChatCompletionMessageParam.ofUser(ChatCompletionUserMessageParam.builder().content("Give me two ideas for a weekend hobby.").build())
                ))
                .build());
        System.out.println("=== Chat completion via client.chat().completions() ===");
        printChatOutputs(chat);
    }

    private static void simplePrompt(OpenAIClient client, String model) {
        Response response = client.responses().create(ResponseCreateParams.builder()
                .model(model)
                .input("List three snack ideas for a road trip.")
                .build());
        System.out.println("=== One-off prompt ===");
        printTextOutputs(response);
    }

    private static void personaPrompt(OpenAIClient client, String model) {
        List<ResponseInputItem> inputs = new ArrayList<>();
        inputs.add(systemMessage("You are a concise travel planner that answers with short bullet points."));
        inputs.add(userMessage("Plan a one-day itinerary for Kyoto with lunch and dinner suggestions."));

        Response response = client.responses().create(ResponseCreateParams.builder()
                .model(model)
                .instructions("Keep answers under 100 words.")
                .input(ResponseCreateParams.Input.ofResponse(inputs))
                .build());
        System.out.println("=== Persona prompt with system + user messages ===");
        printTextOutputs(response);
    }

    private static void multiTurnChat(OpenAIClient client, String model) {
        Response response1 = client.responses().create(ResponseCreateParams.builder()
                .model(model)
                .input("Give me two quick vegetarian dinner ideas.")
                .store(true)
                .build());
        System.out.println("=== Multi-turn chat: turn 1 ===");
        printTextOutputs(response1);

        Optional<String> responseId = response1._id().asString();
        if (responseId.isEmpty()) {
            System.out.println("Response id missing; cannot continue multi-turn example.");
            return;
        }

        Response response2 = client.responses().create(ResponseCreateParams.builder()
                .model(model)
                .previousResponseId(responseId.get())
                .input("For the first idea, give me a shopping list.")
                .store(true)
                .build());
        System.out.println("=== Multi-turn chat: turn 2 ===");
        printTextOutputs(response2);
    }

    private static ResponseInputItem systemMessage(String content) {
        ResponseInputText text = ResponseInputText.builder()
                .text(content)
                .build();

        ResponseInputItem.Message message = ResponseInputItem.Message.builder()
                .role(ResponseInputItem.Message.Role.SYSTEM)
                .content(List.of(ResponseInputContent.ofInputText(text)))
                .build();

        return ResponseInputItem.ofMessage(message);
    }

    private static ResponseInputItem userMessage(String content) {
        ResponseInputText text = ResponseInputText.builder()
                .text(content)
                .build();

        ResponseInputItem.Message message = ResponseInputItem.Message.builder()
                .role(ResponseInputItem.Message.Role.USER)
                .content(List.of(ResponseInputContent.ofInputText(text)))
                .build();

        return ResponseInputItem.ofMessage(message);
    }

    private static void printTextOutputs(Response response) {
        for (ResponseOutputItem item : response.output()) {
            if (!item.isMessage()) {
                continue;
            }

            ResponseOutputMessage message = item.asMessage();
            for (ResponseOutputMessage.Content content : message.content()) {
                if (content.isOutputText()) {
                    ResponseOutputText outputText = content.asOutputText();
                    System.out.println(outputText.text());
                }
            }
        }
        System.out.println();
    }

    private static void printChatOutputs(ChatCompletion chat) {
        chat.choices().forEach(choice -> {
            if (choice.message() != null && choice.message().content() != null) {
                System.out.println(choice.message().content());
            }
        });
        System.out.println();
    }
}
