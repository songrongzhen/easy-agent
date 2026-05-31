package io.github.songrongzhen.easyagent.autoconfigure;

import io.github.songrongzhen.easyagent.core.registry.ToolRegistry;
import io.github.songrongzhen.easyagent.skill.config.EasyAgentSkillProperties;
import io.github.songrongzhen.easyagent.skill.service.SkillGeneratorService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(name = "io.github.songrongzhen.easyagent.skill.service.SkillGeneratorService")
@ConditionalOnProperty(prefix = "easy-agent.skill", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(EasyAgentSkillProperties.class)
public class EasyAgentSkillAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SkillGeneratorService skillGeneratorService(ToolRegistry toolRegistry, EasyAgentSkillProperties properties) {
        return new SkillGeneratorService(toolRegistry, properties);
    }
}
