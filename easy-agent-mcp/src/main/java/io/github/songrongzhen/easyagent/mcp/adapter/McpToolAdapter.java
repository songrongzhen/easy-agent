package io.github.songrongzhen.easyagent.mcp.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.songrongzhen.easyagent.core.executor.ToolExecutor;
import io.github.songrongzhen.easyagent.core.model.ParameterDefinition;
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

import java.util.*;

public class McpToolAdapter {

    private static final Logger log = LoggerFactory.getLogger(McpToolAdapter.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final McpToolExposurePolicy exposurePolicy;

    public McpToolAdapter(ToolRegistry toolRegistry, ToolExecutor toolExecutor, McpToolExposurePolicy exposurePolicy) {
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
        this.exposurePolicy = exposurePolicy;
    }

    public List<McpProtocol.Tool> convertToMcpTools() {
        return toolRegistry.getEnabledTools().stream()
                .filter(exposurePolicy == null ? tool -> true : exposurePolicy::isAllowed)
                .map(this::toMcpTool)
                .toList();
    }

    public McpProtocol.CallToolResult executeMcpTool(String toolName, Map<String, Object> arguments) {
        if (toolName == null || toolName.isEmpty()) {
            return McpErrorFactory.toolError(McpErrorCode.INVALID_PARAMS, "工具名称不能为空");
        }

        ToolDefinition toolDefinition = toolRegistry.getTool(toolName);
        if (toolDefinition == null) {
            return McpErrorFactory.toolError(McpErrorCode.TOOL_NOT_FOUND, "toolName=" + toolName);
        }
        if (exposurePolicy != null && !exposurePolicy.isAllowed(toolDefinition)) {
            return McpErrorFactory.toolError(McpErrorCode.TOOL_NOT_ALLOWED, "toolName=" + toolName);
        }
        
        try {
            String argsJson = OBJECT_MAPPER.writeValueAsString(arguments != null ? arguments : Map.of());
            ToolInvocation invocation = new ToolInvocation(toolName, argsJson);
            ToolResult result = toolExecutor.execute(invocation);

            if (result.success()) {
                return new McpProtocol.CallToolResult(
                        List.of(McpProtocol.Content.text(result.result())),
                        false
                );
            } else {
                return McpErrorFactory.toolError(McpErrorCode.TOOL_EXECUTION_FAILED, result.error());
            }
        } catch (Exception e) {
            log.error("Failed to execute MCP tool: {}", toolName, e);
            return McpErrorFactory.toolError(McpErrorCode.TOOL_EXECUTION_FAILED, e.getMessage());
        }
    }

    public McpToolExposurePolicy getExposurePolicy() {
        return exposurePolicy;
    }

    private McpProtocol.Tool toMcpTool(ToolDefinition toolDef) {
        Map<String, McpProtocol.PropertyDef> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (ParameterDefinition param : toolDef.parameters()) {
            properties.put(param.name(), new McpProtocol.PropertyDef(
                    mapJavaTypeToJsonType(param.type()),
                    param.description()
            ));
            if (param.required()) {
                required.add(param.name());
            }
        }

        McpProtocol.JsonSchema schema = new McpProtocol.JsonSchema("object", properties, required);
        return new McpProtocol.Tool(toolDef.name(), toolDef.description(), schema);
    }

    private String mapJavaTypeToJsonType(String javaType) {
        if (javaType == null) return "string";
        return switch (javaType.toLowerCase()) {
            case "int", "integer", "long", "short", "byte" -> "integer";
            case "float", "double" -> "number";
            case "boolean" -> "boolean";
            case "string", "char", "character" -> "string";
            case "list", "arraylist", "set", "hashset" -> "array";
            case "map", "hashmap", "linkedhashmap" -> "object";
            default -> "string";
        };
    }
}
