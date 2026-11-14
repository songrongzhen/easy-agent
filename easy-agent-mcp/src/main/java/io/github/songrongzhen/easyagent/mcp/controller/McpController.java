package io.github.songrongzhen.easyagent.mcp.controller;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.songrongzhen.easyagent.core.model.ToolCallContext;
import io.github.songrongzhen.easyagent.mcp.config.EasyAgentMcpProperties;
import io.github.songrongzhen.easyagent.mcp.context.McpCallContextResolver;
import io.github.songrongzhen.easyagent.mcp.protocol.McpErrorCode;
import io.github.songrongzhen.easyagent.mcp.protocol.McpErrorFactory;
import io.github.songrongzhen.easyagent.mcp.protocol.McpProtocol;
import io.github.songrongzhen.easyagent.mcp.server.EasyAgentMcpServer;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.cors.CorsConfiguration;
import java.util.Map;
import java.util.Objects;

/**
 * MCP HTTP 边界，校验传输和协议后交由宿主解析可信身份。
 */
@RestController
public class McpController {

    /**
     * 当前边界使用的 JSON 编解码器。
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    /**
     * MCP 请求分发器。
     */
    private final EasyAgentMcpServer mcpServer;

    /**
     * 组件配置。
     */
    private final EasyAgentMcpProperties properties;

    /**
     * 宿主可信身份解析器。
     */
    private final McpCallContextResolver contextResolver;

    /**
     * 创建 MCP HTTP 边界并注入可信上下文解析器。
     *
     * @param mcpServer  MCP 请求分发器
     * @param properties 组件配置
     */
    public McpController(EasyAgentMcpServer mcpServer, EasyAgentMcpProperties properties) {
        this(mcpServer, properties, McpCallContextResolver.anonymous());
    }

    /**
     * 创建 MCP HTTP 边界并注入可信上下文解析器。
     *
     * @param mcpServer       MCP 请求分发器
     * @param properties      组件配置
     * @param contextResolver 宿主可信身份解析器
     */
    public McpController(EasyAgentMcpServer mcpServer, EasyAgentMcpProperties properties,
        McpCallContextResolver contextResolver) {
        this.contextResolver = contextResolver;
        this.mcpServer = mcpServer;
        this.properties = properties;
    }

    /**
     * 校验 HTTP 与 JSON-RPC 边界并通过宿主解析器取得可信上下文。
     *
     * @param body    请求正文
     * @param headers 传输层请求头
     * @return 根据协议和传输校验结果构造的 HTTP 响应
     */
    public ResponseEntity<String> handleHttpMcp(String body, HttpHeaders headers) {
        return handleHttpMcp(body, headers, null);
    }

