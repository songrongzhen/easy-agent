package io.github.songrongzhen.easyagent.rag.loader;

import io.github.songrongzhen.easyagent.rag.store.DocumentChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PdfDocumentLoader implements DocumentLoader {

    private static final Logger log = LoggerFactory.getLogger(PdfDocumentLoader.class);
    private static final int DEFAULT_CHUNK_SIZE = 1000;
    private static final int DEFAULT_CHUNK_OVERLAP = 200;

    private final String resourcePath;
    private final int chunkSize;
    private final int chunkOverlap;

    public PdfDocumentLoader(String resourcePath, int chunkSize, int chunkOverlap) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("PDF chunkSize must be greater than 0");
        }
        if (chunkOverlap < 0) {
            throw new IllegalArgumentException("PDF chunkOverlap must be greater than or equal to 0");
        }
        if (chunkOverlap >= chunkSize) {
            throw new IllegalArgumentException("PDF chunkOverlap must be smaller than chunkSize");
        }
        this.resourcePath = resourcePath;
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    /**
     * 加载资源目录下的 PDF 文档。
     */
    public List<DocumentChunk> load() {
        List<DocumentChunk> allChunks = new ArrayList<>();
        log.info("PDF loader: Starting to load from path: {}", resourcePath);
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            String searchPattern = resourcePath + "**/*.pdf";
            log.info("PDF loader: Search pattern: {}", searchPattern);
            Resource[] resources = resolver.getResources(searchPattern);
            log.info("PDF loader: Found {} PDF files", resources.length);

            if (resources.length == 0) {
                log.info("No PDF files found at path: {}", resourcePath);
                return allChunks;
            }

            for (Resource resource : resources) {
                String filename = resource.getFilename();
                log.info("Loading PDF document: {}", filename);
                try {
                    List<DocumentChunk> chunks = load(filename, filename, resource.getInputStream());
                    allChunks.addAll(chunks);
                    log.info("Loaded {} chunks from {}", chunks.size(), filename);
                } catch (Exception e) {
                    log.error("Failed to load PDF: {}", filename, e);
                }
            }
        } catch (IOException e) {
            log.error("Failed to resolve PDF resources from path: {}", resourcePath, e);
        }
        log.info("PDF loader: Total loaded {} chunks", allChunks.size());
        return allChunks;
    }

    /**
     * 判断是否支持该 PDF 文件。
     */
    @Override
    public boolean supports(String filename) {
        return filename != null && filename.toLowerCase().endsWith(".pdf");
    }

    /**
     * 从输入流加载 PDF 文档块。
     */
    @Override
    public List<DocumentChunk> load(String documentId, String filename, InputStream inputStream) throws IOException {
        String resolvedDocumentId = documentId != null && !documentId.isBlank() ? documentId : filename;
        String text = extractTextFromPdf(inputStream);
        return chunkText(text, resolvedDocumentId, filename);
    }

    private String extractTextFromPdf(InputStream inputStream) throws IOException {
        try (var document = org.apache.pdfbox.Loader.loadPDF(inputStream.readAllBytes())) {
            var stripper = new org.apache.pdfbox.text.PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private List<DocumentChunk> chunkText(String text, String documentId, String source) {
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
