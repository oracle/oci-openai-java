package com.examples.demo;

import com.openai.client.OpenAIClient;
import com.openai.core.MultipartField;
import com.openai.models.files.FileCreateParams;
import com.openai.models.files.FileListPage;
import com.openai.models.files.FileListParams;
import com.openai.models.files.FileObject;
import com.openai.models.files.FilePurpose;
import com.oracle.genai.openai.OciOpenAI;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Example client that uploads a file to the OpenAI-compatible OCI Generative AI "Files" endpoint
 * (and then lists files) using {@link com.oracle.genai.openai.OciOpenAI}.
 *
 * How to run:
 *
 * 1) Build the SDK once from the repo root (required because the examples depend on it):
 * {@code mvn -DskipTests package}
 *
 * 2) Build the examples module:
 * {@code cd examples}
 * {@code mvn -DskipTests package}
 *
 * 3) Set the required environment variables:
 * {@code export OCI_GENAI_DP_BASE_URL="https://<region-endpoint>/20231130/openai/v1"}
 * {@code export OCI_COMPARTMENT_ID="ocid1.compartment.oc1...."}
 * {@code export OCI_GENAI_PROJECT_ID="ocid1.generativeaiproject.oc1...."}
 *
 * Optional environment variables:
 * {@code export OCI_AUTH_PROFILE="DEFAULT"} (or your local OCI CLI profile)
 * {@code export OCI_OPENAI_AUTH_TYPE="security_token"}
 *
 * 4) Run from your IDE by invoking {@link #main(String[])}.
 *
 * Note: This example expects you have valid local OCI credentials (e.g. via {@code ~/.oci/config}
 * and the selected profile) consistent with the auth type.
 */
public class FileUploadExample {
    // Configuration is read from environment variables to keep this example portable.
    //
    // Required:
    //   OCI_GENAI_DP_BASE_URL            e.g. https://dev.inference.generativeai.us-phoenix-1.oci.oraclecloud.com/20231130/openai/v1
    //   OCI_COMPARTMENT_ID               ocid1.compartment.oc1....
    //   OCI_GENAI_PROJECT_ID             ocid1.generativeaiproject.oc1....
    //
    // Optional:
    //   OCI_AUTH_PROFILE                 (default: DEFAULT)
    //   OCI_OPENAI_AUTH_TYPE             (default: security_token)
    private static final String ENV_BASE_URL = "OCI_GENAI_DP_BASE_URL";
    private static final String ENV_COMPARTMENT_ID = "OCI_COMPARTMENT_ID";
    private static final String ENV_PROJECT_ID = "OCI_GENAI_PROJECT_ID";
    private static final String ENV_PROFILE = "OCI_AUTH_PROFILE";
    private static final String ENV_AUTH_TYPE = "OCI_OPENAI_AUTH_TYPE";

    private final OpenAIClient ociOpenAI;

    public FileUploadExample() throws IOException {
        String baseUrl = requireEnv(ENV_BASE_URL);
        String compartmentId = requireEnv(ENV_COMPARTMENT_ID);
        String projectId = requireEnv(ENV_PROJECT_ID);

        String profile = envOrDefault(ENV_PROFILE, "DEFAULT");
        String authType = envOrDefault(ENV_AUTH_TYPE, "security_token");
        String opcRequestId = "genai_openai_file_upload_example";

        Map<String, String> defaultHeaders = new HashMap<>();
        defaultHeaders.put("opc-compartment-id", compartmentId);
        defaultHeaders.put("opc-request-id", opcRequestId);
        defaultHeaders.put("OpenAi-Project", projectId);

        this.ociOpenAI = OciOpenAI.builder()
                .defaultHeaders(defaultHeaders)
                .baseUrl(baseUrl)
                .compartmentId(compartmentId)
                .authType(authType)
                .profile(profile)
                .logRequestsAndResponses("info")
                .build();
    }

    public void close() {ociOpenAI.close();}


    public FileObject uploadFile(final String filePath) {
        java.io.File file = new java.io.File(filePath);
        FileCreateParams build = FileCreateParams.builder()
                .purpose(FilePurpose.USER_DATA)
                .file(file.toPath())
                .build();
        return ociOpenAI.files().create(build);
    }

    /**
     * Uploads file content from an {@link InputStream} while also providing a filename.
     *
     * @param fileName filename to attach to the multipart part
     * @param inputStream file content
     * @return uploaded file
     */
    public FileObject uploadFile(final String fileName, final InputStream inputStream) {
        System.out.println("Uploading file using the InputStream of file " + fileName);
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(inputStream, "inputStream");

        // Refer to https://github.com/openai/openai-java/issues/284
        FileCreateParams params = FileCreateParams.builder()
                .purpose(FilePurpose.USER_DATA)
                .file(MultipartField.<InputStream>builder()
                        .value(inputStream)
                        .filename(fileName)
                        .build())
                .build();

        return ociOpenAI.files().create(params);
    }

    public void get(final String id) {
        Objects.requireNonNull(id, "The file ID should not be null");
        System.out.println("Retrieving FileObject using the ID " + id);
        FileObject fileObject = ociOpenAI.files().retrieve(id);
        System.out.println("Retrieved file object " + fileObject);
    }

    public void delete (final String id) {
        Objects.requireNonNull(id, "The file ID should not be null");
        System.out.println("Deleting FileObject using the ID " + id);
        ociOpenAI.files().delete(id);
        System.out.println("File with ID " + id + " deleted successfully");
    }

    /**
     * Uploads raw bytes.
     */
    public FileObject uploadFile(final String fileName, final byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        System.out.println("Uploading file using the byte[]. But wrapping byte array with ByteArrayInputStream");

        // Work around to pass file name as the FileCreateParams dont have any method to set the
        // fileName. Not having fileName set will fail the upload
        return uploadFile(fileName, new ByteArrayInputStream(bytes));
    }

    public FileListPage listFiles() {
        FileListPage list = ociOpenAI.files().list(FileListParams.builder().build());
        System.out.println("Total files list is " + list.data().size());
        list.data().forEach(System.out::println);
        return list;
    }

    public static void main(String[] args) throws IOException {
        String path1 = "examples/sample-files/AcceptAccessReview.html";
        String path2 = "examples/sample-files/AcceptHighRisk.html";
        String path3 = "examples/sample-files/AcceptRecommendation.html";

        FileUploadExample fileUploadExample = new FileUploadExample();

        try {
            System.out.println("Uploading (path): " + Path.of(path1).toAbsolutePath());
            FileObject uploaded1 = fileUploadExample.uploadFile(path1);
            System.out.println(uploaded1);

            System.out.println("Uploading (fileName+inputStream): " + Path.of(path2).toAbsolutePath());
            try (InputStream in = Files.newInputStream(Path.of(path2))) {
                FileObject uploaded2 = fileUploadExample.uploadFile(Path.of(path2).getFileName().toString(), in);
                System.out.println(uploaded2);
            }

            try (InputStream in = Files.newInputStream(Path.of(path3))) {
                FileObject uploaded3 = fileUploadExample.uploadFile(Path.of(path3).getFileName().toString(),
                        in.readAllBytes());
                System.out.println(uploaded3);
            }

            FileListPage fileListPage = fileUploadExample.listFiles();
            if (!fileListPage.data().isEmpty()) {
                String id = fileListPage.data().stream().findAny().get().id();
                fileUploadExample.get(id);

                fileListPage.data().forEach(fileObject -> fileUploadExample.delete(fileObject.id()));
            }
        } finally {
            fileUploadExample.close();
        }
    }

    private static String requireEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + key);
        }
        return value;
    }

    private static String envOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }
}
