package io.github.songrongzhen.easyagent.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "easy-agent.rag")
public class EasyAgentRagProperties {

    private boolean enabled = true;

    private StorageType storageType = StorageType.AUTO;

    private PgVector pgVector = new PgVector();

    private Pdf pdf = new Pdf();

    private Embedding embedding = new Embedding();

    public enum StorageType {
        AUTO,
        PGVECTOR,
        PDF,
        IN_MEMORY
    }

    public static class PgVector {
        private boolean enabled = true;
        private String tableName = "easy_agent_vector_store";
        private int dimensions = 1536;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public int getDimensions() { return dimensions; }
        public void setDimensions(int dimensions) { this.dimensions = dimensions; }
    }

    public static class Pdf {
        private boolean enabled = true;
        private String resourcePath = "classpath:knowledge/";
        private int chunkSize = 1000;
        private int chunkOverlap = 200;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getResourcePath() { return resourcePath; }
        public void setResourcePath(String resourcePath) { this.resourcePath = resourcePath; }
        public int getChunkSize() { return chunkSize; }
        public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }
        public int getChunkOverlap() { return chunkOverlap; }
        public void setChunkOverlap(int chunkOverlap) { this.chunkOverlap = chunkOverlap; }
    }

    public static class Embedding {
        private String model = "text-embedding-v3";
        private int dimensions = 1536;
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public int getDimensions() { return dimensions; }
        public void setDimensions(int dimensions) { this.dimensions = dimensions; }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public StorageType getStorageType() { return storageType; }
    public void setStorageType(StorageType storageType) { this.storageType = storageType; }
    public PgVector getPgVector() { return pgVector; }
    public void setPgVector(PgVector pgVector) { this.pgVector = pgVector; }
    public Pdf getPdf() { return pdf; }
    public void setPdf(Pdf pdf) { this.pdf = pdf; }
    public Embedding getEmbedding() { return embedding; }
    public void setEmbedding(Embedding embedding) { this.embedding = embedding; }
}
