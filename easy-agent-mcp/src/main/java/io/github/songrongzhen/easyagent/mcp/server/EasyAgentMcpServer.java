package io.github.songrongzhen.easyagent.mcp.server;

import io.github.songrongzhen.easyagent.core.executor.ToolExecutor;
import io.github.songrongzhen.easyagent.core.model.ToolCallContext;
import io.github.songrongzhen.easyagent.core.registry.ToolRegistry;
import io.github.songrongzhen.easyagent.mcp.adapter.McpToolAdapter;
import io.github.songrongzhen.easyagent.mcp.config.EasyAgentMcpProperties;
import io.github.songrongzhen.easyagent.mcp.protocol.McpErrorCode;
import io.github.songrongzhen.easyagent.mcp.protocol.McpErrorFactory;
import io.github.songrongzhen.easyagent.mcp.protocol.McpProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * 无状态 MCP 请求分发，显式向工具执行链传递调用上下文。
 */
public class EasyAgentMcpServer {

    /**
     * 当前组件的日志记录器。
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(EasyAgentMcpServer.class);

    /**
     * 组件配置。
     */
    private final EasyAgentMcpProperties properties;

    /**
     * MCP 工具发现与执行适配器。
     */
    private final McpToolAdapter mcpToolAdapter;

    /**
     * 创建 EasyAgentMcpServer，保存调用所需的显式依赖。
     *
     * @param properties   组件配置
     * @param toolRegistry 工具注册表
     * @param toolExecutor 公共工具执行器
     */
    public EasyAgentMcpServer(EasyAgentMcpProperties properties, ToolRegistry toolRegistry, ToolExecutor toolExecutor) {
        this.properties = properties;
        this.mcpToolAdapter = new McpToolAdapter(toolRegistry, toolExecutor,
            new McpToolExposurePolicy(properties.getToolExposure()));
    }

    /**
     * 校验并分发 MCP 请求，显式传递可信调用上下文。
     *
     * @param request MCP 请求
     * @return 承载成功结果或协议错误的 JSON-RPC 响应
     */
    public McpProtocol.JsonRpcResponse handleRequest(McpProtocol.JsonRpcRequest request) {
        return handleRequest(request, ToolCallContext.anonymous(ToolCallContext.SOURCE_MCP));
    }