    /**
     * 校验 HTTP 与 JSON-RPC 边界并通过宿主解析器取得可信上下文。
     *
     * @param body           请求正文
     * @param headers        传输层请求头
     * @param servletRequest 宿主已经完成认证处理的 HTTP 请求
     * @return 根据协议和传输校验结果构造的 HTTP 响应
     */
    @PostMapping(value = "/mcp", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> handleHttpMcp(@RequestBody(required = false) String body,
        @RequestHeader HttpHeaders headers, HttpServletRequest servletRequest) {
        ResponseEntity<String> rejected = validateTransport(headers);
        if (rejected != null) {
            return rejected;
        }
        if (!accepts(headers, MediaType.APPLICATION_JSON) || !accepts(headers, MediaType.TEXT_EVENT_STREAM)) {
            return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
        }
        JsonNode message;
        try {
            message = OBJECT_MAPPER.readTree(body == null ? "" : body);
        } catch (JsonProcessingException e) {
            return error(McpErrorCode.PARSE_ERROR, "Invalid JSON");
        }
        if (message == null || message.isMissingNode()) {
            return error(McpErrorCode.PARSE_ERROR, "Empty body");
        }
        if (!message.isObject() || !message.path("jsonrpc").isTextual()
            || !McpProtocol.JSONRPC_VERSION.equals(message.path("jsonrpc").textValue())) {
            return error(McpErrorCode.INVALID_REQUEST, "Expected a single JSON-RPC 2.0 object");
        }
        JsonNode method = message.get("method");
        JsonNode id = message.get("id");
        if (method == null || !method.isTextual() || method.textValue().isBlank() || message.has("result")
            || message.has("error") || (id != null && !(id.isTextual() || id.isIntegralNumber()))) {
            // This stateless server never sends client requests, so unsolicited responses are
            // rejected.
            return error(McpErrorCode.INVALID_REQUEST, "Invalid method or id; client responses are not expected");
        }
        JsonNode params = message.get("params");
        if (params != null && !params.isObject()) {
            return jsonResponse(HttpStatus.BAD_REQUEST,
                McpErrorFactory.jsonRpcError(id == null ? null : OBJECT_MAPPER.convertValue(id, Object.class),
                    McpErrorCode.INVALID_PARAMS, "params must be an object"));
        }
        boolean notification = id == null;
        if (notification != method.textValue().startsWith("notifications/")) {
            return error(McpErrorCode.INVALID_REQUEST, "Requests require an id; notifications must omit it");
        }
        McpProtocol.JsonRpcRequest request = new McpProtocol.JsonRpcRequest(McpProtocol.JSONRPC_VERSION,
            id == null ? null : OBJECT_MAPPER.convertValue(id, Object.class), method.textValue(),
            params == null ? null : OBJECT_MAPPER.convertValue(params, new TypeReference<Map<String, Object>>() {
            }));
        if (notification) {
            // Notifications cannot invoke tools. Unknown notifications are safely ignored.
            return ResponseEntity.accepted().build();
        }
        try {
            var context = Objects.requireNonNull(contextResolver.resolve(servletRequest));
            return jsonResponse(HttpStatus.OK,
                mcpServer.handleRequest(request, context.withSource(ToolCallContext.SOURCE_MCP)));
        } catch (McpCallContextResolver.AuthenticationException | SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    /**
     * 校验传输头并响应不支持的独立消息流请求。
     *
     * @param headers 传输层请求头
     * @return 根据协议和传输校验结果构造的 HTTP 响应
     */
    @GetMapping("/mcp")
    public ResponseEntity<String> handleGetMessage(@RequestHeader HttpHeaders headers) {
        ResponseEntity<String> rejected = validateTransport(headers);
        if (rejected != null) {
            return rejected;
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).header(HttpHeaders.ALLOW, "POST").build();
    }

    /**
     * 校验传输头并拒绝无状态服务不支持的会话删除请求。
     *
     * @param headers 传输层请求头
     * @return 根据协议和传输校验结果构造的 HTTP 响应
     */
    @DeleteMapping("/mcp")
    public ResponseEntity<String> deleteSession(@RequestHeader HttpHeaders headers) {
        return handleGetMessage(headers);
    }

    /**
     * 返回服务存活状态。
     *
     * @param headers 传输层请求头
     * @return 根据协议和传输校验结果构造的 HTTP 响应
     */
    @GetMapping("/mcp/health")
    public ResponseEntity<String> healthCheck(@RequestHeader HttpHeaders headers) {
        ResponseEntity<String> rejected = validateTransport(headers);
        return rejected != null ? rejected
            : ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body("{\"status\":\"ok\"}");
    }

    /**
     * 返回当前工具发现是否已经就绪。
     *
     * @param headers 传输层请求头
     * @return 根据协议和传输校验结果构造的 HTTP 响应
     */
    @GetMapping("/mcp/ready")
    public ResponseEntity<String> readyCheck(@RequestHeader HttpHeaders headers) {
        ResponseEntity<String> rejected = validateTransport(headers);
        return rejected != null ? rejected
            : ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body("{\"status\":\"ready\",\"protocolVersion\":\"" + McpProtocol.LATEST_PROTOCOL_VERSION + "\"}");
    }

    private ResponseEntity<String> validateTransport(HttpHeaders headers) {
        String origin = headers.getOrigin();
        if (origin != null) {
            CorsConfiguration config = new CorsConfiguration();
            if (properties.getCors() != null) {
                config.setAllowedOriginPatterns(properties.getCors().getAllowedOriginPatterns());
            }
            if (config.checkOrigin(origin) == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }
        String version = headers.getFirst("MCP-Protocol-Version");
        // With no session state, a missing header uses the 2025-03-26 compatibility baseline.
        if (version != null && !McpProtocol.SUPPORTED_PROTOCOL_VERSIONS.contains(version)) {
            return error(McpErrorCode.INVALID_REQUEST, "Unsupported MCP-Protocol-Version");
        }
        return null;
    }

    private boolean accepts(HttpHeaders headers, MediaType required) {
        try {
            return headers.getAccept().stream().anyMatch(type -> type.getQualityValue() > 0 && type.includes(required));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private ResponseEntity<String> error(McpErrorCode code, String detail) {
        return jsonResponse(HttpStatus.BAD_REQUEST, McpErrorFactory.jsonRpcError(null, code, detail));
    }

    private ResponseEntity<String> jsonResponse(HttpStatus status, McpProtocol.JsonRpcResponse response) {
        try {
            return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(OBJECT_MAPPER.writeValueAsString(response));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize MCP JSON-RPC response", e);
        }
    }
}
