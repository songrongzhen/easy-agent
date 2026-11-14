package io.github.songrongzhen.easyagent.core.model;

import com.fasterxml.jackson.databind.JavaType;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * Internal runtime binding. Never serialize this object to a discovery response.
 */
public record RegisteredTool(ToolDefinition definition, Object proxy, Method targetMethod, Method invocableMethod,
    List<ParameterBinding> parameters, Map<String, Object> inputSchema) {

    public RegisteredTool {
        parameters = List.copyOf(parameters);
        inputSchema = ToolCallContext.snapshot(inputSchema);
    }

    /**
     * 单个参数的公开描述与解析后的实际 Java 类型。
     *
     * @param name        工具名称
     * @param description 工具的可读用途说明
     * @param required    是否要求存在宿主授权策略
     * @param javaType    解析后的完整 Java 参数类型
     */
    public record ParameterBinding(String name, String description, boolean required, JavaType javaType) {
    }
}
