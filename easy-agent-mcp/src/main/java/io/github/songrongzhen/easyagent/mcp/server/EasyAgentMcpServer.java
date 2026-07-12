package io.github.songrongzhen.easyagent.mcp.server;

import io.github.songrongzhen.easyagent.core.executor.ToolExecutor;
import io.github.songrongzhen.easyagent.core.registry.ToolRegistry;
import io.github.songrongzhen.easyagent.mcp.adapter.McpToolAdapter;
import io.github.songrongzhen.easyagent.mcp.config.EasyAgentMcpProperties;
import io.github.songrongzhen.easyagent.mcp.protocol.McpErrorCode;
import io.github.songrongzhen.easyagent.mcp.protocol.McpErrorFactory;
import io.github.songrongzhen.easyagent.mcp.protocol.McpProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
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
        this.mcpToolAdapter = new McpToolAdapter(toolRegistry, toolExecutor,
                new McpToolExposurePolicy(properties.getToolExposure()));
    }

    public McpProtocol.JsonRpcResponse handleRequest(McpProtocol.JsonRpcRequest request) {
        if (request == null) {
            return McpErrorFactory.jsonRpcError(null, McpErrorCode.INVALID_REQUEST, "request body is required");
        }
        if (request.method() == null || request.method().isBlank()) {
            return McpErrorFactory.jsonRpcError(request.id(), McpErrorCode.INVALID_REQUEST, "method is required");
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
            return McpErrorFactory.jsonRpcError(request.id(), McpErrorCode.METHOD_NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to handle MCP request: {}", request.method(), e);
            return McpErrorFactory.jsonRpcError(request.id(), McpErrorCode.INTERNAL_ERROR, e.getMessage());
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
            throw new McpRequestException(McpErrorCode.INVALID_PARAMS.getCode(), McpErrorCode.INVALID_PARAMS.getMessage());
        }
        if (!McpProtocol.SUPPORTED_PROTOCOL_VERSIONS.contains(requestedVersion)) {
            throw new McpRequestException(McpErrorCode.INVALID_PARAMS.getCode(),
                    McpErrorFactory.buildMessage(McpErrorCode.INVALID_PARAMS,
                            "不支持的 protocolVersion: " + requestedVersion + "，当前支持: " + McpProtocol.SUPPORTED_PROTOCOL_VERSIONS));
        }
        return requestedVersion;
    }

    private Map<String, Object> handleInitializedNotification() {
        log.debug("MCP client initialized");
        return Map.of();
    }

    private McpProtocol.ListToolsResult handleToolsList() {
        List<McpProtocol.Tool> tools = new ArrayList<>(mcpToolAdapter.convertToMcpTools());
        if (properties.getToolExposure() != null && properties.getToolExposure().isEnabled()) {
            tools = tools.stream()
                    .filter(tool -> mcpToolAdapter.getExposurePolicy().isAllowed(tool.name()))
                    .toList();
        }
        return new McpProtocol.ListToolsResult(tools);
    }

    private McpProtocol.CallToolResult handleToolsCall(McpProtocol.JsonRpcRequest request) {
        Map<String, Object> params = request.params();
        if (params == null) {
            return McpErrorFactory.toolError(McpErrorCode.INVALID_PARAMS, "缺少参数");
        }
        
        Object nameObj = params.get("name");
        if (!(nameObj instanceof String toolName)) {
            return McpErrorFactory.toolError(McpErrorCode.INVALID_PARAMS, "tool name 必须是字符串");
        }
        if (toolName == null || toolName.isEmpty()) {
            return McpErrorFactory.toolError(McpErrorCode.INVALID_PARAMS, "tool name 不能为空");
        }

        Object argumentsObj = params.get("arguments");
        if (argumentsObj != null && !(argumentsObj instanceof Map<?, ?>)) {
            return McpErrorFactory.toolError(McpErrorCode.INVALID_PARAMS, "arguments 必须是对象");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = argumentsObj instanceof Map<?, ?> ? (Map<String, Object>) argumentsObj : Map.of();

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
        log.info("Easy Agent MCP Server started - tools will be resolved dynamically from ToolRegistry");
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
