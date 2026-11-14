package io.github.songrongzhen.easyagent.core.model;

/**
 * 工具执行结果，保留旧 error 字段并提供结构化错误详情。
 *
 * @param toolName    工具名称
 * @param success     本次工具调用是否成功
 * @param result      工具执行结果
 * @param error       失败详情
 * @param errorDetail 结构化失败详情
 */
public record ToolResult(String toolName, boolean success, String result, String error, ToolError errorDetail) {

    /**
     * 创建兼容结果，保留旧构造入口。
     *
     * @param toolName 工具名称
     * @param success  本次工具调用是否成功
     * @param result   工具执行结果
     * @param error    失败详情
     */
    public ToolResult(String toolName, boolean success, String result, String error) {
        this(toolName, success, result, error, null);
    }

    /**
     * 构造成功结果，保留现有结果字符串表示。
     *
     * @param name   工具名称
     * @param result 工具执行结果
     * @return 成功结果或可对外展示的结构化工具错误
     */
    public static ToolResult success(String name, String result) {
        return new ToolResult(name, true, result, null);
    }

    /**
     * 构造失败结果；结构化入口同时保持旧 error 字段可读。
     *
     * @param name  工具名称
     * @param error 失败详情
     * @return 成功结果或可对外展示的结构化工具错误
     */
    public static ToolResult failure(String name, String error) {
        return new ToolResult(name, false, null, error);
    }

    /**
     * 构造失败结果；结构化入口同时保持旧 error 字段可读。
     *
     * @param name  工具名称
     * @param error 失败详情
     * @return 成功结果或可对外展示的结构化工具错误
     */
    public static ToolResult failure(String name, ToolError error) {
        return new ToolResult(name, false, null, error.message(), error);
    }
}
