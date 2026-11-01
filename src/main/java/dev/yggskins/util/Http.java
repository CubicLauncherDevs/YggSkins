package dev.yggskins.util;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Cliente HTTP sencillo y bloqueante con reintentos para 429 y 5xx.
 * Pensado para ejecutarse sobre hilos virtuales.
 */
public final class Http {

    public record Response(int status, String body) {
        public boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    public record Bytes(int status, byte[] body) {
        public boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    private final HttpClient client;
    private final Duration timeout;
    private final int maxAttempts;

    public Http(int timeoutSeconds) {
        this(timeoutSeconds, 3);
    }

    public Http(int timeoutSeconds, int maxAttempts) {
        this.timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        this.maxAttempts = Math.max(1, maxAttempts);
        this.client = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public Response get(String url, Map<String, String> headers) throws IOException, InterruptedException {
        HttpResponse<String> response = execute("GET", url, null, null, headers,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Response(response.statusCode(), response.body());
    }

    public Bytes getBytes(String url, Map<String, String> headers) throws IOException, InterruptedException {
        HttpResponse<byte[]> response = execute("GET", url, null, null, headers,
                HttpResponse.BodyHandlers.ofByteArray());
        return new Bytes(response.statusCode(), response.body());
    }

    public Response postJson(String url, String body, Map<String, String> headers) throws IOException, InterruptedException {
        HttpResponse<String> response = execute("POST", url, body.getBytes(StandardCharsets.UTF_8),
                "application/json", headers, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Response(response.statusCode(), response.body());
    }

    public Response postBytes(String url, byte[] body, String contentType, Map<String, String> headers)
            throws IOException, InterruptedException {
        HttpResponse<String> response = execute("POST", url, body, contentType, headers,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Response(response.statusCode(), response.body());
    }

    private <T> HttpResponse<T> execute(String method, String url, byte[] body, String contentType,
                                        Map<String, String> headers, HttpResponse.BodyHandler<T> handler)
            throws IOException, InterruptedException {
        IOException lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(timeout)
                    .header("User-Agent", "YggSkins/1.0")
                    .header("Accept", "application/json, image/png");
            if (headers != null) {
                headers.forEach(builder::header);
            }
            if (body == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                if (contentType != null) {
                    builder.header("Content-Type", contentType);
                }
                builder.method(method, HttpRequest.BodyPublishers.ofByteArray(body));
            }

            try {
                HttpResponse<T> response = client.send(builder.build(), handler);
                if (attempt < maxAttempts && (response.statusCode() == 429 || response.statusCode() >= 500)) {
                    sleep(retryDelay(attempt, response.headers().firstValue("Retry-After").orElse(null)));
                    continue;
                }
                return response;
            } catch (IOException e) {
                lastError = e;
                if (attempt < maxAttempts) {
                    sleep(retryDelay(attempt, null));
                }
            }
        }
        throw lastError != null ? lastError : new IOException("Peticion HTTP fallida: " + url);
    }

    private static long retryDelay(int attempt, String retryAfter) {
        if (retryAfter != null) {
            try {
                return Math.min(10_000L, Long.parseLong(retryAfter.trim()) * 1000L);
            } catch (NumberFormatException ignored) {
                // Ignorado: se usa el backoff exponencial.
            }
        }
        return Math.min(5_000L, 250L * (1L << Math.min(attempt, 5)));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
