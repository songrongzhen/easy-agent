package io.github.songrongzhen.easyagent.autoconfigure;

import io.github.songrongzhen.easyagent.core.registry.ToolRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 仅在显式启用且 Micrometer 和 MeterRegistry 可用时装配指标。
 */
@AutoConfiguration(
    after = EasyAgentCoreAutoConfiguration.class,
    afterName = "org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration")
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnBean(MeterRegistry.class)
@ConditionalOnProperty(prefix = "easy-agent.execution.metrics", name = "enabled", havingValue = "true")
public class EasyAgentMetricsAutoConfiguration {

    /**
     * 装配当前工具能力需要的依赖或扩展实现。
     *
     * @param meters 宿主 Micrometer 注册表
     * @param tools  按工具名称保存的配置或运行时绑定
     * @return 配置或装配得到的 MicrometerToolObservation 实例
     */
    @Bean
    @ConditionalOnMissingBean(MicrometerToolObservation.class)
    public MicrometerToolObservation micrometerToolObservation(MeterRegistry meters, ToolRegistry tools) {
        return new MicrometerToolObservation(meters, tools);
    }
}
