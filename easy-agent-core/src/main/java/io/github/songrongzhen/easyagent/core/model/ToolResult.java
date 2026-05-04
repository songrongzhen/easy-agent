package io.github.songrongzhen.easyagent.core.model;

public record ToolResult(
        String toolName,
        boolean success,
        String result,
        String error
) {
    public static ToolResult success(String toolName, String result) {
        return new ToolResult(toolName, true, result, null);
    }

    public static ToolResult failure(String toolName, String error) {
        return new ToolResult(toolName, false, null, error);
    }
}
