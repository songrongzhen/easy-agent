package io.github.songrongzhen.easyagent.llm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.songrongzhen.easyagent.core.executor.ToolExecutor;
import io.github.songrongzhen.easyagent.core.model.ToolCallContext;
import io.github.songrongzhen.easyagent.core.model.ToolError;
import io.github.songrongzhen.easyagent.core.model.ToolInvocation;
import io.github.songrongzhen.easyagent.core.model.ToolResult;
import io.github.songrongzhen.easyagent.core.registry.ToolRegistry;
import io.github.songrongzhen.easyagent.llm.config.EasyAgentLlmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 模型对话与工具调用编排服务，所有业务工具执行统一委托公共执行器。
 */
public class AgentLlmService {

    /**
     * 当前组件的日志记录器。
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentLlmService.class);

    /**
     * Agent 默认允许的最大工具调用轮数。
     */
    private static final int DEFAULT_MAX_TOOL_ROUNDS = 5;

    /**
     * 当前边界使用的 JSON 编解码器。
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 独立的模型请求重试配置。
     */
    private final EasyAgentLlmProperties.RequestRetry requestRetry;

    /**
     * 底层模型请求服务。
     */
    private final LlmService llmService;

    /**
     * 工具注册表。
     */
    private final ToolRegistry toolRegistry;

    /**
     * 公共工具执行器。
     */
    private final ToolExecutor toolExecutor;

    /**
     * Agent 编排配置。
     */
    private final EasyAgentLlmProperties.ToolExecution toolExecutionProperties;

    /**
     * 创建 AgentLlmService，保存调用所需的显式依赖。
     *
     * @param llmService              底层模型请求服务
     * @param toolRegistry            工具注册表
     * @param toolExecutor            公共工具执行器
     * @param toolExecutionProperties Agent 编排配置
     */
    public AgentLlmService(LlmService llmService, ToolRegistry toolRegistry, ToolExecutor toolExecutor,
        EasyAgentLlmProperties toolExecutionProperties) {
        this.requestRetry = toolExecutionProperties == null ? new EasyAgentLlmProperties.RequestRetry()
            : toolExecutionProperties.getRequestRetry();
        this.llmService = llmService;
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
        this.toolExecutionProperties = toolExecutionProperties == null ? new EasyAgentLlmProperties.ToolExecution()
            : toolExecutionProperties.getToolExecution();
    }

    /**
     * 使用注册工具进行对话编排，为每个工具调用生成独立子调用上下文。
     *
     * @param messages 对话消息
     * @return 模型返回的对话或工具调用响应
     */
    public ChatResponse chatWithRegisteredTools(List<ChatMessage> messages) {
        return chatWithRegisteredTools(messages, resolveMaxToolRounds());
    }

    /**
     * 使用注册工具进行对话编排，为每个工具调用生成独立子调用上下文。
     *
     * @param messages      对话消息
     * @param maxToolRounds 最大工具调用轮数
     * @return 模型返回的对话或工具调用响应
     */
    public ChatResponse chatWithRegisteredTools(List<ChatMessage> messages, int maxToolRounds) {
        return chatWithRegisteredTools(messages, maxToolRounds,
            ToolCallContext.anonymous(ToolCallContext.SOURCE_AGENT));
    }

    /**
     * 使用注册工具进行对话编排，为每个工具调用生成独立子调用上下文。
     *
     * @param messages 对话消息
     * @param context  可信调用上下文
     * @return 模型返回的对话或工具调用响应
     */
    public ChatResponse chatWithRegisteredTools(List<ChatMessage> messages, ToolCallContext context) {
        return chatWithRegisteredTools(messages, resolveMaxToolRounds(), context);
    }

