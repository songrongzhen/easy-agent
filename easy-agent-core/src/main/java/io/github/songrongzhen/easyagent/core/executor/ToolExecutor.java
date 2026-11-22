package io.github.songrongzhen.easyagent.core.executor;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import io.github.songrongzhen.easyagent.core.config.ToolExecutionOptions;
import io.github.songrongzhen.easyagent.core.model.RegisteredTool;
import io.github.songrongzhen.easyagent.core.model.ToolCallContext;
import io.github.songrongzhen.easyagent.core.model.ToolError;
import io.github.songrongzhen.easyagent.core.model.ToolInvocation;
import io.github.songrongzhen.easyagent.core.model.ToolResult;
import io.github.songrongzhen.easyagent.core.registry.ToolMethodResolver;
import io.github.songrongzhen.easyagent.core.registry.ToolRegistry;
import io.github.songrongzhen.easyagent.core.schema.ToolSchemaGenerator;
import io.github.songrongzhen.easyagent.core.spi.ToolAuthorizationPolicy;
import io.github.songrongzhen.easyagent.core.spi.ToolExceptionMapper;
import io.github.songrongzhen.easyagent.core.spi.ToolExecutionListener;
import io.github.songrongzhen.easyagent.core.spi.ToolObservation;
import io.github.songrongzhen.easyagent.core.spi.ToolRateLimiter;
import io.github.songrongzhen.easyagent.core.spi.ToolRetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Every entry point delegates to this execution contract.
 */
public class ToolExecutor {

    /**
     * 当前组件的日志记录器。
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(ToolExecutor.class);

    /**
     * 工具注册表。
     */
    private final ToolRegistry registry;

    /**
     * 工具专用 ObjectMapper。
     */
    private final ObjectMapper mapper;

    /**
     * 逻辑调用监听器。
     */
    private final List<ToolExecutionListener> listeners;

    /**
     * 按顺序执行的宿主授权策略。
     */
    private final List<ToolAuthorizationPolicy> policies;

    /**
     * 按顺序执行的宿主限流器。
     */
    private final List<ToolRateLimiter> limiters;

    /**
     * 业务异常映射扩展。
     */
    private final List<ToolExceptionMapper> mappers;

    /**
     * 执行观测扩展。
     */
    private final List<ToolObservation> observations;

    /**
     * 工具重试策略或配置。
     */
    private final ToolRetryPolicy retry;

    /**
     * 生效执行选项解析器。
     */
    private final ToolExecutionOptions.Resolver options;

