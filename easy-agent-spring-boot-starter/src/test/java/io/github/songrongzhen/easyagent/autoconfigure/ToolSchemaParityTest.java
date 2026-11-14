package io.github.songrongzhen.easyagent.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.songrongzhen.easyagent.core.annotation.EasyTool;
import io.github.songrongzhen.easyagent.core.annotation.ToolParam;
import io.github.songrongzhen.easyagent.core.executor.ToolExecutor;
import io.github.songrongzhen.easyagent.core.model.ToolCallContext;
import io.github.songrongzhen.easyagent.core.registry.ToolRegistry;
import io.github.songrongzhen.easyagent.core.spi.ToolAuthorizationPolicy;
import io.github.songrongzhen.easyagent.llm.config.EasyAgentLlmProperties;
import io.github.songrongzhen.easyagent.llm.service.AgentLlmService;
import io.github.songrongzhen.easyagent.llm.service.ChatMessage;
import io.github.songrongzhen.easyagent.llm.service.ChatResponse;
import io.github.songrongzhen.easyagent.llm.service.LlmService;
import io.github.songrongzhen.easyagent.llm.service.ToolCall;
import io.github.songrongzhen.easyagent.llm.service.ToolDescriptor;
import io.github.songrongzhen.easyagent.mcp.adapter.McpToolAdapter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 工具执行契约的测试夹具与回归验证。 */
class ToolSchemaParityTest {

    /**
     * 工具执行契约的测试夹具与回归验证。
     *
     * @param name 工具名称
     */
    public record User(String name) {
    }

    /** 工具执行契约的测试夹具与回归验证。 */
    public static class Service {

        int writes;

        @EasyTool(name = "saveOwners", description = "save owners")
        public String save(@ToolParam(name = "owners") List<User> owners) {
            writes++;
            return owners.get(0).name();
        }
    }

    @Test
    void mcpAndAgentShareSchemaAndExecutionAuthorization() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EasyAgentCoreAutoConfiguration.class))
            .withBean(Service.class)
            .withBean(
                ToolAuthorizationPolicy.class,
                () -> (ctx, t, args) -> "alice".equals(ctx.principalId())
                    ? ToolAuthorizationPolicy.Decision.allow()
                    : ToolAuthorizationPolicy.Decision.deny("denied"))
            .run(
                ctx -> {
                    var tools = ctx.getBean(ToolRegistry.class);
                    var executor = ctx.getBean(ToolExecutor.class);
                    var adapter = new McpToolAdapter(tools, executor, null);
                    var llm = new FakeLlm();
                    var agent = new AgentLlmService(
                        llm, tools, executor, new EasyAgentLlmProperties());
                    agent.chatWithRegisteredTools(List.of());
                    assertThat(llm.descriptors.get(0).inputSchema())
                        .isEqualTo(adapter.convertToMcpTools().get(0).inputSchema());
                    assertThat(
                        adapter.executeMcpTool(
                            "saveOwners",
                            Map.of(
                                "owners",
                                List.of(Map.of("name", "Ada"))))
                            .isError())
                        .isTrue();
                    assertThat(ctx.getBean(Service.class).writes).isZero();
                    var trusted = new ToolCallContext(null, "MCP", "alice", "tenant", Map.of());
                    assertThat(
                        adapter.executeMcpTool(
                            "saveOwners",
                            Map.of(
                                "owners",
                                List.of(Map.of("name", "Ada"))),
                            trusted)
                            .isError())
                        .isFalse();
                    assertThat(ctx.getBean(Service.class).writes).isEqualTo(1);
                    String denied = llm.results.get(0);
                    assertThat(
                        new ObjectMapper()
                            .readTree(denied)
                            .at("/errorDetail/code")
                            .asText())
                        .isEqualTo("TOOL_ACCESS_DENIED");
                });
    }

    /** 工具执行契约的测试夹具与回归验证。 */
    static class FakeLlm implements LlmService {

        int round;

        List<ToolDescriptor> descriptors;

        List<String> results = new ArrayList<>();

        @Override
        public ChatResponse chat(List<ChatMessage> messages) {
            return ChatResponse.of("done", "test");
        }

        @Override
        public ChatResponse chatWithTools(List<ChatMessage> messages, List<ToolDescriptor> tools) {
            descriptors = tools;
            if (round++ == 0) {
                return ChatResponse.ofToolCalls(
                    List.of(
                        new ToolCall(
                            "model",
                            "saveOwners",
                            "{\"owners\":[{\"name\":\"Ada\"}]}")),
                    "test");
            }
            messages.stream()
                .filter(m -> m.role() == ChatMessage.Role.TOOL)
                .forEach(m -> results.add(m.content()));
            return ChatResponse.of("done", "test");
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
