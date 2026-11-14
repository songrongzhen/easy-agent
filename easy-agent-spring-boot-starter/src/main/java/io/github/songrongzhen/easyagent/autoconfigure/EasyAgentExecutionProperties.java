package io.github.songrongzhen.easyagent.autoconfigure;

import io.github.songrongzhen.easyagent.core.config.ToolExecutionOptions;
import io.github.songrongzhen.easyagent.core.model.ToolCallContext;
import io.github.songrongzhen.easyagent.core.registry.ToolRegistry;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 公共治理及按工具覆盖配置，保留未配置状态以计算迁移优先级。
 */
@Getter
@Setter
@ConfigurationProperties("easy-agent.execution")
public class EasyAgentExecutionProperties {

    /**
     * 当前组件的日志记录器。
     */
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(EasyAgentExecutionProperties.class);

    /**
     * 公共执行授权配置。
     */
    private Authorization authorization = new Authorization();

    /**
     * 非阻塞限流配置。
     */
    private RateLimit rateLimit = new RateLimit();

    /**
     * 可选指标配置。
     */
    private Metrics metrics = new Metrics();

    /**
     * 工具重试策略或配置。
     */
    private Retry retry = new Retry();

    /**
     * 按工具名称保存的配置或运行时绑定。
     */
    private Map<String, ToolOptions> tools = new LinkedHashMap<>();

    /**
     * 显式旧配置中的总尝试次数，仅用于 AGENT 兼容回退。
     */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Integer legacyAttempts;

    /**
     * 显式旧配置中的退避毫秒数。
     */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Long legacyBackoff;

    /**
     * 显式旧配置中的工具重试开关。
     */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Boolean legacyEnabled;

    /**
     * 公共授权装配要求，不代表独立的身份认证系统。
     */
    @Getter
    @Setter
    public static class Authorization {

        /**
         * 是否要求存在宿主授权策略。
         */
        private boolean required;
    }

    /**
     * 宿主限流器开关，启用时必须存在实际实现。
     */
    @Getter
    @Setter
    public static class RateLimit {

        /**
         * 是否启用当前功能。
         */
        private boolean enabled;
    }

    /**
     * 可选 Micrometer 指标开关。
     */
    @Getter
    @Setter
    public static class Metrics {

        /**
         * 是否启用当前功能。
         */
        private boolean enabled;
    }

    /**
     * 公共重试默认值；包装类型的 null 表示用户未显式配置。
     */
    @Getter
    @Setter
    public static class Retry {

        /**
         * 公共总尝试次数默认值，null 表示未配置。
         */
        private Integer defaultMaxAttempts;

        /**
         * 公共退避毫秒数默认值，null 表示未配置。
         */
        private Long defaultBackoffMillis;
    }

    /**
     * 按工具声明的副作用及重试覆盖值。
     */
    @Getter
    @Setter
    public static class ToolOptions {

        /**
         * 宿主声明的工具副作用类别。
         */
        private ToolExecutionOptions.Effect effect;

        /**
         * 包含首次执行的总尝试次数。
         */
        private Integer maxAttempts;

        /**
         * 仅在下次尝试前等待的毫秒数。
         */
        private Long backoffMillis;
    }

    /**
     * 读取显式旧配置并校验原始值，记录一次迁移提示。
     *
     * @param environment 宿主配置环境
     */
    public void prepare(Environment environment) {
        Binder binder = Binder.get(environment);
        String prefix = "easy-agent.llm.tool-execution.";
        legacyAttempts = binder.bind(prefix + "retry-attempts", Integer.class).orElse(null);
        legacyBackoff = binder.bind(prefix + "retry-backoff-millis", Long.class).orElse(null);
        legacyEnabled = binder.bind(prefix + "retry-enabled", Boolean.class).orElse(null);
        if (legacyAttempts != null || legacyBackoff != null || legacyEnabled != null) {
            LOGGER.warn("Deprecated LLM tool retry configuration detected; migrate to"
                + " easy-agent.execution.tools with explicit effect. New fields"
                + " take precedence; legacy fallback applies only to AGENT.");
        }
        positive(retry.defaultMaxAttempts);
        nonnegative(retry.defaultBackoffMillis);
        positive(legacyAttempts);
        nonnegative(legacyBackoff);
        tools.values().forEach(value -> {
            positive(value.maxAttempts);
            nonnegative(value.backoffMillis);
        });
    }

    /**
     * 按单工具配置、公共默认值、显式旧配置和内置默认值的顺序解析执行选项。
     *
     * @param name   工具名称
     * @param source 调用入口标识
     * @return 当前工具及入口生效的执行选项
     */
    public ToolExecutionOptions resolve(String name, String source) {
        ToolOptions tool = tools.getOrDefault(name, new ToolOptions());
        boolean legacy = ToolCallContext.SOURCE_AGENT.equals(source) && !Boolean.FALSE.equals(legacyEnabled);
        int maxAttempts = resolveMaxAttempts(tool, legacy);
        long backoffMillis = resolveBackoffMillis(tool, legacy);
        return new ToolExecutionOptions(tool.effect, maxAttempts, backoffMillis);
    }

    private int resolveMaxAttempts(ToolOptions tool, boolean legacy) {
        if (tool.maxAttempts != null) {
            return tool.maxAttempts;
        }
        if (retry.defaultMaxAttempts != null) {
            return retry.defaultMaxAttempts;
        }
        if (legacy && legacyAttempts != null) {
            return legacyAttempts;
        }
        return 1;
    }

    private long resolveBackoffMillis(ToolOptions tool, boolean legacy) {
        if (tool.backoffMillis != null) {
            return tool.backoffMillis;
        }
        if (retry.defaultBackoffMillis != null) {
            return retry.defaultBackoffMillis;
        }
        if (legacy && legacyBackoff != null) {
            return legacyBackoff;
        }
        return 0L;
    }

    /**
     * 在注册完成后校验工具重试安全属性及次数边界。
     *
     * @param registry 工具注册表
     */
    public void validateTools(ToolRegistry registry) {
        Set<String> names = new HashSet<>(tools.keySet());
        registry.getAllTools().forEach(t -> names.add(t.name()));
        // Also validate global defaults when no tools exist yet.
        if (names.isEmpty()) {
            names.add("__default__");
        }
        names.forEach(name -> {
            resolve(name, ToolCallContext.SOURCE_JAVA);
            resolve(name, ToolCallContext.SOURCE_AGENT);
        });
    }

    private static void positive(Integer value) {
        if (value != null && value < 1) {
            throw new IllegalArgumentException("Tool retry maxAttempts must be at least 1");
        }
    }

    private static void nonnegative(Long value) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException("Tool retry backoffMillis must be nonnegative");
        }
    }
}
