package io.github.songrongzhen.easyagent.mcp.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
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

import java.util.Map;

@RestController
@RequestMapping
public class McpController {

    private static final Logger log = LoggerFactory.getLogger(McpController.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final EasyAgentMcpServer mcpServer;
    private final EasyAgentMcpProperties properties;

    public McpController(EasyAgentMcpServer mcpServer, EasyAgentMcpProperties properties) {
        this.mcpServer = mcpServer;
        this.properties = properties;
    }

    @PostMapping(value = "/mcp",
                 consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> handleHttpMcp(@RequestBody String requestBody) {
        McpProtocol.JsonRpcRequest request;
        try {
            request = OBJECT_MAPPER.readValue(requestBody, McpProtocol.JsonRpcRequest.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse MCP JSON-RPC request", e);
            return jsonResponse(McpProtocol.JsonRpcResponse.error(null, McpProtocol.PARSE_ERROR, "Parse error: " + e.getOriginalMessage()));
        } catch (Exception e) {
            log.error("Failed to handle HTTP MCP request", e);
            return jsonResponse(McpProtocol.JsonRpcResponse.error(null, McpProtocol.INTERNAL_ERROR, e.getMessage()));
        }

        log.debug("Received HTTP MCP request: method={}, id={}", request.method(), request.id());
        McpProtocol.JsonRpcResponse response = mcpServer.handleRequest(request);
        if (request.id() == null) {
            return ResponseEntity.ok().build();
        }
        return jsonResponse(response);
    }

    @GetMapping("/mcp/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("{\"status\":\"ok\"}");
    }

    @GetMapping("/mcp/ready")
    public ResponseEntity<String> readyCheck() {
        return ResponseEntity.ok("{\"status\":\"ready\",\"protocolVersion\":\"" + McpProtocol.LATEST_PROTOCOL_VERSION + "\"}");
    }

    @GetMapping(value = "/mcp")
    public ResponseEntity<String> handleGetMessage(@RequestParam("method") String method,
                                                   @RequestParam(value = "params", required = false) String paramsJson) {
        try {
            Map<String, Object> params = null;
            if (paramsJson != null && !paramsJson.isEmpty()) {
                params = OBJECT_MAPPER.readValue(paramsJson, Map.class);
            }
            
            McpProtocol.JsonRpcRequest request = new McpProtocol.JsonRpcRequest("2.0", "1", method, params);
            McpProtocol.JsonRpcResponse response = mcpServer.handleRequest(request);

            return jsonResponse(response);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse MCP GET params", e);
            return jsonResponse(McpProtocol.JsonRpcResponse.error("1", McpProtocol.PARSE_ERROR, "Parse error: " + e.getOriginalMessage()));
        } catch (Exception e) {
            log.error("Failed to handle MCP GET request", e);
            return jsonResponse(McpProtocol.JsonRpcResponse.error("1", McpProtocol.INTERNAL_ERROR, e.getMessage()));
        }
    }

    private ResponseEntity<String> jsonResponse(McpProtocol.JsonRpcResponse response) {
        try {
            String responseBody = OBJECT_MAPPER.writeValueAsString(response);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            return ResponseEntity.ok().headers(headers).body(responseBody);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize MCP JSON-RPC response", e);
        }
    }
}
