package io.github.songrongzhen.easyagent.autoconfigure;

import io.github.songrongzhen.easyagent.core.executor.ToolExecutor;
import io.github.songrongzhen.easyagent.core.processor.EasyToolBeanPostProcessor;
import io.github.songrongzhen.easyagent.core.registry.ToolRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class EasyAgentCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ToolRegistry toolRegistry() {
        return new ToolRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public ToolExecutor toolExecutor(ToolRegistry toolRegistry, ApplicationContext applicationContext) {
        return new ToolExecutor(toolRegistry, applicationContext);
    }

    @Bean
    public EasyToolBeanPostProcessor easyToolBeanPostProcessor(ToolRegistry toolRegistry, ApplicationContext applicationContext) {
        return new EasyToolBeanPostProcessor(toolRegistry, applicationContext);
    }
}
