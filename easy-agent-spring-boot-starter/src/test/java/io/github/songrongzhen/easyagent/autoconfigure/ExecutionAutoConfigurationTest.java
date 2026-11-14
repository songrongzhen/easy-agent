package io.github.songrongzhen.easyagent.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.songrongzhen.easyagent.core.annotation.EasyTool;
import io.github.songrongzhen.easyagent.core.annotation.ToolParam;
import io.github.songrongzhen.easyagent.core.executor.ToolExecutor;
import io.github.songrongzhen.easyagent.core.model.ToolInvocation;
import io.github.songrongzhen.easyagent.core.registry.ToolRegistry;
import io.github.songrongzhen.easyagent.core.spi.ToolAuthorizationPolicy;
import io.github.songrongzhen.easyagent.core.spi.ToolRateLimiter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** 工具执行契约的测试夹具与回归验证。 */
class ExecutionAutoConfigurationTest {

    final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(EasyAgentCoreAutoConfiguration.class));

    /** 工具执行契约的测试夹具与回归验证。 */
    public static class Service {

        int calls;

        @EasyTool(name = "queryUser", description = "query")
        public String query(@ToolParam(name = "value") String value) {
            calls++;
            return value;
        }
    }

    @Test
    void defaultsAllowAnonymousAndExecuteOnce() {
        runner.withBean(Service.class)
            .run(
                ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx.getBean(ToolRegistry.class).size()).isEqualTo(1);
                    var result = ctx.getBean(ToolExecutor.class)
                        .execute(
                            new ToolInvocation(
                                "queryUser", "{\"value\":\"ok\"}"));
                    assertThat(result.success()).isTrue();
                    assertThat(ctx.getBean(Service.class).calls).isEqualTo(1);
                    var props = ctx.getBean(EasyAgentExecutionProperties.class);
                    assertThat(props.resolve("queryUser", "AGENT").maxAttempts())
                        .isEqualTo(1);
                    assertThat(props.resolve("queryUser", "AGENT").backoffMillis())
                        .isZero();
                });
    }

    @Test
    void authorizationRequiredWithoutPolicyFails() {
        runner.withPropertyValues("easy-agent.execution.authorization.required=true")
            .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void authorizationBeanIsActuallyUsed() {
        runner.withBean(Service.class)
            .withBean(
                ToolAuthorizationPolicy.class,
                () -> (c, t, a) -> ToolAuthorizationPolicy.Decision.deny("denied"))
            .withPropertyValues("easy-agent.execution.authorization.required=true")
            .run(
                ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(
                        ctx.getBean(ToolExecutor.class)
                            .execute(
                                new ToolInvocation(
                                    "queryUser",
                                    "{\"value\":\"x\"}"))
                            .errorDetail()
                            .code())
                        .isEqualTo("TOOL_ACCESS_DENIED");
                    assertThat(ctx.getBean(Service.class).calls).isZero();
                });
    }

    @Test
    void rateLimitEnabledRequiresImplementation() {
        runner.withPropertyValues("easy-agent.execution.rate-limit.enabled=true")
            .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void limiterOnlyRunsWhenEnabled() {
        for (boolean enabled : List.of(false, true)) {
            AtomicInteger limitCalls = new AtomicInteger();
            runner.withBean(Service.class)
                .withBean(
                    ToolRateLimiter.class,
                    () -> (c, t) -> {
                        limitCalls.incrementAndGet();
                        return new ToolRateLimiter.Decision(false, 10L);
                    })
                .withPropertyValues("easy-agent.execution.rate-limit.enabled=" + enabled)
                .run(
                    ctx -> {
                        assertThat(ctx).hasNotFailed();
                        var result = ctx.getBean(ToolExecutor.class)
                            .execute(
                                new ToolInvocation(
                                    "queryUser", "{\"value\":\"x\"}"));
                        assertThat(result.success()).isEqualTo(!enabled);
                        assertThat(limitCalls.get()).isEqualTo(enabled ? 1 : 0);
                    });
        }
    }

    @Test
    void explicitLegacyRetryOnlyAppliesToAgentAndCountsTotalAttempts() {
        runner.withBean(Service.class)
            .withPropertyValues(
                "easy-agent.llm.tool-execution.retry-attempts=3",
                "easy-agent.execution.tools.queryUser.effect=READ_ONLY")
            .run(
                ctx -> {
                    assertThat(ctx).hasNotFailed();
                    var props = ctx.getBean(EasyAgentExecutionProperties.class);
                    assertThat(props.resolve("queryUser", "AGENT").maxAttempts())
                        .isEqualTo(3);
                    assertThat(props.resolve("queryUser", "MCP").maxAttempts())
                        .isEqualTo(1);
                });
    }

    @Test
    void legacyOneAttemptNeedsNoEffectButMultipleAttemptsRequireMigration() {
        runner.withBean(Service.class)
            .withPropertyValues("easy-agent.llm.tool-execution.retry-attempts=1")
            .run(ctx -> assertThat(ctx).hasNotFailed());
        runner.withBean(Service.class)
            .withPropertyValues("easy-agent.llm.tool-execution.retry-attempts=3")
            .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void precedenceIsPerField() {
        runner.withBean(Service.class)
            .withPropertyValues(
                "easy-agent.llm.tool-execution.retry-attempts=9",
                "easy-agent.llm.tool-execution.retry-backoff-millis=90",
                "easy-agent.execution.retry.default-max-attempts=2",
                "easy-agent.execution.retry.default-backoff-millis=20",
                "easy-agent.execution.tools.queryUser.effect=READ_ONLY",
                "easy-agent.execution.tools.queryUser.max-attempts=3")
            .run(
                ctx -> {
                    assertThat(ctx).hasNotFailed();
                    var options = ctx.getBean(EasyAgentExecutionProperties.class)
                        .resolve("queryUser", "AGENT");
                    assertThat(options.maxAttempts()).isEqualTo(3);
                    assertThat(options.backoffMillis()).isEqualTo(20);
                });
    }

    @Test
    void invalidConfigurationFailsAtStartup() {
        for (String property : List.of(
            "easy-agent.execution.retry.default-max-attempts=0",
            "easy-agent.execution.retry.default-backoff-millis=-1",
            "easy-agent.execution.tools.queryUser.max-attempts=-1",
            "easy-agent.llm.tool-execution.retry-attempts=0")) {
            runner.withPropertyValues(property).run(ctx -> assertThat(ctx).hasFailed());
        }
    }

    @Test
    void dedicatedMapperDoesNotReplaceHostMapper() {
        runner.withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .run(
                ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasBean("jacksonObjectMapper");
                    assertThat(ctx).hasBean("easyAgentToolObjectMapper");
                    assertThat(ctx.getBean("jacksonObjectMapper"))
                        .isNotSameAs(ctx.getBean("easyAgentToolObjectMapper"));
                });
    }

    @Test
    void optionalMetricsConditions() {
        var metrics = runner.withConfiguration(
            AutoConfigurations.of(EasyAgentMetricsAutoConfiguration.class));
        metrics.withPropertyValues("easy-agent.execution.metrics.enabled=true")
            .run(
                ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).doesNotHaveBean(MicrometerToolObservation.class);
                });
        metrics.withClassLoader(new FilteredClassLoader("io.micrometer"))
            .withPropertyValues("easy-agent.execution.metrics.enabled=true")
            .run(
                ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).doesNotHaveBean("micrometerToolObservation");
                });
        metrics.withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .run(ctx -> assertThat(ctx).doesNotHaveBean(MicrometerToolObservation.class));
        metrics.withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .withPropertyValues("easy-agent.execution.metrics.enabled=true")
            .run(
                ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(MicrometerToolObservation.class);
                });
    }
}
