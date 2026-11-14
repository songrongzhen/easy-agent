package io.github.songrongzhen.easyagent.core.spi;

import io.github.songrongzhen.easyagent.core.model.ToolCallContext;
import io.github.songrongzhen.easyagent.core.model.ToolDefinition;

/**
 * 宿主实现的非阻塞限流扩展，每次真实尝试在授权和参数绑定成功后申请许可。 多个限流器依次执行，后续拒绝不会退还前面已消耗的许可；实现异常会终止执行。
 */
public interface ToolRateLimiter {

    /**
     * 立即申请本次尝试的许可，不等待额度。
     *
     * @param context 可信调用上下文；重试保留同一逻辑调用标识
     * @param tool    已注册的工具定义
     * @return 非空许可决定，拒绝时不调用业务方法
     */
    Decision tryAcquire(ToolCallContext context, ToolDefinition tool);

    /**
     * 本次许可决定。
     *
     * @param allowed          是否获得许可
     * @param retryAfterMillis 建议调用方等待的毫秒数，可为空，不触发组件内部自动重试
     */
    record Decision(boolean allowed, Long retryAfterMillis) {
    }
}
