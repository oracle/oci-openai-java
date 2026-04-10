/**
 * Copyright (c) 2025 Oracle and/or its affiliates.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */
package com.examples.demo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.openai.client.OpenAIClient;
import com.openai.core.http.HttpResponseFor;
import com.openai.errors.OpenAIServiceException;
import com.openai.models.audio.transcriptions.TranscriptionCreateParams;
import com.openai.models.audio.transcriptions.TranscriptionCreateResponse;
import com.oracle.genai.openai.OciOpenAI;

public final class SpeechToTextExample {

    private static final String COMPARTMENT_ID = "<YOUR COMPARTMENT_ID>";
    private static final String BASE_URL = "https://inference.generativeai.us-chicago-1.oci.oraclecloud.com/openai/v1";
    private static final String AUDIO_SAMPLE_RESOURCE = "audio-sample.wav";
    private static final String MODEL = "openai.gpt-4o-transcribe-diarize";

    private SpeechToTextExample() {
    }

    public static void main(String[] args) throws Exception {
        OpenAIClient client = OciOpenAI.builder()
                .compartmentId(COMPARTMENT_ID)
                .authType("security_token")
                .baseUrl(BASE_URL)
                .profile("DEFAULT")
                .timeout(Duration.ofMinutes(2))
                .logRequestsAndResponses("info")
                .build();

        try {
            try {
                speechToText(client, AUDIO_SAMPLE_RESOURCE, MODEL);
            } catch (OpenAIServiceException e) {
                printServiceException(e);
                throw e;
            }
        } finally {
            client.close();
        }
    }

    private static void speechToText(OpenAIClient client, String audioResource, String model) throws Exception {
        Optional<Path> audioPath = materializeResource(audioResource);
        if (audioPath.isEmpty()) {
            System.out.println("=== Speech-to-text ===");
            System.out.println("Missing example resource: " + audioResource);
            System.out.println();
            return;
        }

        try (HttpResponseFor<TranscriptionCreateResponse> response = client.audio()
                .transcriptions()
                .withRawResponse()
                .create(TranscriptionCreateParams.builder()
                        .model(model)
                        .file(audioPath.get())
                        .build())) {
            System.out.println("=== Speech-to-text ===");
            printOpcRequestId(response);

            TranscriptionCreateResponse transcript = response.parse();
            printUsageJson(transcript);
            printTranscriptJson(transcript);
        }
    }

    private static Optional<Path> materializeResource(String resourceName) throws IOException {
        try (InputStream inputStream = SpeechToTextExample.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (inputStream == null) {
                return Optional.empty();
            }

            String suffix = resourceName.contains(".") ? resourceName.substring(resourceName.lastIndexOf('.')) : ".tmp";
            Path tempFile = Files.createTempFile("oci-openai-audio-sample-", suffix);
            tempFile.toFile().deleteOnExit();
            Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            return Optional.of(tempFile);
        }
    }

    private static void printOpcRequestId(HttpResponseFor<?> response) {
        List<String> values = response.headers().values("opc-request-id");
        if (!values.isEmpty()) {
            System.out.println("opc-request-id: " + values.get(0));
        }
    }

    private static void printUsageJson(TranscriptionCreateResponse transcript) {
        Optional<JsonNode> json = transcript._json().map(value -> value.convert(JsonNode.class));
        if (json.isPresent() && json.get().has("usage")) {
            System.out.println(json.get().get("usage").toPrettyString());
        } else {
            System.out.println("usage: <not returned>");
        }
    }

    private static void printTranscriptJson(TranscriptionCreateResponse transcript) {
        Optional<JsonNode> json = transcript._json().map(value -> value.convert(JsonNode.class));
        if (json.isPresent()) {
            System.out.println(json.get().toPrettyString());
        } else {
            System.out.println(transcript);
        }
        System.out.println();
    }

    private static void printServiceException(OpenAIServiceException e) {
        System.out.println("=== Request failed ===");
        System.out.println("status: " + e.statusCode());

        List<String> requestIds = e.headers().values("opc-request-id");
        if (!requestIds.isEmpty()) {
            System.out.println("opc-request-id: " + requestIds.get(0));
        }

        try {
            JsonNode errorJson = e.body().convert(JsonNode.class);
            System.out.println(errorJson.toPrettyString());
        } catch (RuntimeException ignored) {
            System.out.println(e.getMessage());
        }
        System.out.println();
    }
}
