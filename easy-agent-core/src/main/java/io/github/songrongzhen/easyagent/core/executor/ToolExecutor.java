package io.github.songrongzhen.easyagent.core.executor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
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
import java.util.List;

public class ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);
    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper()
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
        for (LogicalType type : List.of(LogicalType.Integer, LogicalType.Float, LogicalType.Boolean)) {
            mapper.coercionConfigFor(type).setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail);
        }
        return mapper;
    }

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

        if (!toolDef.enabled()) {
            return ToolResult.failure(invocation.toolName(), "Tool is disabled: " + invocation.toolName());
        }

        try {
            Object bean = applicationContext.getBean(toolDef.beanName());
            Method method = findMethod(bean.getClass(), toolDef.methodName(), toolDef.parameters());
            Object[] args = resolveArguments(invocation.arguments(), toolDef.parameters(), method);
            notifyBefore(invocation);
            Object result = method.invoke(bean, args);
            String resultStr = result == null ? "" : OBJECT_MAPPER.writeValueAsString(result);
            ToolResult toolResult = ToolResult.success(invocation.toolName(), resultStr);
            notifyAfter(invocation, toolResult);
            return toolResult;
        } catch (Exception e) {
            log.error("Failed to execute tool: {}", invocation.toolName(), e);
            Throwable error = e instanceof java.lang.reflect.InvocationTargetException && e.getCause() != null
                    ? e.getCause() : e;
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
        if (argumentsJson == null || argumentsJson.isBlank()) {
            throw new IllegalArgumentException("Tool arguments must be a JSON object");
        }
        JsonNode argsNode = OBJECT_MAPPER.readTree(argumentsJson);
        if (argsNode == null || !argsNode.isObject()) {
            throw new IllegalArgumentException("Tool arguments must be a JSON object");
        }
        paramDefs = paramDefs == null ? List.of() : paramDefs;
        Parameter[] methodParams = method.getParameters();
        Object[] args = new Object[paramDefs.size()];

        for (int i = 0; i < paramDefs.size(); i++) {
            ParameterDefinition paramDef = paramDefs.get(i);
            JsonNode valueNode = argsNode.get(paramDef.name());
            if (paramDef.required() && (valueNode == null || valueNode.isNull())) {
                throw new IllegalArgumentException("Missing required parameter: " + paramDef.name());
            }
            Class<?> paramType = methodParams[i].getType();
            if ((valueNode == null || valueNode.isNull()) && paramType.isPrimitive()) {
                throw new IllegalArgumentException("Parameter " + paramDef.name()
                        + " cannot be null for primitive type " + paramType.getSimpleName());
            }
            try {
                args[i] = convertValue(valueNode, paramType);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Invalid parameter " + paramDef.name()
                        + ": expected " + paramType.getSimpleName(), e);
            }
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

    private Object convertValue(JsonNode valueNode, Class<?> targetType) throws JsonProcessingException {
        if (valueNode == null || valueNode.isNull()) {
            return null;
        }
        return OBJECT_MAPPER.treeToValue(valueNode, targetType);
    }
}
