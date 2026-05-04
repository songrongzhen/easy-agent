package io.github.songrongzhen.easyagent.mcp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.songrongzhen.easyagent.mcp.config.EasyAgentMcpProperties;
import io.github.songrongzhen.easyagent.mcp.protocol.McpProtocol;
import io.github.songrongzhen.easyagent.mcp.server.EasyAgentMcpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping
public class McpSseController {

    private static final Logger log = LoggerFactory.getLogger(McpSseController.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final EasyAgentMcpServer mcpServer;
    private final EasyAgentMcpProperties properties;
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public McpSseController(EasyAgentMcpServer mcpServer, EasyAgentMcpProperties properties) {
        this.mcpServer = mcpServer;
        this.properties = properties;
    }

    @GetMapping(value = "${easy-agent.mcp.sse-endpoint:/mcp/sse}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sseConnect() {
        SseEmitter emitter = new SseEmitter(0L);
        String sessionId = java.util.UUID.randomUUID().toString();
        emitters.put(sessionId, emitter);

        emitter.onCompletion(() -> emitters.remove(sessionId));
        emitter.onTimeout(() -> emitters.remove(sessionId));
        emitter.onError(e -> emitters.remove(sessionId));

        try {
            String messageEndpoint = properties.getMessageEndpoint() + "?sessionId=" + sessionId;
            emitter.send(SseEmitter.event()
                    .name("endpoint")
                    .data(messageEndpoint));
        } catch (IOException e) {
            log.error("Failed to send SSE endpoint event", e);
        }

        return emitter;
    }

    @PostMapping(value = "${easy-agent.mcp.message-endpoint:/mcp/messages}",
                 consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> handleMessage(
            @RequestBody String requestBody,
            @RequestParam(value = "sessionId", required = false) String sessionId) {
        try {
            McpProtocol.JsonRpcRequest request = OBJECT_MAPPER.readValue(requestBody, McpProtocol.JsonRpcRequest.class);
            McpProtocol.JsonRpcResponse response = mcpServer.handleRequest(request);

            String responseBody = OBJECT_MAPPER.writeValueAsString(response);

            if (sessionId != null && emitters.containsKey(sessionId)) {
                SseEmitter emitter = emitters.get(sessionId);
                try {
                    emitter.send(SseEmitter.event()
                            .name("message")
                            .data(responseBody));
                } catch (IOException e) {
                    log.error("Failed to send SSE event", e);
                }
            }

            return ResponseEntity.ok(responseBody);
        } catch (Exception e) {
            log.error("Failed to handle MCP message", e);
            return ResponseEntity.internalServerError().body("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
}
