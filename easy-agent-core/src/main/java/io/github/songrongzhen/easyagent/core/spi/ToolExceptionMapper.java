package io.github.songrongzhen.easyagent.core.spi;

import io.github.songrongzhen.easyagent.core.model.ToolCallContext;
import io.github.songrongzhen.easyagent.core.model.ToolError;
import java.util.Optional;

/**
 * 业务异常映射扩展；按 Spring Order 顺序选择首个匹配结果，未知异常由执行器脱敏处理。
 */
public interface ToolExceptionMapper {

    /**
     * 将已经解包的业务异常转换为稳定错误，不能依靠异常文案推断瞬时故障。
     *
     * @param cause   业务方法或原有代理切面抛出的异常
     * @param context 可信调用上下文，包含当前逻辑调用标识
     * @return 匹配时返回可安全对外展示的错误；不匹配时返回 Optional.empty()
     */
    Optional<ToolError> map(Throwable cause, ToolCallContext context);
}
