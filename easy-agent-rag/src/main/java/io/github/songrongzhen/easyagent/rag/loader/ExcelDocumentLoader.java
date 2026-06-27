package io.github.songrongzhen.easyagent.rag.loader;

import io.github.songrongzhen.easyagent.rag.store.DocumentChunk;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ExcelDocumentLoader implements DocumentLoader {

    private static final Logger log = LoggerFactory.getLogger(ExcelDocumentLoader.class);

    private final String resourcePath;

    public ExcelDocumentLoader(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    /**
     * 加载资源目录下的 Excel 文档。
     */
    public List<DocumentChunk> load() {
        List<DocumentChunk> allChunks = new ArrayList<>();
        log.info("Excel loader: Starting to load from path: {}", resourcePath);
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            String xlsxPattern = resourcePath + "**/*.xlsx";
            String xlsPattern = resourcePath + "**/*.xls";
            log.info("Excel loader: Search patterns - xlsx: {}, xls: {}", xlsxPattern, xlsPattern);
            
            Resource[] xlsxResources = resolver.getResources(xlsxPattern);
            Resource[] xlsResources = resolver.getResources(xlsPattern);
            log.info("Excel loader: Found {} xlsx files, {} xls files", xlsxResources.length, xlsResources.length);
            
            List<Resource> allResources = new ArrayList<>();
            allResources.addAll(Arrays.asList(xlsxResources));
            allResources.addAll(Arrays.asList(xlsResources));

            if (allResources.isEmpty()) {
                log.info("No Excel files found at path: {}", resourcePath);
                return allChunks;
            }

            for (Resource resource : allResources) {
                String filename = resource.getFilename();
                log.info("Loading Excel document: {}", filename);
                try {
                    List<DocumentChunk> chunks = load(filename, filename, resource.getInputStream());
                    allChunks.addAll(chunks);
                    log.info("Loaded {} chunks from {}", chunks.size(), filename);
                } catch (Exception e) {
                    log.error("Failed to load Excel: {}", filename, e);
                }
            }
        } catch (IOException e) {
            log.error("Failed to resolve Excel resources from path: {}", resourcePath, e);
        }
        log.info("Excel loader: Total loaded {} chunks", allChunks.size());
        return allChunks;
    }

    /**
     * 判断是否支持该 Excel 文件。
     */
    @Override
    public boolean supports(String filename) {
        if (filename == null) {
            return false;
        }
        String lowerFilename = filename.toLowerCase();
        return lowerFilename.endsWith(".xlsx") || lowerFilename.endsWith(".xls");
    }

    /**
     * 从输入流加载 Excel 文档块。
     */
    @Override
    public List<DocumentChunk> load(String documentId, String filename, InputStream inputStream) throws IOException {
        String resolvedDocumentId = documentId != null && !documentId.isBlank() ? documentId : filename;
        return loadExcel(resolvedDocumentId, filename, inputStream);
    }

    private List<DocumentChunk> loadExcel(String documentId, String filename, InputStream inputStream) throws IOException {
        List<DocumentChunk> chunks = new ArrayList<>();
        
        try (Workbook workbook = createWorkbook(inputStream, filename)) {
            
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                String sheetName = sheet.getSheetName();
                
                List<DocumentChunk> sheetChunks = processSheet(sheet, documentId, filename, sheetName, sheetIndex);
                chunks.addAll(sheetChunks);
            }
        }
        return chunks;
    }

    private List<DocumentChunk> processSheet(Sheet sheet, String documentId, String filename, String sheetName, int sheetIndex) {
        List<DocumentChunk> chunks = new ArrayList<>();
        
        int firstRow = sheet.getFirstRowNum();
        int lastRow = sheet.getLastRowNum();
        
        if (lastRow < 0) {
            return chunks;
        }

        Row headerRow = sheet.getRow(firstRow);
        List<String> headers = new ArrayList<>();
        
        if (headerRow != null) {
            for (Cell cell : headerRow) {
                String header = getCellValueAsString(cell);
                headers.add(header != null ? header.trim() : "Column" + cell.getColumnIndex());
            }
        }

        for (int rowIndex = firstRow + 1; rowIndex <= lastRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;

            try {
                DocumentChunk chunk = createChunkFromRow(row, headers, documentId, filename, sheetName, sheetIndex, rowIndex);
                if (chunk != null) {
                    chunks.add(chunk);
                }
            } catch (Exception e) {
                log.warn("Failed to process row {} in sheet {} of Excel: {}", rowIndex, sheetName, filename, e);
            }
        }
        
        return chunks;
    }

    private DocumentChunk createChunkFromRow(Row row, List<String> headers, String documentId, String filename,
                                             String sheetName, int sheetIndex, int rowIndex) {
        StringBuilder content = new StringBuilder();
        Map<String, Object> metadata = new HashMap<>();
        
        metadata.put("documentId", documentId);
        metadata.put("source", filename);
        metadata.put("sheet", sheetName);
        metadata.put("sheetIndex", sheetIndex);
        metadata.put("rowIndex", rowIndex);
        
        boolean hasContent = false;
        
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            String value = getCellValueAsString(cell);
            
            if (value != null && !value.trim().isEmpty()) {
                hasContent = true;
                String header = headers.get(i);
                content.append(header).append("：").append(value.trim()).append("\n");
                metadata.put("col_" + header, value.trim());
            }
        }
        
        if (!hasContent) {
            return null;
        }

        return new DocumentChunk(
                documentId + "-sheet-" + sheetIndex + "-row-" + rowIndex,
                content.toString().trim(),
                filename,
                metadata,
                null
        );
    }

    private Workbook createWorkbook(InputStream is, String filename) throws IOException {
        if (filename.toLowerCase().endsWith(".xlsx")) {
            return new XSSFWorkbook(is);
        } else if (filename.toLowerCase().endsWith(".xls")) {
            return new HSSFWorkbook(is);
        } else {
            throw new IllegalArgumentException("Unsupported Excel format: " + filename);
        }
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return null;
        }
        
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    LocalDateTime dateTime = cell.getLocalDateTimeCellValue();
                    yield dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                } else {
                    double value = cell.getNumericCellValue();
                    if (value == (long) value) {
                        yield String.valueOf((long) value);
                    } else {
                        yield String.valueOf(value);
                    }
                }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception e) {
                    try {
                        yield String.valueOf(cell.getNumericCellValue());
                    } catch (Exception e2) {
                        yield null;
                    }
                }
            }
            default -> null;
        };
    }
}
