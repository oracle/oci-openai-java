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
JDK versions. The coordinates for the latest release are:
```xml
<dependency>
    <groupId>com.oracle.genai</groupId>
    <artifactId>oci-openai-java-sdk</artifactId>
    <version>0.1.13</version>
</dependency>
```

---

## Examples
Examples for OCI OpenAI Java SDK can be found [examples/src/main/java/com/examples/demo](examples/src/main/java/com/examples/demo).

---

### Signers

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