package io.github.songrongzhen.easyagent.core.config;

/**
 * 单工具执行配置，多次尝试必须声明安全的副作用类别。
 *
 * @param effect        宿主声明的工具副作用类别
 * @param maxAttempts   包含首次执行的总尝试次数
 * @param backoffMillis 仅在下次尝试前等待的毫秒数
 */
public record ToolExecutionOptions(Effect effect, int maxAttempts, long backoffMillis) {

    public ToolExecutionOptions {
        if (effect == null) {
            effect = Effect.UNKNOWN;
        }
        if (maxAttempts < 1 || backoffMillis < 0) {
            throw new IllegalArgumentException("Invalid tool retry attempts/backoff");
        }
        if (maxAttempts > 1 && effect != Effect.READ_ONLY && effect != Effect.IDEMPOTENT) {
            throw new IllegalArgumentException("Multiple tool attempts require READ_ONLY or IDEMPOTENT effect; migrate"
                + " easy-agent.execution.tools");
        }
    }

    /**
     * 返回 UNKNOWN、一次执行及零退避的安全默认选项。
     *
     * @return 配置或装配得到的 ToolExecutionOptions 实例
     */
    public static ToolExecutionOptions defaults() {
        return new ToolExecutionOptions(Effect.UNKNOWN, 1, 0);
    }

    /**
     * 宿主声明的工具副作用类别，决定是否允许自动重试。
     */
    public enum Effect {

        /**
         * 未声明安全属性，默认仅允许一次执行。
         */
        UNKNOWN,
        /**
         * 只读操作，可对可靠归类的瞬时故障进行重试。
         */
        READ_ONLY,
        /**
         * 宿主保证幂等及必要去重的操作，可安全重试。
         */
        IDEMPOTENT,
        /**
         * 非幂等操作，不允许自动重试。
         */
        NON_IDEMPOTENT
    }

    /**
     * 根据工具名称及调用入口解析实际执行配置。
     */
    @FunctionalInterface
    public interface Resolver {

        /**
         * 根据工具名称和可信入口标识解析实际生效的执行选项。
         *
         * @param toolName 工具名称
         * @param source   调用入口标识
         * @return 配置或装配得到的 ToolExecutionOptions 实例
         */
        ToolExecutionOptions resolve(String toolName, String source);
    }
}
