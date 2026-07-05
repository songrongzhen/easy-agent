package io.github.songrongzhen.easyagent.core.spi;

import io.github.songrongzhen.easyagent.core.model.ToolDefinition;

import java.util.Collection;

/**
 * 提供编程式工具定义。
 */
public interface ToolProvider {

    /**
     * 返回需要注册的工具定义。
     */
    Collection<ToolDefinition> provide();

    /**
     * 返回注册优先级，数值越小越先注册。
     */
    default int priority() {
        return 0;
    }
}
