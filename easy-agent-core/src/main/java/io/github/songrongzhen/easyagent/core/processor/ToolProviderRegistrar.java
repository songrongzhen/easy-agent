package io.github.songrongzhen.easyagent.core.processor;

import io.github.songrongzhen.easyagent.core.model.ToolDefinition;
import io.github.songrongzhen.easyagent.core.registry.ToolRegistry;
import io.github.songrongzhen.easyagent.core.spi.ToolProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * 在全部单例初始化后注册 SPI 工具，配置错误直接阻止启动。
 */
public class ToolProviderRegistrar implements SmartInitializingSingleton {

    /**
     * 工具注册表。
     */
    private final ToolRegistry toolRegistry;

    /**
     * 工具定义提供者。
     */
    private final List<ToolProvider> toolProviders;

    /**
     * 创建 ToolProviderRegistrar，保存调用所需的显式依赖。
     *
     * @param toolRegistry  工具注册表
     * @param toolProviders 工具定义提供者
     */
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
        toolProviders.stream().sorted(Comparator.comparingInt(ToolProvider::priority)).forEach(this::registerProvider);
    }

    private void registerProvider(ToolProvider toolProvider) {
        Collection<ToolDefinition> definitions = toolProvider.provide();
        if (definitions == null || definitions.isEmpty()) {
            return;
        }
        definitions.stream().filter(definition -> definition != null).forEach(definition -> {
            int priority = definition.priority() != 0 ? definition.priority() : toolProvider.priority();
            ToolDefinition normalized = new ToolDefinition(definition.name(), definition.description(),
                definition.category(), isBlank(definition.source()) ? toolProvider.source() : definition.source(),
                priority, definition.beanName(), definition.methodName(), definition.parameters(),
                isBlank(definition.version()) ? toolProvider.version() : definition.version(), definition.enabled());
            toolRegistry.register(normalized, null, toolProvider.bindings().get(normalized.name()),
                toolProvider.inputSchemas().get(normalized.name()), ToolRegistry.ConflictStrategy.FAIL_FAST);
        });
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
