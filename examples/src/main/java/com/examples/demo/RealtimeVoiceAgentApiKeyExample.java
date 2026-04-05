/**
 * Copyright (c) 2025 Oracle and/or its affiliates.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */
package com.examples.demo;

import static java.nio.charset.StandardCharsets.US_ASCII;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Realtime Voice Agent example with API key authentication.
 *
 * <p>Connects via WebSocket, sends a WAV audio file, prints xAI's transcription
 * and voice response, and saves the response audio to response.wav.
 *
 * <p>Setup:
 * <pre>
 * 1. Update ENDPOINT, MODEL, API_KEY below
 * 2. Place a 24kHz PCM16 mono WAV file in the project
 * 3. mvn compile -pl examples
 * 4. mvn exec:java -pl examples
 *      -Dexec.mainClass="com.examples.demo.RealtimeVoiceAgentApiKeyExample"
 *      -Dexec.args="path/to/audio.wav"
 * </pre>
 */
public final class RealtimeVoiceAgentApiKeyExample {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int WAV_HEADER_SIZE = 44;
    private static final int CHUNK_SIZE = 3200;
    private static final int CHUNK_DELAY_MS = 50;
    private static final int RESPONSE_TIMEOUT_SECONDS = 30;
    private static final String OUTPUT_WAV = "response.wav";
    private static final int SAMPLE_RATE = 24000;

    private static final String ENDPOINT =
            "wss://inference.generativeai.us-chicago-1.oci.oraclecloud.com";
    private static final String MODEL = "xai.grok-voice-agent";
    private static final String API_KEY = "<YOUR_API_KEY>";

    private final List<String> receivedEvents = new CopyOnWriteArrayList<>();
    private final List<String> transcriptFragments = new ArrayList<>();
    private final List<String> audioFragments = new ArrayList<>();
    private volatile String inputTranscript;

    private RealtimeVoiceAgentApiKeyExample() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println(
                    "Usage: RealtimeVoiceAgentApiKeyExample <path-to-wav-file>");
            System.exit(1);
        }
        new RealtimeVoiceAgentApiKeyExample().run(args[0]);
    }

    private void run(String wavFilePath) throws Exception {
        System.out.println("Realtime Voice Agent — API Key Auth");

        String wsUrl = ENDPOINT + "/openai/v1/realtime?model=" + MODEL;
        URI uri = URI.create(wsUrl);
        System.out.println("Connecting to: " + wsUrl);

        WebSocket.Builder wsBuilder = HttpClient.newHttpClient().newWebSocketBuilder();
        wsBuilder.header("Authorization", "Bearer " + API_KEY);

        WebSocket ws = wsBuilder.buildAsync(uri, new RealtimeListener()).join();
        waitForEvent("conversation.created", 10);
        System.out.println("Session created");

        byte[] wavBytes = Files.readAllBytes(Path.of(wavFilePath));
        byte[] pcmBytes = Arrays.copyOfRange(wavBytes, WAV_HEADER_SIZE, wavBytes.length);
        String audioBase64 = Base64.getEncoder().encodeToString(pcmBytes);

        int offset = 0;
        int chunkCount = 0;
        while (offset < audioBase64.length()) {
            int end = Math.min(offset + CHUNK_SIZE, audioBase64.length());
            String chunk = audioBase64.substring(offset, end);
            ws.sendText(
                    "{\"type\":\"input_audio_buffer.append\",\"audio\":\""
                            + chunk + "\"}",
                    true).join();
            offset = end;
            chunkCount++;
            Thread.sleep(CHUNK_DELAY_MS);
        }
        System.out.println("Streamed " + chunkCount + " audio chunks ("
                + pcmBytes.length + " bytes)");

        Thread.sleep(500);
        ws.sendText("{\"type\":\"input_audio_buffer.commit\"}", true).join();
        ws.sendText(
                "{\"type\":\"response.create\",\"response\":"
                        + "{\"modalities\":[\"text\",\"audio\"]}}",
                true).join();
        System.out.println("Sent commit + response.create");

        waitForEvent("conversation.item.input_audio_transcription.completed",
                RESPONSE_TIMEOUT_SECONDS);
        System.out.println("Input transcription: " + inputTranscript);

        waitForEvent("response.output_audio_transcript.delta",
                RESPONSE_TIMEOUT_SECONDS);
        System.out.println("Voice response: "
                + String.join("", transcriptFragments));

        waitForEvent("response.output_audio.delta", RESPONSE_TIMEOUT_SECONDS);
        System.out.println("Received " + audioFragments.size()
                + " audio fragments");

        writeResponseAudio();

        ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
        System.out.println("Connection closed");
    }

    private void writeResponseAudio() throws Exception {
        ByteArrayOutputStream pcmOut = new ByteArrayOutputStream();
        for (String fragment : audioFragments) {
            pcmOut.write(Base64.getDecoder().decode(fragment));
        }
        byte[] pcmData = pcmOut.toByteArray();
        try (FileOutputStream fos = new FileOutputStream(OUTPUT_WAV)) {
            ByteBuffer header = ByteBuffer.allocate(WAV_HEADER_SIZE)
                    .order(ByteOrder.LITTLE_ENDIAN);
            int dataSize = pcmData.length;
            header.put("RIFF".getBytes(US_ASCII)).putInt(36 + dataSize)
                    .put("WAVE".getBytes(US_ASCII));
            header.put("fmt ".getBytes(US_ASCII)).putInt(16)
                    .putShort((short) 1).putShort((short) 1);
            header.putInt(SAMPLE_RATE).putInt(SAMPLE_RATE * 2)
                    .putShort((short) 2).putShort((short) 16);
            header.put("data".getBytes(US_ASCII)).putInt(dataSize);
            fos.write(header.array());
            fos.write(pcmData);
        }
        System.out.println("Saved response audio to " + OUTPUT_WAV
                + " (" + pcmData.length + " bytes)");
    }

    private void waitForEvent(String eventType, int timeoutSeconds)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L);
        while (System.currentTimeMillis() < deadline) {
            for (String event : receivedEvents) {
                if (event.contains("\"type\":\"" + eventType + "\"")) {
                    return;
                }
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException(
                "Timed out waiting for '" + eventType + "'");
    }

    private class RealtimeListener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            System.out.println("WebSocket connected");
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket,
                CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                handleMessage(buffer.toString());
                buffer.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket,
                int statusCode, String reason) {
            System.out.println("WebSocket closed: " + statusCode);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            System.err.println("WebSocket error: " + error.getMessage());
        }

        private void handleMessage(String message) {
            receivedEvents.add(message);
            try {
                JsonNode node = MAPPER.readTree(message);
                String type = node.has("type")
                        ? node.get("type").asText() : "unknown";
                switch (type) {
                    case "conversation.created":
                        break;
                    case "conversation.item.input_audio_transcription.completed":
                        if (node.has("transcript")) {
                            inputTranscript = node.get("transcript").asText();
                        }
                        break;
                    case "response.output_audio_transcript.delta":
                        if (node.has("delta")) {
                            transcriptFragments.add(
                                    node.get("delta").asText());
                        }
                        break;
                    case "response.output_audio.delta":
                        if (node.has("delta")) {
                            audioFragments.add(node.get("delta").asText());
                        }
                        break;
                    case "error":
                        System.err.println("Server error: " + message);
                        break;
                    default:
                        break;
                }
            } catch (JsonProcessingException e) {
                System.err.println("Failed to parse: " + message);
            }
        }
    }
}
