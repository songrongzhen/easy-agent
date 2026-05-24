package io.github.songrongzhen.easyagent.llm.service;

import java.util.List;
import java.util.function.Consumer;

public interface LlmService {

    ChatResponse chat(List<ChatMessage> messages);

    ChatResponse chatWithTools(List<ChatMessage> messages, List<ToolDescriptor> tools);

    boolean isAvailable();

    String getProviderName();

    /**
     * 流式对话
     *
     * @param messages 消息列表
     * @param consumer 流式回调，接收每个token
     */
    default void chatStream(List<ChatMessage> messages, Consumer<String> consumer) {
        throw new UnsupportedOperationException("Streaming is not supported by this provider");
    }
}
