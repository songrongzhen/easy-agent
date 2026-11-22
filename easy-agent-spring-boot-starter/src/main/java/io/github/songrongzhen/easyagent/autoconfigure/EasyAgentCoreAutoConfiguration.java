package io.github.songrongzhen.easyagent.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.songrongzhen.easyagent.core.config.ToolExecutionOptions.Resolver;
import io.github.songrongzhen.easyagent.core.executor.ToolExecutor;
import io.github.songrongzhen.easyagent.core.processor.EasyToolBeanPostProcessor;
import io.github.songrongzhen.easyagent.core.processor.ToolProviderRegistrar;
import io.github.songrongzhen.easyagent.core.registry.ToolMethodResolver;
import io.github.songrongzhen.easyagent.core.registry.ToolRegistry;
import io.github.songrongzhen.easyagent.core.schema.ToolSchemaGenerator;
import io.github.songrongzhen.easyagent.core.spi.ToolAuthorizationPolicy;
import io.github.songrongzhen.easyagent.core.spi.ToolExceptionMapper;
import io.github.songrongzhen.easyagent.core.spi.ToolExecutionListener;
import io.github.songrongzhen.easyagent.core.spi.ToolObservation;
import io.github.songrongzhen.easyagent.core.spi.ToolProvider;
import io.github.songrongzhen.easyagent.core.spi.ToolRateLimiter;
import io.github.songrongzhen.easyagent.core.spi.ToolRetryPolicy;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;
import org.springframework.core.env.Environment;
import java.util.List;

/**
 * 工具运行时元数据、公共执行契约和治理扩展的自动装配。
 */
@AutoConfiguration
@EnableConfigurationProperties(EasyAgentExecutionProperties.class)
public class EasyAgentCoreAutoConfiguration {

    /**
     * 装配独立于全局 mapper 的工具专用 ObjectMapper。
     *
     * @return Schema 与参数绑定共用的工具 ObjectMapper
     */
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @Bean(name = "easyAgentToolObjectMapper", defaultCandidate = false)
    @ConditionalOnMissingBean(name = "easyAgentToolObjectMapper")
    public static ObjectMapper easyAgentToolObjectMapper() {
        return ToolExecutor.createObjectMapper();
    }

    /**
     * 装配当前工具能力需要的依赖或扩展实现。
     *
     * @param mapper 工具专用 ObjectMapper
     * @return 使用工具专用 mapper 的 Schema 生成器
     */
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @Bean
    @ConditionalOnMissingBean
    public static ToolSchemaGenerator toolSchemaGenerator(@Qualifier("easyAgentToolObjectMapper") ObjectMapper mapper) {
        return new ToolSchemaGenerator(mapper);
    }

    /**
     * 装配当前工具能力需要的依赖或扩展实现。
     *
     * @param schemas 公共 Schema 生成器
     * @return 解析确定方法及完整泛型的共用解析器
     */
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @Bean
    @ConditionalOnMissingBean
    public static ToolMethodResolver toolMethodResolver(ToolSchemaGenerator schemas) {
        return new ToolMethodResolver(schemas);
    }

    /**
     * 装配当前工具能力需要的依赖或扩展实现。
     *
     * @param resolver 注册方法解析器
     * @param context  宿主 Spring 应用容器，用于查找工具 Bean
     * @return 配置或装配得到的 ToolRegistry 实例
     */
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @Bean
    @ConditionalOnMissingBean
    public static ToolRegistry toolRegistry(ToolMethodResolver resolver, ApplicationContext context) {
        return new ToolRegistry(resolver, context);
    }

    /**
     * 装配当前工具能力需要的依赖或扩展实现。
     *
     * @return 不绕过次数、中断与副作用硬边界的重试策略
     */
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @Bean
    @ConditionalOnMissingBean
    public ToolRetryPolicy toolRetryPolicy() {
        return ToolRetryPolicy.safeDefault();
    }

