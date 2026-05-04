package io.github.songrongzhen.easyagent.rag.loader;

import io.github.songrongzhen.easyagent.rag.store.DocumentChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PdfDocumentLoader {

    private static final Logger log = LoggerFactory.getLogger(PdfDocumentLoader.class);
    private static final int DEFAULT_CHUNK_SIZE = 1000;
    private static final int DEFAULT_CHUNK_OVERLAP = 200;

    private final String resourcePath;
    private final int chunkSize;
    private final int chunkOverlap;

    public PdfDocumentLoader(String resourcePath, int chunkSize, int chunkOverlap) {
        this.resourcePath = resourcePath;
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    public List<DocumentChunk> load() {
        List<DocumentChunk> allChunks = new ArrayList<>();
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(resourcePath + "**/*.pdf");

            if (resources.length == 0) {
                log.info("No PDF files found at path: {}", resourcePath);
                return allChunks;
            }

            for (Resource resource : resources) {
                String filename = resource.getFilename();
                log.info("Loading PDF document: {}", filename);
                try {
                    String text = extractTextFromPdf(resource);
                    List<DocumentChunk> chunks = chunkText(text, filename);
                    allChunks.addAll(chunks);
                    log.info("Loaded {} chunks from {}", chunks.size(), filename);
                } catch (Exception e) {
                    log.error("Failed to load PDF: {}", filename, e);
                }
            }
        } catch (IOException e) {
            log.error("Failed to resolve PDF resources from path: {}", resourcePath, e);
        }
        return allChunks;
    }

    private String extractTextFromPdf(Resource resource) throws IOException {
        try (var document = org.apache.pdfbox.Loader.loadPDF(resource.getContentAsByteArray())) {
            var stripper = new org.apache.pdfbox.text.PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private List<DocumentChunk> chunkText(String text, String source) {
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
                metadata.put("chunkIndex", chunkIndex);
                metadata.put("source", source);
                chunks.add(new DocumentChunk(
                        source + "-chunk-" + chunkIndex,
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
