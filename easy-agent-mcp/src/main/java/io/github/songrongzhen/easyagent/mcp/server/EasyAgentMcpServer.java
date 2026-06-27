package io.github.songrongzhen.easyagent.mcp.server;

import io.github.songrongzhen.easyagent.core.executor.ToolExecutor;
import io.github.songrongzhen.easyagent.core.registry.ToolRegistry;
import io.github.songrongzhen.easyagent.mcp.adapter.McpToolAdapter;
import io.github.songrongzhen.easyagent.mcp.adapter.SkillMcpAdapter;
import io.github.songrongzhen.easyagent.mcp.config.EasyAgentMcpProperties;
import io.github.songrongzhen.easyagent.mcp.protocol.McpProtocol;
import io.github.songrongzhen.easyagent.skill.service.SkillGeneratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EasyAgentMcpServer {

    private static final Logger log = LoggerFactory.getLogger(EasyAgentMcpServer.class);

    private final EasyAgentMcpProperties properties;
    private final McpToolAdapter mcpToolAdapter;
    private final SkillMcpAdapter skillMcpAdapter;

    public EasyAgentMcpServer(EasyAgentMcpProperties properties,
                              ToolRegistry toolRegistry,
                              ToolExecutor toolExecutor,
                              SkillGeneratorService skillGeneratorService) {
        this.properties = properties;
        this.mcpToolAdapter = new McpToolAdapter(toolRegistry, toolExecutor);
        this.skillMcpAdapter = (skillGeneratorService != null) ? new SkillMcpAdapter(skillGeneratorService) : null;
    }

    public McpProtocol.JsonRpcResponse handleRequest(McpProtocol.JsonRpcRequest request) {
        if (request == null) {
            return McpProtocol.JsonRpcResponse.error(null, McpProtocol.INVALID_REQUEST, "Invalid request: request body is required");
        }
        if (request.method() == null || request.method().isBlank()) {
            return McpProtocol.JsonRpcResponse.error(request.id(), McpProtocol.INVALID_REQUEST, "Invalid request: method is required");
        }

        try {
            Object result = switch (request.method()) {
                case "initialize" -> handleInitialize(request);
                case "notifications/initialized" -> handleInitializedNotification();
                case "tools/list" -> handleToolsList();
                case "tools/call" -> handleToolsCall(request);
                case "resources/list" -> handleResourcesList();
                case "prompts/list" -> handlePromptsList();
                case "ping" -> Map.of();
                default -> throw new UnsupportedOperationException("Method not found: " + request.method());
            };
            return McpProtocol.JsonRpcResponse.success(request.id(), result);
        } catch (McpRequestException e) {
            log.warn("Invalid MCP request: method={}, message={}", request.method(), e.getMessage());
            return McpProtocol.JsonRpcResponse.error(request.id(), e.getCode(), e.getMessage());
        } catch (UnsupportedOperationException e) {
            log.error("Method not found: {}", request.method(), e);
            return McpProtocol.JsonRpcResponse.error(request.id(), McpProtocol.METHOD_NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to handle MCP request: {}", request.method(), e);
            return McpProtocol.JsonRpcResponse.error(request.id(), McpProtocol.INTERNAL_ERROR, e.getMessage());
        }
    }

    private McpProtocol.InitializeResult handleInitialize(McpProtocol.JsonRpcRequest request) {
        String protocolVersion = resolveProtocolVersion(request.params());
        log.info("MCP client initializing with protocolVersion={}", protocolVersion);
        return new McpProtocol.InitializeResult(
                protocolVersion,
                new McpProtocol.ServerCapabilities(
                        new McpProtocol.ToolCapabilities(false),
                        null,
                        null
                ),
                new McpProtocol.Implementation(properties.getServerName(), properties.getServerVersion())
        );
    }

    private String resolveProtocolVersion(Map<String, Object> params) {
        if (params == null || !params.containsKey("protocolVersion")) {
            return McpProtocol.LATEST_PROTOCOL_VERSION;
        }

        Object protocolVersionObj = params.get("protocolVersion");
        if (protocolVersionObj == null) {
            return McpProtocol.LATEST_PROTOCOL_VERSION;
        }
        if (!(protocolVersionObj instanceof String requestedVersion) || requestedVersion.isBlank()) {
            throw new McpRequestException(McpProtocol.INVALID_PARAMS, "Invalid protocolVersion: must be a string");
        }
        if (!McpProtocol.SUPPORTED_PROTOCOL_VERSIONS.contains(requestedVersion)) {
            throw new McpRequestException(McpProtocol.INVALID_PARAMS,
                    "Unsupported protocolVersion: " + requestedVersion
                            + ", supported versions: " + McpProtocol.SUPPORTED_PROTOCOL_VERSIONS);
        }
        return requestedVersion;
    }

    private Map<String, Object> handleInitializedNotification() {
        log.debug("MCP client initialized");
        return Map.of();
    }

    private McpProtocol.ListToolsResult handleToolsList() {
        List<McpProtocol.Tool> tools = new ArrayList<>(mcpToolAdapter.convertToMcpTools());
        if (skillMcpAdapter != null) {
            tools.addAll(skillMcpAdapter.getSkillTools());
        }
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
        
        Object nameObj = params.get("name");
        if (!(nameObj instanceof String toolName)) {
            return new McpProtocol.CallToolResult(
                    List.of(McpProtocol.Content.text("Error: tool name must be a string")),
                    true
            );
        }
        if (toolName == null || toolName.isEmpty()) {
            return new McpProtocol.CallToolResult(
                    List.of(McpProtocol.Content.text("Error: tool name is required")),
                    true
            );
        }

        Object argumentsObj = params.get("arguments");
        if (argumentsObj != null && !(argumentsObj instanceof Map<?, ?>)) {
            return new McpProtocol.CallToolResult(
                    List.of(McpProtocol.Content.text("Error: arguments must be an object")),
                    true
            );
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = argumentsObj instanceof Map<?, ?> ? (Map<String, Object>) argumentsObj : Map.of();
        
        if (toolName.startsWith("skill.")) {
            if (skillMcpAdapter == null) {
                return new McpProtocol.CallToolResult(
                        List.of(McpProtocol.Content.text("Error: Skill module is not enabled")),
                        true
                );
            }
            log.info("MCP skill tool call: {}", toolName);
            return skillMcpAdapter.executeSkillTool(toolName, arguments);
        }
        
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
        List<McpProtocol.Tool> tools = new ArrayList<>(mcpToolAdapter.convertToMcpTools());
        if (skillMcpAdapter != null) {
            tools.addAll(skillMcpAdapter.getSkillTools());
        }
        log.info("Easy Agent MCP Server started - {} tools available", tools.size());
        for (McpProtocol.Tool tool : tools) {
            log.info("  MCP Tool: {} - {}", tool.name(), tool.description());
        }
    }

    private static class McpRequestException extends RuntimeException {
        private final int code;

        private McpRequestException(int code, String message) {
            super(message);
            this.code = code;
        }

        private int getCode() {
            return code;
        }
    }
}
