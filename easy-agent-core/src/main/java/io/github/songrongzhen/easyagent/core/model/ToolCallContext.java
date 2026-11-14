package io.github.songrongzhen.easyagent.core.model;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 工具调用的可信上下文，身份由宿主入口提供，不从工具参数推断。
 *
 * @param invocationId 一次逻辑调用的标识，重试期间保持不变
 * @param source       调用入口
 * @param principalId  宿主已验证的身份标识，可为空
 * @param tenantId     宿主已验证的租户标识，可为空
 * @param attributes   仅包含必要 JSON 值的扩展属性，构造时生成深度只读快照
 */
public record ToolCallContext(String invocationId, String source, String principalId, String tenantId,
    Map<String, Object> attributes) {

    /**
     * MCP 协议入口。
     */
    public static final String SOURCE_MCP = "MCP";

    /**
     * Agent 编排入口。
     */
    public static final String SOURCE_AGENT = "AGENT";

    /**
     * 直接 Java 调用入口。
     */
    public static final String SOURCE_JAVA = "JAVA";

    /**
     * 父逻辑调用标识在扩展属性中的键。
     */
    public static final String PARENT_INVOCATION_ID = "parentInvocationId";

    public ToolCallContext {
        if (invocationId == null || invocationId.isBlank()) {
            invocationId = UUID.randomUUID().toString();
        }
        attributes = snapshot(attributes == null ? Map.of() : attributes);
    }

    /**
     * 为入口创建匿名上下文，不解析或推断调用身份。
     *
     * @param source 调用入口
     * @return 具有新调用标识的匿名上下文
     */
    public static ToolCallContext anonymous(String source) {
        return new ToolCallContext(null, source, null, null, Map.of());
    }

    /**
     * 为独立工具调用创建子上下文，保留可信身份并关联父调用。
     *
     * @param source 子调用入口
     * @return 具有新调用标识的子上下文
     */
    public ToolCallContext child(String source) {
        Map<String, Object> values = new LinkedHashMap<>(attributes);
        values.put(PARENT_INVOCATION_ID, invocationId);
        return new ToolCallContext(null, source, principalId, tenantId, values);
    }

    /**
     * 调整入口标识，保留当前逻辑调用标识和可信身份。
     *
     * @param source 实际调用入口
     * @return 更新入口后的上下文
     */
    public ToolCallContext withSource(String source) {
        return new ToolCallContext(invocationId, source, principalId, tenantId, attributes);
    }

    /**
     * 生成字符串键 Map 的深度只读快照，嵌套集合统一表示为 JSON 数组 List。
     *
     * @param values 待复制的 JSON 对象，可为 null
     * @return 只读 Map；输入为 null 时返回 null，不承诺保留具体 Map 实现类型
     * @throws IllegalArgumentException 包含非字符串键或非 JSON 值时抛出
     */
    public static Map<String, Object> snapshot(Map<String, ?> values) {
        return values == null ? null : snapshotMap(values);
    }

    /**
     * 生成 JSON 值的深度只读快照，不承诺保留输入的具体集合类型。
     *
     * @param value JSON 标量、Map 或 Collection，可为空
     * @return Map 转为只读 Map，Collection 转为只读 List，标量和 null 保持原值
     * @throws IllegalArgumentException 包含非字符串键或非 JSON 值时抛出
     */
    public static Object snapshotValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return snapshotMap(map);
        }
        if (value instanceof Collection<?> collection) {
            List<Object> copy = new ArrayList<>(collection.size());
            for (Object element : collection) {
                copy.add(snapshotValue(element));
            }
            return Collections.unmodifiableList(copy);
        }
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof Byte
            || value instanceof Short || value instanceof Integer || value instanceof Long || value instanceof Float
            || value instanceof Double || value instanceof BigInteger || value instanceof BigDecimal) {
            return value;
        }
        throw new IllegalArgumentException("Context and schema values must be JSON values, not active objects");
    }

    private static Map<String, Object> snapshotMap(Map<?, ?> values) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("Only string keys are supported");
            }
            copy.put(key, snapshotValue(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }
}
