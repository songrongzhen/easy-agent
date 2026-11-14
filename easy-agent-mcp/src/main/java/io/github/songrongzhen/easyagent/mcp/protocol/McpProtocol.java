package io.github.songrongzhen.easyagent.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.songrongzhen.easyagent.core.model.ToolCallContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 协议消息及 Schema 数据结构。
 */
public class McpProtocol {

    /**
     * JSON-RPC 协议版本。
     */
    public static final String JSONRPC_VERSION = "2.0";

    /**
     * 默认协商的 MCP 协议版本。
     */
    public static final String LATEST_PROTOCOL_VERSION = "2025-11-25";

    /**
     * 当前服务支持协商的 MCP 协议版本集合。
     */
    public static final List<String> SUPPORTED_PROTOCOL_VERSIONS = List.of(LATEST_PROTOCOL_VERSION, "2025-06-18",
        "2025-03-26", "2024-11-05");

    /**
     * JSON 解析失败错误码。
     */
    public static final int PARSE_ERROR = -32700;

    /**
     * JSON-RPC 请求无效错误码。
     */
    public static final int INVALID_REQUEST = -32600;

    /**
     * JSON-RPC 方法不存在错误码。
     */
    public static final int METHOD_NOT_FOUND = -32601;

    /**
     * JSON-RPC 参数无效错误码。
     */
    public static final int INVALID_PARAMS = -32602;

    /**
     * JSON-RPC 内部错误码。
     */
    public static final int INTERNAL_ERROR = -32603;

    /**
     * 单条 JSON-RPC 请求消息。
     *
     * @param jsonrpc JSON-RPC 协议版本
     * @param id      JSON-RPC 请求关联标识
     * @param method  显式目标方法
     * @param params  JSON-RPC 协议参数
     */
    public record JsonRpcRequest(String jsonrpc, Object id, String method, Map<String, Object> params) {
    }

    /**
     * JSON-RPC 响应，按协议承载成功结果或错误。
     *
     * @param jsonrpc JSON-RPC 协议版本
     * @param id      JSON-RPC 请求关联标识
     * @param result  工具执行结果
     * @param error   失败详情
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record JsonRpcResponse(String jsonrpc, @JsonInclude(JsonInclude.Include.ALWAYS) Object id, Object result,
        JsonRpcError error) {

        public JsonRpcResponse {
            if (jsonrpc == null) {
                jsonrpc = JSONRPC_VERSION;
            }
        }

        /**
         * 构造包含结果对象的 JSON-RPC 成功响应。
         *
         * @param id     JSON-RPC 请求关联标识
         * @param result 工具执行结果
         * @return 承载成功结果或协议错误的 JSON-RPC 响应
         */
        public static JsonRpcResponse success(Object id, Object result) {
            return new JsonRpcResponse(JSONRPC_VERSION, id, result, null);
        }