    /**
     * 装配当前工具能力需要的依赖或扩展实现。
     *
     * @param properties  组件配置
     * @param environment 宿主配置环境
     * @param policies    按顺序执行的宿主授权策略
     * @param limiters    按顺序执行的宿主限流器
     * @return 配置或装配得到的 Resolver 实例
     */
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @Bean
    public Resolver toolExecutionOptions(EasyAgentExecutionProperties properties, Environment environment,
        List<ToolAuthorizationPolicy> policies, List<ToolRateLimiter> limiters) {
        properties.prepare(environment);
        if (properties.getAuthorization().isRequired() && policies.isEmpty()) {
            throw new IllegalStateException("authorization.required=true requires a ToolAuthorizationPolicy bean");
        }
        if (properties.getRateLimit().isEnabled() && limiters.isEmpty()) {
            throw new IllegalStateException("rate-limit.enabled=true requires a ToolRateLimiter bean");
        }
        return properties::resolve;
    }

    /**
     * 装配当前工具能力需要的依赖或扩展实现。
     *
     * @param registry     工具注册表
     * @param context      宿主 Spring 应用容器，用于查找工具 Bean
     * @param mapper       工具专用 ObjectMapper
     * @param listeners    逻辑调用监听器
     * @param policies     按顺序执行的宿主授权策略
     * @param limiters     按顺序执行的宿主限流器
     * @param mappers      业务异常映射扩展
     * @param observations 执行观测扩展
     * @param retryPolicy  工具重试策略
     * @param properties   组件配置
     * @param options      生效执行选项解析器
     * @return 配置或装配得到的 ToolExecutor 实例
     */
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @Bean
    @ConditionalOnMissingBean
    public ToolExecutor toolExecutor(ToolRegistry registry, ApplicationContext context,
        @Qualifier("easyAgentToolObjectMapper") ObjectMapper mapper, List<ToolExecutionListener> listeners,
        List<ToolAuthorizationPolicy> policies, List<ToolRateLimiter> limiters, List<ToolExceptionMapper> mappers,
        List<ToolObservation> observations, ToolRetryPolicy retryPolicy, EasyAgentExecutionProperties properties,
        Resolver options) {
        return new ToolExecutor(registry, context, mapper, listeners, policies,
            properties.getRateLimit().isEnabled() ? limiters : List.of(), mappers, observations, retryPolicy, options);
    }

    /**
     * 装配注解扫描器，延后绑定最终 Spring 代理。
     *
     * @param registry 工具注册表
     * @param context  宿主 Spring 应用容器，用于查找工具 Bean
     * @return 配置或装配得到的 EasyToolBeanPostProcessor 实例
     */
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @Bean
    public static EasyToolBeanPostProcessor easyToolBeanPostProcessor(ToolRegistry registry,
        ApplicationContext context) {
        return new EasyToolBeanPostProcessor(registry, context);
    }

    /**
     * 装配当前工具能力需要的依赖或扩展实现。
     *
     * @param registry  工具注册表
     * @param providers 宿主提供的工具定义 SPI 列表
     * @return 配置或装配得到的 ToolProviderRegistrar 实例
     */
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @Bean
    public ToolProviderRegistrar toolProviderRegistrar(ToolRegistry registry, List<ToolProvider> providers) {
        return new ToolProviderRegistrar(registry, providers);
    }

    /**
     * 装配当前工具能力需要的依赖或扩展实现。
     *
     * @param registry   工具注册表
     * @param properties 组件配置
     * @param registrar  在单例完成后注册 SPI 工具的组件
     * @param executor   公共工具执行器
     * @param options    生效执行选项解析器
     * @return 配置或装配得到的 SmartInitializingSingleton 实例
     */
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @Bean
    public SmartInitializingSingleton easyAgentExecutionValidator(ToolRegistry registry,
        EasyAgentExecutionProperties properties, ToolProviderRegistrar registrar, ToolExecutor executor,
        Resolver options) {
        return () -> properties.validateTools(registry);
    }
}
