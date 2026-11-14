package io.github.songrongzhen.easyagent.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.songrongzhen.easyagent.core.annotation.EasyTool;
import io.github.songrongzhen.easyagent.core.annotation.ToolParam;
import io.github.songrongzhen.easyagent.core.executor.ToolExecutor;
import io.github.songrongzhen.easyagent.core.model.ToolCallContext;
import io.github.songrongzhen.easyagent.core.model.ToolError;
import io.github.songrongzhen.easyagent.core.model.ToolInvocation;
import io.github.songrongzhen.easyagent.core.spi.ToolExceptionMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 工具执行契约的测试夹具与回归验证。 */
class MicrometerToolObservationTest {

    /** 工具执行契约的测试夹具与回归验证。 */
    public static class Service {

        int calls;

        @EasyTool(name = "queryUser", description = "query")
        public String query(@ToolParam(name = "id") Long id) {
            if (++calls < 3) {
                throw new IllegalStateException();
            }
            return "ok";
        }
    }

    @Test
    void countsLogicalCallsBusinessAttemptsAndRetries() {
        new ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    EasyAgentCoreAutoConfiguration.class,
                    EasyAgentMetricsAutoConfiguration.class))
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .withBean(Service.class)
            .withBean(
                ToolExceptionMapper.class,
                () -> (e, ctx) -> Optional.of(
                    new ToolError(
                        "TEMP",
                        ToolError.Category.TRANSIENT,
                        "later",
                        ctx.invocationId())))
            .withPropertyValues(
                "easy-agent.execution.metrics.enabled=true",
                "easy-agent.execution.tools.queryUser.effect=READ_ONLY",
                "easy-agent.execution.tools.queryUser.max-attempts=3")
            .run(
                ctx -> {
                    assertThat(ctx).hasNotFailed();
                    var executor = ctx.getBean(ToolExecutor.class);
                    var meters = ctx.getBean(MeterRegistry.class);
                    assertThat(
                        executor.execute(
                            new ToolInvocation(
                                "queryUser", "{\"id\":1}"))
                            .success())
                        .isTrue();
                    assertThat(sum(meters, "easy.agent.tool.calls")).isEqualTo(1);
                    assertThat(sum(meters, "easy.agent.tool.attempts")).isEqualTo(3);
                    assertThat(sum(meters, "easy.agent.tool.retries")).isEqualTo(2);
                    assertThat(
                        meters
                            .find("easy.agent.tool.duration")
                            .timers()
                            .stream()
                            .mapToLong(Timer::count)
                            .sum())
                        .isEqualTo(1);
                    executor.execute(new ToolInvocation("queryUser", "{}"));
                    for (int i = 0; i < 5; i++) {
                        executor.execute(
                            new ToolInvocation("arbitrary-" + i, "{}"),
                            new ToolCallContext(
                                "id-" + i,
                                "source-" + i,
                                "user-" + i,
                                null,
                                Map.of()));
                    }
                    assertThat(sum(meters, "easy.agent.tool.calls")).isEqualTo(7);
                    assertThat(sum(meters, "easy.agent.tool.attempts")).isEqualTo(3);
                    assertThat(
                        meters.get("easy.agent.tool.calls")
                            .tag("tool", "UNKNOWN")
                            .tag("source", "OTHER")
                            .counter()
                            .count())
                        .isEqualTo(5);
                    for (var meter : meters.getMeters()) {
                        assertThat(meter.getId().getTags())
                            .allMatch(
                                tag -> Set.of(
                                    "tool",
                                    "source",
                                    "outcome",
                                    "error_category")
                                    .contains(tag.getKey()));
                    }
                });
    }

    private double sum(MeterRegistry meters, String name) {
        return meters.find(name).counters().stream().mapToDouble(Counter::count).sum();
    }
}
