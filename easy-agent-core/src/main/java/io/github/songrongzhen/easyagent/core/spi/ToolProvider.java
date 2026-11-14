package io.github.songrongzhen.easyagent.core.spi;

import io.github.songrongzhen.easyagent.core.model.ToolDefinition;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;

/**
 * 提供编程式工具定义。
 */
public interface ToolProvider {

    /**
     * 返回需要注册的工具定义。
     */
    Collection<ToolDefinition> provide();

    /**
     * 提供消除工具重载歧义的显式方法绑定。
     *
     * @return 工具名称到显式目标方法的映射
     */
    default Map<String, Method> bindings() {
        return Map.of();
    }

    /**
     * 提供已有工具参数的描述补充，不覆盖类型或校验结构。
     *
     * @return 按工具名称提供的参数描述补充映射
     */
    default Map<String, Map<String, Object>> inputSchemas() {
        return Map.of();
    }

    /**
     * 返回注册优先级，数值越小越先注册。
     */
    default int priority() {
        return 0;
    }

    /**
     * 返回工具来源名称。
     */
    default String source() {
        return getClass().getSimpleName();
    }

    /**
     * 返回工具版本。
     */
    default String version() {
        return "1";
    }
}
