package io.github.songrongzhen.easyagent.llm.service;

import io.github.songrongzhen.easyagent.core.model.ParameterDefinition;
import io.github.songrongzhen.easyagent.core.model.ToolDefinition;

import java.util.Collection;
import java.util.List;

public final class ToolDescriptorConverter {

    private ToolDescriptorConverter() {
    }

    public static List<ToolDescriptor> fromToolDefinitions(Collection<ToolDefinition> toolDefinitions) {
        if (toolDefinitions == null || toolDefinitions.isEmpty()) {
            return List.of();
        }

        return toolDefinitions.stream()
                .filter(ToolDefinition::enabled)
                .map(ToolDescriptorConverter::fromToolDefinition)
                .toList();
    }

    public static ToolDescriptor fromToolDefinition(ToolDefinition toolDefinition) {
        List<ToolParameter> parameters = toolDefinition.parameters() == null
                ? List.of()
                : toolDefinition.parameters().stream()
                        .map(ToolDescriptorConverter::fromParameterDefinition)
                        .toList();

        return new ToolDescriptor(
                toolDefinition.name(),
                toolDefinition.description(),
                parameters
        );
    }

    private static ToolParameter fromParameterDefinition(ParameterDefinition parameterDefinition) {
        return new ToolParameter(
                parameterDefinition.name(),
                mapJavaTypeToJsonType(parameterDefinition.type()),
                parameterDefinition.description(),
                parameterDefinition.required()
        );
    }

    private static String mapJavaTypeToJsonType(String javaType) {
        if (javaType == null || javaType.isBlank()) {
            return "string";
        }

        return switch (javaType.toLowerCase()) {
            case "byte", "short", "int", "integer", "long" -> "integer";
            case "float", "double", "bigdecimal", "biginteger" -> "number";
            case "boolean" -> "boolean";
            case "list", "arraylist", "linkedlist", "set", "hashset", "array" -> "array";
            case "map", "hashmap", "linkedhashmap" -> "object";
            default -> "string";
        };
    }
}
