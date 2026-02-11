# OCI OpenAI SDK for java

## About

The **OCI OpenAI Java** library provides secure and convenient access to the OpenAI-compatible REST API hosted by **OCI Generative AI Service**.

---

## Table of Contents
- [oci-openai-java](#oci-openai-java)
    - [Table of Contents](#table-of-contents)
    - [Before You Start](#before-you-start)
    - [Installation](#installation)
    - [Examples](#examples)
        - [Signers](#signers)
    - [Contributing](#contributing)
    - [Security](#security)
    - [License](#license)

---

## Before you start

**Important!**

Note that this package, as well as API keys package described below, only supports OpenAI, xAi Grok and Meta LLama models on OCI Generative AI.

Before you start using this package, determine if this is the right option for you.

If you are looking for a seamless way to port your code from an OpenAI compatible endpoint to OCI Generative AI endpoint, and you are currently using OpenAI-style API keys, you might want to use [OCI Generative AI API Keys](https://docs.oracle.com/en-us/iaas/Content/generative-ai/api-keys.htm) instead.

With OCI Generative AI API Keys, use the native `openai` SDK like before. Just update the `base_url`, create API keys in your OCI console, insure the policy granting the key access to generative AI services is present and you are good to go.

- Create an API key in Console: **Generative AI** -> **API Keys**
- Create a security policy: **Identity & Security** -> **Policies**

To authorize a specific API Key
```
allow any-user to use generative-ai-family in compartment <compartment-name> where ALL { request.principal.type='generativeaiapikey', request.principal.id='ocid1.generativeaiapikey.oc1.us-chicago-1....' }
```

To authorize any API Key
```
allow any-user to use generative-ai-family in compartment <compartment-name> where ALL { request.principal.type='generativeaiapikey' }
```

---

## Installation
All providers in this module are distributed as single jar on the Maven Central
Repository. The jar is compiled for JDK 17, and is forward compatible with later
JDK versions. The coordinates for the latest release (pending release to maven central) are:
```xml
<dependency>
    <groupId>com.oracle.genai</groupId>
    <artifactId>oci-openai-java-sdk</artifactId>
    <version>0.1.20</version>
</dependency>
```

## Usage

### Quick Start

Create a drop-in `OpenAIClient` that targets OCI and send your first text generation request.

```java
import java.time.Duration;
import java.util.Objects;

import com.openai.client.OpenAIClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.oracle.genai.openai.OciOpenAI;

public class QuickStart {
    private static final String COMPARTMENT_ID = "<YOUR_COMPARTMENT_OCID>";

    public static void main(String[] args) throws Exception {
        OpenAIClient client = OciOpenAI.builder()
                .compartmentId(COMPARTMENT_ID)
                .authType("oci_config")          // or security_token, instance_principal, resource_principal
                .profile("DEFAULT")              // OCI config profile when using config-based auth
                .region("us-chicago-1")          // or baseUrl/serviceEndpoint override
                .timeout(Duration.ofMinutes(2))
                .logRequestsAndResponses(true)   // optional debug logging
                .build();

        try {
            Response response = client.responses().create(ResponseCreateParams.builder()
                    .model("openai.gpt-4o")
                    .store(false)
                    .input(Objects.requireNonNull("Write a short poem about cloud computing."))
                    .build());

            System.out.println(response.output());
        } finally {
            client.close();
        }
    }
}
```

> - Provide **one** of `region`, `baseUrl`, or `serviceEndpoint` to resolve the OCI Generative AI endpoint.

### Authentication

`OciOpenAI` supports the same OCI IAM flows exposed by the OCI Java SDK through the `authType` value (or you can pass a prebuilt `BasicAuthenticationDetailsProvider` via `authProvider`).

#### 1) OCI Config / Session Token

Use when developing locally with an OCI config file. For session tokens, set `authType` to `security_token`; for long-lived keys use `oci_config`.

```java
OpenAIClient client = OciOpenAI.builder()
        .compartmentId("<COMPARTMENT_OCID>")
        .conversationStoreId("<CONVERSATION_STORE_OCID>")
        .authType("security_token")   // or "oci_config"
        .profile("DEFAULT")
        .region("us-chicago-1")
        .build();
```

#### 2) Resource Principal Authentication

For workloads running in OCI Functions, Container Instances, etc.

```java
OpenAIClient client = OciOpenAI.builder()
        .compartmentId("<COMPARTMENT_OCID>")
        .authType("resource_principal")
        .region("us-chicago-1")
        .build();
```
> Ensure the dynamic group bound to the resource principal has policies to call the Generative AI APIs.

#### 3) Instance Principal Authentication

For code running on OCI Compute instances.

```java
OpenAIClient client = OciOpenAI.builder()
        .compartmentId("<COMPARTMENT_OCID>")
        .authType("instance_principal")
        .region("us-chicago-1")
        .build();
```
> The instance must belong to a dynamic group that grants access to the Generative AI endpoints.

#### 4) Custom Auth Provider

The library supports multiple OCI authentication methods (signers). Choose the one that matches your runtime environment and security posture.

Supported auth providers

- `SessionTokenAuthenticationDetailsProvider` — Uses an OCI session token from your local OCI CLI profile.
- `ResourcePrincipalAuthenticationDetailsProvider` — Uses Resource Principal auth.
- `InstancePrincipalsAuthenticationDetailsProvider` — Uses Instance Principal auth. Best for OCI Compute instances with dynamic group policies.
- `ConfigFileAuthenticationDetailsProvider` — Uses an OCI user API key. Suitable for service accounts/automation where API keys are managed securely.

Minimal examples of constructing each auth type:
```
// 1) Session (local dev; uses ~/.oci/config + session token)
OpenAIClient openAIClient = OciOpenAI.builder().authType("security_token").build();

// 2) Resource Principal (OCI services with RP)
OpenAIClient openAIClient = OciOpenAI.builder().authType("resource_principal").build();

// 3) Instance Principal (OCI Compute)
OpenAIClient openAIClient = OciOpenAI.builder().authType("instance_principal").build();

// 4) User Principal (API key in ~/.oci/config)
OpenAIClient openAIClient = OciOpenAI.builder().authType("oci_config").build();
```

### Client Initialization Parameters

`OciOpenAI.builder()` mirrors the OpenAI Java client while adding OCI routing and headers.

| Parameter | Description | Required |
|-----------|-------------|----------|
| `compartmentId` | OCID sent as `opc-compartment-id`/`CompartmentId` | ✅ |
| `authType` or `authProvider` | Authentication mechanism (`oci_config`, `security_token`, `instance_principal`, `resource_principal`, or a `BasicAuthenticationDetailsProvider`) | ✅ |
| `region` | OCI region short code; auto-derives base URL | ✅<br/>*(unless `baseUrl` or `serviceEndpoint` is set)* |
| `baseUrl` | Fully qualified endpoint override (includes API path) | ❌ |
| `serviceEndpoint` | Service endpoint without API path; `/openai/v1` is appended | ❌ |
| `conversationStoreId` | Optional Conversation Store OCID attached to every call | ❌ |
| `timeout` | Request timeout (applies to HTTP client and client options) | ❌ |
| `logRequestsAndResponses` | Print request/response bodies for debugging | ❌ |

### Base URL and endpoint overrides

```java
// Explicit baseUrl (e.g., dedicated endpoint)
OpenAIClient client = OciOpenAI.builder()
        .compartmentId("<COMPARTMENT_OCID>")
        .authType("security_token")
        .profile("DEFAULT")
        .baseUrl("https://inference.generativeai.us-chicago-1.oci.oraclecloud.com/20231130/openai/v1")
        .build();

// Derive from service endpoint; SDK appends /openai/v1
OpenAIClient client2 = OciOpenAI.builder()
        .compartmentId("<COMPARTMENT_OCID>")
        .authType("security_token")
        .profile("DEFAULT")
        .serviceEndpoint("https://inference.generativeai.us-chicago-1.oci.oraclecloud.com")
        .build();
```

### Error Handling

The OpenAI Java SDK exceptions still apply. Catch `NotFoundException`, `UnauthorizedException`, or `BadRequestException` to inspect headers/diagnostics when OCI rejects a call.

```java
try {
    // call Responses API
} catch (NotFoundException | UnauthorizedException | BadRequestException e) {
    System.out.println(e.headers());
}
```

### Cleanup

`OpenAIClient` implements `AutoCloseable`. Close it when finished to release HTTP resources:

```java
try (OpenAIClient client = OciOpenAI.builder()
        .compartmentId("<COMPARTMENT_OCID>")
        .authType("security_token")
        .region("us-chicago-1")
        .build()) {
    // use client
}
```

---

## Examples
Examples for OCI OpenAI Java SDK can be found [examples/src/main/java/com/examples/demo](examples/src/main/java/com/examples/demo).

---

---

## Third-Party APIs

Developers choosing to distribute a binary implementation of this project are responsible for obtaining and providing all required licenses and copyright notices for the third-party code used in order to ensure compliance with their respective open source licenses.

---

## Contributing

This project welcomes contributions from the community.
Before submitting a pull request, please [review our contribution guide](./CONTRIBUTING.md).

---

## Security

Please consult the [security guide](./SECURITY.md) for our responsible security vulnerability disclosure process.

---


## License

Copyright (c) 2025 Oracle and/or its affiliates.

Released under the Universal Permissive License v1.0 as shown at
[https://oss.oracle.com/licenses/upl/](https://oss.oracle.com/licenses/upl/)