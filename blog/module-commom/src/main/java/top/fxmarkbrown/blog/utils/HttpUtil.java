package top.fxmarkbrown.blog.utils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public final class HttpUtil {

    private static final int DEFAULT_TIMEOUT_MILLIS = 10000;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(DEFAULT_TIMEOUT_MILLIS))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private HttpUtil() {
    }

    public static String get(String url) {
        return get(url, DEFAULT_TIMEOUT_MILLIS);
    }

    public static String get(String url, int timeoutMillis) {
        return send(url, "GET", null, timeoutMillis, defaultHeaders());
    }

    public static String get(String url, Map<String, String> headers) {
        return send(url, "GET", null, DEFAULT_TIMEOUT_MILLIS, mergeHeaders(defaultHeaders(), headers));
    }

    public static byte[] getBytes(String url) {
        return getBytes(url, DEFAULT_TIMEOUT_MILLIS, defaultHeaders());
    }

    public static byte[] getBytes(String url, int timeoutMillis) {
        return getBytes(url, timeoutMillis, defaultHeaders());
    }

    public static byte[] getBytes(String url, Map<String, String> headers) {
        return getBytes(url, DEFAULT_TIMEOUT_MILLIS, mergeHeaders(defaultHeaders(), headers));
    }

    public static String postJson(String url, String body) {
        return postJson(url, body, DEFAULT_TIMEOUT_MILLIS);
    }

    public static String postJson(String url, String body, int timeoutMillis) {
        return send(url, "POST", body, timeoutMillis, mergeHeaders(defaultHeaders(), Map.of("Content-Type", "application/json")));
    }

    public static String postJson(String url, String body, Map<String, String> headers) {
        return send(url, "POST", body, DEFAULT_TIMEOUT_MILLIS,
                mergeHeaders(mergeHeaders(defaultHeaders(), Map.of("Content-Type", "application/json")), headers));
    }

    public static String postJson(String url, String body, int timeoutMillis, Map<String, String> headers) {
        return send(url, "POST", body, timeoutMillis,
                mergeHeaders(mergeHeaders(defaultHeaders(), Map.of("Content-Type", "application/json")), headers));
    }

    private static byte[] getBytes(String url, int timeoutMillis, Map<String, String> headers) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(Math.max(timeoutMillis, 1000L)));

            headers.forEach(requestBuilder::header);

            HttpResponse<byte[]> response = HTTP_CLIENT.send(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("HTTP request failed with status " + response.statusCode() + ": " + url);
            }
            return response.body();
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("HTTP request failed: " + url, exception);
        }
    }

    private static String send(String url, String method, String body, int timeoutMillis, Map<String, String> headers) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(Math.max(timeoutMillis, 1000L)));

            headers.forEach(requestBuilder::header);

            HttpRequest request = "POST".equalsIgnoreCase(method)
                    ? requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body, StandardCharsets.UTF_8)).build()
                    : requestBuilder.GET().build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("HTTP request failed with status " + response.statusCode() + ": " + url);
            }
            return response.body();
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("HTTP request failed: " + url, exception);
        }
    }

    private static Map<String, String> defaultHeaders() {
        return Map.of(
                "Accept", "application/json",
                "Accept-Encoding", "identity",
                "User-Agent", "FxMarkBrown-Blog/1.0"
        );
    }

    private static Map<String, String> mergeHeaders(Map<String, String> left, Map<String, String> right) {
        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        if (left != null) {
            headers.putAll(left);
        }
        if (right != null) {
            headers.putAll(right);
        }
        return headers;
    }
}
