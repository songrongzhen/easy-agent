package io.github.songrongzhen.easyagent.mcp.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.songrongzhen.easyagent.core.model.ToolError;
import java.util.List;

/**
 * 分别构造 JSON-RPC 协议错误和工具执行错误。
 */
public final class McpErrorFactory {

    private McpErrorFactory() {
    }

    /**
     * 构造 JSON-RPC 协议错误响应。
     *
     * @param id        JSON-RPC 请求关联标识
     * @param errorCode 预定义的协议或工具错误码
     * @return 承载成功结果或协议错误的 JSON-RPC 响应
     */
    public static McpProtocol.JsonRpcResponse jsonRpcError(Object id, McpErrorCode errorCode) {
        return McpProtocol.JsonRpcResponse.error(id, errorCode.getCode(), errorCode.getMessage());
    }

    /**
     * 构造 JSON-RPC 协议错误响应。
     *
     * @param id        JSON-RPC 请求关联标识
     * @param errorCode 预定义的协议或工具错误码
     * @param detail    可安全对外展示的错误详情
     * @return 承载成功结果或协议错误的 JSON-RPC 响应
     */
    public static McpProtocol.JsonRpcResponse jsonRpcError(Object id, McpErrorCode errorCode, String detail) {
        return McpProtocol.JsonRpcResponse.error(id, errorCode.getCode(), buildMessage(errorCode, detail));
    }

    /**
     * 构造 isError=true 的工具执行错误响应。
     *
     * @param errorCode 预定义的协议或工具错误码
     * @return 成功结果或可对外展示的结构化工具错误
     */
    public static McpProtocol.CallToolResult toolError(McpErrorCode errorCode) {
        return new McpProtocol.CallToolResult(List.of(McpProtocol.Content.text(buildMessage(errorCode, null))), true);
    }

    /**
     * 构造 isError=true 的工具执行错误响应。
     *
     * @param errorCode 预定义的协议或工具错误码
     * @param detail    可安全对外展示的错误详情
     * @return 成功结果或可对外展示的结构化工具错误
     */
    public static McpProtocol.CallToolResult toolError(McpErrorCode errorCode, String detail) {
        return new McpProtocol.CallToolResult(List.of(McpProtocol.Content.text(buildMessage(errorCode, detail))), true);
    }

    /**
     * 构造 isError=true 的工具执行错误响应。
     *
     * @param error 失败详情
     * @return 成功结果或可对外展示的结构化工具错误
     */
    public static McpProtocol.CallToolResult toolError(ToolError error) {
        try {
            return new McpProtocol.CallToolResult(
                List.of(McpProtocol.Content.text(new ObjectMapper().writeValueAsString(error))), true);
        } catch (JsonProcessingException e) {
            return toolError(McpErrorCode.TOOL_EXECUTION_FAILED);
        }
    }

    /**
     * 合并受控错误描述与可对外展示的详情。
     *
     * @param errorCode 预定义的协议或工具错误码
     * @param detail    可安全对外展示的错误详情
     * @return 可对外展示的受控错误描述
     */
    public static String buildMessage(McpErrorCode errorCode, String detail) {
        if (detail == null || detail.isBlank()) {
            return errorCode.getMessage();
        }
        return errorCode.getMessage() + "：" + detail;
    }
}