    /**
     * 创建工具专用严格 mapper，不修改宿主全局 ObjectMapper。
     *
     * @return 独立的严格 JSON ObjectMapper
     */
    public static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new Jdk8Module())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
        for (LogicalType type : List.of(LogicalType.Integer, LogicalType.Float, LogicalType.Boolean)) {
            mapper.coercionConfigFor(type).setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail);
        }
        return mapper;
    }

    /**
     * 创建 ToolExecutor，保存调用所需的显式依赖。
     *
     * @param registry 工具注册表
     * @param context  宿主 Spring 应用容器，用于查找工具 Bean
     */
    public ToolExecutor(ToolRegistry registry, ApplicationContext context) {
        this(registry, context, List.of());
    }

    /**
     * 创建 ToolExecutor，保存调用所需的显式依赖。
     *
     * @param registry  工具注册表
     * @param context   宿主 Spring 应用容器，用于查找工具 Bean
     * @param listeners 逻辑调用监听器
     */
    public ToolExecutor(ToolRegistry registry, ApplicationContext context, List<ToolExecutionListener> listeners) {
        this(registry, context, listeners,
            registry.resolver() == null ? createObjectMapper() : registry.resolver().mapper());
    }

    /**
     * 创建 ToolExecutor，保存调用所需的显式依赖。
     *
     * @param registry  工具注册表
     * @param context   宿主 Spring 应用容器，用于查找工具 Bean
     * @param listeners 逻辑调用监听器
     * @param mapper    工具专用 ObjectMapper
     */
    public ToolExecutor(ToolRegistry registry, ApplicationContext context, List<ToolExecutionListener> listeners,
        ObjectMapper mapper) {
        this(registry, context, mapper, listeners, List.of(), List.of(), List.of(), List.of(),
            ToolRetryPolicy.safeDefault(), (tool, source) -> ToolExecutionOptions.defaults());
    }

    /**
     * 创建 ToolExecutor，保存调用所需的显式依赖。
     *
     * @param registry     工具注册表
     * @param context      宿主 Spring 应用容器，用于查找工具 Bean
     * @param mapper       工具专用 ObjectMapper
     * @param listeners    逻辑调用监听器
     * @param policies     按顺序执行的宿主授权策略
     * @param limiters     按顺序执行的宿主限流器
     * @param mappers      业务异常映射扩展
     * @param observations 执行观测扩展
     * @param retry        工具重试策略或配置
     * @param options      生效执行选项解析器
     */
    public ToolExecutor(ToolRegistry registry, ApplicationContext context, ObjectMapper mapper,
        List<ToolExecutionListener> listeners, List<ToolAuthorizationPolicy> policies, List<ToolRateLimiter> limiters,
        List<ToolExceptionMapper> mappers, List<ToolObservation> observations, ToolRetryPolicy retry,
        ToolExecutionOptions.Resolver options) {
        this.registry = registry;
        registry.configure(new ToolMethodResolver(new ToolSchemaGenerator(mapper)), context);
        if (registry.resolver().mapper() != mapper) {
            throw new IllegalArgumentException(
                "Schema generation and argument binding must share the same tool ObjectMapper");
        }
        this.mapper = mapper;
        this.listeners = sorted(listeners);
        this.policies = sorted(policies);
        this.limiters = sorted(limiters);
        this.mappers = sorted(mappers);
        this.observations = sorted(observations);
        this.retry = Objects.requireNonNull(retry);
        this.options = Objects.requireNonNull(options);
    }

    private static <T> List<T> sorted(List<T> list) {
        List<T> result = new ArrayList<>(list == null ? List.of() : list);
        AnnotationAwareOrderComparator.sort(result);
        return List.copyOf(result);
    }

    /**
     * 通过公共契约执行工具，旧入口按匿名 JAVA 上下文委托。
     *
     * @param invocation 原始工具调用
     * @return 成功结果或可对外展示的结构化工具错误
     */
    public ToolResult execute(ToolInvocation invocation) {
        return execute(invocation, ToolCallContext.anonymous(ToolCallContext.SOURCE_JAVA));
    }

    /**
     * 使用宿主提供的工具调用上下文执行工具，供授权、限流及观测扩展使用。
     *
     * @param invocation 原始工具调用
     * @param context    工具调用上下文，其中的身份及租户信息应由宿主验证后提供
     * @return 成功结果或可对外展示的结构化工具错误
     */
    public ToolResult execute(ToolInvocation invocation, ToolCallContext context) {
        Objects.requireNonNull(context, "ToolCallContext");
        String name = invocation == null ? null : invocation.toolName();
        long start = System.nanoTime();
        State state = new State();
        observe(o -> o.onCallStarted(context, name));
        try {
            state.result = run(invocation, context, state);
        } catch (Exception e) {
            state.cause = e;
            state.result = internal(name, context, e);
        } finally {
            notifyFinalListeners(invocation, context, state);
            observe(o -> o.onCallFinished(context, name, System.nanoTime() - start, state.attempts, state.result));
        }
        return state.result;
    }

    private ToolResult run(ToolInvocation invocation, ToolCallContext context, State state) throws Exception {
        String name = invocation == null ? null : invocation.toolName();
        RegisteredTool tool = registry.getRegisteredTool(name);
        if (tool == null) {
            return failure(name, context, "TOOL_NOT_FOUND", ToolError.Category.VALIDATION, "Tool not found");
        }
        if (!tool.definition().enabled()) {
            return failure(name, context, "TOOL_DISABLED", ToolError.Category.AUTHORIZATION, "Tool is disabled");
        }
        JsonNode raw;
        Map<String, Object> arguments;
        try {
            raw = mapper.readTree(invocation.arguments() == null ? "" : invocation.arguments());
            if (raw == null || !raw.isObject()) {
                throw new IllegalArgumentException("Expected JSON object");
            }
            arguments = ToolCallContext.snapshot(mapper.convertValue(raw, new TypeReference<Map<String, Object>>() {
            }));
        } catch (Exception e) {
            return failure(name, context, "TOOL_ARGUMENT_INVALID", ToolError.Category.VALIDATION,
                "Tool arguments must be a valid JSON object");
        }
        ToolExecutionOptions config = options.resolve(name, context.source());
        return executeAttempts(invocation, context, state, tool, raw, arguments, config);
    }

    /**
     * 每次尝试重新授权、绑定和申请许可，只有进入业务方法才增加真实尝试次数。
     */
    private ToolResult executeAttempts(ToolInvocation invocation, ToolCallContext context, State state,
        RegisteredTool tool, JsonNode raw, Map<String, Object> arguments, ToolExecutionOptions config) {
        String name = invocation.toolName();
        for (int attempt = 1; attempt <= config.maxAttempts(); attempt++) {
            state.cause = null;
            ToolResult rejection = authorize(tool, context, arguments);
            if (rejection != null) {
                return rejection;
            }
            Object[] bound;
            try {
                bound = bind(tool, raw);
            } catch (IllegalArgumentException exception) {
                return failure(name, context, "TOOL_ARGUMENT_INVALID", ToolError.Category.VALIDATION,
                    exception.getMessage());
            }
            rejection = acquirePermit(tool, context);
            if (rejection != null) {
                return rejection;
            }
            notifyBefore(invocation, context, state);
            state.attempts++;
            long started = System.nanoTime();
            AttemptResult outcome = invokeAttempt(tool, bound, context, state);
            state.result = outcome.result();
            int currentAttempt = attempt;
            long elapsedNanos = System.nanoTime() - started;
            observe(observer -> observer.onAttemptFinished(context, name, currentAttempt, elapsedNanos, state.result));
            if (!shouldRetry(tool, context, config, attempt, outcome) || !awaitBackoff(config)) {
                return state.result;
            }
        }
        return state.result;
    }

    /**
     * 任一策略拒绝即返回受控错误；策略异常继续由逻辑调用统一收尾。
     */
    private ToolResult authorize(RegisteredTool tool, ToolCallContext context, Map<String, Object> arguments) {
        for (ToolAuthorizationPolicy policy : policies) {
            ToolAuthorizationPolicy.Decision decision = Objects
                .requireNonNull(policy.authorize(context, tool.definition(), arguments));
            if (!decision.allowed()) {
                return failure(tool.definition().name(), context, "TOOL_ACCESS_DENIED",
                    ToolError.Category.AUTHORIZATION,
                    decision.message() == null ? "Tool access denied" : decision.message());
            }
        }
        return null;
    }

    /**
     * 限流器依次申请许可，拒绝时不进入业务，也不等待额度。
     */
    private ToolResult acquirePermit(RegisteredTool tool, ToolCallContext context) {
        for (ToolRateLimiter limiter : limiters) {
            ToolRateLimiter.Decision decision = Objects.requireNonNull(limiter.tryAcquire(context, tool.definition()));
            if (!decision.allowed()) {
                return ToolResult.failure(tool.definition().name(),
                    new ToolError("TOOL_RATE_LIMITED", ToolError.Category.RATE_LIMIT, "Tool rate limited",
                        context.invocationId(), decision.retryAfterMillis()));
            }
        }
        return null;
    }

    /**
     * 在代理上执行一次业务调用；序列化失败不进入业务重试判断。
     */
    private AttemptResult invokeAttempt(RegisteredTool tool, Object[] bound, ToolCallContext context, State state) {
        String name = tool.definition().name();
        try {
            Object value = tool.invocableMethod().invoke(tool.proxy(), bound);
            try {
                return new AttemptResult(
                    ToolResult.success(name, value == null ? "" : mapper.writeValueAsString(value)), false);
            } catch (Exception exception) {
                state.cause = exception;
                return new AttemptResult(internal(name, context, exception), false);
            }
        } catch (InvocationTargetException exception) {
            state.cause = exception.getCause();
            if (state.cause instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            try {
                return new AttemptResult(mapException(name, state.cause, context), true);
            } catch (Exception mappingFailure) {
                return new AttemptResult(internal(name, context, mappingFailure), false);
            }
        } catch (Exception exception) {
            state.cause = exception;
            return new AttemptResult(internal(name, context, exception), false);
        }
    }

    /**
     * 先执行不可绕过的次数、中断及副作用边界，再询问宿主策略。
     */
    private boolean shouldRetry(RegisteredTool tool, ToolCallContext context, ToolExecutionOptions config, int attempt,
        AttemptResult outcome) {
        if (!outcome.businessFailure() || outcome.result().success() || attempt >= config.maxAttempts()
            || Thread.currentThread().isInterrupted() || config.effect() == ToolExecutionOptions.Effect.NON_IDEMPOTENT
            || config.effect() == ToolExecutionOptions.Effect.UNKNOWN) {
            return false;
        }
        return retry.shouldRetry(tool.definition(), context, config, attempt, outcome.result().errorDetail());
    }

    /**
     * 只在确定还有下一次尝试时等待，中断后恢复标记并停止重试。
     */
    private boolean awaitBackoff(ToolExecutionOptions config) {
        try {
            if (config.backoffMillis() > 0) {
                TimeUnit.MILLISECONDS.sleep(config.backoffMillis());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
        return !Thread.currentThread().isInterrupted();
    }

    private void notifyBefore(ToolInvocation invocation, ToolCallContext context, State state) {
        if (!state.before) {
            // 在派发前标记，保证抛出异常的前置监听器也能收到最终失败事件。
            state.before = true;
            for (ToolExecutionListener listener : listeners) {
                listener.beforeExecution(invocation, context);
            }
        }
    }

    private void notifyFinalListeners(ToolInvocation invocation, ToolCallContext context, State state) {
        if (!state.before) {
            return;
        }
        for (ToolExecutionListener listener : listeners) {
            try {
                if (state.result != null && state.result.success()) {
                    listener.afterExecution(invocation, state.result, context);
                } else {
                    Throwable failureCause = state.cause;
                    if (failureCause == null) {
                        String message = state.result == null ? "Execution failed" : state.result.error();
                        failureCause = new IllegalStateException(message);
                    }
                    listener.onError(invocation, failureCause, context);
                }
            } catch (Exception exception) {
                LOGGER.warn("Tool listener failed, invocationId={}", context.invocationId(), exception);
            }
        }
    }

    /**
     * 单次调用结果及其是否源于可交给重试策略判断的业务异常。
     */
    private record AttemptResult(ToolResult result, boolean businessFailure) {
    }

    private Object[] bind(RegisteredTool tool, JsonNode raw) {
        Object[] result = new Object[tool.parameters().size()];
        for (int i = 0; i < result.length; i++) {
            var p = tool.parameters().get(i);
            JsonNode value = raw.get(p.name());
            if ((value == null || value.isNull()) && (p.required() || p.javaType().isPrimitive())) {
                throw new IllegalArgumentException("Missing parameter: " + p.name());
            }
            try {
                result[i] = mapper.convertValue(value, p.javaType());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                    "Invalid parameter " + p.name() + ": expected " + p.javaType().toCanonical(), e);
            }
        }
        return result;
    }

    private ToolResult mapException(String name, Throwable cause, ToolCallContext context) {
        for (var errorMapper : mappers) {
            var mapped = errorMapper.map(cause, context);
            if (mapped.isPresent()) {
                ToolError e = mapped.get();
                return ToolResult.failure(name,
                    new ToolError(e.code(), e.category(), e.message(), context.invocationId(), e.retryAfterMillis()));
            }
        }
        return internal(name, context, cause);
    }

    private ToolResult internal(String name, ToolCallContext context, Throwable cause) {
        LOGGER.error("Tool execution failed: tool={}, invocationId={}", name, context.invocationId(), cause);
        return failure(name, context, "TOOL_EXECUTION_FAILED", ToolError.Category.INTERNAL, "工具执行失败，请提供调用标识排查");
    }

    private ToolResult failure(String name, ToolCallContext ctx, String code, ToolError.Category category,
        String message) {
        return ToolResult.failure(name, new ToolError(code, category, message, ctx.invocationId()));
    }

    private void observe(Consumer<ToolObservation> event) {
        for (var observer : observations) {
            try {
                event.accept(observer);
            } catch (Exception e) {
                LOGGER.warn("Tool observation failed", e);
            }
        }
    }

    /**
     * 一次逻辑调用的内部状态，用于统一收尾和观测。
     */
    private static class State {

        /**
         * 实际进入业务方法的次数。
         */
        int attempts;

        /**
         * 是否已经开始派发业务前置监听事件。
         */
        boolean before;

        /**
         * 工具执行结果。
         */
        ToolResult result;

        /**
         * 业务方法或代理切面抛出的异常。
         */
        Throwable cause;
    }
}
