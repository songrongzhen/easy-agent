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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EasyToolBeanPostProcessor implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(EasyToolBeanPostProcessor.class);
    private static final String SOURCE_PREFIX = "annotation";

    private final ToolRegistry toolRegistry;
    private final ApplicationContext applicationContext;

    public EasyToolBeanPostProcessor(ToolRegistry toolRegistry, ApplicationContext applicationContext) {
        this.toolRegistry = toolRegistry;
        this.applicationContext = applicationContext;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Set<String> seenToolNames = new HashSet<>();
        ReflectionUtils.doWithMethods(bean.getClass(), method -> {
            EasyTool easyTool = AnnotationUtils.findAnnotation(method, EasyTool.class);
            if (easyTool != null && easyTool.enabled()) {
                // doWithMethods 会沿继承链/CGLIB 代理类遍历，同一逻辑方法（子类覆盖、
                // 代理方法与目标类原方法）可能被访问多次；同名工具只注册首个，
                // 否则 registerOrFail 会抛 IllegalStateException 直接打断应用启动。
                if (!seenToolNames.add(easyTool.name())) {
                    return;
                }
                ToolDefinition definition = buildToolDefinition(easyTool, method, beanName);
                toolRegistry.registerOrFail(definition);
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
                SOURCE_PREFIX,
                0,
                beanName,
                method.getName(),
                parameters,
                "1",
                easyTool.enabled()
        );
    }
}
