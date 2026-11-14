package io.github.songrongzhen.easyagent.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.module.SimpleModule;

import io.github.songrongzhen.easyagent.core.annotation.EasyTool;
import io.github.songrongzhen.easyagent.core.annotation.ToolParam;
import io.github.songrongzhen.easyagent.core.executor.ToolExecutor;
import io.github.songrongzhen.easyagent.core.model.ParameterDefinition;
import io.github.songrongzhen.easyagent.core.model.ToolDefinition;
import io.github.songrongzhen.easyagent.core.model.ToolInvocation;
import io.github.songrongzhen.easyagent.core.model.ToolResult;
import io.github.songrongzhen.easyagent.core.processor.EasyToolBeanPostProcessor;
import io.github.songrongzhen.easyagent.core.processor.ToolProviderRegistrar;
import io.github.songrongzhen.easyagent.core.registry.ToolMethodResolver;
import io.github.songrongzhen.easyagent.core.registry.ToolRegistry;
import io.github.songrongzhen.easyagent.core.schema.ToolSchemaGenerator;
import io.github.songrongzhen.easyagent.core.spi.ToolProvider;

import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.support.GenericApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/** 工具执行契约的测试夹具与回归验证。 */
class ToolRuntimeTest {

    /**
     * 工具执行契约的测试夹具与回归验证。
     *
     * @param name 工具名称
     */
    public record User(@JsonProperty("user_name") @JsonAlias("name") String name) {
    }

    /**
     * 工具执行契约的测试夹具与回归验证。
     *
     * @param value 待处理值
     * @param next  next
     */
    public record Node(String value, Node next) {
    }

    /**
     * 工具执行契约的测试夹具与回归验证。
     *
     * @param values values
     */
    public record Page<T>(List<T> values) {
    }

    /** 一次逻辑调用的内部状态，用于统一收尾和观测。 */
    public enum State {

        /** ON 类型。 */
        ON;

        @JsonValue
        public String value() {
            return "on";
        }
    }

    /** 工具执行契约的测试夹具与回归验证。 */
    public static class Service {

        public String users(List<User> values) {
            return values.get(0).name();
        }

        public String nested(Map<String, List<User>> values) {
            return values.get("x").get(0).name();
        }

        public String array(User[] values) {
            return values[0].name();
        }

        public String object(Object value) {
            return value.getClass().getSimpleName();
        }

        public String optional(Optional<User> value) {
            return value.map(User::name).orElse("empty");
        }

        public int primitive(int value) {
            return value;
        }

        public String overloaded(Long value) {
            return "long";
        }

        public String overloaded(String value) {
            return "string";
        }

        public Node recursive(Node node) {
            return node;
        }

        public void pages(Page<User> users, Page<Node> nodes) {
        }

        public void enumValue(State value) {
        }

        public void invalid(Map<Integer, User> values) {
        }

        public static void staticTool() {
        }

        @EasyTool(name = "annotated", description = "generic")
        public String annotated(@ToolParam(name = "values") List<User> values) {
            return users(values);
        }
    }

    /** 工具执行契约的测试夹具与回归验证。 */
    public static class Base<T> {

        public String generic(List<T> values) {
            return values.get(0).getClass().getSimpleName();
        }
    }

    /** 工具执行契约的测试夹具与回归验证。 */
    public static class Inherited extends Base<User> {
    }

    /** 工具执行契约的测试夹具与回归验证。 */
    public interface Api {

        String users(List<User> values);
    }

    /** 工具执行契约的测试夹具与回归验证。 */
    public static class ApiService extends Service implements Api {
    }

    private GenericApplicationContext context;

    private ToolRegistry registry;

    private ToolExecutor executor;

    @BeforeEach
    void setup() {
        context = new GenericApplicationContext();
        context.refresh();
        registry = new ToolRegistry();
        executor = new ToolExecutor(registry, context);
    }

    @AfterEach
    void close() {
        context.close();
    }

