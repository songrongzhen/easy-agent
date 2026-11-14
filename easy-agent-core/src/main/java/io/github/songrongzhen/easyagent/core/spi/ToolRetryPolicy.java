package io.github.songrongzhen.easyagent.core.spi;

import io.github.songrongzhen.easyagent.core.config.ToolExecutionOptions;
import io.github.songrongzhen.easyagent.core.model.ToolCallContext;
import io.github.songrongzhen.easyagent.core.model.ToolDefinition;
import io.github.songrongzhen.easyagent.core.model.ToolError;

/**
 * 工具重试决策扩展；执行器先检查次数、中断及副作用硬边界，策略不能绕过这些边界。
 */
public interface ToolRetryPolicy {

    /**
     * 判断业务失败后是否允许再次尝试，不负责等待或重放调用。
     *
     * @param tool    已注册的工具定义
     * @param context 重试期间保持不变的可信逻辑调用上下文
     * @param options 本工具及入口的生效配置
     * @param attempt 已经执行的业务尝试次数
     * @param error   当前业务失败的结构化错误
     * @return 是否建议再次尝试
     */
    boolean shouldRetry(ToolDefinition tool, ToolCallContext context, ToolExecutionOptions options, int attempt,
        ToolError error);

    /**
     * 创建默认安全策略，仅允许安全工具的 TRANSIENT 失败重试。
     *
     * @return 无状态的默认重试策略
     */
    static ToolRetryPolicy safeDefault() {
        return (tool, context, options, attempt, error) -> error != null
            && error.category() == ToolError.Category.TRANSIENT
            && (options.effect() == ToolExecutionOptions.Effect.READ_ONLY
                || options.effect() == ToolExecutionOptions.Effect.IDEMPOTENT);
    }
}
