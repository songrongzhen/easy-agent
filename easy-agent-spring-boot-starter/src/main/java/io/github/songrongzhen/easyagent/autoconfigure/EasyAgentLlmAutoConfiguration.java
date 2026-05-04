package io.github.songrongzhen.easyagent.autoconfigure;

import io.github.songrongzhen.easyagent.llm.config.EasyAgentLlmProperties;
import io.github.songrongzhen.easyagent.llm.provider.LlmServiceFactory;
import io.github.songrongzhen.easyagent.llm.service.LlmService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(name = "io.github.songrongzhen.easyagent.llm.service.LlmService")
@ConditionalOnProperty(prefix = "easy-agent.llm", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(EasyAgentLlmProperties.class)
public class EasyAgentLlmAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(LlmService.class)
    public LlmService llmService(EasyAgentLlmProperties properties) {
        return LlmServiceFactory.create(properties);
    }
}
