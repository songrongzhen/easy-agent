package io.github.songrongzhen.easyagent.llm.provider;

import io.github.songrongzhen.easyagent.llm.client.OpenAiCompatibleClient;
import io.github.songrongzhen.easyagent.llm.config.EasyAgentLlmProperties;
import io.github.songrongzhen.easyagent.llm.service.LlmService;

public class LlmServiceFactory {

    public static LlmService create(EasyAgentLlmProperties properties) {
        return switch (properties.getProvider()) {
            case DASHSCOPE -> createDashScope(properties);
            case DEEPSEEK -> createDeepSeek(properties);
            case OLLAMA -> createOllama(properties);
            case OPENAI -> createOpenAi(properties);
            case NONE -> new NoOpLlmService();
        };
    }

    private static LlmService createDashScope(EasyAgentLlmProperties properties) {
        EasyAgentLlmProperties.DashScope ds = properties.getDashScope();
        OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                ds.getBaseUrl(), ds.getApiKey(), ds.getModel(), "dashscope", properties.getChatOptions()
        );
        return new OpenAiCompatibleLlmService(client);
    }

    private static LlmService createDeepSeek(EasyAgentLlmProperties properties) {
        EasyAgentLlmProperties.DeepSeek ds = properties.getDeepSeek();
        OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                ds.getBaseUrl(), ds.getApiKey(), ds.getModel(), "deepseek", properties.getChatOptions()
        );
        return new OpenAiCompatibleLlmService(client);
    }

    private static LlmService createOllama(EasyAgentLlmProperties properties) {
        EasyAgentLlmProperties.Ollama ol = properties.getOllama();
        OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                ol.getBaseUrl(), null, ol.getModel(), "ollama", properties.getChatOptions()
        );
        return new OpenAiCompatibleLlmService(client);
    }

    private static LlmService createOpenAi(EasyAgentLlmProperties properties) {
        EasyAgentLlmProperties.OpenAi oa = properties.getOpenAi();
        OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                oa.getBaseUrl(), oa.getApiKey(), oa.getModel(), "openai", properties.getChatOptions()
        );
        return new OpenAiCompatibleLlmService(client);
    }
}
