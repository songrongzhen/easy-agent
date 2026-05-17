package io.github.songrongzhen.easyagent.mcp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.songrongzhen.easyagent.mcp.config.EasyAgentMcpProperties;
import io.github.songrongzhen.easyagent.mcp.protocol.McpProtocol;
import io.github.songrongzhen.easyagent.mcp.server.EasyAgentMcpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping
public class McpController {

    private static final Logger log = LoggerFactory.getLogger(McpController.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final EasyAgentMcpServer mcpServer;
    private final EasyAgentMcpProperties properties;
    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    public record SessionInfo(SseEmitter emitter, boolean initialized) {}

    public McpController(EasyAgentMcpServer mcpServer, EasyAgentMcpProperties properties) {
        this.mcpServer = mcpServer;
        this.properties = properties;
    }

    @GetMapping(value = "${easy-agent.mcp.sse-endpoint:/mcp/sse}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sseConnect() {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        String sessionId = UUID.randomUUID().toString();
        
        sessions.put(sessionId, new SessionInfo(emitter, false));

        emitter.onCompletion(() -> {
            sessions.remove(sessionId);
            log.debug("SSE session completed: {}", sessionId);
        });
        emitter.onTimeout(() -> {
            sessions.remove(sessionId);
            log.debug("SSE session timeout: {}", sessionId);
        });
        emitter.onError(e -> {
            sessions.remove(sessionId);
            log.debug("SSE session error: {}", sessionId);
        });

        try {
            String messageEndpoint = properties.getMessageEndpoint() + "?sessionId=" + sessionId;
            emitter.send(SseEmitter.event()
                    .name("endpoint")
                    .data(messageEndpoint));
            log.info("SSE connection established, sessionId: {}", sessionId);
        } catch (IOException e) {
            log.error("Failed to send SSE endpoint event", e);
        }

        return emitter;
    }

    @PostMapping(value = "/mcp",
                 consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> handleHttpMcp(@RequestBody String requestBody) {
        try {
            McpProtocol.JsonRpcRequest request = OBJECT_MAPPER.readValue(requestBody, McpProtocol.JsonRpcRequest.class);
            log.debug("Received HTTP MCP request: method={}, id={}", request.method(), request.id());

            // Check if this is a notification (no id)
            if (request.id() == null) {
                log.debug("Received notification: {}", request.method());
                // For notifications, just return 200 OK without body
                return ResponseEntity.ok().build();
            }

            McpProtocol.JsonRpcResponse response = mcpServer.handleRequest(request);
            String responseBody = OBJECT_MAPPER.writeValueAsString(response);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Access-Control-Allow-Origin", "*");
            return ResponseEntity.ok().headers(headers).body(responseBody);
        } catch (Exception e) {
            log.error("Failed to handle HTTP MCP request", e);
            return ResponseEntity.internalServerError().body("{\"jsonrpc\":\"2.0\",\"id\":null,\"result\":null,\"error\":{\"code\":-32603,\"message\":\"" + e.getMessage() + "\"}}");
        }
    }

    @PostMapping(value = "${easy-agent.mcp.message-endpoint:/mcp/messages}",
                 consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> handleMessage(
            @RequestBody String requestBody,
            @RequestParam(value = "sessionId", required = false) String sessionId) {
        try {
            McpProtocol.JsonRpcRequest request = OBJECT_MAPPER.readValue(requestBody, McpProtocol.JsonRpcRequest.class);
            log.debug("Received MCP request: method={}, id={}", request.method(), request.id());

            if (request.id() == null) {
                log.debug("Received notification: {}", request.method());
                return ResponseEntity.ok().build();
            }

            McpProtocol.JsonRpcResponse response = mcpServer.handleRequest(request);

            if ("initialize".equals(request.method())) {
                if (sessionId != null && sessions.containsKey(sessionId)) {
                    sessions.put(sessionId, new SessionInfo(sessions.get(sessionId).emitter(), true));
                    log.info("MCP session initialized: {}", sessionId);
                }
            }

            String responseBody = OBJECT_MAPPER.writeValueAsString(response);

            if (sessionId != null && sessions.containsKey(sessionId)) {
                SessionInfo session = sessions.get(sessionId);
                try {
                    session.emitter().send(SseEmitter.event()
                            .name("message")
                            .data(responseBody));
                    log.debug("Sent response via SSE: sessionId={}, method={}", sessionId, request.method());
                } catch (IOException e) {
                    log.error("Failed to send SSE event", e);
                }
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Access-Control-Allow-Origin", "*");
            return ResponseEntity.ok().headers(headers).body(responseBody);
        } catch (Exception e) {
            log.error("Failed to handle MCP message", e);
            return ResponseEntity.internalServerError().body("{\"jsonrpc\":\"2.0\",\"id\":null,\"result\":null,\"error\":{\"code\":-32603,\"message\":\"" + e.getMessage() + "\"}}");
        }
    }

    @GetMapping("/mcp/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("{\"status\":\"ok\"}");
    }

    @GetMapping("/mcp/ready")
    public ResponseEntity<String> readyCheck() {
        return ResponseEntity.ok("{\"status\":\"ready\",\"protocolVersion\":\"" + McpProtocol.LATEST_PROTOCOL_VERSION + "\"}");
    }

    @GetMapping(value = "${easy-agent.mcp.message-endpoint:/mcp/messages}")
    public ResponseEntity<String> handleGetMessage(@RequestParam("method") String method,
                                                   @RequestParam(value = "params", required = false) String paramsJson) {
        try {
            Map<String, Object> params = null;
            if (paramsJson != null && !paramsJson.isEmpty()) {
                params = OBJECT_MAPPER.readValue(paramsJson, Map.class);
            }
            
            McpProtocol.JsonRpcRequest request = new McpProtocol.JsonRpcRequest("2.0", "1", method, params);
            McpProtocol.JsonRpcResponse response = mcpServer.handleRequest(request);

            String responseBody = OBJECT_MAPPER.writeValueAsString(response);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Access-Control-Allow-Origin", "*");
            return ResponseEntity.ok().headers(headers).body(responseBody);
        } catch (Exception e) {
            log.error("Failed to handle MCP GET request", e);
            return ResponseEntity.internalServerError().body("{\"jsonrpc\":\"2.0\",\"id\":null,\"result\":null,\"error\":{\"code\":-32603,\"message\":\"" + e.getMessage() + "\"}}");
        }
    }
}
