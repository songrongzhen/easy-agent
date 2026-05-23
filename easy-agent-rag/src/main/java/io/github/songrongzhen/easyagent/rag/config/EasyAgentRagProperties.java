package io.github.songrongzhen.easyagent.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG模块配置
 */
@ConfigurationProperties(prefix = "easy-agent.rag")
public class EasyAgentRagProperties {

    private boolean enabled = true;

    private StorageType storageType = StorageType.AUTO;

    private PgVector pgVector = new PgVector();

    private Pdf pdf = new Pdf();

    private Excel excel = new Excel();

    private Search search = new Search();

    public enum StorageType {
        AUTO,
        PGVECTOR,
        IN_MEMORY
    }

    public enum SearchStrategyType {
        AUTO,
        EMBEDDING,
        COSINE,
        TF_IDF
    }

    public static class Search {
        
        private SearchStrategyType strategy = SearchStrategyType.AUTO;
        
        private Embedding embedding = new Embedding();
        
        private Cosine cosine = new Cosine();
        
        private TfIdf tfIdf = new TfIdf();

        public SearchStrategyType getStrategy() { return strategy; }
        public void setStrategy(SearchStrategyType strategy) { this.strategy = strategy; }
        public Embedding getEmbedding() { return embedding; }
        public void setEmbedding(Embedding embedding) { this.embedding = embedding; }
        public Cosine getCosine() { return cosine; }
        public void setCosine(Cosine cosine) { this.cosine = cosine; }
        public TfIdf getTfIdf() { return tfIdf; }
        public void setTfIdf(TfIdf tfIdf) { this.tfIdf = tfIdf; }
    }

    public static class Embedding {
        
        private boolean enabled = true;
        
        private EmbeddingProvider provider = EmbeddingProvider.AUTO;
        
        private String model = "text-embedding-v3";
        
        private double minSimilarity = 0.3;

        public enum EmbeddingProvider {
            AUTO,
            OLLAMA,
            OPENAI,
            DASHSCOPE
        }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public EmbeddingProvider getProvider() { return provider; }
        public void setProvider(EmbeddingProvider provider) { this.provider = provider; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public double getMinSimilarity() { return minSimilarity; }
        public void setMinSimilarity(double minSimilarity) { this.minSimilarity = minSimilarity; }
    }

    public static class Cosine {
        
        private boolean enabled = true;
        
        private double minSimilarity = 0.1;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public double getMinSimilarity() { return minSimilarity; }
        public void setMinSimilarity(double minSimilarity) { this.minSimilarity = minSimilarity; }
    }

    public static class TfIdf {
        
        private boolean enabled = true;
        
        private double minScore = 0.1;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public double getMinScore() { return minScore; }
        public void setMinScore(double minScore) { this.minScore = minScore; }
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

    public static class Excel {
        private boolean enabled = true;
        private String resourcePath = "classpath:knowledge/";
        
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getResourcePath() { return resourcePath; }
        public void setResourcePath(String resourcePath) { this.resourcePath = resourcePath; }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public StorageType getStorageType() { return storageType; }
    public void setStorageType(StorageType storageType) { this.storageType = storageType; }
    public PgVector getPgVector() { return pgVector; }
    public void setPgVector(PgVector pgVector) { this.pgVector = pgVector; }
    public Pdf getPdf() { return pdf; }
    public void setPdf(Pdf pdf) { this.pdf = pdf; }
    public Excel getExcel() { return excel; }
    public void setExcel(Excel excel) { this.excel = excel; }
    public Search getSearch() { return search; }
    public void setSearch(Search search) { this.search = search; }
}
