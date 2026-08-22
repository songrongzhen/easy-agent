package io.github.songrongzhen.easyagent.rag.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

public class UrlDocumentFetcher {

    private static final int MAX_REDIRECTS = 3;
    private static final int MAX_CONTENT_BYTES = 2 * 1024 * 1024;
    private static final String USER_AGENT = "easy-agent-rag";

    private final Duration timeout;
    private final UrlSafetyValidator urlSafetyValidator;
    private final HttpClient httpClient;

    public UrlDocumentFetcher(Duration timeout, UrlSafetyValidator urlSafetyValidator) {
        this.timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
        this.urlSafetyValidator = urlSafetyValidator;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * 请求并读取 HTML 知识库页面。
     */
    public FetchedHtmlDocument fetch(URI uri) throws IOException {
        URI currentUri = uri;
        for (int redirectCount = 0; redirectCount <= MAX_REDIRECTS; redirectCount++) {
            HttpResponse<InputStream> response = sendRequest(currentUri);
            // ofInputStream() 只有在流被关闭（或读到底）时才归还底层连接；
            // 重定向与校验失败路径若不显式关闭，每次都会泄漏一条连接
            // （http→https 跳转是常见场景，反复抓取会耗尽连接）。
            try (InputStream body = response.body()) {
                int statusCode = response.statusCode();
                if (isRedirect(statusCode)) {
                    currentUri = resolveRedirectUri(currentUri, response);
                    continue;
                }
                validateStatusCode(statusCode, currentUri);
                String contentType = response.headers().firstValue("Content-Type").orElse("");
                validateHtmlContentType(contentType, currentUri);
                String html = readBody(body, resolveCharset(contentType), currentUri);
                return new FetchedHtmlDocument(currentUri.toString(), currentUri.getHost(), contentType, html);
            }
        }
        throw new IOException("HTML knowledge URL redirected too many times: " + uri);
    }

    private HttpResponse<InputStream> sendRequest(URI uri) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (java.net.http.HttpTimeoutException e) {
            throw new IOException("Request html knowledge url timeout after " + timeout + ": " + uri, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request html knowledge url interrupted: " + uri, e);
        }
    }

    private boolean isRedirect(int statusCode) {
        return statusCode == 301 || statusCode == 302 || statusCode == 303 || statusCode == 307 || statusCode == 308;
    }

    private URI resolveRedirectUri(URI currentUri, HttpResponse<InputStream> response) throws IOException {
        Optional<String> location = response.headers().firstValue("Location");
        if (location.isEmpty() || location.get().isBlank()) {
            throw new IOException("HTML knowledge URL redirect missing Location header: " + currentUri);
        }
        URI redirectUri = currentUri.resolve(location.get().trim());
        return urlSafetyValidator.validate(redirectUri.toString());
    }

    private void validateStatusCode(int statusCode, URI uri) throws IOException {
        if (statusCode < 200 || statusCode >= 300) {
            throw new IOException("Request html knowledge url failed with status " + statusCode + ": " + uri);
        }
    }

    private void validateHtmlContentType(String contentType, URI uri) {
        if (contentType == null || contentType.isBlank()) {
            return;
        }
        String lower = contentType.toLowerCase();
        if (lower.contains("text/html") || lower.contains("application/xhtml+xml")) {
            return;
        }
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase();
        if (path.endsWith(".html") || path.endsWith(".htm")) {
            return;
        }
        throw new IllegalArgumentException("Only HTML content is supported for URL knowledge loading: " + contentType);
    }

    private Charset resolveCharset(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return StandardCharsets.UTF_8;
        }
        String[] parts = contentType.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.toLowerCase().startsWith("charset=")) {
                String charsetName = trimmed.substring("charset=".length()).trim();
                try {
                    return Charset.forName(charsetName);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Unsupported HTML charset: " + charsetName, e);
                }
            }
        }
        return StandardCharsets.UTF_8;
    }

    private String readBody(InputStream inputStream, Charset charset, URI uri) throws IOException {
        try (InputStream body = inputStream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = body.read(buffer)) != -1) {
                total += read;
                if (total > MAX_CONTENT_BYTES) {
                    throw new IOException("HTML knowledge URL response is too large: " + uri);
                }
                output.write(buffer, 0, read);
            }
            return output.toString(charset);
        }
    }
}
