package io.github.songrongzhen.easyagent.core.registry;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.songrongzhen.easyagent.core.model.ParameterDefinition;
import io.github.songrongzhen.easyagent.core.model.RegisteredTool;
import io.github.songrongzhen.easyagent.core.model.ToolDefinition;
import io.github.songrongzhen.easyagent.core.schema.ToolSchemaGenerator;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.ResolvableType;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 在注册阶段解析确定方法、代理可调用方法及完整泛型类型。
 */
public class ToolMethodResolver {

    /**
     * 公共 Schema 生成器。
     */
    private final ToolSchemaGenerator schemas;

    /**
     * 创建 ToolMethodResolver，保存调用所需的显式依赖。
     *
     * @param schemas 公共 Schema 生成器
     */
    public ToolMethodResolver(ToolSchemaGenerator schemas) {
        this.schemas = schemas;
    }

    /**
     * 返回注册和执行共用的类型解析依赖。
     *
     * @return 完成校验的内部运行时绑定
     */
    public ObjectMapper mapper() {
        return schemas.mapper();
    }

    /**
     * 在注册阶段按显式绑定或唯一候选解析确定方法、代理调用入口和完整泛型参数。
     *
     * @param definition 工具定义
     * @param proxy      最终 Spring 代理对象
     * @param explicit   消除重载歧义的显式方法绑定
     * @param supplement 仅用于补充已有参数描述的 Schema 片段
     * @return 完成校验的内部运行时绑定
     */
    public RegisteredTool resolve(ToolDefinition definition, Object proxy, Method explicit,
        Map<String, Object> supplement) {
        Class<?> target = AopUtils.getTargetClass(proxy);
        int count = definition.parameters() == null ? 0 : definition.parameters().size();
        List<Method> candidates = Arrays.stream(target.getMethods()).filter(m -> !m.isBridge() && !m.isSynthetic()
            && m.getName().equals(definition.methodName()) && m.getParameterCount() == count).toList();
        Method method;
        if (explicit != null) {
            if (!explicit.getDeclaringClass().isAssignableFrom(target)
                || !explicit.getName().equals(definition.methodName()) || explicit.getParameterCount() != count) {
                throw new IllegalArgumentException("Invalid binding for " + definition.name());
            }
            method = BridgeMethodResolver.findBridgedMethod(ClassUtils.getMostSpecificMethod(explicit, target));
        } else {
            if (candidates.size() != 1) {
                throw new IllegalArgumentException("Ambiguous or missing method for tool " + definition.name() + ": "
                    + candidates + ". Provide ToolProvider.bindings(), e.g. Map.of(toolName,"
                    + " Service.class.getMethod(methodName, Long.class))");
            }
            method = candidates.get(0);
        }
        if (!Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers())) {
            throw new IllegalArgumentException("Tool methods must be public and non-static: " + method);
        }
        Method invocable;
        try {
            invocable = AopUtils.selectInvocableMethod(method, proxy.getClass());
        } catch (IllegalStateException e) {
            throw new IllegalArgumentException("Tool method is not exposed by proxy: " + method
                + ". Add it to the proxy interface or use class proxies", e);
        }
        ReflectionUtils.makeAccessible(invocable);
        List<RegisteredTool.ParameterBinding> bindings = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (int i = 0; i < count; i++) {
            ParameterDefinition p = definition.parameters().get(i);
            if (p == null || p.name() == null || p.name().isBlank() || !names.add(p.name())) {
                throw new IllegalArgumentException("Invalid or duplicate parameter name for " + definition.name());
            }
            ResolvableType resolved = ResolvableType.forMethodParameter(method, i, target);
            bindings
                .add(new RegisteredTool.ParameterBinding(p.name(), p.description(), p.required(), javaType(resolved)));
        }
        return new RegisteredTool(definition, proxy, method, invocable, bindings,
            schemas.generate(bindings, supplement));
    }

    private JavaType javaType(ResolvableType type) {
        var factory = mapper().getTypeFactory();
        Class<?> raw = type.resolve(Object.class);
        if (type.isArray()) {
            return factory.constructArrayType(javaType(type.getComponentType()));
        }
        ResolvableType[] args = type.getGenerics();
        if (args.length == 0) {
            return factory.constructType(raw);
        }
        return factory.constructParametricType(raw, Arrays.stream(args).map(this::javaType).toArray(JavaType[]::new));
    }
}