    /**
     * 校验并分发 MCP 请求，显式传递可信调用上下文。
     *
     * @param request MCP 请求
     * @param context 可信调用上下文
     * @return 承载成功结果或协议错误的 JSON-RPC 响应
     */
    public McpProtocol.JsonRpcResponse handleRequest(McpProtocol.JsonRpcRequest request, ToolCallContext context) {
        if (request == null) {
            return McpErrorFactory.jsonRpcError(null, McpErrorCode.INVALID_REQUEST, "request body is required");
        }
        if (!McpProtocol.JSONRPC_VERSION.equals(request.jsonrpc()) || !(request.id() instanceof String
            || request.id() instanceof Integer || request.id() instanceof Long || request.id() instanceof BigInteger)
            || request.method() == null || request.method().isBlank()) {
            return McpErrorFactory.jsonRpcError(request.id(), McpErrorCode.INVALID_REQUEST, "method is required");
        }
        try {
            Object result = switch (request.method()) {
            case "initialize" ->
                handleInitialize(request);
            case "tools/list" ->
                handleToolsList();
            case "tools/call" ->
                handleToolsCall(request, context);
            case "resources/list" ->
                handleResourcesList();
            case "prompts/list" ->
                handlePromptsList();
            case "ping" ->
                Map.of();
            default ->
                throw new UnsupportedOperationException("Method not found: " + request.method());
            };
            return McpProtocol.JsonRpcResponse.success(request.id(), result);
        } catch (McpRequestException e) {
            LOGGER.warn("Invalid MCP request: method={}, message={}", request.method(), e.getMessage());
            return McpProtocol.JsonRpcResponse.error(request.id(), e.getCode(), e.getMessage());
        } catch (UnsupportedOperationException e) {
            LOGGER.error("Method not found: {}", request.method(), e);
            return McpErrorFactory.jsonRpcError(request.id(), McpErrorCode.METHOD_NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Failed to handle MCP request: {}", request.method(), e);
            return McpErrorFactory.jsonRpcError(request.id(), McpErrorCode.INTERNAL_ERROR);
        }
    }

    private McpProtocol.InitializeResult handleInitialize(McpProtocol.JsonRpcRequest request) {
        String protocolVersion = resolveProtocolVersion(request.params());
        LOGGER.info("MCP client initializing with protocolVersion={}", protocolVersion);
        return new McpProtocol.InitializeResult(protocolVersion,
            new McpProtocol.ServerCapabilities(new McpProtocol.ToolCapabilities(false), null, null),
            new McpProtocol.Implementation(properties.getServerName(), properties.getServerVersion()));
    }

    private String resolveProtocolVersion(Map<String, Object> params) {
        if (params == null || !(params.get("capabilities") instanceof Map<?, ?>)
            || !(params.get("clientInfo") instanceof Map<?, ?> clientInfo)
            || !(clientInfo.get("name") instanceof String name) || name.isBlank()
            || !(clientInfo.get("version") instanceof String version) || version.isBlank()) {
            throw new McpRequestException(McpErrorCode.INVALID_PARAMS.getCode(),
                "initialize requires capabilities and clientInfo");
        }
        if (!(params.get("protocolVersion") instanceof String requestedVersion) || requestedVersion.isBlank()) {
            throw new McpRequestException(McpErrorCode.INVALID_PARAMS.getCode(),
                "protocolVersion must be a non-empty string");
        }
        if (!McpProtocol.SUPPORTED_PROTOCOL_VERSIONS.contains(requestedVersion)) {
            return McpProtocol.LATEST_PROTOCOL_VERSION;
        }
        return requestedVersion;
    }

    private McpProtocol.ListToolsResult handleToolsList() {
        return new McpProtocol.ListToolsResult(mcpToolAdapter.convertToMcpTools());
    }

    private McpProtocol.CallToolResult handleToolsCall(McpProtocol.JsonRpcRequest request, ToolCallContext context) {
        Map<String, Object> params = request.params();
        if (params == null) {
            throw new McpRequestException(McpErrorCode.INVALID_PARAMS.getCode(), "缺少参数");
        }
        Object nameObj = params.get("name");
        if (!(nameObj instanceof String toolName)) {
            throw new McpRequestException(McpErrorCode.INVALID_PARAMS.getCode(), "tool name 必须是字符串");
        }
        if (toolName.isBlank()) {
            throw new McpRequestException(McpErrorCode.INVALID_PARAMS.getCode(), "tool name 不能为空");
        }
        Object argumentsObj = params.get("arguments");
        if (argumentsObj != null && !(argumentsObj instanceof Map<?, ?>)) {
            throw new McpRequestException(McpErrorCode.INVALID_PARAMS.getCode(), "arguments 必须是对象");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = argumentsObj instanceof Map<?, ?> ? (Map<String, Object>) argumentsObj
            : Map.of();
        LOGGER.info("MCP tool call: {}", toolName);
        return mcpToolAdapter.executeMcpTool(toolName, arguments, context);
    }

    private McpProtocol.ListResourcesResult handleResourcesList() {
        return new McpProtocol.ListResourcesResult(List.of());
    }

    private McpProtocol.ListPromptsResult handlePromptsList() {
        return new McpProtocol.ListPromptsResult(List.of());
    }

    /**
     * 记录服务启动事件，工具列表仍从注册表动态读取。
     */
    public void start() {
        LOGGER.info("Easy Agent MCP Server started - tools will be resolved dynamically from" + " ToolRegistry");
    }

    /**
     * 请求协议或参数无效时的内部受控异常。
     */
    private static class McpRequestException extends RuntimeException {

        /**
         * 稳定的错误码。
         */
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
