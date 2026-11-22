package io.github.songrongzhen.easyagent.autoconfigure;

import io.github.songrongzhen.easyagent.core.executor.ToolExecutor;
import io.github.songrongzhen.easyagent.core.registry.ToolRegistry;
import io.github.songrongzhen.easyagent.mcp.config.EasyAgentMcpProperties;
import io.github.songrongzhen.easyagent.mcp.config.McpCorsConfig;
import io.github.songrongzhen.easyagent.mcp.context.McpCallContextResolver;
import io.github.songrongzhen.easyagent.mcp.controller.McpController;
import io.github.songrongzhen.easyagent.mcp.server.EasyAgentMcpServer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * MCP HTTP 服务及可信上下文解析器的自动装配。
 */
@AutoConfiguration(after = EasyAgentCoreAutoConfiguration.class)
@ConditionalOnClass(name = "io.github.songrongzhen.easyagent.mcp.server.EasyAgentMcpServer")
@ConditionalOnWebApplication
@ConditionalOnProperty(prefix = "easy-agent.mcp", name = "enabled", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties(EasyAgentMcpProperties.class)
@Import(McpCorsConfig.class)
public class EasyAgentMcpAutoConfiguration {

    /**
     * 装配当前工具能力需要的依赖或扩展实现。
     *
     * @param properties   组件配置
     * @param toolRegistry 工具注册表
     * @param toolExecutor 公共工具执行器
     * @return 配置或装配得到的 EasyAgentMcpServer 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public EasyAgentMcpServer easyAgentMcpServer(EasyAgentMcpProperties properties, ToolRegistry toolRegistry,
        ToolExecutor toolExecutor) {
        EasyAgentMcpServer server = new EasyAgentMcpServer(properties, toolRegistry, toolExecutor);
        server.start();
        return server;
    }

    /**
     * 装配当前工具能力需要的依赖或扩展实现。
     *
     * @return 配置或装配得到的 McpCallContextResolver 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public McpCallContextResolver mcpCallContextResolver() {
        return McpCallContextResolver.anonymous();
    }

    /**
     * 装配当前工具能力需要的依赖或扩展实现。
     *
     * @param mcpServer  MCP 请求分发器
     * @param properties 组件配置
     * @param resolver   MCP 工具调用上下文解析器，由宿主提供已验证的身份信息
     * @return 配置或装配得到的 McpController 实例
     */
    @Bean
    @ConditionalOnMissingBean(name = "mcpController")
    public McpController mcpController(EasyAgentMcpServer mcpServer, EasyAgentMcpProperties properties,
        McpCallContextResolver resolver) {
        return new McpController(mcpServer, properties, resolver);
    }
}