    private ToolDefinition definition(String name, String method, boolean required, int count) {
        List<ParameterDefinition> parameters = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            parameters.add(
                new ParameterDefinition(
                    i == 0 ? "value" : "other",
                    "description",
                    "wrongLegacyType",
                    required));
        }
        return new ToolDefinition(
            name,
            "description",
            "category",
            "source",
            0,
            "service",
            method,
            parameters,
            "1",
            true);
    }

    private void register(String method, Class<?> type) throws Exception {
        registry.register(
            definition(method, method, true, 1),
            new Service(),
            Service.class.getMethod(method, type),
            null,
            null);
    }

    private ToolResult execute(String method, String json) {
        return executor.execute(new ToolInvocation(method, json));
    }

    @Test
    void bindsCompleteGenericTypes() throws Exception {
        register("users", List.class);
        register("nested", Map.class);
        register("array", User[].class);
        assertEquals("\"Ada\"", execute("users", "{\"value\":[{\"user_name\":\"Ada\"}]}").result());
        assertEquals(
            "\"Ada\"", execute("nested", "{\"value\":{\"x\":[{\"name\":\"Ada\"}]}}").result());
        assertEquals("\"Ada\"", execute("array", "{\"value\":[{\"name\":\"Ada\"}]}").result());
    }

    @Test
    void resolvesInheritedGeneric() throws Exception {
        registry.register(
            definition("inherited", "generic", true, 1),
            new Inherited(),
            Base.class.getMethod("generic", List.class),
            null,
            null);
        assertEquals("\"User\"", execute("inherited", "{\"value\":[{\"name\":\"Ada\"}]}").result());
    }

    @Test
    void validatesStrictJsonAndNulls() throws Exception {
        register("primitive", int.class);
        for (String json : List.of(
            "{}",
            "{\"value\":null}",
            "{\"value\":1.2}",
            "{\"value\":1,\"value\":2}",
            "{\"value\":1} {}",
            "[]")) {
            assertEquals(
                "TOOL_ARGUMENT_INVALID", execute("primitive", json).errorDetail().code(), json);
        }
        registry.register(
            definition("optionalPrimitive", "primitive", false, 1),
            new Service(),
            Service.class.getMethod("primitive", int.class),
            null,
            null);
        assertFalse(execute("optionalPrimitive", "{}").success());
    }

    @Test
    void supportsOptionalAndAnyJson() throws Exception {
        registry.register(
            definition("optional", "optional", false, 1), new Service(), null, null, null);
        assertEquals("\"empty\"", execute("optional", "{}").result());
        register("object", Object.class);
        assertEquals("\"ArrayList\"", execute("object", "{\"value\":[1]}").result());
        assertEquals("\"String\"", execute("object", "{\"value\":\"hello\"}").result());
    }

    @Test
    void requiredOptionalRejectsNullInBothSchemaAndBinding() throws Exception {
        register("optional", Optional.class);
        JsonNode schema = ToolExecutor.createObjectMapper()
            .valueToTree(registry.getRegisteredTool("optional").inputSchema());
        assertTrue(schema.at("/properties/value/$ref").isTextual());
        assertFalse(schema.at("/properties/value").has("anyOf"));
        assertEquals(
            "TOOL_ARGUMENT_INVALID",
            execute("optional", "{\"value\":null}").errorDetail().code());
        assertEquals("\"Ada\"", execute("optional", "{\"value\":{\"name\":\"Ada\"}}").result());
    }

    @Test
    void spiAmbiguityFailsAndExplicitBindingWins() throws Exception {
        assertThrows(
            IllegalArgumentException.class,
            () -> registry.register(
                definition("overloaded", "overloaded", true, 1),
                new Service(),
                null,
                null,
                null));
        registry.register(
            definition("overloaded", "overloaded", true, 1),
            new Service(),
            Service.class.getMethod("overloaded", Long.class),
            null,
            null);
        assertEquals("\"long\"", execute("overloaded", "{\"value\":7}").result());
    }

    @Test
    void registryConflictsReplacementAndDeletionAreAtomic() throws Exception {
        context.getBeanFactory().registerSingleton("service", new Service());
        var first = definition("tool", "users", true, 1);
        registry.register(first);
        var runtime = registry.getRegisteredTool("tool");
        registry.registerIfAbsent(definition("tool", "missing", true, 1));
        assertSame(runtime, registry.getRegisteredTool("tool"));
        assertThrows(IllegalStateException.class, () -> registry.register(first));
        assertThrows(
            IllegalArgumentException.class,
            () -> registry.registerOrReplace(definition("tool", "missing", true, 1)));
        assertSame(runtime, registry.getRegisteredTool("tool"));
        registry.registerOrReplace(definition("tool", "array", true, 1));
        assertEquals(
            User[].class,
            registry.getRegisteredTool("tool").parameters().get(0).javaType().getRawClass());
        registry.unregister("tool");
        assertNull(registry.getRegisteredTool("tool"));
        assertNull(registry.getTool("tool"));
    }

    @Test
    void invokesJdkAndClassProxyAdvice() throws Exception {
        for (boolean classProxy : List.of(false, true)) {
            AtomicInteger advice = new AtomicInteger();
            ProxyFactory factory = new ProxyFactory(new ApiService());
            factory.setProxyTargetClass(classProxy);
            factory.addAdvice(
                (MethodInterceptor) invocation -> {
                    advice.incrementAndGet();
                    return invocation.proceed();
                });
            registry.register(
                definition("proxy" + classProxy, "users", true, 1),
                factory.getProxy(),
                null,
                null,
                null);
            assertTrue(execute("proxy" + classProxy, "{\"value\":[{\"name\":\"Ada\"}]}").success());
            assertEquals(1, advice.get());
        }
    }

    @Test
    void rejectsMethodsMissingFromJdkInterfaceAndStaticMethods() {
        Object proxy = new ProxyFactory(new ApiService()).getProxy();
        assertThrows(
            IllegalArgumentException.class,
            () -> registry.register(
                definition("hidden", "object", true, 1), proxy, null, null, null));
        assertThrows(
            IllegalArgumentException.class,
            () -> registry.register(
                definition("static", "staticTool", true, 0),
                new Service(),
                null,
                null,
                null));
    }

    @Test
    void annotationBindingWaitsForFinalProxy() {
        var processor = new EasyToolBeanPostProcessor(registry, context);
        processor.postProcessAfterInitialization(new Service(), "service");
        AtomicInteger advice = new AtomicInteger();
        ProxyFactory factory = new ProxyFactory(new Service());
        factory.setProxyTargetClass(true);
        factory.addAdvice(
            (MethodInterceptor) invocation -> {
                advice.incrementAndGet();
                return invocation.proceed();
            });
        context.getBeanFactory().registerSingleton("service", factory.getProxy());
        processor.afterSingletonsInstantiated();
        assertTrue(execute("annotated", "{\"values\":[{\"name\":\"Ada\"}]}").success());
        assertEquals(1, advice.get());
    }

    @Test
    void schemaIsCompleteRecursiveAndImmutable() throws Exception {
        register("users", List.class);
        register("recursive", Node.class);
        register("enumValue", State.class);
        register("object", Object.class);
        JsonNode schema = ToolExecutor.createObjectMapper()
            .valueToTree(registry.getRegisteredTool("users").inputSchema());
        assertEquals("array", schema.at("/properties/value/type").asText());
        assertTrue(schema.at("/properties/value/items/$ref").asText().startsWith("#/$defs/"));
        assertTrue(schema.get("$defs").elements().next().at("/properties/user_name").isObject());
        JsonNode recursive = ToolExecutor.createObjectMapper()
            .valueToTree(registry.getRegisteredTool("recursive").inputSchema());
        assertEquals(1, recursive.get("$defs").size());
        JsonNode enums = ToolExecutor.createObjectMapper()
            .valueToTree(registry.getRegisteredTool("enumValue").inputSchema());
        assertEquals("on", enums.at("/properties/value/enum/0").asText());
        assertEquals(
            Map.of("description", "description"),
            ((Map<?, ?>) registry.getRegisteredTool("object").inputSchema().get("properties"))
                .get("value"));
        assertThrows(
            UnsupportedOperationException.class,
            () -> registry.getRegisteredTool("users").inputSchema().clear());
        registry.register(definition("pages", "pages", true, 2), new Service(), null, null, null);
        assertEquals(
            4,
            ((Map<?, ?>) registry.getRegisteredTool("pages").inputSchema().get("$defs"))
                .size());
    }

    @Test
    void rejectsUnsupportedSchemaAndInvalidSupplements() {
        assertThrows(
            IllegalArgumentException.class,
            () -> registry.register(
                definition("invalid", "invalid", true, 1),
                new Service(),
                null,
                null,
                null));
        assertThrows(
            IllegalArgumentException.class,
            () -> registry.register(
                definition("bad", "users", true, 1),
                new Service(),
                null,
                Map.of("properties", Map.of("value", Map.of("type", "string"))),
                null));
        registry.register(
            definition("ok", "users", true, 1),
            new Service(),
            null,
            Map.of("properties", Map.of("value", Map.of("description", "owners"))),
            null);
        assertTrue(registry.getRegisteredTool("ok").inputSchema().toString().contains("owners"));
    }

    /** 工具执行契约的测试夹具与回归验证。 */
    public static class BridgeBase<T> {

        @EasyTool(name = "bridge", description = "generic bridge")
        public String convert(@ToolParam(name = "value") T value) {
            return value.toString();
        }
    }

    /** 工具执行契约的测试夹具与回归验证。 */
    public static class BridgeChild extends BridgeBase<User> {

        @Override
        @EasyTool(name = "bridge", description = "generic bridge")
        public String convert(@ToolParam(name = "value") User value) {
            return value.name();
        }
    }

    @Test
    void bridgeMethodsDoNotRegisterDuplicates() {
        var processor = new EasyToolBeanPostProcessor(registry, context);
        var bean = new BridgeChild();
        context.getBeanFactory().registerSingleton("bridgeService", bean);
        processor.postProcessAfterInitialization(bean, "bridgeService");
        processor.afterSingletonsInstantiated();
        assertEquals(1, registry.size());
        assertEquals("\"Ada\"", execute("bridge", "{\"value\":{\"name\":\"Ada\"}}").result());
    }

    /**
     * 工具执行契约的测试夹具与回归验证。
     *
     * @param value   待处理值
     * @param ignored ignored
     */
    public record Visible(
        @JsonProperty(value = "renamed", required = true) String value,
        @JsonIgnore String ignored) {
    }

    /** 工具执行契约的测试夹具与回归验证。 */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
    public static class Polymorphic {

        public String value;
    }

    /** 工具执行契约的测试夹具与回归验证。 */
    public static class Special {

        public String value;
    }

    /** 工具执行契约的测试夹具与回归验证。 */
    public static class SchemaService {

        public void visible(Visible value) {
        }

        public void polymorphic(Polymorphic value) {
        }

        public void special(Special value) {
        }
    }

    @Test
    void respectsIgnoredAndExplicitRequiredDtoMetadata() {
        registry.register(
            definition("visible", "visible", true, 1), new SchemaService(), null, null, null);
        JsonNode schema = ToolExecutor.createObjectMapper()
            .valueToTree(registry.getRegisteredTool("visible").inputSchema());
        JsonNode dto = schema.path("$defs").elements().next();
        assertTrue(dto.path("properties").has("renamed"));
        assertFalse(dto.path("properties").has("ignored"));
        assertEquals("renamed", dto.at("/required/0").asText());
    }

    @Test
    void rejectsPolymorphismAndModuleCustomDeserializers() {
        assertThrows(
            IllegalArgumentException.class,
            () -> registry.register(
                definition("poly", "polymorphic", true, 1),
                new SchemaService(),
                null,
                null,
                null));
        var mapper = ToolExecutor.createObjectMapper();
        var module = new SimpleModule();
        module.addDeserializer(
            Special.class,
            new JsonDeserializer<Special>() {

                @Override
                public Special deserialize(JsonParser parser, DeserializationContext context) {
                    return new Special();
                }
            });
        mapper.registerModule(module);
        var custom = new ToolRegistry(new ToolMethodResolver(new ToolSchemaGenerator(mapper)), context);
        assertThrows(
            IllegalArgumentException.class,
            () -> custom.register(
                definition("special", "special", true, 1),
                new SchemaService(),
                null,
                null,
                null));
    }

    @Test
    void declarationCollectionsAreDefensiveSnapshots() {
        List<ParameterDefinition> params = new ArrayList<>(List.of(new ParameterDefinition("value", "", "List", true)));
        var definition = new ToolDefinition("users", "desc", "cat", "service", "users", params, true);
        params.clear();
        assertEquals(1, definition.parameters().size());
        assertThrows(UnsupportedOperationException.class, () -> definition.parameters().clear());
    }

    @Test
    void providerRegistrationErrorsAreNotSwallowed() {
        context.getBeanFactory().registerSingleton("service", new Service());
        ToolProvider provider = () -> List.of(definition("ambiguous", "overloaded", true, 1));
        assertThrows(
            IllegalArgumentException.class,
            () -> new ToolProviderRegistrar(registry, List.of(provider)).registerProviders());
    }
}
