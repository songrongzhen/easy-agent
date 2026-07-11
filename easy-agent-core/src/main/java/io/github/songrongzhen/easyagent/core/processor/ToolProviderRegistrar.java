package io.github.songrongzhen.easyagent.core.processor;

import io.github.songrongzhen.easyagent.core.model.ToolDefinition;
import io.github.songrongzhen.easyagent.core.registry.ToolRegistry;
import io.github.songrongzhen.easyagent.core.spi.ToolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public class ToolProviderRegistrar implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(ToolProviderRegistrar.class);

    private final ToolRegistry toolRegistry;
    private final List<ToolProvider> toolProviders;

    public ToolProviderRegistrar(ToolRegistry toolRegistry, List<ToolProvider> toolProviders) {
        this.toolRegistry = toolRegistry;
        this.toolProviders = toolProviders == null ? List.of() : toolProviders;
    }

    /**
     * 所有单例初始化后注册 ToolProvider 工具定义。
     */
    @Override
    public void afterSingletonsInstantiated() {
        registerProviders();
    }

    /**
     * 注册所有 ToolProvider 提供的工具定义。
     */
    public void registerProviders() {
        toolProviders.stream()
                .sorted(Comparator.comparingInt(ToolProvider::priority))
                .forEach(this::registerProvider);
    }

    private void registerProvider(ToolProvider toolProvider) {
        try {
            Collection<ToolDefinition> definitions = toolProvider.provide();
            if (definitions == null || definitions.isEmpty()) {
                return;
            }
            definitions.stream()
                    .filter(definition -> definition != null)
                    .forEach(definition -> {
                        int priority = definition.priority() != 0 ? definition.priority() : toolProvider.priority();
                        ToolDefinition normalized = new ToolDefinition(
                                definition.name(),
                                definition.description(),
                                definition.category(),
                                isBlank(definition.source()) ? toolProvider.getClass().getSimpleName() : definition.source(),
                                priority,
                                definition.beanName(),
                                definition.methodName(),
                                definition.parameters(),
                                isBlank(definition.version()) ? "1" : definition.version(),
                                definition.enabled()
                        );
                        toolRegistry.registerOrFail(normalized);
                    });
        } catch (Exception e) {
            log.error("Failed to register tools from provider: {}", toolProvider.getClass().getName(), e);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
