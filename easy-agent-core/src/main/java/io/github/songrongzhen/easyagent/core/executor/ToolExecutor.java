package io.github.songrongzhen.easyagent.core.executor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.songrongzhen.easyagent.core.model.ParameterDefinition;
import io.github.songrongzhen.easyagent.core.model.ToolDefinition;
import io.github.songrongzhen.easyagent.core.model.ToolInvocation;
import io.github.songrongzhen.easyagent.core.model.ToolResult;
import io.github.songrongzhen.easyagent.core.registry.ToolRegistry;
import io.github.songrongzhen.easyagent.core.spi.ToolExecutionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

public class ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ToolRegistry toolRegistry;
    private final ApplicationContext applicationContext;
    private final List<ToolExecutionListener> executionListeners;

    public ToolExecutor(ToolRegistry toolRegistry, ApplicationContext applicationContext) {
        this(toolRegistry, applicationContext, List.of());
    }

    public ToolExecutor(ToolRegistry toolRegistry,
                        ApplicationContext applicationContext,
                        List<ToolExecutionListener> executionListeners) {
        this.toolRegistry = toolRegistry;
        this.applicationContext = applicationContext;
        this.executionListeners = executionListeners == null ? List.of() : executionListeners;
    }

    public ToolResult execute(ToolInvocation invocation) {
        if (invocation == null || invocation.toolName() == null || invocation.toolName().isBlank()) {
            return ToolResult.failure(null, "Tool name is required");
        }

        ToolDefinition toolDef = toolRegistry.getTool(invocation.toolName());
        if (toolDef == null) {
            return ToolResult.failure(invocation.toolName(), "Tool not found: " + invocation.toolName());
        }

        try {
            notifyBefore(invocation);
            Object bean = applicationContext.getBean(toolDef.beanName());
            Method method = findMethod(bean.getClass(), toolDef.methodName(), toolDef.parameters());
            Object[] args = resolveArguments(invocation.arguments(), toolDef.parameters(), method);
            Object result = method.invoke(bean, args);
            String resultStr = result == null ? "" : OBJECT_MAPPER.writeValueAsString(result);
            ToolResult toolResult = ToolResult.success(invocation.toolName(), resultStr);
            notifyAfter(invocation, toolResult);
            return toolResult;
        } catch (Exception e) {
            log.error("Failed to execute tool: {}", invocation.toolName(), e);
            Throwable error = e.getCause() != null ? e.getCause() : e;
            notifyError(invocation, error);
            return ToolResult.failure(invocation.toolName(), error.getMessage());
        }
    }

    private void notifyBefore(ToolInvocation invocation) {
        for (ToolExecutionListener listener : executionListeners) {
            listener.beforeExecution(invocation);
        }
    }

    private void notifyAfter(ToolInvocation invocation, ToolResult result) {
        for (ToolExecutionListener listener : executionListeners) {
            try {
                listener.afterExecution(invocation, result);
            } catch (Exception e) {
                log.warn("Tool execution listener afterExecution failed: {}", listener.getClass().getName(), e);
            }
        }
    }

    private void notifyError(ToolInvocation invocation, Throwable error) {
        for (ToolExecutionListener listener : executionListeners) {
            try {
                listener.onError(invocation, error);
            } catch (Exception e) {
                log.warn("Tool execution listener onError failed: {}", listener.getClass().getName(), e);
            }
        }
    }

    private Method findMethod(Class<?> beanClass, String methodName, List<ParameterDefinition> paramDefs) throws NoSuchMethodException {
        if (methodName == null || methodName.isBlank()) {
            throw new NoSuchMethodException("Method name is required");
        }
        Method[] methods = beanClass.getMethods();
        for (Method m : methods) {
            if (m.getName().equals(methodName) && matchesParameters(m.getParameters(), paramDefs)) {
                return m;
            }
        }
        throw new NoSuchMethodException("Method not found: " + methodName + " with signature " + signatureOf(paramDefs));
    }

    private Object[] resolveArguments(String argumentsJson, List<ParameterDefinition> paramDefs, Method method) throws JsonProcessingException {
        if (paramDefs.isEmpty()) {
            return new Object[0];
        }

        JsonNode argsNode = OBJECT_MAPPER.readTree(argumentsJson);
        Parameter[] methodParams = method.getParameters();
        Object[] args = new Object[paramDefs.size()];
        Map<String, JsonNode> argMap = new LinkedHashMap<>();
        if (argsNode != null && argsNode.isObject()) {
            argsNode.fields().forEachRemaining(entry -> argMap.put(entry.getKey(), entry.getValue()));
        }

        for (int i = 0; i < paramDefs.size(); i++) {
            ParameterDefinition paramDef = paramDefs.get(i);
            JsonNode valueNode = argMap.get(paramDef.name());
            if (paramDef.required() && (valueNode == null || valueNode.isNull())) {
                throw new IllegalArgumentException("Missing required parameter: " + paramDef.name());
            }
            Class<?> paramType = methodParams[i].getType();
            args[i] = convertValue(valueNode, paramType);
        }

        return args;
    }

    private boolean matchesParameters(Parameter[] methodParams, List<ParameterDefinition> paramDefs) {
        if (methodParams == null) {
            return paramDefs == null || paramDefs.isEmpty();
        }
        if (paramDefs == null) {
            return methodParams.length == 0;
        }
        if (methodParams.length != paramDefs.size()) {
            return false;
        }
        for (int i = 0; i < methodParams.length; i++) {
            Parameter methodParam = methodParams[i];
            ParameterDefinition toolParam = paramDefs.get(i);
            if (toolParam == null || toolParam.name() == null || toolParam.name().isBlank()) {
                return false;
            }
            if (methodParam == null || methodParam.getName() == null || methodParam.getName().isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String signatureOf(List<ParameterDefinition> paramDefs) {
        if (paramDefs == null || paramDefs.isEmpty()) {
            return "()";
        }
        StringBuilder builder = new StringBuilder("(");
        for (int i = 0; i < paramDefs.size(); i++) {
            ParameterDefinition paramDef = paramDefs.get(i);
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(paramDef == null ? "unknown" : paramDef.type()).append(" ").append(paramDef == null ? "unknown" : paramDef.name());
        }
        builder.append(")");
        return builder.toString();
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
