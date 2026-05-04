package io.github.songrongzhen.easyagent.core.spi;

import io.github.songrongzhen.easyagent.core.model.ToolDefinition;

import java.util.Collection;

public interface ToolProvider {

    Collection<ToolDefinition> provide();

    default int priority() {
        return 0;
    }
}
