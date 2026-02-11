/**
 * Copyright (c) 2025 Oracle and/or its affiliates.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */
package com.oracle.genai.openai;

import java.io.IOException;

import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SessionTokenAuthenticationDetailsProvider;

/**
 * Utility factory that instantiates OCI {@link BasicAuthenticationDetailsProvider}
 * implementations based on a supplied authentication type string.
 */
public class AuthProviderFactory {
    /**
     * Creates a {@link BasicAuthenticationDetailsProvider} matching the requested authentication type.
     *
     * @param authType the desired authentication mechanism; expected values include {@code security_token},
     *                 {@code oci_config}, {@code instance_principal}, and {@code resource_principal}
     * @param profile the OCI configuration profile name to use when the auth type relies on a config file
     * @return a concrete {@link BasicAuthenticationDetailsProvider} instance ready for use
     * @throws IOException if reading OCI configuration files fails for the given profile
     * @throws IllegalArgumentException if the supplied authentication type is not recognized
     */
    public static BasicAuthenticationDetailsProvider create(String authType, String profile) throws IOException {
        return switch (authType.toLowerCase()) {
            case "security_token" -> new SessionTokenAuthenticationDetailsProvider(profile);
            case "oci_config" -> new ConfigFileAuthenticationDetailsProvider(profile);
            case "instance_principal" -> InstancePrincipalsAuthenticationDetailsProvider.builder().build();
            case "resource_principal" -> ResourcePrincipalAuthenticationDetailsProvider.builder().build();
            default -> throw new IllegalArgumentException("Unsupported auth type: " + authType);
        };
    }
}
