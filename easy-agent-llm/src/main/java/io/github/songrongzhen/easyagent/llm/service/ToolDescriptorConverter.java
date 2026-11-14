package io.github.songrongzhen.easyagent.llm.service;

import io.github.songrongzhen.easyagent.core.model.ParameterDefinition;
import io.github.songrongzhen.easyagent.core.model.RegisteredTool;
import io.github.songrongzhen.easyagent.core.model.ToolDefinition;
import java.util.Collection;
import java.util.List;

/**
 * 将工具描述转换为模型工具契约，注册工具优先使用公共 Schema。
 */
public final class ToolDescriptorConverter {

    /**
     * 将已注册工具转换为模型描述，直接复用公共 Schema。
     *
     * @param tools 按工具名称保存的配置或运行时绑定
     * @return 按输入顺序转换的模型工具描述列表
     */
    public static List<ToolDescriptor> fromRegisteredTools(Collection<RegisteredTool> tools) {
        return tools.stream().filter(t -> t.definition().enabled()).map(t -> {
            var legacy = fromToolDefinition(t.definition());
            return new ToolDescriptor(legacy.name(), legacy.description(), legacy.parameters(), t.inputSchema());
        }).toList();
    }

    private ToolDescriptorConverter() {
    }

    /**
     * 将旧手工工具定义转换为兼容的模型工具描述。
     *
     * @param toolDefinitions 待转换的工具描述集合
     * @return 按输入顺序转换的模型工具描述列表
     */
    public static List<ToolDescriptor> fromToolDefinitions(Collection<ToolDefinition> toolDefinitions) {
        if (toolDefinitions == null || toolDefinitions.isEmpty()) {
            return List.of();
        }
        return toolDefinitions.stream().filter(ToolDefinition::enabled).map(ToolDescriptorConverter::fromToolDefinition)
            .toList();
    }

    /**
     * 将旧手工工具定义转换为兼容的模型工具描述。
     *
     * @param toolDefinition 工具描述
     * @return 转换后的单个模型工具描述
     */
    public static ToolDescriptor fromToolDefinition(ToolDefinition toolDefinition) {
        List<ToolParameter> parameters = toolDefinition.parameters() == null ? List.of()
            : toolDefinition.parameters().stream().map(ToolDescriptorConverter::fromParameterDefinition).toList();
        return new ToolDescriptor(toolDefinition.name(), toolDefinition.description(), parameters);
    }

    private static ToolParameter fromParameterDefinition(ParameterDefinition parameterDefinition) {
        return new ToolParameter(parameterDefinition.name(), mapJavaTypeToJsonType(parameterDefinition.type()),
            parameterDefinition.description(), parameterDefinition.required());
    }

    private static String mapJavaTypeToJsonType(String javaType) {
        if (javaType == null || javaType.isBlank()) {
            return "string";
        }
        return switch (javaType.toLowerCase()) {
        case "byte", "short", "int", "integer", "long" ->
            "integer";
        case "float", "double", "bigdecimal", "biginteger" ->
            "number";
        case "boolean" ->
            "boolean";
        case "list", "arraylist", "linkedlist", "set", "hashset", "array" ->
            "array";
        case "map", "hashmap", "linkedhashmap" ->
            "object";
        case "object" ->
            "any";
        default ->
            "string";
        };
    }
}
