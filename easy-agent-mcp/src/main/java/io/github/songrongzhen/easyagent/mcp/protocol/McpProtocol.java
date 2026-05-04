package io.github.songrongzhen.easyagent.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class McpProtocol {

    public static final String JSONRPC_VERSION = "2.0";
    public static final String LATEST_PROTOCOL_VERSION = "2024-11-05";

    public record JsonRpcRequest(
            String jsonrpc,
            Object id,
            String method,
            Map<String, Object> params
    ) {
        public JsonRpcRequest {
            if (jsonrpc == null) jsonrpc = JSONRPC_VERSION;
        }
    }

    public record JsonRpcResponse(
            String jsonrpc,
            Object id,
            Object result,
            JsonRpcError error
    ) {
        public JsonRpcResponse {
            if (jsonrpc == null) jsonrpc = JSONRPC_VERSION;
        }

        public static JsonRpcResponse success(Object id, Object result) {
            return new JsonRpcResponse(JSONRPC_VERSION, id, result, null);
        }

        public static JsonRpcResponse error(Object id, int code, String message) {
            return new JsonRpcResponse(JSONRPC_VERSION, id, null, new JsonRpcError(code, message));
        }
    }

    public record JsonRpcError(int code, String message, Object data) {
        public JsonRpcError(int code, String message) {
            this(code, message, null);
        }
    }

    public record InitializeResult(
            @JsonProperty("protocolVersion") String protocolVersion,
            @JsonProperty("capabilities") ServerCapabilities capabilities,
            @JsonProperty("serverInfo") Implementation serverInfo
    ) {}

    public record Implementation(
            @JsonProperty("name") String name,
            @JsonProperty("version") String version
    ) {}

    public record ServerCapabilities(
            @JsonProperty("tools") ToolCapabilities tools,
            @JsonProperty("resources") Object resources,
            @JsonProperty("prompts") Object prompts
    ) {}

    public record ToolCapabilities(
            @JsonProperty("listChanged") boolean listChanged
    ) {}

    public record ListToolsResult(
            @JsonProperty("tools") List<Tool> tools
    ) {}

    public record Tool(
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("inputSchema") JsonSchema inputSchema
    ) {}

    public record JsonSchema(
            @JsonProperty("type") String type,
            @JsonProperty("properties") Map<String, PropertyDef> properties,
            @JsonProperty("required") List<String> required
    ) {}

    public record PropertyDef(
            @JsonProperty("type") String type,
            @JsonProperty("description") String description
    ) {}

    public record CallToolResult(
            @JsonProperty("content") List<Content> content,
            @JsonProperty("isError") boolean isError
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Content(
            @JsonProperty("type") String type,
            @JsonProperty("text") String text
    ) {
        public static Content text(String text) {
            return new Content("text", text);
        }
    }

    public record ListResourcesResult(
            @JsonProperty("resources") List<Object> resources
    ) {}

    public record ListPromptsResult(
            @JsonProperty("prompts") List<Object> prompts
    ) {}
}
