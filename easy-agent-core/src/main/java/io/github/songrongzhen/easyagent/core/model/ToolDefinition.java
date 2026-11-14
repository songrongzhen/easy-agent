package io.github.songrongzhen.easyagent.core.model;

import java.util.List;

/**
 * 保留旧构造入口的工具描述，参数列表在构造时复制为不可变快照。
 *
 * @param name        工具名称
 * @param description 工具的可读用途说明
 * @param category    错误分类或工具分类
 * @param source      工具注册来源标识
 * @param priority    注册优先级，数值越小越早注册
 * @param beanName    宿主 Bean 名称
 * @param methodName  目标方法名称
 * @param parameters  工具参数描述或绑定列表
 * @param version     工具契约版本
 * @param enabled     是否启用当前功能
 */
public record ToolDefinition(String name, String description, String category, String source, int priority,
    String beanName, String methodName, List<ParameterDefinition> parameters, String version, boolean enabled) {

    public ToolDefinition {
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
    }

    /**
     * 创建兼容工具描述，保留旧构造入口。
     *
     * @param name        工具名称
     * @param description 工具的可读用途说明
     * @param category    错误分类或工具分类
     * @param beanName    宿主 Bean 名称
     * @param methodName  目标方法名称
     * @param parameters  工具参数描述或绑定列表
     * @param enabled     是否启用当前功能
     */
    public ToolDefinition(String name, String description, String category, String beanName, String methodName,
        List<ParameterDefinition> parameters, boolean enabled) {
        this(name, description, category, null, 0, beanName, methodName, parameters, null, enabled);
    }
}
