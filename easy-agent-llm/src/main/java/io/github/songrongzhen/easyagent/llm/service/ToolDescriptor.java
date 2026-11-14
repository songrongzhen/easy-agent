package io.github.songrongzhen.easyagent.llm.service;

import io.github.songrongzhen.easyagent.core.model.ToolCallContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 面向模型的工具描述，兼容旧参数列表并保存完整输入 Schema。
 *
 * @param name        工具名称
 * @param description 工具的可读用途说明
 * @param parameters  工具参数描述或绑定列表
 * @param inputSchema 完整输入 Schema
 */
public record ToolDescriptor(String name, String description, List<ToolParameter> parameters,
    Map<String, Object> inputSchema) {

    public ToolDescriptor {
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
        inputSchema = ToolCallContext.snapshot(inputSchema);
    }

    /**
     * 将旧参数列表转换为完整输入 Schema。
     *
     * @param name        工具名称
     * @param description 工具的可读用途说明
     * @param parameters  工具参数描述或绑定列表
     */
    public ToolDescriptor(String name, String description, List<ToolParameter> parameters) {
        this(name, description, parameters, legacySchema(parameters));
    }

    private static Map<String, Object> legacySchema(List<ToolParameter> parameters) {
        Map<String, Object> props = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (var p : parameters == null ? List.<ToolParameter>of() : parameters) {
            Map<String, Object> prop = new LinkedHashMap<>();
            String type = p.type() == null ? "object" : p.type();
            if (!type.equals("any")) {
                prop.put("type", type);
            }
            if (type.equals("array")) {
                prop.put("items", Map.of());
            }
            if (p.description() != null) {
                prop.put("description", p.description());
            }
            props.put(p.name(), prop);
            if (p.required()) {
                required.add(p.name());
            }
        }
        return Map.of("type", "object", "properties", props, "required", required);
    }
}
