package io.github.songrongzhen.easyagent.llm.provider;

import io.github.songrongzhen.easyagent.llm.client.OpenAiCompatibleClient;
import io.github.songrongzhen.easyagent.llm.service.*;

import java.util.List;
import java.util.function.Consumer;

public class OpenAiCompatibleLlmService implements LlmService {

    private final OpenAiCompatibleClient client;

    public OpenAiCompatibleLlmService(OpenAiCompatibleClient client) {
        this.client = client;
    }

    @Override
    public ChatResponse chat(List<ChatMessage> messages) {
        return client.chat(messages);
    }

    @Override
    public ChatResponse chatWithTools(List<ChatMessage> messages, List<ToolDescriptor> tools) {
        return client.chatWithTools(messages, tools);
    }

    @Override
    public boolean isAvailable() {
        return client.isAvailable();
    }

    @Override
    public String getProviderName() {
        return client.getProviderName();
    }

    @Override
    public void chatStream(List<ChatMessage> messages, Consumer<String> consumer) {
        client.chatStream(messages, consumer);
    }
}
