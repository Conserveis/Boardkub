package th.ac.kmitl.connect.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Google Cloud Storage through its JSON API.
 *
 * <p>No key file is used. On GKE the Pod runs as the Kubernetes ServiceAccount
 * {@code boardkub-backend}; Workload Identity Federation for GKE lets that identity
 * ask the metadata server for a short-lived access token, and IAM allows it to
 * create and delete objects in the media bucket only.
 *
 * <p>Calling the REST API directly (instead of the google-cloud-storage client
 * library) keeps the image small and avoids pulling gRPC and dozens of transitive
 * dependencies into the Trivy scan.
 */
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "gcs")
public class GcsMediaStorage implements MediaStorage {

    private static final String TOKEN_URL =
            "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token";
    private static final String API = "https://storage.googleapis.com";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final String bucket;
    private final String prefix;
    private final String publicBaseUrl;

    private volatile String token;
    private volatile Instant tokenExpiry = Instant.EPOCH;

    public GcsMediaStorage(@Value("${app.storage.gcs.bucket}") String bucket,
                           @Value("${app.storage.gcs.prefix:}") String prefix,
                           @Value("${app.storage.public-base-url:}") String publicBaseUrl) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("app.storage.gcs.bucket (GCS_BUCKET) is required when STORAGE_TYPE=gcs");
        }
        this.bucket = bucket;
        this.prefix = prefix == null || prefix.isBlank() ? "" : prefix.replaceAll("/+$", "") + "/";
        this.publicBaseUrl = (publicBaseUrl == null || publicBaseUrl.isBlank())
                ? API + "/" + bucket
                : publicBaseUrl.replaceAll("/+$", "");
    }

    @Override
    public void put(String objectName, byte[] data, String contentType) {
        // Multipart upload: object metadata (cache headers) and bytes in one request
        String boundary = "boardkub-" + UUID.randomUUID();
        String metadata;
        try {
            metadata = json.writeValueAsString(Map.of(
                    "name", prefix + objectName,
                    "contentType", contentType,
                    // Object names are never reused, so browsers and CDNs may cache forever
                    "cacheControl", "public, max-age=31536000, immutable"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        ByteArrayOutputStream body = new ByteArrayOutputStream(data.length + 512);
        writeAscii(body, "--" + boundary + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n");
        body.writeBytes(metadata.getBytes(StandardCharsets.UTF_8));
        writeAscii(body, "\r\n--" + boundary + "\r\nContent-Type: " + contentType + "\r\n\r\n");
        body.writeBytes(data);
        writeAscii(body, "\r\n--" + boundary + "--\r\n");

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(API + "/upload/storage/v1/b/" + bucket + "/o?uploadType=multipart"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + accessToken())
                .header("Content-Type", "multipart/related; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        HttpResponse<String> res = send(req);
        if (res.statusCode() / 100 != 2) {
            throw new IllegalStateException("Cloud Storage upload failed: HTTP " + res.statusCode());
        }
    }

    @Override
    public void delete(String objectName) {
        String encoded = URLEncoder.encode(prefix + objectName, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(API + "/storage/v1/b/" + bucket + "/o/" + encoded))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + accessToken())
                .DELETE()
                .build();
        HttpResponse<String> res = send(req);
        if (res.statusCode() / 100 != 2 && res.statusCode() != 404) {
            throw new IllegalStateException("Cloud Storage delete failed: HTTP " + res.statusCode());
        }
    }

    @Override
    public String publicUrl(String objectName) {
        return publicBaseUrl + "/" + prefix + objectName;
    }

    /** Short-lived OAuth token from the GKE metadata server, cached until shortly before expiry. */
    private String accessToken() {
        if (token != null && Instant.now().isBefore(tokenExpiry)) {
            return token;
        }
        synchronized (this) {
            if (token != null && Instant.now().isBefore(tokenExpiry)) {
                return token;
            }
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(TOKEN_URL))
                    .timeout(Duration.ofSeconds(5))
                    .header("Metadata-Flavor", "Google")
                    .GET()
                    .build();
            HttpResponse<String> res = send(req);
            if (res.statusCode() != 200) {
                throw new IllegalStateException("Metadata server returned HTTP " + res.statusCode()
                        + " — is Workload Identity set up for the boardkub-backend ServiceAccount?");
            }
            try {
                JsonNode n = json.readTree(res.body());
                token = n.get("access_token").asText();
                tokenExpiry = Instant.now().plusSeconds(Math.max(60, n.get("expires_in").asLong() - 120));
            } catch (IOException e) {
                throw new IllegalStateException("Cannot parse metadata token response", e);
            }
            return token;
        }
    }

    private HttpResponse<String> send(HttpRequest req) {
        try {
            return http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException("Cloud Storage request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", e);
        }
    }

    private static void writeAscii(ByteArrayOutputStream out, String s) {
        out.writeBytes(s.getBytes(StandardCharsets.US_ASCII));
    }
}