        /**
         * 构造 JSON-RPC 错误响应。
         *
         * @param id      JSON-RPC 请求关联标识
         * @param code    稳定的错误码
         * @param message 可安全对外展示的说明
         * @return 承载成功结果或协议错误的 JSON-RPC 响应
         */
        public static JsonRpcResponse error(Object id, int code, String message) {
            return new JsonRpcResponse(JSONRPC_VERSION, id, null, new JsonRpcError(code, message));
        }
    }

    /**
     * JSON-RPC 协议错误，不等同于工具执行失败。
     *
     * @param code    稳定的错误码
     * @param message 可安全对外展示的说明
     * @param data    可选的协议错误附加信息
     */
    public record JsonRpcError(int code, String message, Object data) {

        /**
         * 创建 JsonRpcError，保存调用所需的显式依赖。
         *
         * @param code    稳定的错误码
         * @param message 可安全对外展示的说明
         */
        public JsonRpcError(int code, String message) {
            this(code, message, null);
        }
    }

    /**
     * MCP 初始化协商结果。
     *
     * @param protocolVersion 协商后的 MCP 协议版本
     * @param capabilities    协议端点声明的能力
     * @param serverInfo      MCP 服务实现信息
     */
    public record InitializeResult(@JsonProperty("protocolVersion") String protocolVersion,
        @JsonProperty("capabilities") ServerCapabilities capabilities,
        @JsonProperty("serverInfo") Implementation serverInfo) {
    }

    /**
     * 协议端点的实现名称与版本。
     *
     * @param name    工具名称
     * @param version 工具契约版本
     */
    public record Implementation(@JsonProperty("name") String name, @JsonProperty("version") String version) {
    }

    /**
     * MCP 服务声明的工具、资源和提示模板能力。
     *
     * @param tools     按工具名称保存的配置或运行时绑定
     * @param resources 当前提供的资源列表
     * @param prompts   当前提供的提示模板列表
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ServerCapabilities(@JsonProperty("tools") ToolCapabilities tools,
        @JsonProperty("resources") Object resources, @JsonProperty("prompts") Object prompts) {
    }

    /**
     * MCP 工具能力声明。
     *
     * @param listChanged 是否支持工具列表变更通知
     */
    public record ToolCapabilities(@JsonProperty("listChanged") boolean listChanged) {
    }

    /**
     * MCP 工具发现响应。
     *
     * @param tools 按工具名称保存的配置或运行时绑定
     */
    public record ListToolsResult(@JsonProperty("tools") List<Tool> tools) {
    }

    /**
     * MCP 工具公开描述，仅包含名称、用途和输入 Schema。
     *
     * @param name        工具名称
     * @param description 工具的可读用途说明
     * @param inputSchema 完整输入 Schema
     */
    public record Tool(@JsonProperty("name") String name, @JsonProperty("description") String description,
        @JsonProperty("inputSchema") Map<String, Object> inputSchema) {

        public Tool {
            inputSchema = ToolCallContext.snapshot(inputSchema);
        }

        /**
         * 将旧简化 Schema 转换为 Map 表示。
         *
         * @param name        工具名称
         * @param description 工具的可读用途说明
         * @param schema      兼容保留的旧 Schema 描述
         */
        public Tool(String name, String description, JsonSchema schema) {
            this(name, description, legacySchema(schema));
        }

        private static Map<String, Object> legacySchema(JsonSchema schema) {
            Map<String, Object> props = new LinkedHashMap<>();
            if (schema.properties() != null) {
                schema.properties().forEach((name, p) -> {
                    Map<String, Object> value = new LinkedHashMap<>();
                    if (p.type() != null) {
                        value.put("type", p.type());
                    }
                    if (p.description() != null) {
                        value.put("description", p.description());
                    }
                    props.put(name, value);
                });
            }
            return Map.of("type", schema.type(), "properties", props, "required",
                schema.required() == null ? List.of() : schema.required());
        }
    }

    /**
     * 为旧调用方保留的简化输入 Schema 构造模型。
     *
     * @param type       参数或协议内容的类型标识
     * @param properties 组件配置
     * @param required   是否要求存在宿主授权策略
     */
    public record JsonSchema(@JsonProperty("type") String type,
        @JsonProperty("properties") Map<String, PropertyDef> properties,
        @JsonProperty("required") List<String> required) {
    }

    /**
     * 为旧 Schema 构造入口保留的参数属性描述。
     *
     * @param type        参数或协议内容的类型标识
     * @param description 工具的可读用途说明
     */
    public record PropertyDef(@JsonProperty("type") String type, @JsonProperty("description") String description) {
    }

    /**
     * MCP 工具执行结果，使用 isError 区分业务成功与工具错误。
     *
     * @param content 工具结果的协议内容项
     * @param isError 是否为工具执行错误
     */
    public record CallToolResult(@JsonProperty("content") List<Content> content,
        @JsonProperty("isError") boolean isError) {
    }

    /**
     * MCP 文本内容项。
     *
     * @param type 参数或协议内容的类型标识
     * @param text 文本类型的协议内容
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Content(@JsonProperty("type") String type, @JsonProperty("text") String text) {

        /**
         * 构造文本类型的 MCP 内容项。
         *
         * @param text 文本类型的协议内容
         * @return 配置或装配得到的 Content 实例
         */
        public static Content text(String text) {
            return new Content("text", text);
        }
    }

    /**
     * MCP 资源发现响应。
     *
     * @param resources 当前提供的资源列表
     */
    public record ListResourcesResult(@JsonProperty("resources") List<Object> resources) {
    }

    /**
     * MCP 提示模板发现响应。
     *
     * @param prompts 当前提供的提示模板列表
     */
    public record ListPromptsResult(@JsonProperty("prompts") List<Object> prompts) {
    }
}
