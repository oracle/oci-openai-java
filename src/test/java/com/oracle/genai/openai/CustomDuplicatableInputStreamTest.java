/**
 * Copyright (c) 2025 Oracle and/or its affiliates.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */
package com.oracle.genai.openai;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class CustomDuplicatableInputStreamTest {

    @Test
    void duplicateCreatesIndependentStreams() throws IOException {
        byte[] payload = "payload".getBytes(StandardCharsets.UTF_8);
        CustomDuplicatableInputStream duplicatableInputStream = new CustomDuplicatableInputStream(payload);

        InputStream first = duplicatableInputStream.duplicate();
        InputStream second = duplicatableInputStream.duplicate();

        // Modify the original backing array to ensure duplicates were defensive copies.
        payload[0] = 'X';

        byte[] firstBytes = first.readAllBytes();
        byte[] secondBytes = second.readAllBytes();

        assertNotSame(first, second);
        assertArrayEquals("payload".getBytes(StandardCharsets.UTF_8), firstBytes);
        assertArrayEquals("payload".getBytes(StandardCharsets.UTF_8), secondBytes);
    }
}
