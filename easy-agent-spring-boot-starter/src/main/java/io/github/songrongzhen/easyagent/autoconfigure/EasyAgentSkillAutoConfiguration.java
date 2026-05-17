package io.github.songrongzhen.easyagent.autoconfigure;

import io.github.songrongzhen.easyagent.skill.config.EasyAgentSkillProperties;
import io.github.songrongzhen.easyagent.skill.registry.SkillRegistry;
import io.github.songrongzhen.easyagent.skill.service.SkillLoaderService;
import io.github.songrongzhen.easyagent.skill.watcher.SkillFileWatcher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ContextRefreshedEvent;

@AutoConfiguration
@ConditionalOnClass(name = "io.github.songrongzhen.easyagent.skill.service.SkillLoaderService")
@ConditionalOnProperty(prefix = "easy-agent.skill", name = "enabled", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties(EasyAgentSkillProperties.class)
public class EasyAgentSkillAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SkillRegistry skillRegistry() {
        return new SkillRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public SkillLoaderService skillLoaderService(SkillRegistry skillRegistry, EasyAgentSkillProperties properties) {
        return new SkillLoaderService(skillRegistry, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public SkillFileWatcher skillFileWatcher(EasyAgentSkillProperties properties, SkillLoaderService skillLoaderService) {
        return new SkillFileWatcher(properties, skillLoaderService);
    }

    @Bean
    public SkillInitializer skillInitializer(SkillLoaderService skillLoaderService, SkillFileWatcher skillFileWatcher) {
        return new SkillInitializer(skillLoaderService, skillFileWatcher);
    }

    public static class SkillInitializer implements ApplicationListener<ContextRefreshedEvent> {
        private final SkillLoaderService skillLoaderService;
        private final SkillFileWatcher skillFileWatcher;
        private volatile boolean initialized = false;

        public SkillInitializer(SkillLoaderService skillLoaderService, SkillFileWatcher skillFileWatcher) {
            this.skillLoaderService = skillLoaderService;
            this.skillFileWatcher = skillFileWatcher;
        }

        @Override
        public void onApplicationEvent(ContextRefreshedEvent event) {
            if (!initialized) {
                initialized = true;
                skillLoaderService.loadSkills();
                skillFileWatcher.start();
            }
        }
    }
}
