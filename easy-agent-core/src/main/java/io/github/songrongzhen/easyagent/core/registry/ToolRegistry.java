package io.github.songrongzhen.easyagent.core.registry;

import io.github.songrongzhen.easyagent.core.executor.ToolExecutor;
import io.github.songrongzhen.easyagent.core.model.RegisteredTool;
import io.github.songrongzhen.easyagent.core.model.ToolDefinition;
import io.github.songrongzhen.easyagent.core.schema.ToolSchemaGenerator;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册表，以运行时绑定为唯一事实来源，注册和替换前完成统一校验。
 */
public class ToolRegistry implements ApplicationContextAware {

    /**
     * 按工具名称保存的配置或运行时绑定。
     */
    private final Map<String, RegisteredTool> tools = new ConcurrentHashMap<>();

    /**
     * 注册方法解析器。
     */
    private ToolMethodResolver resolver;

    /**
     * 宿主 Spring 应用容器，用于查找工具 Bean。
     */
    private ApplicationContext context;

    /**
     * 创建 ToolRegistry，保存调用所需的显式依赖。
     */
    public ToolRegistry() {
    }

    /**
     * 创建 ToolRegistry，保存调用所需的显式依赖。
     *
     * @param resolver 注册方法解析器
     * @param context  宿主 Spring 应用容器，用于查找工具 Bean
     */
    public ToolRegistry(ToolMethodResolver resolver, ApplicationContext context) {
        configure(resolver, context);
    }

