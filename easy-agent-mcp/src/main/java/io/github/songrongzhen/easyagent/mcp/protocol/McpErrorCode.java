package io.github.songrongzhen.easyagent.mcp.protocol;

public enum McpErrorCode {

    PARSE_ERROR(-32700, "解析请求失败"),
    INVALID_REQUEST(-32600, "请求格式不合法"),
    METHOD_NOT_FOUND(-32601, "请求的方法不存在"),
    INVALID_PARAMS(-32602, "请求参数不合法"),
    INTERNAL_ERROR(-32603, "服务内部错误"),
    TOOL_NOT_FOUND(4101, "工具不存在"),
    TOOL_NOT_ALLOWED(4102, "当前工具未被允许暴露"),
    TOOL_ARGUMENTS_INVALID(4103, "工具参数不合法"),
    TOOL_EXECUTION_FAILED(4104, "工具执行失败");

    private final int code;
    private final String message;

    McpErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
