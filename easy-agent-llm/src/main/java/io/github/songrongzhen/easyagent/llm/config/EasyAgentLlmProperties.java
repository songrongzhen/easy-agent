package io.github.songrongzhen.easyagent.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 模型供应商、请求重试及 Agent 编排配置。
 */
@ConfigurationProperties(prefix = "easy-agent.llm")
public class EasyAgentLlmProperties {

    /**
     * 是否启用当前功能。
     */
    private boolean enabled = true;

    /**
     * 模型供应商选择方式。
     */
    private LlmProvider provider = LlmProvider.AUTO;

    /**
     * 模型名称。
     */
    private String model;

    /**
     * 模型服务凭证。
     */
    private String apiKey;

    /**
     * DashScope 模型供应商配置。
     */
    private DashScope dashScope = new DashScope();

    /**
     * DeepSeek 模型供应商配置。
     */
    private DeepSeek deepSeek = new DeepSeek();

    /**
     * Ollama 模型供应商配置。
     */
    private Ollama ollama = new Ollama();

    /**
     * OpenAI 兼容模型供应商配置。
     */
    private OpenAi openAi = new OpenAi();

    /**
     * 模型生成参数配置。
     */
    private ChatOptions chatOptions = new ChatOptions();

    /**
     * Agent 编排与旧工具重试兼容配置。
     */
    private ToolExecution toolExecution = new ToolExecution();

    /**
     * 独立的模型请求重试配置。
     */
    private RequestRetry requestRetry = new RequestRetry();

    /**
     * 读取独立的模型请求重试配置。
     *
     * @return 当前独立的模型请求重试配置
     */
    public RequestRetry getRequestRetry() {
        return requestRetry;
    }

    /**
     * 设置独立的模型请求重试配置。
     *
     * @param value 待处理值
     */
    public void setRequestRetry(RequestRetry value) {
        requestRetry = value;
    }

    /**
     * 只影响模型请求的重试配置，不重放业务工具调用。
     */
    public static class RequestRetry {

        /**
         * 包含首次执行的总尝试次数。
         */
        private int maxAttempts = 1;

        /**
         * 仅在下次尝试前等待的毫秒数。
         */
        private long backoffMillis = 0;

        /**
         * 读取包含首次执行的总尝试次数。
         *
         * @return 当前注册的工具数量
         */
        public int getMaxAttempts() {
            return maxAttempts;
        }

        /**
         * 设置包含首次执行的总尝试次数。
         *
         * @param value 待处理值
         */
        public void setMaxAttempts(int value) {
            if (value < 1) {
                throw new IllegalArgumentException("request maxAttempts must be positive");
            }
            maxAttempts = value;
        }

        /**
         * 读取仅在下次尝试前等待的毫秒数。
         *
         * @return 当前仅在下次尝试前等待的毫秒数
         */
        public long getBackoffMillis() {
            return backoffMillis;
        }

        /**
         * 设置仅在下次尝试前等待的毫秒数。
         *
         * @param value 待处理值
         */
        public void setBackoffMillis(long value) {
            if (value < 0) {
                throw new IllegalArgumentException("request backoff must be nonnegative");
            }
            backoffMillis = value;
        }
    }

    /**
     * 可选模型供应商。
     */
    public enum LlmProvider {

        /**
         * 根据模型名称选择供应商。
         */
        AUTO,
        /**
         * 不启用实际模型供应商。
         */
        NONE,
        /**
         * 阿里云 DashScope 兼容接口。
         */
        DASHSCOPE,
        /**
         * DeepSeek 兼容接口。
         */
        DEEPSEEK,
        /**
         * Ollama 兼容接口。
         */
        OLLAMA,
        /**
         * OpenAI 兼容接口。
         */
        OPENAI
    }

    /**
     * DashScope 兼容接口的连接配置。
     */
    public static class DashScope {

        /**
         * 模型服务凭证。
         */
        private String apiKey;

        /**
         * 模型名称。
         */
        private String model = "qwen-max";

        /**
         * 模型兼容接口的服务地址。
         */
        private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode";

        /**
         * 读取模型服务凭证。
         *
         * @return 当前模型服务凭证
         */
        public String getApiKey() {
            return apiKey;
        }

