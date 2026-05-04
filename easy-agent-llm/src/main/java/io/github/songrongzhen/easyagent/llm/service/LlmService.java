package io.github.songrongzhen.easyagent.llm.service;

import java.util.List;

public interface LlmService {

    ChatResponse chat(List<ChatMessage> messages);

    ChatResponse chatWithTools(List<ChatMessage> messages, List<ToolDescriptor> tools);

    boolean isAvailable();

    String getProviderName();
}
