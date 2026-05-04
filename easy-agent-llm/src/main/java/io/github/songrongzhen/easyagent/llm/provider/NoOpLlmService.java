package io.github.songrongzhen.easyagent.llm.provider;

import io.github.songrongzhen.easyagent.llm.service.ChatMessage;
import io.github.songrongzhen.easyagent.llm.service.ChatResponse;
import io.github.songrongzhen.easyagent.llm.service.LlmService;
import io.github.songrongzhen.easyagent.llm.service.ToolDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class NoOpLlmService implements LlmService {

    private static final Logger log = LoggerFactory.getLogger(NoOpLlmService.class);

    public NoOpLlmService() {
        log.info("No LLM provider configured, running in MCP-only mode");
    }

    @Override
    public ChatResponse chat(List<ChatMessage> messages) {
        log.warn("No LLM provider configured, chat request ignored");
        return ChatResponse.of("No LLM provider configured. Please configure an LLM provider (dashscope/deepseek/ollama) to enable chat capabilities.", "none");
    }

    @Override
    public ChatResponse chatWithTools(List<ChatMessage> messages, List<ToolDescriptor> tools) {
        log.warn("No LLM provider configured, chat with tools request ignored");
        return ChatResponse.of("No LLM provider configured. Please configure an LLM provider to enable tool calling capabilities.", "none");
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String getProviderName() {
        return "none";
    }
}
