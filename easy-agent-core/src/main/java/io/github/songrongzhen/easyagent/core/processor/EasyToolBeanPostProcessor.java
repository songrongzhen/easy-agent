package io.github.songrongzhen.easyagent.core.processor;

import io.github.songrongzhen.easyagent.core.annotation.EasyTool;
import io.github.songrongzhen.easyagent.core.annotation.ToolParam;
import io.github.songrongzhen.easyagent.core.model.ParameterDefinition;
import io.github.songrongzhen.easyagent.core.model.ToolDefinition;
import io.github.songrongzhen.easyagent.core.registry.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

public class EasyToolBeanPostProcessor implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(EasyToolBeanPostProcessor.class);

    private final ToolRegistry toolRegistry;
    private final ApplicationContext applicationContext;

    public EasyToolBeanPostProcessor(ToolRegistry toolRegistry, ApplicationContext applicationContext) {
        this.toolRegistry = toolRegistry;
        this.applicationContext = applicationContext;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        ReflectionUtils.doWithMethods(bean.getClass(), method -> {
            EasyTool easyTool = AnnotationUtils.findAnnotation(method, EasyTool.class);
            if (easyTool != null && easyTool.enabled()) {
                ToolDefinition definition = buildToolDefinition(easyTool, method, beanName);
                toolRegistry.register(definition);
            }
        });
        return bean;
    }

    private ToolDefinition buildToolDefinition(EasyTool easyTool, Method method, String beanName) {
        List<ParameterDefinition> parameters = new ArrayList<>();
        Parameter[] methodParams = method.getParameters();

        for (Parameter param : methodParams) {
            ToolParam toolParam = param.getAnnotation(ToolParam.class);
            if (toolParam != null) {
                parameters.add(new ParameterDefinition(
                        toolParam.name(),
                        toolParam.description(),
                        param.getType().getSimpleName(),
                        toolParam.required()
                ));
            } else {
                parameters.add(new ParameterDefinition(
                        param.getName(),
                        "",
                        param.getType().getSimpleName(),
                        true
                ));
            }
        }

        return new ToolDefinition(
                easyTool.name(),
                easyTool.description(),
                easyTool.category(),
                beanName,
                method.getName(),
                parameters,
                easyTool.enabled()
        );
    }
}
