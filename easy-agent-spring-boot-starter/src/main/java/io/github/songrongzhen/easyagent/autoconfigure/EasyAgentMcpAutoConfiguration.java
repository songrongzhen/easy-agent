package io.github.songrongzhen.easyagent.autoconfigure;

import io.github.songrongzhen.easyagent.core.executor.ToolExecutor;
import io.github.songrongzhen.easyagent.core.registry.ToolRegistry;
import io.github.songrongzhen.easyagent.mcp.config.EasyAgentMcpProperties;
import io.github.songrongzhen.easyagent.mcp.server.EasyAgentMcpServer;
import io.github.songrongzhen.easyagent.skill.service.SkillGeneratorService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.lang.Nullable;

@AutoConfiguration
@ConditionalOnClass(name = "io.github.songrongzhen.easyagent.mcp.server.EasyAgentMcpServer")
@ConditionalOnWebApplication
@ConditionalOnProperty(prefix = "easy-agent.mcp", name = "enabled", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties(EasyAgentMcpProperties.class)
public class EasyAgentMcpAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public EasyAgentMcpServer easyAgentMcpServer(EasyAgentMcpProperties properties,
                                                  ToolRegistry toolRegistry,
                                                  ToolExecutor toolExecutor,
                                                  @Nullable SkillGeneratorService skillGeneratorService) {
        EasyAgentMcpServer server = new EasyAgentMcpServer(properties, toolRegistry, toolExecutor, skillGeneratorService);
        server.start();
        return server;
    }

    @Bean
    @ConditionalOnMissingBean(name = "mcpController")
    public io.github.songrongzhen.easyagent.mcp.controller.McpController mcpController(
            EasyAgentMcpServer mcpServer, EasyAgentMcpProperties properties) {
        return new io.github.songrongzhen.easyagent.mcp.controller.McpController(mcpServer, properties);
    }
}
