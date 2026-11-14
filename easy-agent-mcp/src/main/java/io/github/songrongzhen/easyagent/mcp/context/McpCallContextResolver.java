package io.github.songrongzhen.easyagent.mcp.context;

import io.github.songrongzhen.easyagent.core.model.ToolCallContext;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Hosts resolve verified identity here; arbitrary authorization headers are not credentials.
 */
@FunctionalInterface
public interface McpCallContextResolver {

    /**
     * 从宿主已经认证的 HTTP 请求中取得可信身份，不信任任意请求头或工具参数。
     *
     * @param request 宿主已完成认证处理的 HTTP 请求
     * @return 新的可信调用上下文
     */
    ToolCallContext resolve(HttpServletRequest request);

    /**
     * 返回默认匿名上下文解析器，不把请求头当作已验证身份。
     *
     * @return 默认匿名解析器
     */
    static McpCallContextResolver anonymous() {
        return request -> ToolCallContext.anonymous(ToolCallContext.SOURCE_MCP);
    }

    /**
     * 宿主身份解析失败，HTTP 边界映射为未认证响应。
     */
    class AuthenticationException extends RuntimeException {

        /**
         * 标记宿主身份解析失败，不携带可能泄露凭证的异常原文。
         */
        public AuthenticationException() {
            super("Authentication required");
        }
    }
}
