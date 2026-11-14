package io.github.songrongzhen.easyagent.llm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.github.songrongzhen.easyagent.core.config.ToolExecutionOptions;
import io.github.songrongzhen.easyagent.core.executor.ToolExecutor;
import io.github.songrongzhen.easyagent.core.model.ParameterDefinition;
import io.github.songrongzhen.easyagent.core.model.ToolCallContext;
import io.github.songrongzhen.easyagent.core.model.ToolDefinition;
import io.github.songrongzhen.easyagent.core.model.ToolError;
import io.github.songrongzhen.easyagent.core.registry.ToolRegistry;
import io.github.songrongzhen.easyagent.core.spi.ToolAuthorizationPolicy;
import io.github.songrongzhen.easyagent.core.spi.ToolRetryPolicy;
import io.github.songrongzhen.easyagent.llm.config.EasyAgentLlmProperties;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 工具执行契约的测试夹具与回归验证。 */
class AgentExecutionTest {

    /** 工具执行契约的测试夹具与回归验证。 */
    public static class Service {

        int calls;

        public String call(String value) {
            calls++;
            if (calls < 3) {
                throw new IllegalStateException("temporary");
            }
            return value;
        }
    }

    @Test
    void executorOwnsRetriesAndToolCallsHaveDistinctChildIds() {
        try (var context = new GenericApplicationContext()) {
            context.refresh();
            var tools = new ToolRegistry();
            var service = new Service();
            List<ToolCallContext> seen = new ArrayList<>();
            var executor = new ToolExecutor(
                tools,
                context,
                ToolExecutor.createObjectMapper(),
                List.of(),
                List.of(
                    (ctx, t, args) -> {
                        seen.add(ctx);
                        return ToolAuthorizationPolicy.Decision.allow();
                    }),
                List.of(),
                List.of(
                    (e, ctx) -> Optional.of(
                        new ToolError(
                            "TEMP",
                            ToolError.Category.TRANSIENT,
                            "later",
                            ctx.invocationId()))),
                List.of(),
                ToolRetryPolicy.safeDefault(),
                (n, s) -> new ToolExecutionOptions(
                    ToolExecutionOptions.Effect.READ_ONLY, 3, 0));
            tools.register(
                new ToolDefinition(
                    "tool",
                    "desc",
                    "cat",
                    "service",
                    "call",
                    List.of(new ParameterDefinition("value", "", "String", true)),
                    true),
                service,
                null,
                null,
                null);
            var properties = new EasyAgentLlmProperties();
            properties.getToolExecution().setRetryAttempts(3);
            var llm = new FakeLlm();
            new AgentLlmService(llm, tools, executor, properties)
                .chatWithRegisteredTools(
                    List.of(),
                    new ToolCallContext("parent", "JAVA", "alice", "tenant", Map.of()));
            assertEquals(4, service.calls);
            assertEquals(4, seen.size());
            assertEquals(seen.get(0).invocationId(), seen.get(2).invocationId());
            assertNotEquals(seen.get(2).invocationId(), seen.get(3).invocationId());
            assertEquals("alice", seen.get(0).principalId());
            assertEquals("AGENT", seen.get(0).source());
            assertEquals("parent", seen.get(0).attributes().get("parentInvocationId"));
            assertNotEquals("model-id", seen.get(0).invocationId());
            assertEquals(
                tools.getRegisteredTool("tool").inputSchema(), llm.tools.get(0).inputSchema());
        }
    }

    @Test
    void modelRequestRetryDoesNotReplayBusiness() {
        var properties = new EasyAgentLlmProperties();
        properties.getRequestRetry().setMaxAttempts(2);
        try (var context = new GenericApplicationContext()) {
            context.refresh();
            var registry = new ToolRegistry();
            var executor = new ToolExecutor(registry, context);
            var llm = new FakeLlm() {

                @Override
                public ChatResponse chat(List<ChatMessage> messages) {
                    if (round++ == 0) {
                        throw new IllegalStateException();
                    }
                    return ChatResponse.of("done", "test");
                }
            };
            assertEquals(
                "done",
                new AgentLlmService(llm, registry, executor, properties)
                    .chatWithRegisteredTools(List.of())
                    .content());
            assertEquals(2, llm.round);
        }
    }

    /** 工具执行契约的测试夹具与回归验证。 */
    static class FakeLlm implements LlmService {

        int round;

        List<ToolDescriptor> tools;

        @Override
        public ChatResponse chat(List<ChatMessage> messages) {
            return ChatResponse.of("done", "test");
        }

        @Override
        public ChatResponse chatWithTools(List<ChatMessage> messages, List<ToolDescriptor> tools) {
            this.tools = tools;
            if (round++ > 0) {
                return ChatResponse.of("done", "test");
            }
            return ChatResponse.ofToolCalls(
                List.of(
                    new ToolCall("model-id", "tool", "{\"value\":\"x\"}"),
                    new ToolCall("second", "tool", "{\"value\":\"y\"}")),
                "test");
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String getProviderName() {
            return "test";
        }
    }
}
