package io.github.songrongzhen.easyagent.mcp.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.songrongzhen.easyagent.core.executor.ToolExecutor;
import io.github.songrongzhen.easyagent.core.model.ParameterDefinition;
import io.github.songrongzhen.easyagent.core.model.ToolDefinition;
import io.github.songrongzhen.easyagent.core.model.ToolInvocation;
import io.github.songrongzhen.easyagent.core.model.ToolResult;
import io.github.songrongzhen.easyagent.core.registry.ToolRegistry;
import io.github.songrongzhen.easyagent.mcp.protocol.McpProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class McpToolAdapter {

    private static final Logger log = LoggerFactory.getLogger(McpToolAdapter.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;

    public McpToolAdapter(ToolRegistry toolRegistry, ToolExecutor toolExecutor) {
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
    }

    public List<McpProtocol.Tool> convertToMcpTools() {
        return toolRegistry.getEnabledTools().stream()
                .map(this::toMcpTool)
                .toList();
    }

    public McpProtocol.CallToolResult executeMcpTool(String toolName, Map<String, Object> arguments) {
        if (toolName == null || toolName.isEmpty()) {
            return new McpProtocol.CallToolResult(
                    List.of(McpProtocol.Content.text("Error: tool name is required")),
                    true
            );
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
                return new McpProtocol.CallToolResult(
                        List.of(McpProtocol.Content.text("Error: " + result.error())),
                        true
                );
            }
        } catch (Exception e) {
            log.error("Failed to execute MCP tool: {}", toolName, e);
            return new McpProtocol.CallToolResult(
                    List.of(McpProtocol.Content.text("Error: " + e.getMessage())),
                    true
            );
        }
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