    /**
     * 使用注册工具进行对话编排，为每个工具调用生成独立子调用上下文。
     *
     * @param messages      对话消息
     * @param maxToolRounds 最大工具调用轮数
     * @param context       可信调用上下文
     * @return 模型返回的对话或工具调用响应
     */
    public ChatResponse chatWithRegisteredTools(List<ChatMessage> messages, int maxToolRounds,
        ToolCallContext context) {
        Objects.requireNonNull(context);
        if (maxToolRounds <= 0) {
            throw new IllegalArgumentException("maxToolRounds must be greater than 0");
        }
        List<ToolDescriptor> tools = ToolDescriptorConverter
            .fromRegisteredTools(toolRegistry.getEnabledRegisteredTools());
        if (tools.isEmpty()) {
            return invokeWithRetry(() -> llmService.chat(messages == null ? List.of() : messages));
        }
        List<ChatMessage> conversation = new ArrayList<>(messages == null ? List.of() : messages);
        ChatResponse response = null;
        ToolCallLoopGuard loopGuard = new ToolCallLoopGuard(resolveRepeatedToolCallThreshold());
        for (int round = 0; round < maxToolRounds; round++) {
            response = invokeChatWithTools(conversation, tools);
            if (response == null) {
                return ChatResponse.of("LLM returned no response", "none");
            }
            if (!response.hasToolCalls()) {
                return response;
            }
            conversation.add(ChatMessage.assistantWithToolCalls(response.content(), response.toolCalls()));
            boolean stopped = false;
            for (ToolCall toolCall : response.toolCalls()) {
                if (toolCall == null || toolCall.id() == null || toolCall.id().isBlank()) {
                    return ChatResponse.of("LLM returned an invalid tool call without an id", "none");
                }
                if (!stopped && loopGuard.isRepeated(toolCall)) {
                    LOGGER.warn("Repeated tool call detected, stop looping: {}", toolCall.name());
                    stopped = true;
                }
                // Complete every pending call, including skipped calls, before asking for a final
                // answer.
                String result = stopped
                    ? buildErrorPayload("TOOL_CALL_LOOP_STOPPED", "Tool execution stopped due to repeated calls")
                    : executeToolCall(toolCall, context.child(ToolCallContext.SOURCE_AGENT));
                conversation.add(ChatMessage.toolResult(toolCall.id(), result));
            }
            if (stopped) {
                return finishConversation(conversation, "Repeated tool calls were stopped");
            }
        }
        return finishConversation(conversation, "Maximum tool execution rounds reached");
    }

    private ChatResponse finishConversation(List<ChatMessage> conversation, String reason) {
        List<ChatMessage> finalMessages = new ArrayList<>(conversation);
        finalMessages.add(ChatMessage.system(reason + ". Do not request more tools. Answer using the available tool"
            + " results and explain any incomplete work."));
        ChatResponse finalResponse = fallbackToChat(finalMessages);
        if (finalResponse == null || finalResponse.hasToolCalls()) {
            return ChatResponse.of("Unable to produce a final answer: " + reason, "none");
        }
        return finalResponse;
    }

    private ChatResponse invokeChatWithTools(List<ChatMessage> conversation, List<ToolDescriptor> tools) {
        try {
            return retryEnabled() ? invokeWithRetry(() -> llmService.chatWithTools(conversation, tools))
                : llmService.chatWithTools(conversation, tools);
        } catch (RuntimeException e) {
            LOGGER.warn("chatWithTools failed, fallback={}", fallbackEnabled(), e);
            return fallbackEnabled() ? fallbackToChat(conversation)
                : ChatResponse.of("LLM tool call failed: " + e.getMessage(), "none");
        }
    }

    private String executeToolCall(ToolCall call, ToolCallContext context) {
        return formatToolResult(toolExecutor.execute(new ToolInvocation(call.name(), call.arguments()), context));
    }

    private ChatResponse fallbackToChat(List<ChatMessage> messages) {
        try {
            return invokeWithRetry(() -> llmService.chat(messages == null ? List.of() : messages));
        } catch (RuntimeException e) {
            LOGGER.error("Fallback chat failed", e);
            return ChatResponse.of("LLM request failed: " + e.getMessage(), "none");
        }
    }

    private String formatToolResult(ToolResult result) {
        if (result == null) {
            return buildErrorPayload("TOOL_EXECUTION_FAILED", "Tool returned empty result");
        }
        return serializePayload(result);
    }

    private String buildErrorPayload(String code, String message) {
        return serializePayload(
            ToolResult.failure(null, new ToolError(code, ToolError.Category.INTERNAL, message, null)));
    }

    private String serializePayload(Object payload) {
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize tool result", e);
        }
    }

    private boolean retryEnabled() {
        return requestRetry.getMaxAttempts() > 1;
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
        return requestRetry.getMaxAttempts();
    }

    private long resolveRetryBackoffMillis() {
        return requestRetry.getBackoffMillis();
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
                if (i + 1 < attempts) {
                    sleepQuietly(resolveRetryBackoffMillis());
                }
                if (Thread.currentThread().isInterrupted()) {
                    throw e;
                }
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

    /**
     * 用于独立模型请求重试的调用函数。
     */
    private interface SupplierWithException<T> {

        /**
         * 读取。
         *
         * @return 当前
         */
        T get();
    }

    /**
     * 按工具名称及原始参数组合限制当前会话的重复调用。
     */
    private static class ToolCallLoopGuard {

        /**
         * 重复工具调用的停止阈值。
         */
        private final int threshold;

        /**
         * 当前会话中各工具参数组合的累计调用次数。
         */
        private final Map<String, Integer> counts = new HashMap<>();

        private ToolCallLoopGuard(int threshold) {
            this.threshold = threshold;
        }

        boolean isRepeated(ToolCall toolCall) {
            String key = (toolCall == null ? "null" : String.valueOf(toolCall.name())) + "::"
                + (toolCall == null ? "null" : String.valueOf(toolCall.arguments()));
            int next = counts.getOrDefault(key, 0) + 1;
            counts.put(key, next);
            return next >= threshold;
        }
    }
}
