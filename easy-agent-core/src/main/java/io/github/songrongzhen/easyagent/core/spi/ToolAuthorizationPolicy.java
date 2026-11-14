package io.github.songrongzhen.easyagent.core.spi;

import io.github.songrongzhen.easyagent.core.model.ToolCallContext;
import io.github.songrongzhen.easyagent.core.model.ToolDefinition;
import java.util.Map;

/**
 * 公共工具执行授权策略；按 Spring Order 顺序调用，全部允许后才能绑定参数和执行业务。 策略异常会终止执行，不会按允许处理。
 */
public interface ToolAuthorizationPolicy {

    /**
     * 检查本次真实尝试是否有权执行工具。
     *
     * @param context   宿主提供的可信身份及逻辑调用上下文
     * @param tool      已注册的工具定义
     * @param arguments 深度只读的原始 JSON 参数，仍属于需要验证权限关系的业务输入
     * @return 非空授权决定；拒绝时短路后续策略和业务执行
     */
    Decision authorize(ToolCallContext context, ToolDefinition tool, Map<String, Object> arguments);

    /**
     * 工具级授权决定。
     *
     * @param allowed 是否允许本次尝试
     * @param message 可对外展示的拒绝原因，允许时可为空
     */
    record Decision(boolean allowed, String message) {

        /**
         * @return 允许本次尝试的决定
         */
        public static Decision allow() {
            return new Decision(true, null);
        }

        /**
         * @param message 可对外展示的拒绝原因
         * @return 拒绝本次尝试的决定
         */
        public static Decision deny(String message) {
            return new Decision(false, message);
        }
    }
}
