package io.github.songrongzhen.easyagent.core.executor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.songrongzhen.easyagent.core.model.ParameterDefinition;
import io.github.songrongzhen.easyagent.core.model.ToolDefinition;
import io.github.songrongzhen.easyagent.core.model.ToolInvocation;
import io.github.songrongzhen.easyagent.core.model.ToolResult;
import io.github.songrongzhen.easyagent.core.registry.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ToolRegistry toolRegistry;
    private final ApplicationContext applicationContext;

    public ToolExecutor(ToolRegistry toolRegistry, ApplicationContext applicationContext) {
        this.toolRegistry = toolRegistry;
        this.applicationContext = applicationContext;
    }

    public ToolResult execute(ToolInvocation invocation) {
        ToolDefinition toolDef = toolRegistry.getTool(invocation.toolName());
        if (toolDef == null) {
            return ToolResult.failure(invocation.toolName(), "Tool not found: " + invocation.toolName());
        }

        try {
            Object bean = applicationContext.getBean(toolDef.beanName());
            Method method = findMethod(bean.getClass(), toolDef.methodName(), toolDef.parameters());
            Object[] args = resolveArguments(invocation.arguments(), toolDef.parameters(), method);
            Object result = method.invoke(bean, args);
            String resultStr = result == null ? "" : OBJECT_MAPPER.writeValueAsString(result);
            return ToolResult.success(invocation.toolName(), resultStr);
        } catch (Exception e) {
            log.error("Failed to execute tool: {}", invocation.toolName(), e);
            return ToolResult.failure(invocation.toolName(), e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        }
    }

    private Method findMethod(Class<?> beanClass, String methodName, List<ParameterDefinition> paramDefs) throws NoSuchMethodException {
        Method[] methods = beanClass.getMethods();
        for (Method m : methods) {
            if (m.getName().equals(methodName) && m.getParameterCount() == paramDefs.size()) {
                return m;
            }
        }
        throw new NoSuchMethodException("Method not found: " + methodName + " with " + paramDefs.size() + " parameters");
    }

    private Object[] resolveArguments(String argumentsJson, List<ParameterDefinition> paramDefs, Method method) throws JsonProcessingException {
        if (paramDefs.isEmpty()) {
            return new Object[0];
        }

        JsonNode argsNode = OBJECT_MAPPER.readTree(argumentsJson);
        Parameter[] methodParams = method.getParameters();
        Object[] args = new Object[paramDefs.size()];

        for (int i = 0; i < paramDefs.size(); i++) {
            ParameterDefinition paramDef = paramDefs.get(i);
            JsonNode valueNode = argsNode.get(paramDef.name());
            Class<?> paramType = methodParams[i].getType();
            args[i] = convertValue(valueNode, paramType);
        }

        return args;
    }

    private Object convertValue(JsonNode valueNode, Class<?> targetType) {
        if (valueNode == null || valueNode.isNull()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.treeToValue(valueNode, targetType);
        } catch (JsonProcessingException e) {
            if (targetType == String.class) {
                return valueNode.asText();
            } else if (targetType == Integer.class || targetType == int.class) {
                return valueNode.asInt();
            } else if (targetType == Long.class || targetType == long.class) {
                return valueNode.asLong();
            } else if (targetType == Double.class || targetType == double.class) {
                return valueNode.asDouble();
            } else if (targetType == Boolean.class || targetType == boolean.class) {
                return valueNode.asBoolean();
            }
            return valueNode.asText();
        }
    }
}
