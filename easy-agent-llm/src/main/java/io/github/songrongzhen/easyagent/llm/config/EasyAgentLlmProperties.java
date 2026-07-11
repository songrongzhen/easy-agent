package io.github.songrongzhen.easyagent.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "easy-agent.llm")
public class EasyAgentLlmProperties {

    private boolean enabled = true;

    private LlmProvider provider = LlmProvider.AUTO;

    private String model;

    private String apiKey;

    private DashScope dashScope = new DashScope();

    private DeepSeek deepSeek = new DeepSeek();

    private Ollama ollama = new Ollama();

    private OpenAi openAi = new OpenAi();

    private ChatOptions chatOptions = new ChatOptions();

    private ToolExecution toolExecution = new ToolExecution();

    public enum LlmProvider {
        AUTO,
        NONE,
        DASHSCOPE,
        DEEPSEEK,
        OLLAMA,
        OPENAI
    }

    public static class DashScope {
        private String apiKey;
        private String model = "qwen-max";
        private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode";
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }

    public static class DeepSeek {
        private String apiKey;
        private String model = "deepseek-chat";
        private String baseUrl = "https://api.deepseek.com";
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }

    public static class Ollama {
        private String baseUrl = "http://localhost:11434";
        private String model = "llama3";
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
    }

    public static class OpenAi {
        private String apiKey;
        private String model = "gpt-4o";
        private String baseUrl = "https://api.openai.com";
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }

    public static class ChatOptions {
        private double temperature = 0.7;
        private double topP = 1.0;
        private int maxTokens = 4096;
        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }
        public double getTopP() { return topP; }
        public void setTopP(double topP) { this.topP = topP; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    }

    public static class ToolExecution {
        private int maxToolRounds = 5;
        private boolean retryEnabled = true;
        private int retryAttempts = 1;
        private long retryBackoffMillis = 200L;
        private boolean fallbackToChatEnabled = true;
        private int repeatedToolCallThreshold = 3;

        public int getMaxToolRounds() { return maxToolRounds; }
        public void setMaxToolRounds(int maxToolRounds) { this.maxToolRounds = maxToolRounds; }
        public boolean isRetryEnabled() { return retryEnabled; }
        public void setRetryEnabled(boolean retryEnabled) { this.retryEnabled = retryEnabled; }
        public int getRetryAttempts() { return retryAttempts; }
        public void setRetryAttempts(int retryAttempts) { this.retryAttempts = retryAttempts; }
        public long getRetryBackoffMillis() { return retryBackoffMillis; }
        public void setRetryBackoffMillis(long retryBackoffMillis) { this.retryBackoffMillis = retryBackoffMillis; }
        public boolean isFallbackToChatEnabled() { return fallbackToChatEnabled; }
        public void setFallbackToChatEnabled(boolean fallbackToChatEnabled) { this.fallbackToChatEnabled = fallbackToChatEnabled; }
        public int getRepeatedToolCallThreshold() { return repeatedToolCallThreshold; }
        public void setRepeatedToolCallThreshold(int repeatedToolCallThreshold) { this.repeatedToolCallThreshold = repeatedToolCallThreshold; }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public LlmProvider getProvider() { return provider; }
    public void setProvider(LlmProvider provider) { this.provider = provider; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public DashScope getDashScope() { return dashScope; }
    public void setDashScope(DashScope dashScope) { this.dashScope = dashScope; }
    public DeepSeek getDeepSeek() { return deepSeek; }
    public void setDeepSeek(DeepSeek deepSeek) { this.deepSeek = deepSeek; }
    public Ollama getOllama() { return ollama; }
    public void setOllama(Ollama ollama) { this.ollama = ollama; }
    public OpenAi getOpenAi() { return openAi; }
    public void setOpenAi(OpenAi openAi) { this.openAi = openAi; }
    public ChatOptions getChatOptions() { return chatOptions; }
    public void setChatOptions(ChatOptions chatOptions) { this.chatOptions = chatOptions; }
    public ToolExecution getToolExecution() { return toolExecution; }
    public void setToolExecution(ToolExecution toolExecution) { this.toolExecution = toolExecution; }
    
    public LlmProvider inferProvider() {
        if (provider != LlmProvider.AUTO) {
            return provider;
        }
        
        if (model == null || model.isEmpty()) {
            return LlmProvider.NONE;
        }
        
        String modelLower = model.toLowerCase();
        
        if (modelLower.contains("qwen") || modelLower.contains("tongyi")) {
            return LlmProvider.DASHSCOPE;
        } else if (modelLower.contains("deepseek")) {
            return LlmProvider.DEEPSEEK;
        } else if (modelLower.contains("llama") || modelLower.contains("mistral") || modelLower.contains("codellama")) {
            return LlmProvider.OLLAMA;
        } else if (modelLower.contains("gpt") || modelLower.contains("o1") || modelLower.contains("o3")) {
            return LlmProvider.OPENAI;
        }
        
        return LlmProvider.DASHSCOPE;
    }
}
