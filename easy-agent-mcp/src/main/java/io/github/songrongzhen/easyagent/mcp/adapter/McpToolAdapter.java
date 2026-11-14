package io.github.songrongzhen.easyagent.mcp.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.songrongzhen.easyagent.core.executor.ToolExecutor;
import io.github.songrongzhen.easyagent.core.model.ToolCallContext;
import io.github.songrongzhen.easyagent.core.model.ToolDefinition;
import io.github.songrongzhen.easyagent.core.model.ToolInvocation;
import io.github.songrongzhen.easyagent.core.model.ToolResult;
import io.github.songrongzhen.easyagent.core.registry.ToolRegistry;
import io.github.songrongzhen.easyagent.mcp.protocol.McpErrorCode;
import io.github.songrongzhen.easyagent.mcp.protocol.McpErrorFactory;
import io.github.songrongzhen.easyagent.mcp.protocol.McpProtocol;
import io.github.songrongzhen.easyagent.mcp.server.McpToolExposurePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具发现与执行适配，保留入口暴露策略并传递可信上下文。
 */
public class McpToolAdapter {

    /**
     * 当前组件的日志记录器。
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(McpToolAdapter.class);

    /**
     * 当前边界使用的 JSON 编解码器。
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 工具注册表。
     */
    private final ToolRegistry toolRegistry;

    /**
     * 公共工具执行器。
     */
    private final ToolExecutor toolExecutor;

    /**
     * 当前 MCP 入口的工具暴露策略。
     */
    private final McpToolExposurePolicy exposurePolicy;

    /**
     * 创建 McpToolAdapter，保存调用所需的显式依赖。
     *
     * @param toolRegistry   工具注册表
     * @param toolExecutor   公共工具执行器
     * @param exposurePolicy 当前 MCP 入口的工具暴露策略
     */
    public McpToolAdapter(ToolRegistry toolRegistry, ToolExecutor toolExecutor, McpToolExposurePolicy exposurePolicy) {
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
        this.exposurePolicy = exposurePolicy;
    }

    /**
     * 按完整工具定义执行入口暴露过滤并读取公共 Schema。
     *
     * @return 配置或装配得到的 List<McpProtocol.Tool> 实例
     */
    public List<McpProtocol.Tool> convertToMcpTools() {
        return toolRegistry.getEnabledRegisteredTools().stream()
            .filter(tool -> exposurePolicy == null || exposurePolicy.isAllowed(tool.definition()))
            .map(tool -> new McpProtocol.Tool(tool.definition().name(), tool.definition().description(),
                tool.inputSchema()))
            .toList();
    }

    /**
     * 检查 MCP 入口暴露规则后委托公共执行器，不自行重试业务。
     *
     * @param toolName  工具名称
     * @param arguments 原始工具参数
     * @return 成功结果或可对外展示的结构化工具错误
     */
    public McpProtocol.CallToolResult executeMcpTool(String toolName, Map<String, Object> arguments) {
        return executeMcpTool(toolName, arguments, ToolCallContext.anonymous(ToolCallContext.SOURCE_MCP));
    }

    /**
     * 检查 MCP 入口暴露规则后委托公共执行器，不自行重试业务。
     *
     * @param toolName  工具名称
     * @param arguments 原始工具参数
     * @param context   可信调用上下文
     * @return 成功结果或可对外展示的结构化工具错误
     */
    public McpProtocol.CallToolResult executeMcpTool(String toolName, Map<String, Object> arguments,
        ToolCallContext context) {
        if (toolName == null || toolName.isEmpty()) {
            return McpErrorFactory.toolError(McpErrorCode.INVALID_PARAMS, "工具名称不能为空");
        }
        ToolDefinition toolDefinition = toolRegistry.getTool(toolName);
        if (toolDefinition != null && exposurePolicy != null && !exposurePolicy.isAllowed(toolDefinition)) {
            return McpErrorFactory.toolError(McpErrorCode.TOOL_NOT_ALLOWED, "toolName=" + toolName);
        }
        try {
            String argsJson = OBJECT_MAPPER.writeValueAsString(arguments != null ? arguments : Map.of());
            ToolInvocation invocation = new ToolInvocation(toolName, argsJson);
            ToolResult result = toolExecutor.execute(invocation, context.withSource(ToolCallContext.SOURCE_MCP));
            if (result.success()) {
                return new McpProtocol.CallToolResult(
                    List.of(McpProtocol.Content.text(formatToolResult(result.result()))), false);
            } else {
                return McpErrorFactory.toolError(result.errorDetail());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to execute MCP tool: {}", toolName, e);
            return McpErrorFactory.toolError(McpErrorCode.TOOL_EXECUTION_FAILED);
        }
    }

    /**
     * 读取当前 MCP 入口的工具暴露策略。
     *
     * @return 当前当前 MCP 入口的工具暴露策略
     */
    public McpToolExposurePolicy getExposurePolicy() {
        return exposurePolicy;
    }

    private String formatToolResult(String result) {
        if (result == null || result.isBlank()) {
            return "";
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(result);
            if (node != null && node.isTextual()) {
                return node.asText();
            }
        } catch (Exception ignored) {
            return result;
        }
        return result;
    }
}
