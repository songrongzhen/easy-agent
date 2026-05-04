package io.github.songrongzhen.easyagent.core.spi;

import io.github.songrongzhen.easyagent.core.model.ToolInvocation;
import io.github.songrongzhen.easyagent.core.model.ToolResult;

public interface ToolExecutionListener {

    default void beforeExecution(ToolInvocation invocation) {}

    default void afterExecution(ToolInvocation invocation, ToolResult result) {}

    default void onError(ToolInvocation invocation, Throwable error) {}
}