        /**
         * 设置模型服务凭证。
         *
         * @param apiKey 模型服务凭证
         */
        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        /**
         * 读取模型名称。
         *
         * @return 当前模型名称
         */
        public String getModel() {
            return model;
        }

        /**
         * 设置模型名称。
         *
         * @param model 模型名称
         */
        public void setModel(String model) {
            this.model = model;
        }

        /**
         * 读取模型兼容接口的服务地址。
         *
         * @return 当前模型兼容接口的服务地址
         */
        public String getBaseUrl() {
            return baseUrl;
        }

        /**
         * 设置模型兼容接口的服务地址。
         *
         * @param baseUrl 模型兼容接口的服务地址
         */
        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    /**
     * DeepSeek 兼容接口的连接配置。
     */
    public static class DeepSeek {

        /**
         * 模型服务凭证。
         */
        private String apiKey;

        /**
         * 模型名称。
         */
        private String model = "deepseek-chat";

        /**
         * 模型兼容接口的服务地址。
         */
        private String baseUrl = "https://api.deepseek.com";

        /**
         * 读取模型服务凭证。
         *
         * @return 当前模型服务凭证
         */
        public String getApiKey() {
            return apiKey;
        }

        /**
         * 设置模型服务凭证。
         *
         * @param apiKey 模型服务凭证
         */
        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        /**
         * 读取模型名称。
         *
         * @return 当前模型名称
         */
        public String getModel() {
            return model;
        }

        /**
         * 设置模型名称。
         *
         * @param model 模型名称
         */
        public void setModel(String model) {
            this.model = model;
        }

        /**
         * 读取模型兼容接口的服务地址。
         *
         * @return 当前模型兼容接口的服务地址
         */
        public String getBaseUrl() {
            return baseUrl;
        }

        /**
         * 设置模型兼容接口的服务地址。
         *
         * @param baseUrl 模型兼容接口的服务地址
         */
        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    /**
     * Ollama 兼容接口的连接配置。
     */
    public static class Ollama {

        /**
         * 模型兼容接口的服务地址。
         */
        private String baseUrl = "http://localhost:11434";

        /**
         * 模型名称。
         */
        private String model = "llama3";

        /**
         * 读取模型兼容接口的服务地址。
         *
         * @return 当前模型兼容接口的服务地址
         */
        public String getBaseUrl() {
            return baseUrl;
        }

        /**
         * 设置模型兼容接口的服务地址。
         *
         * @param baseUrl 模型兼容接口的服务地址
         */
        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        /**
         * 读取模型名称。
         *
         * @return 当前模型名称
         */
        public String getModel() {
            return model;
        }

        /**
         * 设置模型名称。
         *
         * @param model 模型名称
         */
        public void setModel(String model) {
            this.model = model;
        }
    }

    /**
     * OpenAI 兼容接口的连接配置。
     */
    public static class OpenAi {

        /**
         * 模型服务凭证。
         */
        private String apiKey;

        /**
         * 模型名称。
         */
        private String model = "gpt-4o";

        /**
         * 模型兼容接口的服务地址。
         */
        private String baseUrl = "https://api.openai.com";

        /**
         * 读取模型服务凭证。
         *
         * @return 当前模型服务凭证
         */
        public String getApiKey() {
            return apiKey;
        }

        /**
         * 设置模型服务凭证。
         *
         * @param apiKey 模型服务凭证
         */
        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        /**
         * 读取模型名称。
         *
         * @return 当前模型名称
         */
        public String getModel() {
            return model;
        }

        /**
         * 设置模型名称。
         *
         * @param model 模型名称
         */
        public void setModel(String model) {
            this.model = model;
        }

        /**
         * 读取模型兼容接口的服务地址。
         *
         * @return 当前模型兼容接口的服务地址
         */
        public String getBaseUrl() {
            return baseUrl;
        }

        /**
         * 设置模型兼容接口的服务地址。
         *
         * @param baseUrl 模型兼容接口的服务地址
         */
        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    /**
     * 模型生成参数，包括采样策略和最大输出长度。
     */
    public static class ChatOptions {

        /**
         * 模型采样温度。
         */
        private double temperature = 0.7;

        /**
         * 模型核采样概率阈值。
         */
        private double topP = 1.0;

        /**
         * 单次生成的最大 token 数。
         */
        private int maxTokens = 4096;

