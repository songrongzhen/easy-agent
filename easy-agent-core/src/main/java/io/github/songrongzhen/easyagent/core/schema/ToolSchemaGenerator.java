package io.github.songrongzhen.easyagent.core.schema;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.deser.DefaultDeserializationContext;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import io.github.songrongzhen.easyagent.core.model.RegisteredTool;
import io.github.songrongzhen.easyagent.core.model.ToolCallContext;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Generates the contract from the same mapper and JavaTypes used for binding.
 */
public class ToolSchemaGenerator {

    /**
     * 工具专用 ObjectMapper。
     */
    private final ObjectMapper mapper;

    /**
     * 创建 ToolSchemaGenerator，保存调用所需的显式依赖。
     *
     * @param mapper 工具专用 ObjectMapper
     */
    public ToolSchemaGenerator(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    /**
     * 返回注册和执行共用的类型解析依赖。
     *
     * @return Schema 与参数绑定共用的工具 ObjectMapper
     */
    public ObjectMapper mapper() {
        return mapper;
    }

    /**
     * 根据解析后的 Java 类型生成公共工具输入 Schema。
     *
     * @param parameters 工具参数描述或绑定列表
     * @param supplement 仅用于补充已有参数描述的 Schema 片段
     * @return 深度不可变的完整工具输入 Schema
     */
    public Map<String, Object> generate(List<RegisteredTool.ParameterBinding> parameters,
        Map<String, Object> supplement) {
        Map<String, Object> defs = new LinkedHashMap<>();
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (var p : parameters) {
            // Required applies to the JSON value, including an Optional parameter's outer null.
            JavaType schemaType = p.required() && p.javaType().getRawClass() == Optional.class
                ? p.javaType().containedTypeOrUnknown(0)
                : p.javaType();
            Map<String, Object> schema = schema(schemaType, defs);
            if (!p.required() && !p.javaType().isPrimitive()) {
                schema = nullable(schema);
            }
            schema = new LinkedHashMap<>(schema);
            if (p.description() != null && !p.description().isBlank()) {
                schema.put("description", p.description());
            }
            properties.put(p.name(), schema);
            if (p.required() || p.javaType().isPrimitive()) {
                required.add(p.name());
            }
        }
        if (supplement != null && !supplement.isEmpty()) {
            if (!supplement.keySet().equals(Set.of("properties"))
                || !(supplement.get("properties") instanceof Map<?, ?> overrides)) {
                throw unsupported("inputSchemas only permits properties.<parameter>.description");
            }
            for (var entry : overrides.entrySet()) {
                if (!properties.containsKey(entry.getKey()) || !(entry.getValue() instanceof Map<?, ?> fields)
                    || !fields.keySet().equals(Set.of("description"))
                    || !(fields.get("description") instanceof String)) {
                    throw unsupported("inputSchemas only permits descriptions of existing parameters");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> property = (Map<String, Object>) properties.get(entry.getKey());
                property.put("description", fields.get("description"));
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "object");
        result.put("properties", properties);
        result.put("required", required);
        if (!defs.isEmpty()) {
            result.put("$defs", defs);
        }
        return ToolCallContext.snapshot(result);
    }

    private Map<String, Object> schema(JavaType type, Map<String, Object> defs) {
        Class<?> raw = type.getRawClass();
        BeanDescription bean = mapper.getDeserializationConfig().introspect(type);
        validateDeserializer(type, bean);
        if (raw == Object.class || JsonNode.class.isAssignableFrom(raw)) {
            return new LinkedHashMap<>();
        }
        if (raw == Optional.class) {
            return nullable(schema(type.containedTypeOrUnknown(0), defs));
        }
        if (type.isEnumType()) {
            List<Object> values = new ArrayList<>();
            for (Object value : raw.getEnumConstants()) {
                values.add(mapper.convertValue(value, Object.class));
            }
            return new LinkedHashMap<>(Map.of("enum", values));
        }
        Map<String, Object> scalar = scalarSchema(raw);
        if (scalar != null) {
            return scalar;
        }
        if (type.isArrayType() || type.isCollectionLikeType()) {
            return new LinkedHashMap<>(Map.of("type", "array", "items", schema(type.getContentType(), defs)));
        }
        if (type.isMapLikeType()) {
            if (type.getKeyType().getRawClass() != String.class) {
                throw unsupported("Map keys must be String: " + type);
            }
            return new LinkedHashMap<>(
                Map.of("type", "object", "additionalProperties", schema(type.getContentType(), defs)));
        }
        return objectSchema(type, bean, defs);
    }

    /**
     * 返回标量类型的 Schema；非标量返回 null 并继续按容器或 DTO 处理。
     */
    private Map<String, Object> scalarSchema(Class<?> raw) {
        if (raw == boolean.class || raw == Boolean.class) {
            return new LinkedHashMap<>(Map.of("type", "boolean"));
        }
        if (raw == String.class || raw == char.class || raw == Character.class || raw == UUID.class
            || raw.getName().startsWith("java.time.") || Date.class.isAssignableFrom(raw)) {
            return new LinkedHashMap<>(Map.of("type", "string"));
        }
        if (raw.isPrimitive() || Number.class.isAssignableFrom(raw)) {
            String kind = raw == float.class || raw == double.class || raw == Float.class || raw == Double.class
                || raw == BigDecimal.class ? "number" : "integer";
            return new LinkedHashMap<>(Map.of("type", kind));
        }
        return null;
    }

    /**
     * 按实际 Jackson 反序列化配置拒绝首版无法准确描述的自定义和多态类型。
     */
    private void validateDeserializer(JavaType type, BeanDescription bean) {
        var annotations = mapper.getDeserializationConfig().getAnnotationIntrospector();
        if (annotations.findTypeResolver(mapper.getDeserializationConfig(), bean.getClassInfo(), type) != null
            || annotations.findDeserializer(bean.getClassInfo()) != null) {
            throw unsupported("Polymorphic/custom deserialization: " + type);
        }
        try {
            DefaultDeserializationContext ctx = ((DefaultDeserializationContext) mapper.getDeserializationContext())
                .createInstance(mapper.getDeserializationConfig(), null, mapper.getInjectableValues());
            JsonDeserializer<?> deserializer = ctx.findRootValueDeserializer(type);
            if (deserializer != null
                && !deserializer.getClass().getName().startsWith("com.fasterxml.jackson.databind.deser.")
                && !deserializer.getClass().getName().startsWith("com.fasterxml.jackson.datatype.jdk8.")
                && !deserializer.getClass().getName().startsWith("com.fasterxml.jackson.datatype.jsr310.deser.")) {
                throw unsupported("Custom deserialization: " + type);
            }
        } catch (JsonMappingException e) {
            throw unsupported("Cannot inspect " + type + ": " + e.getOriginalMessage());
        }
    }

    /**
     * 使用完整泛型类型作为定义键，自引用只返回已建立的根级引用。
     */
    private Map<String, Object> objectSchema(JavaType type, BeanDescription bean, Map<String, Object> defs) {
        String key = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(type.toCanonical().getBytes(StandardCharsets.UTF_8));
        Map<String, Object> ref = new LinkedHashMap<>(Map.of("$ref", "#/$defs/" + key));
        if (defs.containsKey(key)) {
            return ref;
        }
        Map<String, Object> object = new LinkedHashMap<>();
        defs.put(key, object);
        Map<String, Object> props = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        List<Object> aliasRequirements = new ArrayList<>();
        for (BeanPropertyDefinition property : bean.findProperties()) {
            addProperty(property, bean, defs, props, required, aliasRequirements);
        }
        object.put("type", "object");
        object.put("properties", props);
        if (!required.isEmpty()) {
            object.put("required", required);
        }
        if (!aliasRequirements.isEmpty()) {
            object.put("allOf", aliasRequirements);
        }
        return ref;
    }

    /**
     * 描述实际可反序列化的属性，并为必填属性的别名生成等价输入条件。
     */
    private void addProperty(BeanPropertyDefinition propertyDefinition, BeanDescription bean, Map<String, Object> defs,
        Map<String, Object> props, List<String> required, List<Object> aliasRequirements) {
        var annotations = mapper.getDeserializationConfig().getAnnotationIntrospector();
        if (!propertyDefinition.couldDeserialize()
            || bean.getIgnoredPropertyNames().contains(propertyDefinition.getName())) {
            return;
        }
        if (propertyDefinition.getPrimaryMember() != null
            && (annotations.findDeserializer(propertyDefinition.getPrimaryMember()) != null
                || annotations.findPropertyTypeResolver(mapper.getDeserializationConfig(),
                    propertyDefinition.getPrimaryMember(), propertyDefinition.getPrimaryType()) != null
                || annotations.findContentDeserializer(propertyDefinition.getPrimaryMember()) != null)) {
            throw unsupported("Custom property deserialization: " + propertyDefinition.getName());
        }
        Map<String, Object> property = schema(propertyDefinition.getPrimaryType(), defs);
        if (!propertyDefinition.getPrimaryType().isPrimitive() && !propertyDefinition.isRequired()) {
            property = nullable(property);
        }
        props.put(propertyDefinition.getName(), property);
        // Jackson aliases are accepted inputs, and share the canonical property's contract.
        List<PropertyName> aliases = propertyDefinition.getPrimaryMember() == null ? List.of()
            : annotations.findPropertyAliases(propertyDefinition.getPrimaryMember());
        if (aliases != null) {
            for (var alias : aliases) {
                props.put(alias.getSimpleName(), property);
            }
        }
        if (propertyDefinition.isRequired()) {
            if (aliases == null || aliases.isEmpty()) {
                required.add(propertyDefinition.getName());
            } else {
                List<Object> alternatives = new ArrayList<>();
                alternatives.add(Map.of("required", List.of(propertyDefinition.getName())));
                for (var alias : aliases) {
                    alternatives.add(Map.of("required", List.of(alias.getSimpleName())));
                }
                aliasRequirements.add(Map.of("anyOf", alternatives));
            }
        }
    }

    private Map<String, Object> nullable(Map<String, Object> schema) {
        if (schema.isEmpty()) {
            return schema;
        }
        return new LinkedHashMap<>(Map.of("anyOf", List.of(schema, Map.of("type", "null"))));
    }

    private IllegalArgumentException unsupported(String detail) {
        return new IllegalArgumentException("Unsupported tool schema: " + detail);
    }
}
