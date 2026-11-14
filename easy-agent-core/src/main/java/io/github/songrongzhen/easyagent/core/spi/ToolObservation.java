package io.github.songrongzhen.easyagent.core.spi;

import io.github.songrongzhen.easyagent.core.model.ToolCallContext;
import io.github.songrongzhen.easyagent.core.model.ToolResult;

/**
 * 逻辑调用与真实业务尝试的观测扩展；回调异常由执行器隔离，不改变业务结果。
 */
public interface ToolObservation {

    /**
     * 在逻辑调用开始时触发，包括之后可能被拒绝的调用。
     *
     * @param context  可信调用上下文
     * @param toolName 请求的工具名称，可能未知或为空，指标实现须控制标签基数
     */
    default void onCallStarted(ToolCallContext context, String toolName) {
    }

    /**
     * 仅在真实业务调用结束后触发，参数、授权和限流拒绝不计为业务尝试。
     *
     * @param context      可信调用上下文
     * @param toolName     已注册工具名称
     * @param attempt      从 1 开始的真实业务尝试序号
     * @param elapsedNanos 本次尝试的单调时钟耗时，单位为纳秒
     * @param result       本次业务尝试的最终结果
     */
    default void onAttemptFinished(ToolCallContext context, String toolName, int attempt, long elapsedNanos,
        ToolResult result) {
    }

    /**
     * 在逻辑调用最终结束时触发一次，包含所有拒绝和失败，耗时包含退避。
     *
     * @param context      可信调用上下文
     * @param toolName     请求的工具名称，可能未知或为空
     * @param elapsedNanos 逻辑调用总耗时，单位为纳秒
     * @param attempts     实际进入业务方法的次数，拒绝时可为 0
     * @param result       本次逻辑调用的最终结果
     */
    default void onCallFinished(ToolCallContext context, String toolName, long elapsedNanos, int attempts,
        ToolResult result) {
    }
}
