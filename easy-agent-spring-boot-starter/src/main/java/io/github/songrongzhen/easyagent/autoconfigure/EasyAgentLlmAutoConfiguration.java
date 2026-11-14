package io.github.songrongzhen.easyagent.autoconfigure;

import io.github.songrongzhen.easyagent.core.executor.ToolExecutor;
import io.github.songrongzhen.easyagent.core.registry.ToolRegistry;
import io.github.songrongzhen.easyagent.llm.config.EasyAgentLlmProperties;
import io.github.songrongzhen.easyagent.llm.provider.LlmServiceFactory;
import io.github.songrongzhen.easyagent.llm.service.AgentLlmService;
import io.github.songrongzhen.easyagent.llm.service.LlmService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 模型服务与 Agent 编排的自动装配。
 */
@AutoConfiguration(after = EasyAgentCoreAutoConfiguration.class)
@ConditionalOnClass(name = "io.github.songrongzhen.easyagent.llm.service.LlmService")
@ConditionalOnProperty(prefix = "easy-agent.llm", name = "enabled", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties(EasyAgentLlmProperties.class)
public class EasyAgentLlmAutoConfiguration {

    /**
     * 根据宿主模型配置装配底层请求服务。
     *
     * @param properties 组件配置
     * @return 配置或装配得到的 LlmService 实例
     */
    @Bean
    @ConditionalOnMissingBean(LlmService.class)
    public LlmService llmService(EasyAgentLlmProperties properties) {
        return LlmServiceFactory.create(properties);
    }

    /**
     * 装配共享公共执行器的 Agent 编排服务。
     *
     * @param llmService   底层模型请求服务
     * @param toolRegistry 工具注册表
     * @param toolExecutor 公共工具执行器
     * @param properties   组件配置
     * @return 配置或装配得到的 AgentLlmService 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public AgentLlmService agentLlmService(LlmService llmService, ToolRegistry toolRegistry, ToolExecutor toolExecutor,
        EasyAgentLlmProperties properties) {
        return new AgentLlmService(llmService, toolRegistry, toolExecutor, properties);
    }
}
