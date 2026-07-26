package io.github.songrongzhen.easyagent.rag.web;

public record FetchedHtmlDocument(
        String url,
        String host,
        String contentType,
        String html
) {
}
