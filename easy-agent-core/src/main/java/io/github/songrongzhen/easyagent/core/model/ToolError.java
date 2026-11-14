package io.github.songrongzhen.easyagent.core.model;

import java.util.Objects;

/**
 * 可安全对外展示的结构化工具错误，包含逻辑调用关联信息。
 *
 * @param code             稳定的错误码
 * @param category         错误分类或工具分类
 * @param message          可安全对外展示的说明
 * @param invocationId     一次逻辑调用的标识
 * @param retryAfterMillis 建议调用方稍后重试的等待毫秒数
 */
public record ToolError(String code, Category category, String message, String invocationId, Long retryAfterMillis) {

    /**
     * 创建不含限流等待建议的结构化错误。
     *
     * @param code         稳定的错误码
     * @param category     错误分类或工具分类
     * @param message      可安全对外展示的说明
     * @param invocationId 一次逻辑调用的标识
     */
    public ToolError(String code, Category category, String message, String invocationId) {
        this(code, category, message, invocationId, null);
    }

    public ToolError {
        Objects.requireNonNull(code);
        Objects.requireNonNull(category);
        if (category != Category.RATE_LIMIT) {
            retryAfterMillis = null;
        }
    }

    /**
     * 稳定的工具错误类别，用于调用方处理和可靠的重试决策。
     */
    public enum Category {

        /**
         * 工具名称或输入参数无效。
         */
        VALIDATION,
        /**
         * 工具禁用或执行授权被拒绝。
         */
        AUTHORIZATION,
        /**
         * 未获得执行许可，由调用方决定稍后再次调用。
         */
        RATE_LIMIT,
        /**
         * 业务规则明确拒绝本次操作。
         */
        BUSINESS,
        /**
         * 宿主可靠识别的暂时性业务故障。
         */
        TRANSIENT,
        /**
         * 未知或组件内部故障，不默认推断为可重试。
         */
        INTERNAL
    }
}
