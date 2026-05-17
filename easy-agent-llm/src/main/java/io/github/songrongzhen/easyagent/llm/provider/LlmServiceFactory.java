package io.github.songrongzhen.easyagent.llm.provider;

import io.github.songrongzhen.easyagent.llm.client.OpenAiCompatibleClient;
import io.github.songrongzhen.easyagent.llm.config.EasyAgentLlmProperties;
import io.github.songrongzhen.easyagent.llm.service.LlmService;

public class LlmServiceFactory {

    public static LlmService create(EasyAgentLlmProperties properties) {
        EasyAgentLlmProperties.LlmProvider provider = properties.inferProvider();
        
        return switch (provider) {
            case DASHSCOPE -> createDashScope(properties);
            case DEEPSEEK -> createDeepSeek(properties);
            case OLLAMA -> createOllama(properties);
            case OPENAI -> createOpenAi(properties);
            case NONE, AUTO -> new NoOpLlmService();
        };
    }

    private static LlmService createDashScope(EasyAgentLlmProperties properties) {
        EasyAgentLlmProperties.DashScope ds = properties.getDashScope();
        String apiKey = getApiKey(properties, ds.getApiKey());
        String model = getModel(properties, ds.getModel());
        
        OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                ds.getBaseUrl(), apiKey, model, "dashscope", properties.getChatOptions()
        );
        return new OpenAiCompatibleLlmService(client);
    }

    private static LlmService createDeepSeek(EasyAgentLlmProperties properties) {
        EasyAgentLlmProperties.DeepSeek ds = properties.getDeepSeek();
        String apiKey = getApiKey(properties, ds.getApiKey());
        String model = getModel(properties, ds.getModel());
        
        OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                ds.getBaseUrl(), apiKey, model, "deepseek", properties.getChatOptions()
        );
        return new OpenAiCompatibleLlmService(client);
    }

    private static LlmService createOllama(EasyAgentLlmProperties properties) {
        EasyAgentLlmProperties.Ollama ol = properties.getOllama();
        String model = getModel(properties, ol.getModel());
        
        OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                ol.getBaseUrl(), null, model, "ollama", properties.getChatOptions()
        );
        return new OpenAiCompatibleLlmService(client);
    }

    private static LlmService createOpenAi(EasyAgentLlmProperties properties) {
        EasyAgentLlmProperties.OpenAi oa = properties.getOpenAi();
        String apiKey = getApiKey(properties, oa.getApiKey());
        String model = getModel(properties, oa.getModel());
        
        OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                oa.getBaseUrl(), apiKey, model, "openai", properties.getChatOptions()
        );
        return new OpenAiCompatibleLlmService(client);
    }
    
    private static String getApiKey(EasyAgentLlmProperties properties, String providerApiKey) {
        if (properties.getApiKey() != null && !properties.getApiKey().isEmpty()) {
            return properties.getApiKey();
        }
        return providerApiKey;
    }
    
    private static String getModel(EasyAgentLlmProperties properties, String providerModel) {
        if (properties.getModel() != null && !properties.getModel().isEmpty()) {
            return properties.getModel();
        }
        return providerModel;
    }
}
