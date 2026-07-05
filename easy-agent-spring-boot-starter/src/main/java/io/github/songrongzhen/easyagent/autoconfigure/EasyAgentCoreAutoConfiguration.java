package io.github.songrongzhen.easyagent.autoconfigure;

import io.github.songrongzhen.easyagent.core.executor.ToolExecutor;
import io.github.songrongzhen.easyagent.core.processor.EasyToolBeanPostProcessor;
import io.github.songrongzhen.easyagent.core.processor.ToolProviderRegistrar;
import io.github.songrongzhen.easyagent.core.registry.ToolRegistry;
import io.github.songrongzhen.easyagent.core.spi.ToolExecutionListener;
import io.github.songrongzhen.easyagent.core.spi.ToolProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.util.List;

@AutoConfiguration
public class EasyAgentCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ToolRegistry toolRegistry() {
        return new ToolRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public ToolExecutor toolExecutor(ToolRegistry toolRegistry,
                                     ApplicationContext applicationContext,
                                     List<ToolExecutionListener> executionListeners) {
        return new ToolExecutor(toolRegistry, applicationContext, executionListeners);
    }

    @Bean
    public EasyToolBeanPostProcessor easyToolBeanPostProcessor(ToolRegistry toolRegistry, ApplicationContext applicationContext) {
        return new EasyToolBeanPostProcessor(toolRegistry, applicationContext);
    }

    @Bean
    public ToolProviderRegistrar toolProviderRegistrar(ToolRegistry toolRegistry, List<ToolProvider> toolProviders) {
        return new ToolProviderRegistrar(toolRegistry, toolProviders);
    }
}
