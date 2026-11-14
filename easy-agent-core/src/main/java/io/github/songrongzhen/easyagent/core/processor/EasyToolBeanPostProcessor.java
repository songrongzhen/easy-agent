package io.github.songrongzhen.easyagent.core.processor;

import io.github.songrongzhen.easyagent.core.annotation.EasyTool;
import io.github.songrongzhen.easyagent.core.annotation.ToolParam;
import io.github.songrongzhen.easyagent.core.model.ParameterDefinition;
import io.github.songrongzhen.easyagent.core.model.ToolDefinition;
import io.github.songrongzhen.easyagent.core.registry.ToolRegistry;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 发现注解工具，在单例初始化完成后绑定最终 Spring 代理。
 */
public class EasyToolBeanPostProcessor implements BeanPostProcessor, SmartInitializingSingleton {

    /**
     * 注解注册工具的来源标识。
     */
    private static final String SOURCE_PREFIX = "annotation";

    /**
     * 工具注册表。
     */
    private final ToolRegistry toolRegistry;

    /**
     * 宿主 Spring 容器。
     */
    private final ApplicationContext applicationContext;

    /**
     * 创建 EasyToolBeanPostProcessor，保存调用所需的显式依赖。
     *
     * @param toolRegistry       工具注册表
     * @param applicationContext 宿主 Spring 容器
     */
    public EasyToolBeanPostProcessor(ToolRegistry toolRegistry, ApplicationContext applicationContext) {
        this.toolRegistry = toolRegistry;
        this.applicationContext = applicationContext;
    }

    /**
     * 按 Bean 名称收集的待最终绑定工具方法。
     */
    private final Map<String, Map<Method, EasyTool>> discovered = new LinkedHashMap<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> target = AopUtils.getTargetClass(bean);
        ReflectionUtils.doWithMethods(target, method -> {
            if (method.isBridge() || method.isSynthetic()) {
                return;
            }
            Method specific = BridgeMethodResolver.findBridgedMethod(ClassUtils.getMostSpecificMethod(method, target));
            EasyTool annotation = AnnotationUtils.findAnnotation(specific, EasyTool.class);
            if (annotation == null) {
                annotation = AnnotationUtils.findAnnotation(method, EasyTool.class);
            }
            if (annotation != null && annotation.enabled()) {
                discovered.computeIfAbsent(beanName, k -> new LinkedHashMap<>()).putIfAbsent(specific, annotation);
            }
        }, ReflectionUtils.USER_DECLARED_METHODS);
        return bean;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterSingletonsInstantiated() {
        discovered.forEach((name, methods) -> {
            Object proxy = applicationContext.getBean(name);
            methods.forEach((method, annotation) -> {
                toolRegistry.register(buildToolDefinition(annotation, method, name), proxy, method, null,
                    ToolRegistry.ConflictStrategy.FAIL_FAST);
            });
        });
        discovered.clear();
    }

    private ToolDefinition buildToolDefinition(EasyTool easyTool, Method method, String beanName) {
        List<ParameterDefinition> parameters = new ArrayList<>();
        Parameter[] methodParams = method.getParameters();
        for (Parameter param : methodParams) {
            ToolParam toolParam = param.getAnnotation(ToolParam.class);
            if (toolParam != null) {
                parameters.add(new ParameterDefinition(toolParam.name(), toolParam.description(),
                    param.getType().getSimpleName(), toolParam.required()));
            } else {
                parameters.add(new ParameterDefinition(param.getName(), "", param.getType().getSimpleName(), true));
            }
        }
        return new ToolDefinition(easyTool.name(), easyTool.description(), easyTool.category(), SOURCE_PREFIX, 0,
            beanName, method.getName(), parameters, "1", easyTool.enabled());
    }
}
