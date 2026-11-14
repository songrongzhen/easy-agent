package io.github.songrongzhen.easyagent.autoconfigure;

import io.github.songrongzhen.easyagent.core.model.ToolCallContext;
import io.github.songrongzhen.easyagent.core.model.ToolResult;
import io.github.songrongzhen.easyagent.core.registry.ToolRegistry;
import io.github.songrongzhen.easyagent.core.spi.ToolObservation;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 将执行观测事件转换为低基数指标，不将身份和调用标识作为标签。
 */
public class MicrometerToolObservation implements ToolObservation {

    /**
     * 宿主 Micrometer 注册表。
     */
    private final MeterRegistry meters;

    /**
     * 按工具名称保存的配置或运行时绑定。
     */
    private final ToolRegistry tools;

    /**
     * 创建 MicrometerToolObservation，保存调用所需的显式依赖。
     *
     * @param meters 宿主 Micrometer 注册表
     * @param tools  按工具名称保存的配置或运行时绑定
     */
    public MicrometerToolObservation(MeterRegistry meters, ToolRegistry tools) {
        this.meters = meters;
        this.tools = tools;
    }

    private Tags tags(ToolCallContext context, String name, ToolResult result) {
        String tool = tools.contains(name) ? name : "UNKNOWN";
        String source = Set.of(ToolCallContext.SOURCE_MCP, ToolCallContext.SOURCE_AGENT, ToolCallContext.SOURCE_JAVA)
            .contains(context.source() == null ? "" : context.source()) ? context.source() : "OTHER";
        String outcome = result != null && result.success() ? "SUCCESS" : "FAILURE";
        String category = result != null && result.errorDetail() != null ? result.errorDetail().category().name()
            : "NONE";
        return Tags.of("tool", tool, "source", source, "outcome", outcome, "error_category", category);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAttemptFinished(ToolCallContext ctx, String tool, int attempt, long nanos, ToolResult result) {
        Tags tags = tags(ctx, tool, result);
        meters.counter("easy.agent.tool.attempts", tags).increment();
        meters.timer("easy.agent.tool.attempt.duration", tags).record(nanos, TimeUnit.NANOSECONDS);
        if (attempt > 1) {
            meters.counter("easy.agent.tool.retries", tags).increment();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCallFinished(ToolCallContext ctx, String tool, long nanos, int attempts, ToolResult result) {
        Tags tags = tags(ctx, tool, result);
        meters.counter("easy.agent.tool.calls", tags).increment();
        meters.timer("easy.agent.tool.duration", tags).record(nanos, TimeUnit.NANOSECONDS);
    }
}