        /**
         * 读取模型采样温度。
         *
         * @return 当前模型采样温度
         */
        public double getTemperature() {
            return temperature;
        }

        /**
         * 设置模型采样温度。
         *
         * @param temperature 模型采样温度
         */
        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        /**
         * 读取模型核采样概率阈值。
         *
         * @return 当前模型核采样概率阈值
         */
        public double getTopP() {
            return topP;
        }

        /**
         * 设置模型核采样概率阈值。
         *
         * @param topP 模型核采样概率阈值
         */
        public void setTopP(double topP) {
            this.topP = topP;
        }

        /**
         * 读取单次生成的最大 token 数。
         *
         * @return 当前注册的工具数量
         */
        public int getMaxTokens() {
            return maxTokens;
        }

        /**
         * 设置单次生成的最大 token 数。
         *
         * @param maxTokens 单次生成的最大 token 数
         */
        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }
    }

    /**
     * Agent 编排配置与兼容保留的旧工具重试绑定入口。
     */
    public static class ToolExecution {

        /**
         * 最大工具调用轮数。
         */
        private int maxToolRounds = 5;

        /**
         * 兼容保留的旧工具重试开关。
         */
        private boolean retryEnabled = true;

        /**
         * 兼容保留的旧工具总尝试次数。
         */
        private int retryAttempts = 1;

        /**
         * 兼容保留的旧工具重试退避毫秒数。
         */
        private long retryBackoffMillis = 200L;

        /**
         * 模型工具调用请求失败后是否降级为普通对话。
         */
        private boolean fallbackToChatEnabled = true;

        /**
         * 同一工具参数组合触发循环保护的次数阈值。
         */
        private int repeatedToolCallThreshold = 3;

        /**
         * 读取最大工具调用轮数。
         *
         * @return 当前注册的工具数量
         */
        public int getMaxToolRounds() {
            return maxToolRounds;
        }

        /**
         * 设置最大工具调用轮数。
         *
         * @param maxToolRounds 最大工具调用轮数
         */
        public void setMaxToolRounds(int maxToolRounds) {
            this.maxToolRounds = maxToolRounds;
        }

        /**
         * 查询兼容保留的旧工具重试开关。
         *
         * @return 当前兼容保留的旧工具重试开关
         */
        public boolean isRetryEnabled() {
            return retryEnabled;
        }

        /**
         * 设置兼容保留的旧工具重试开关。
         *
         * @param retryEnabled 兼容保留的旧工具重试开关
         */
        public void setRetryEnabled(boolean retryEnabled) {
            this.retryEnabled = retryEnabled;
        }

        /**
         * 读取兼容保留的旧工具总尝试次数。
         *
         * @return 当前注册的工具数量
         */
        public int getRetryAttempts() {
            return retryAttempts;
        }

        /**
         * 设置兼容保留的旧工具总尝试次数。
         *
         * @param retryAttempts 兼容保留的旧工具总尝试次数
         */
        public void setRetryAttempts(int retryAttempts) {
            this.retryAttempts = retryAttempts;
        }

        /**
         * 读取兼容保留的旧工具重试退避毫秒数。
         *
         * @return 当前兼容保留的旧工具重试退避毫秒数
         */
        public long getRetryBackoffMillis() {
            return retryBackoffMillis;
        }

        /**
         * 设置兼容保留的旧工具重试退避毫秒数。
         *
         * @param retryBackoffMillis 兼容保留的旧工具重试退避毫秒数
         */
        public void setRetryBackoffMillis(long retryBackoffMillis) {
            this.retryBackoffMillis = retryBackoffMillis;
        }

        /**
         * 查询模型工具调用请求失败后是否降级为普通对话。
         *
         * @return 当前模型工具调用请求失败后是否降级为普通对话
         */
        public boolean isFallbackToChatEnabled() {
            return fallbackToChatEnabled;
        }

        /**
         * 设置模型工具调用请求失败后是否降级为普通对话。
         *
         * @param fallbackToChatEnabled 模型工具调用请求失败后是否降级为普通对话
         */
        public void setFallbackToChatEnabled(boolean fallbackToChatEnabled) {
            this.fallbackToChatEnabled = fallbackToChatEnabled;
        }