    /**
     * 初始化共用方法解析器及宿主容器，保持 Schema 与绑定使用相同 mapper。
     *
     * @param resolver 注册方法解析器
     * @param context  宿主 Spring 应用容器，用于查找工具 Bean
     */
    public synchronized void configure(ToolMethodResolver resolver, ApplicationContext context) {
        if (this.resolver == null) {
            this.resolver = Objects.requireNonNull(resolver);
        }
        if (context != null) {
            this.context = context;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setApplicationContext(ApplicationContext context) {
        this.context = context;
    }

    /**
     * 返回注册和执行共用的类型解析依赖。
     *
     * @return 解析确定方法及完整泛型的共用解析器
     */
    public ToolMethodResolver resolver() {
        return resolver;
    }

    /**
     * 校验并注册工具；重名或非法绑定会报告配置错误。
     *
     * @param definition 工具定义
     */
    public void register(ToolDefinition definition) {
        registerOrFail(definition);
    }

    /**
     * 校验并注册工具；重名或非法绑定会报告配置错误。
     *
     * @param definition 工具定义
     * @param strategy   注册冲突处理策略
     */
    public void register(ToolDefinition definition, ConflictStrategy strategy) {
        register(definition, null, null, null, strategy);
    }

    /**
     * 校验并注册工具；重名或非法绑定会报告配置错误。
     *
     * @param definition 工具定义
     * @param proxy      最终 Spring 代理对象
     * @param method     显式目标方法
     * @param supplement 仅用于补充已有参数描述的 Schema 片段
     * @param strategy   注册冲突处理策略
     */
    public synchronized void register(ToolDefinition definition, Object proxy, Method method,
        Map<String, Object> supplement, ConflictStrategy strategy) {
        if (definition == null || definition.name() == null || definition.name().isBlank()) {
            throw new IllegalArgumentException("Tool name is required");
        }
        strategy = strategy == null ? ConflictStrategy.FAIL_FAST : strategy;
        if (tools.containsKey(definition.name())) {
            if (strategy == ConflictStrategy.KEEP_EXISTING) {
                return;
            }
            if (strategy == ConflictStrategy.FAIL_FAST) {
                throw new IllegalStateException("Tool already registered: " + definition.name());
            }
        }
        if (resolver == null) {
            configure(new ToolMethodResolver(new ToolSchemaGenerator(ToolExecutor.createObjectMapper())), context);
        }
        if (proxy == null) {
            if (context == null) {
                throw new IllegalStateException(
                    "ToolRegistry needs an ApplicationContext or an explicit proxy for" + " registration");
            }
            proxy = context.getBean(definition.beanName());
        }
        var runtime = resolver.resolve(definition, proxy, method, supplement);
        // Replace only after the new binding and schema have been validated.
        tools.put(definition.name(), runtime);
    }

    /**
     * 仅在名称不存在时注册，保留现有运行时绑定。
     *
     * @param d 待注册的工具定义
     */
    public void registerIfAbsent(ToolDefinition d) {
        register(d, ConflictStrategy.KEEP_EXISTING);
    }

    /**
     * 校验新定义成功后原子替换现有运行时绑定。
     *
     * @param d 待注册的工具定义
     */
    public void registerOrReplace(ToolDefinition d) {
        register(d, ConflictStrategy.REPLACE_EXISTING);
    }

    /**
     * 校验并注册工具；重名或非法绑定会报告配置错误。
     *
     * @param d 待注册的工具定义
     */
    public void registerOrFail(ToolDefinition d) {
        register(d, ConflictStrategy.FAIL_FAST);
    }

    /**
     * 移除工具定义及其运行时绑定。
     *
     * @param name 工具名称
     */
    public void unregister(String name) {
        tools.remove(name);
    }

    /**
     * 查询内部运行时绑定，名称不存在时返回 null。
     *
     * @param name 工具名称
     * @return 已注册的运行时绑定，单项查询不存在时返回 null
     */
    public RegisteredTool getRegisteredTool(String name) {
        return name == null ? null : tools.get(name);
    }

    /**
     * 返回已启用工具的内部运行时绑定快照。
     *
     * @return 已注册的运行时绑定，单项查询不存在时返回 null
     */
    public Collection<RegisteredTool> getEnabledRegisteredTools() {
        return tools.values().stream().filter(t -> t.definition().enabled()).toList();
    }

    /**
     * 查询兼容工具描述，名称不存在时返回 null。
     *
     * @param name 工具名称
     * @return 兼容工具描述，单项查询不存在时返回 null
     */
    public ToolDefinition getTool(String name) {
        var t = getRegisteredTool(name);
        return t == null ? null : t.definition();
    }

    /**
     * 返回已注册工具的兼容描述快照。
     *
     * @return 兼容工具描述，单项查询不存在时返回 null
     */
    public Collection<ToolDefinition> getAllTools() {
        return tools.values().stream().map(RegisteredTool::definition).toList();
    }

    /**
     * 返回已启用工具的兼容描述快照。
     *
     * @return 兼容工具描述，单项查询不存在时返回 null
     */
    public Collection<ToolDefinition> getEnabledTools() {
        return getEnabledRegisteredTools().stream().map(RegisteredTool::definition).toList();
    }

    /**
     * 按分类查询工具描述。
     *
     * @param category 错误分类或工具分类
     * @return 兼容工具描述，单项查询不存在时返回 null
     */
    public Collection<ToolDefinition> getToolsByCategory(String category) {
        return getAllTools().stream().filter(t -> Objects.equals(category, t.category())).toList();
    }

    /**
     * 判断指定名称是否已经注册。
     *
     * @param name 工具名称
     * @return 是否满足当前查询条件
     */
    public boolean contains(String name) {
        return getRegisteredTool(name) != null;
    }

    /**
     * 查询已经注册的工具数量。
     *
     * @return 当前注册的工具数量
     */
    public int size() {
        return tools.size();
    }

    /**
     * 移除全部工具定义及其运行时绑定。
     */
    public void clear() {
        tools.clear();
    }

    /**
     * 重名工具注册时的冲突处理策略。
     */
    public enum ConflictStrategy {

        /**
         * 遇到重名工具立即报告配置错误。
         */
        FAIL_FAST,
        /**
         * 保留现有运行时绑定。
         */
        KEEP_EXISTING,
        /**
         * 新定义校验成功后替换现有绑定。
         */
        REPLACE_EXISTING
    }
}
