package io.github.songrongzhen.easyagent.llm.service;

import io.github.songrongzhen.easyagent.core.executor.ToolExecutor;
import io.github.songrongzhen.easyagent.core.model.ToolInvocation;
import io.github.songrongzhen.easyagent.core.model.ToolResult;
import io.github.songrongzhen.easyagent.core.registry.ToolRegistry;

import java.util.ArrayList;
import java.util.List;

public class AgentLlmService {

    private static final int DEFAULT_MAX_TOOL_ROUNDS = 5;

    private final LlmService llmService;
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;

    public AgentLlmService(LlmService llmService, ToolRegistry toolRegistry, ToolExecutor toolExecutor) {
        this.llmService = llmService;
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
    }

    public ChatResponse chatWithRegisteredTools(List<ChatMessage> messages) {
        return chatWithRegisteredTools(messages, DEFAULT_MAX_TOOL_ROUNDS);
    }

    public ChatResponse chatWithRegisteredTools(List<ChatMessage> messages, int maxToolRounds) {
        List<ToolDescriptor> tools = ToolDescriptorConverter.fromToolDefinitions(toolRegistry.getEnabledTools());
        if (tools.isEmpty()) {
            return llmService.chat(messages);
        }

        List<ChatMessage> conversation = new ArrayList<>(messages);
        ChatResponse response = null;

        for (int round = 0; round < maxToolRounds; round++) {
            response = llmService.chatWithTools(conversation, tools);
            if (!response.hasToolCalls()) {
                return response;
            }

            conversation.add(ChatMessage.assistantWithToolCalls(response.content(), response.toolCalls()));
            for (ToolCall toolCall : response.toolCalls()) {
                conversation.add(ChatMessage.toolResult(toolCall.id(), executeToolCall(toolCall)));
            }
        }

        return response != null ? response : llmService.chat(messages);
    }

    private String executeToolCall(ToolCall toolCall) {
        if (toolCall.name() == null || toolCall.name().isBlank()) {
            return "Error: tool name is required";
        }

        String arguments = toolCall.arguments();
        if (arguments == null || arguments.isBlank()) {
            arguments = "{}";
        }

        ToolResult result = toolExecutor.execute(new ToolInvocation(toolCall.name(), arguments));
        if (result.success()) {
            return result.result();
        }
        return "Error: " + result.error();
    }
}
