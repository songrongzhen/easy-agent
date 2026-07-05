package io.github.songrongzhen.easyagent.core.spi;

import io.github.songrongzhen.easyagent.core.model.ToolInvocation;
import io.github.songrongzhen.easyagent.core.model.ToolResult;

/**
 * 监听工具执行过程。
 */
public interface ToolExecutionListener {

    /**
     * 工具执行前触发。
     */
    default void beforeExecution(ToolInvocation invocation) {}

    /**
     * 工具执行成功后触发。
     */
    default void afterExecution(ToolInvocation invocation, ToolResult result) {}

    /**
     * 工具执行失败时触发。
     */
    default void onError(ToolInvocation invocation, Throwable error) {}
}
