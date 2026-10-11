package io.github.songrongzhen.easyagent.mcp.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Map;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.songrongzhen.easyagent.mcp.config.EasyAgentMcpProperties;
import io.github.songrongzhen.easyagent.mcp.protocol.McpErrorCode;
import io.github.songrongzhen.easyagent.mcp.protocol.McpErrorFactory;
import io.github.songrongzhen.easyagent.mcp.protocol.McpProtocol;
import io.github.songrongzhen.easyagent.mcp.server.EasyAgentMcpServer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.cors.CorsConfiguration;

@RestController
public class McpController {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private final EasyAgentMcpServer mcpServer;
    private final EasyAgentMcpProperties properties;

    public McpController(EasyAgentMcpServer mcpServer, EasyAgentMcpProperties properties) {
        this.mcpServer = mcpServer;
        this.properties = properties;
    }

    @PostMapping(value = "/mcp", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> handleHttpMcp(@RequestBody(required = false) String body,
                                               @RequestHeader HttpHeaders headers) {
        ResponseEntity<String> rejected = validateTransport(headers);
        if (rejected != null) return rejected;
        if (!accepts(headers, MediaType.APPLICATION_JSON) || !accepts(headers, MediaType.TEXT_EVENT_STREAM)) {
            return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
        }

        JsonNode message;
        try {
            message = OBJECT_MAPPER.readTree(body == null ? "" : body);
        } catch (JsonProcessingException e) {
            return error(McpErrorCode.PARSE_ERROR, "Invalid JSON");
        }
        if (message == null || message.isMissingNode()) return error(McpErrorCode.PARSE_ERROR, "Empty body");
        if (!message.isObject() || !message.path("jsonrpc").isTextual()
                || !McpProtocol.JSONRPC_VERSION.equals(message.path("jsonrpc").textValue())) {
            return error(McpErrorCode.INVALID_REQUEST, "Expected a single JSON-RPC 2.0 object");
        }
        JsonNode method = message.get("method");
        JsonNode id = message.get("id");
        if (method == null || !method.isTextual() || method.textValue().isBlank()
                || message.has("result") || message.has("error")
                || (id != null && !(id.isTextual() || id.isIntegralNumber()))) {
            // This stateless server never sends client requests, so unsolicited responses are rejected.
            return error(McpErrorCode.INVALID_REQUEST, "Invalid method or id; client responses are not expected");
        }
        JsonNode params = message.get("params");
        if (params != null && !params.isObject()) {
            return jsonResponse(HttpStatus.BAD_REQUEST, McpErrorFactory.jsonRpcError(
                    id == null ? null : OBJECT_MAPPER.convertValue(id, Object.class),
                    McpErrorCode.INVALID_PARAMS, "params must be an object"));
        }
        boolean notification = id == null;
        if (notification != method.textValue().startsWith("notifications/")) {
            return error(McpErrorCode.INVALID_REQUEST, "Requests require an id; notifications must omit it");
        }
        McpProtocol.JsonRpcRequest request = new McpProtocol.JsonRpcRequest(
                McpProtocol.JSONRPC_VERSION,
                id == null ? null : OBJECT_MAPPER.convertValue(id, Object.class),
                method.textValue(),
                params == null ? null : OBJECT_MAPPER.convertValue(params, new TypeReference<Map<String, Object>>() {}));
        if (notification) {
            // Notifications cannot invoke tools. Unknown notifications are safely ignored.
            return ResponseEntity.accepted().build();
        }
        return jsonResponse(HttpStatus.OK, mcpServer.handleRequest(request));
    }

    @GetMapping("/mcp")
    public ResponseEntity<String> handleGetMessage(@RequestHeader HttpHeaders headers) {
        ResponseEntity<String> rejected = validateTransport(headers);
        if (rejected != null) return rejected;
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).header(HttpHeaders.ALLOW, "POST").build();
    }

    @DeleteMapping("/mcp")
    public ResponseEntity<String> deleteSession(@RequestHeader HttpHeaders headers) {
        return handleGetMessage(headers);
    }

    @GetMapping("/mcp/health")
    public ResponseEntity<String> healthCheck(@RequestHeader HttpHeaders headers) {
        ResponseEntity<String> rejected = validateTransport(headers);
        return rejected != null ? rejected : ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body("{\"status\":\"ok\"}");
    }

    @GetMapping("/mcp/ready")
    public ResponseEntity<String> readyCheck(@RequestHeader HttpHeaders headers) {
        ResponseEntity<String> rejected = validateTransport(headers);
        return rejected != null ? rejected : ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body("{\"status\":\"ready\",\"protocolVersion\":\"" + McpProtocol.LATEST_PROTOCOL_VERSION + "\"}");
    }

    private ResponseEntity<String> validateTransport(HttpHeaders headers) {
        String origin = headers.getOrigin();
        if (origin != null) {
            CorsConfiguration config = new CorsConfiguration();
            if (properties.getCors() != null) config.setAllowedOriginPatterns(properties.getCors().getAllowedOriginPatterns());
            if (config.checkOrigin(origin) == null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
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
