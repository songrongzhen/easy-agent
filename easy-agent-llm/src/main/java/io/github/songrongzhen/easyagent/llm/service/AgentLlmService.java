package io.github.songrongzhen.easyagent.llm.service;

import io.github.songrongzhen.easyagent.core.executor.ToolExecutor;
import io.github.songrongzhen.easyagent.core.model.ToolInvocation;
import io.github.songrongzhen.easyagent.core.model.ToolResult;
import io.github.songrongzhen.easyagent.core.registry.ToolRegistry;
import io.github.songrongzhen.easyagent.core.exception.ToolExecutionException;
import io.github.songrongzhen.easyagent.llm.config.EasyAgentLlmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class AgentLlmService {

    private static final Logger log = LoggerFactory.getLogger(AgentLlmService.class);
    private static final int DEFAULT_MAX_TOOL_ROUNDS = 5;

    private final LlmService llmService;
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final EasyAgentLlmProperties.ToolExecution toolExecutionProperties;

    public AgentLlmService(LlmService llmService,
                           ToolRegistry toolRegistry,
                           ToolExecutor toolExecutor,
                           EasyAgentLlmProperties toolExecutionProperties) {
        this.llmService = llmService;
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
        this.toolExecutionProperties = toolExecutionProperties == null
                ? new EasyAgentLlmProperties.ToolExecution()
                : toolExecutionProperties.getToolExecution();
    }

    public ChatResponse chatWithRegisteredTools(List<ChatMessage> messages) {
        return chatWithRegisteredTools(messages, resolveMaxToolRounds());
    }

    public ChatResponse chatWithRegisteredTools(List<ChatMessage> messages, int maxToolRounds) {
        List<ToolDescriptor> tools = ToolDescriptorConverter.fromToolDefinitions(toolRegistry.getEnabledTools());
        if (tools.isEmpty()) {
            return llmService.chat(messages);
        }

        List<ChatMessage> conversation = new ArrayList<>(messages == null ? List.of() : messages);
        ChatResponse response = null;
        ToolCallLoopGuard loopGuard = new ToolCallLoopGuard(resolveRepeatedToolCallThreshold());

        for (int round = 0; round < maxToolRounds; round++) {
            response = invokeChatWithTools(conversation, tools, messages);
            if (!response.hasToolCalls()) {
                return response;
            }

            conversation.add(ChatMessage.assistantWithToolCalls(response.content(), response.toolCalls()));
            for (ToolCall toolCall : response.toolCalls()) {
                if (loopGuard.isRepeated(toolCall)) {
                    log.warn("Repeated tool call detected, stop looping: {}", toolCall.name());
                    return response;
                }
                conversation.add(ChatMessage.toolResult(toolCall.id(), executeToolCall(toolCall)));
            }
        }

        return response != null ? response : fallbackToChat(messages);
    }

    private ChatResponse invokeChatWithTools(List<ChatMessage> conversation,
                                             List<ToolDescriptor> tools,
                                             List<ChatMessage> originalMessages) {
        try {
            return retryEnabled()
                    ? invokeWithRetry(() -> llmService.chatWithTools(conversation, tools))
                    : llmService.chatWithTools(conversation, tools);
        } catch (RuntimeException e) {
            log.warn("chatWithTools failed, fallback={}", fallbackEnabled(), e);
            return fallbackEnabled() ? fallbackToChat(originalMessages) : ChatResponse.of("LLM tool call failed: " + e.getMessage(), "none");
        }
    }

    private String executeToolCall(ToolCall toolCall) {
        if (toolCall == null || toolCall.name() == null || toolCall.name().isBlank()) {
            return buildErrorPayload("TOOL_NAME_EMPTY", "Tool name is required");
        }

        if (toolRegistry.getTool(toolCall.name()) == null) {
            return buildErrorPayload("TOOL_NOT_FOUND", "Tool not found: " + toolCall.name());
        }

        String arguments = toolCall.arguments();
        if (arguments == null || arguments.isBlank()) {
            return buildErrorPayload("INVALID_ARGUMENTS", "Tool arguments are required");
        }

        try {
            ToolResult result = retryEnabled()
                    ? invokeToolWithRetry(toolCall.name(), arguments)
                    : toolExecutor.execute(new ToolInvocation(toolCall.name(), arguments));
            return formatToolResult(result);
        } catch (RuntimeException e) {
            log.warn("Tool call failed: {}", toolCall.name(), e);
            if (fallbackEnabled()) {
                return buildErrorPayload("TOOL_EXECUTION_FAILED", e.getMessage());
            }
            return buildErrorPayload("TOOL_EXECUTION_FAILED", e.getMessage());
        }
    }

    private ToolResult invokeToolWithRetry(String toolName, String arguments) {
        RuntimeException lastException = null;
        ToolResult lastResult = null;
        int attempts = resolveRetryAttempts();
        for (int i = 0; i < attempts; i++) {
            try {
                ToolResult result = toolExecutor.execute(new ToolInvocation(toolName, arguments));
                if (result != null && result.success()) {
                    return result;
                }
                lastResult = result;
                if (!isRetryableFailure(result)) {
                    return result;
                }
            } catch (RuntimeException e) {
                lastException = e;
                sleepQuietly(resolveRetryBackoffMillis());
                continue;
            }
            sleepQuietly(resolveRetryBackoffMillis());
        }
        if (lastException != null) {
            throw lastException;
        }
        if (lastResult != null) {
            return lastResult;
        }
        throw new ToolExecutionException(toolName, new IllegalStateException("Unknown execution failure"));
    }

    private ChatResponse fallbackToChat(List<ChatMessage> messages) {
        try {
            return llmService.chat(messages == null ? List.of() : messages);
        } catch (RuntimeException e) {
            log.error("Fallback chat failed", e);
            return ChatResponse.of("LLM request failed: " + e.getMessage(), "none");
        }
    }

    private String formatToolResult(ToolResult result) {
        if (result == null) {
            return buildErrorPayload("TOOL_EXECUTION_FAILED", "Tool returned empty result");
        }
        StringBuilder builder = new StringBuilder();
        builder.append("{");
        builder.append("\"toolName\":\"").append(escape(result.toolName())).append("\",");
        builder.append("\"success\":").append(result.success()).append(",");
        builder.append("\"result\":");
        if (result.result() == null) {
            builder.append("null");
        } else {
            builder.append("\"").append(escape(result.result())).append("\"");
        }
        builder.append(",");
        builder.append("\"error\":");
        if (result.error() == null) {
            builder.append("null");
        } else {
            builder.append("\"").append(escape(result.error())).append("\"");
        }
        builder.append("}");
        return builder.toString();
    }

    private String buildErrorPayload(String code, String message) {
        return "{\"success\":false,\"errorCode\":\"" + escape(code) + "\",\"message\":\"" + escape(message) + "\"}";
    }

    private boolean isRetryableFailure(ToolResult result) {
        if (result == null || result.success()) {
            return false;
        }
        String error = result.error();
        if (error == null || error.isBlank()) {
            return true;
        }
        String lower = error.toLowerCase();
        return lower.contains("failed to execute tool")
                || lower.contains("timeout")
                || lower.contains("temporarily")
                || lower.contains("connection")
                || lower.contains("503")
                || lower.contains("502")
                || lower.contains("500");
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private boolean retryEnabled() {
        return toolExecutionProperties != null && toolExecutionProperties.isRetryEnabled();
    }

    private boolean fallbackEnabled() {
        return toolExecutionProperties == null || toolExecutionProperties.isFallbackToChatEnabled();
    }

    private int resolveMaxToolRounds() {
        if (toolExecutionProperties == null || toolExecutionProperties.getMaxToolRounds() <= 0) {
            return DEFAULT_MAX_TOOL_ROUNDS;
        }
        return toolExecutionProperties.getMaxToolRounds();
    }

    private int resolveRetryAttempts() {
        if (toolExecutionProperties == null || toolExecutionProperties.getRetryAttempts() < 1) {
            return 1;
        }
        return toolExecutionProperties.getRetryAttempts();
    }

    private long resolveRetryBackoffMillis() {
        if (toolExecutionProperties == null || toolExecutionProperties.getRetryBackoffMillis() < 0) {
            return 0L;
        }
        return toolExecutionProperties.getRetryBackoffMillis();
    }

    private int resolveRepeatedToolCallThreshold() {
        if (toolExecutionProperties == null || toolExecutionProperties.getRepeatedToolCallThreshold() < 1) {
            return 3;
        }
        return toolExecutionProperties.getRepeatedToolCallThreshold();
    }

    private <T> T invokeWithRetry(SupplierWithException<T> supplier) {
        RuntimeException last = null;
        int attempts = resolveRetryAttempts();
        for (int i = 0; i < attempts; i++) {
            try {
                return supplier.get();
            } catch (RuntimeException e) {
                last = e;
                sleepQuietly(resolveRetryBackoffMillis());
            }
        }
        throw last == null ? new RuntimeException("Unknown failure") : last;
    }

    private void sleepQuietly(long backoffMillis) {
        if (backoffMillis <= 0) {
            return;
        }
        try {
            TimeUnit.MILLISECONDS.sleep(backoffMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private interface SupplierWithException<T> {
        T get();
    }

    private static class ToolCallLoopGuard {
        private final int threshold;
        private final Map<String, Integer> counts = new HashMap<>();

        private ToolCallLoopGuard(int threshold) {
            this.threshold = threshold;
        }

        boolean isRepeated(ToolCall toolCall) {
            String key = (toolCall == null ? "null" : String.valueOf(toolCall.name()))
                    + "::"
                    + (toolCall == null ? "null" : String.valueOf(toolCall.arguments()));
            int next = counts.getOrDefault(key, 0) + 1;
            counts.put(key, next);
            return next >= threshold;
        }
    }
}
