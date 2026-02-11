/**
 * Copyright (c) 2025 Oracle and/or its affiliates.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */
package com.oracle.genai.openai;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;

import com.oracle.bmc.http.client.io.DuplicatableInputStream;

/**
 * {@link DuplicatableInputStream} backed by an in-memory byte array so requests can be
 * replayed when the OCI request signer needs a fresh stream instance.
 */
public class CustomDuplicatableInputStream implements DuplicatableInputStream {
    private final byte[] data;

    /**
     * Creates a duplicatable stream backed by the provided byte array.
     *
     * @param data the request payload to store; the array is retained as-is, so callers should avoid mutating it
     */
    public CustomDuplicatableInputStream(byte[] data) {
        this.data = data;
    }

    /**
     * Creates a new {@link InputStream} that reads from a copy of the stored payload bytes.
     *
     * @return a fresh {@link InputStream} positioned at the beginning of the payload
     */
    @Override
    public InputStream duplicate() {
        // Return a new InputStream over a copy of the byte array
        return new ByteArrayInputStream(Arrays.copyOf(data, data.length));
    }
}
