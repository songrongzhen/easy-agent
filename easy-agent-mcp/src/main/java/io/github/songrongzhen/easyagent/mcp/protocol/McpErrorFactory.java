package io.github.songrongzhen.easyagent.mcp.protocol;

public final class McpErrorFactory {

    private McpErrorFactory() {
    }

    public static McpProtocol.JsonRpcResponse jsonRpcError(Object id, McpErrorCode errorCode) {
        return McpProtocol.JsonRpcResponse.error(id, errorCode.getCode(), errorCode.getMessage());
    }

    public static McpProtocol.JsonRpcResponse jsonRpcError(Object id, McpErrorCode errorCode, String detail) {
        return McpProtocol.JsonRpcResponse.error(id, errorCode.getCode(), buildMessage(errorCode, detail));
    }

    public static McpProtocol.CallToolResult toolError(McpErrorCode errorCode) {
        return new McpProtocol.CallToolResult(
                java.util.List.of(McpProtocol.Content.text(buildMessage(errorCode, null))),
                true
        );
    }

    public static McpProtocol.CallToolResult toolError(McpErrorCode errorCode, String detail) {
        return new McpProtocol.CallToolResult(
                java.util.List.of(McpProtocol.Content.text(buildMessage(errorCode, detail))),
                true
        );
    }

    public static String buildMessage(McpErrorCode errorCode, String detail) {
        if (detail == null || detail.isBlank()) {
            return errorCode.getMessage();
        }
        return errorCode.getMessage() + "：" + detail;
    }
}
