package io.github.songrongzhen.easyagent.mcp.server;

import io.github.songrongzhen.easyagent.core.executor.ToolExecutor;
import io.github.songrongzhen.easyagent.core.registry.ToolRegistry;
import io.github.songrongzhen.easyagent.mcp.adapter.McpToolAdapter;
import io.github.songrongzhen.easyagent.mcp.config.EasyAgentMcpProperties;
import io.github.songrongzhen.easyagent.mcp.protocol.McpProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class EasyAgentMcpServer {

    private static final Logger log = LoggerFactory.getLogger(EasyAgentMcpServer.class);

    private final EasyAgentMcpProperties properties;
    private final McpToolAdapter mcpToolAdapter;

    public EasyAgentMcpServer(EasyAgentMcpProperties properties,
                              ToolRegistry toolRegistry,
                              ToolExecutor toolExecutor) {
        this.properties = properties;
        this.mcpToolAdapter = new McpToolAdapter(toolRegistry, toolExecutor);
    }

    public McpProtocol.JsonRpcResponse handleRequest(McpProtocol.JsonRpcRequest request) {
        try {
            Object result = switch (request.method()) {
                case "initialize" -> handleInitialize(request);
                case "notifications/initialized" -> null;
                case "tools/list" -> handleToolsList();
                case "tools/call" -> handleToolsCall(request);
                case "resources/list" -> handleResourcesList();
                case "prompts/list" -> handlePromptsList();
                case "ping" -> Map.of();
                default -> throw new UnsupportedOperationException("Method not found: " + request.method());
            };
            return McpProtocol.JsonRpcResponse.success(request.id(), result);
        } catch (UnsupportedOperationException e) {
            log.error("Method not found: {}", request.method(), e);
            return McpProtocol.JsonRpcResponse.error(request.id(), McpProtocol.METHOD_NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to handle MCP request: {}", request.method(), e);
            return McpProtocol.JsonRpcResponse.error(request.id(), McpProtocol.INTERNAL_ERROR, e.getMessage());
        }
    }

    private McpProtocol.InitializeResult handleInitialize(McpProtocol.JsonRpcRequest request) {
        log.info("MCP client initializing");
        return new McpProtocol.InitializeResult(
                McpProtocol.LATEST_PROTOCOL_VERSION,
                new McpProtocol.ServerCapabilities(
                        new McpProtocol.ToolCapabilities(false),
                        null,
                        null
                ),
                new McpProtocol.Implementation(properties.getServerName(), properties.getServerVersion())
        );
    }

    private McpProtocol.ListToolsResult handleToolsList() {
        List<McpProtocol.Tool> tools = mcpToolAdapter.convertToMcpTools();
        return new McpProtocol.ListToolsResult(tools);
    }

    private McpProtocol.CallToolResult handleToolsCall(McpProtocol.JsonRpcRequest request) {
        Map<String, Object> params = request.params();
        if (params == null) {
            return new McpProtocol.CallToolResult(
                    List.of(McpProtocol.Content.text("Error: missing parameters")),
                    true
            );
        }
        
        String toolName = (String) params.get("name");
        if (toolName == null || toolName.isEmpty()) {
            return new McpProtocol.CallToolResult(
                    List.of(McpProtocol.Content.text("Error: tool name is required")),
                    true
            );
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");
        log.info("MCP tool call: {}", toolName);
        return mcpToolAdapter.executeMcpTool(toolName, arguments);
    }

    private McpProtocol.ListResourcesResult handleResourcesList() {
        return new McpProtocol.ListResourcesResult(List.of());
    }

    private McpProtocol.ListPromptsResult handlePromptsList() {
        return new McpProtocol.ListPromptsResult(List.of());
    }

    public void start() {
        List<McpProtocol.Tool> tools = mcpToolAdapter.convertToMcpTools();
        log.info("Easy Agent MCP Server started - {} tools available", tools.size());
        for (McpProtocol.Tool tool : tools) {
            log.info("  MCP Tool: {} - {}", tool.name(), tool.description());
        }
    }
}
