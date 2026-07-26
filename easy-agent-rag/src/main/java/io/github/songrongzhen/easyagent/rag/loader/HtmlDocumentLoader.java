package io.github.songrongzhen.easyagent.rag.loader;

import io.github.songrongzhen.easyagent.rag.store.DocumentChunk;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HtmlDocumentLoader implements DocumentLoader {

    private static final int DEFAULT_CHUNK_SIZE = 1000;
    private static final int DEFAULT_CHUNK_OVERLAP = 200;

    private final int chunkSize;
    private final int chunkOverlap;

    public HtmlDocumentLoader() {
        this(DEFAULT_CHUNK_SIZE, DEFAULT_CHUNK_OVERLAP);
    }

    public HtmlDocumentLoader(int chunkSize, int chunkOverlap) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("HTML chunkSize must be greater than 0");
        }
        if (chunkOverlap < 0) {
            throw new IllegalArgumentException("HTML chunkOverlap must be greater than or equal to 0");
        }
        if (chunkOverlap >= chunkSize) {
            throw new IllegalArgumentException("HTML chunkOverlap must be smaller than chunkSize");
        }
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    /**
     * 判断是否支持该 HTML 文件。
     */
    @Override
    public boolean supports(String filename) {
        if (filename == null) {
            return false;
        }
        String lowerFilename = filename.toLowerCase();
        return lowerFilename.endsWith(".html") || lowerFilename.endsWith(".htm");
    }

    /**
     * 从输入流加载 HTML 文档块。
     */
    @Override
    public List<DocumentChunk> load(String documentId, String filename, InputStream inputStream) throws IOException {
        String html = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        return loadHtml(documentId, filename, html, filename);
    }

    /**
     * 从 HTML 字符串加载文档块。
     */
    public List<DocumentChunk> loadHtml(String documentId, String source, String html, String baseUri) {
        String resolvedDocumentId = documentId != null && !documentId.isBlank() ? documentId : source;
        Document document = Jsoup.parse(html == null ? "" : html, baseUri == null ? "" : baseUri);
        String title = document.title();
        String text = extractKnowledgeText(document);
        return chunkText(text, resolvedDocumentId, source, title);
    }

    private String extractKnowledgeText(Document document) {
        document.select("script, style, noscript, nav, footer, header, aside, form, button, svg").remove();

        StringBuilder text = new StringBuilder();
        appendLine(text, "Title", document.title());
        appendLine(text, "Description", document.select("meta[name=description]").attr("content"));

        Elements headings = document.select("h1, h2, h3");
        for (Element heading : headings) {
            appendLine(text, heading.tagName().toUpperCase(), heading.text());
        }

        Element body = document.body();
        if (body != null) {
            appendLine(text, "Content", body.text());
        }

        Elements links = document.select("a[href]");
        for (Element link : links) {
            String linkText = link.text();
            String href = link.absUrl("href");
            if (href == null || href.isBlank()) {
                href = link.attr("href");
            }
            if (linkText != null && !linkText.isBlank() && href != null && !href.isBlank()) {
                appendLine(text, "Link", linkText.trim() + " - " + href.trim());
            }
        }

        return text.toString().trim();
    }

    private void appendLine(StringBuilder text, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        text.append(label).append(": ").append(value.trim()).append("\n");
    }

    private List<DocumentChunk> chunkText(String text, String documentId, String source, String title) {
        List<DocumentChunk> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }

        int start = 0;
        int chunkIndex = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            String chunkContent = text.substring(start, end).trim();
            if (!chunkContent.isEmpty()) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("documentId", documentId);
                metadata.put("chunkIndex", chunkIndex);
                metadata.put("source", source);
                metadata.put("contentType", "html");
                if (title != null && !title.isBlank()) {
                    metadata.put("title", title);
                }
                chunks.add(new DocumentChunk(
                        documentId + "-chunk-" + chunkIndex,
                        chunkContent,
                        source,
                        metadata,
                        null
                ));
                chunkIndex++;
            }
            start += chunkSize - chunkOverlap;
        }
        return chunks;
    }
}
