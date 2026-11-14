package io.github.songrongzhen.easyagent.core.spi;

import io.github.songrongzhen.easyagent.core.model.ToolCallContext;
import io.github.songrongzhen.easyagent.core.model.ToolInvocation;
import io.github.songrongzhen.easyagent.core.model.ToolResult;

/**
 * 监听工具执行过程。
 */
public interface ToolExecutionListener {

    /**
     * 在第一次真实业务调用前触发一次，默认委托旧回调。
     *
     * @param invocation 原始工具调用
     * @param context    可信逻辑调用上下文
     */
    default void beforeExecution(ToolInvocation invocation, ToolCallContext context) {
        beforeExecution(invocation);
    }

    /**
     * 在逻辑调用最终成功后触发一次，默认委托旧回调。
     *
     * @param invocation 原始工具调用
     * @param result     最终成功结果
     * @param context    可信逻辑调用上下文
     */
    default void afterExecution(ToolInvocation invocation, ToolResult result, ToolCallContext context) {
        afterExecution(invocation, result);
    }

    /**
     * 在已触发 before 的逻辑调用最终失败后触发一次，默认委托旧回调。
     *
     * @param invocation 原始工具调用
     * @param error      最终失败原因
     * @param context    可信逻辑调用上下文
     */
    default void onError(ToolInvocation invocation, Throwable error, ToolCallContext context) {
        onError(invocation, error);
    }

    /**
     * 工具执行前触发。
     */
    default void beforeExecution(ToolInvocation invocation) {
    }

    /**
     * 工具执行成功后触发。
     */
    default void afterExecution(ToolInvocation invocation, ToolResult result) {
    }

    /**
     * 工具执行失败时触发。
     */
    default void onError(ToolInvocation invocation, Throwable error) {
    }
}