        /**
         * 读取同一工具参数组合触发循环保护的次数阈值。
         *
         * @return 当前注册的工具数量
         */
        public int getRepeatedToolCallThreshold() {
            return repeatedToolCallThreshold;
        }

        /**
         * 设置同一工具参数组合触发循环保护的次数阈值。
         *
         * @param repeatedToolCallThreshold 同一工具参数组合触发循环保护的次数阈值
         */
        public void setRepeatedToolCallThreshold(int repeatedToolCallThreshold) {
            this.repeatedToolCallThreshold = repeatedToolCallThreshold;
        }
    }

    /**
     * 查询是否启用当前功能。
     *
     * @return 当前是否启用当前功能
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用当前功能。
     *
     * @param enabled 是否启用当前功能
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 读取模型供应商选择方式。
     *
     * @return 根据当前配置或模型名称推断的模型供应商
     */
    public LlmProvider getProvider() {
        return provider;
    }

    /**
     * 设置模型供应商选择方式。
     *
     * @param provider 模型供应商选择方式
     */
    public void setProvider(LlmProvider provider) {
        this.provider = provider;
    }

    /**
     * 读取模型名称。
     *
     * @return 当前模型名称
     */
    public String getModel() {
        return model;
    }

    /**
     * 设置模型名称。
     *
     * @param model 模型名称
     */
    public void setModel(String model) {
        this.model = model;
    }

    /**
     * 读取模型服务凭证。
     *
     * @return 当前模型服务凭证
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * 设置模型服务凭证。
     *
     * @param apiKey 模型服务凭证
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * 读取DashScope 模型供应商配置。
     *
     * @return 当前DashScope 模型供应商配置
     */
    public DashScope getDashScope() {
        return dashScope;
    }

    /**
     * 设置DashScope 模型供应商配置。
     *
     * @param dashScope DashScope 模型供应商配置
     */
    public void setDashScope(DashScope dashScope) {
        this.dashScope = dashScope;
    }

    /**
     * 读取DeepSeek 模型供应商配置。
     *
     * @return 当前DeepSeek 模型供应商配置
     */
    public DeepSeek getDeepSeek() {
        return deepSeek;
    }

    /**
     * 设置DeepSeek 模型供应商配置。
     *
     * @param deepSeek DeepSeek 模型供应商配置
     */
    public void setDeepSeek(DeepSeek deepSeek) {
        this.deepSeek = deepSeek;
    }

    /**
     * 读取Ollama 模型供应商配置。
     *
     * @return 当前Ollama 模型供应商配置
     */
    public Ollama getOllama() {
        return ollama;
    }

    /**
     * 设置Ollama 模型供应商配置。
     *
     * @param ollama Ollama 模型供应商配置
     */
    public void setOllama(Ollama ollama) {
        this.ollama = ollama;
    }

    /**
     * 读取OpenAI 兼容模型供应商配置。
     *
     * @return 当前OpenAI 兼容模型供应商配置
     */
    public OpenAi getOpenAi() {
        return openAi;
    }

    /**
     * 设置OpenAI 兼容模型供应商配置。
     *
     * @param openAi OpenAI 兼容模型供应商配置
     */
    public void setOpenAi(OpenAi openAi) {
        this.openAi = openAi;
    }

    /**
     * 读取模型生成参数配置。
     *
     * @return 当前模型生成参数配置
     */
    public ChatOptions getChatOptions() {
        return chatOptions;
    }

    /**
     * 设置模型生成参数配置。
     *
     * @param chatOptions 模型生成参数配置
     */
    public void setChatOptions(ChatOptions chatOptions) {
        this.chatOptions = chatOptions;
    }

    /**
     * 读取Agent 编排与旧工具重试兼容配置。
     *
     * @return 当前Agent 编排与旧工具重试兼容配置
     */
    public ToolExecution getToolExecution() {
        return toolExecution;
    }

    /**
     * 设置Agent 编排与旧工具重试兼容配置。
     *
     * @param toolExecution Agent 编排与旧工具重试兼容配置
     */
    public void setToolExecution(ToolExecution toolExecution) {
        this.toolExecution = toolExecution;
    }

    /**
     * 在供应商为 AUTO 时根据模型名称推断兼容服务。
     *
     * @return 根据当前配置或模型名称推断的模型供应商
     */
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
