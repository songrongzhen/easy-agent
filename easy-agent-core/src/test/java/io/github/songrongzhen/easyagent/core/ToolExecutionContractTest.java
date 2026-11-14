package io.github.songrongzhen.easyagent.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.github.songrongzhen.easyagent.core.config.ToolExecutionOptions;
import io.github.songrongzhen.easyagent.core.executor.ToolExecutor;
import io.github.songrongzhen.easyagent.core.model.ParameterDefinition;
import io.github.songrongzhen.easyagent.core.model.ToolCallContext;
import io.github.songrongzhen.easyagent.core.model.ToolDefinition;
import io.github.songrongzhen.easyagent.core.model.ToolError;
import io.github.songrongzhen.easyagent.core.model.ToolInvocation;
import io.github.songrongzhen.easyagent.core.model.ToolResult;
import io.github.songrongzhen.easyagent.core.registry.ToolRegistry;
import io.github.songrongzhen.easyagent.core.spi.ToolAuthorizationPolicy;
import io.github.songrongzhen.easyagent.core.spi.ToolExceptionMapper;
import io.github.songrongzhen.easyagent.core.spi.ToolExecutionListener;
import io.github.songrongzhen.easyagent.core.spi.ToolObservation;
import io.github.songrongzhen.easyagent.core.spi.ToolRateLimiter;
import io.github.songrongzhen.easyagent.core.spi.ToolRetryPolicy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** 工具执行契约的测试夹具与回归验证。 */
class ToolExecutionContractTest {

    /** 工具执行契约的测试夹具与回归验证。 */
    public static class Service {

        int calls;

        public String success(List<String> value) {
            calls++;
            return "ok";
        }

        public String transientFailure(List<String> value) {
            calls++;
            assertEquals(List.of("original"), value);
            value.clear();
            if (calls < 3) {
                throw new Temporary();
            }
            return "ok";
        }

        public String secret(List<String> value) {
            calls++;
            throw new IllegalArgumentException("password=secret SQL SELECT timeout 503");
        }

        public Object serialization(List<String> value) {
            calls++;
            return new Broken();
        }
    }

    /** 工具执行契约的测试夹具与回归验证。 */
    public static class Temporary extends RuntimeException {
    }

    /** 工具执行契约的测试夹具与回归验证。 */
    public static class Broken {

        public String getValue() {
            throw new IllegalStateException("secret");
        }
    }

    GenericApplicationContext context;

    ToolRegistry registry;

    Service service;

    Events events;

    @BeforeEach
    void setup() {
        context = new GenericApplicationContext();
        context.refresh();
        registry = new ToolRegistry();
        service = new Service();
        events = new Events();
    }

    @AfterEach
    void close() {
        Thread.interrupted();
        context.close();
    }

    ToolExecutor executor(
        String method,
        List<ToolAuthorizationPolicy> policies,
        List<ToolRateLimiter> limiters,
        List<ToolExecutionListener> listeners,
        ToolRetryPolicy policy) {
        ToolExecutor executor = new ToolExecutor(
            registry,
            context,
            ToolExecutor.createObjectMapper(),
            listeners,
            policies,
            limiters,
            List.of(
                (cause, ctx) -> cause instanceof Temporary
                    ? Optional.of(
                        new ToolError(
                            "TEMP",
                            ToolError.Category.TRANSIENT,
                            "later",
                            ctx.invocationId()))
                    : Optional.empty()),
            List.of(events),
            policy,
            (name, source) -> new ToolExecutionOptions(
                ToolExecutionOptions.Effect.READ_ONLY, 3, 0));
        registry.register(
            new ToolDefinition(
                "tool",
                "desc",
                "cat",
                "service",
                method,
                List.of(new ParameterDefinition("value", "", "List", true)),
                true),
            service,
            null,
            null,
            null);
        return executor;
    }

    ToolResult call(ToolExecutor executor) {
        return executor.execute(
            new ToolInvocation("tool", "{\"value\":[\"original\"]}"),
            new ToolCallContext("id", "JAVA", "alice", "tenant", Map.of()));
    }

    @Test
    void retriesRebindAndObserveOneLogicalCall() {
        AtomicInteger authorizations = new AtomicInteger(),
            permits = new AtomicInteger(),
            before = new AtomicInteger(),
            after = new AtomicInteger();
        ToolExecutionListener listener = new ToolExecutionListener() {

            @Override
            public void beforeExecution(ToolInvocation i) {
                before.incrementAndGet();
            }

            @Override
            public void afterExecution(ToolInvocation i, ToolResult r) {
                after.incrementAndGet();
            }
        };
        var executor = executor(
            "transientFailure",
            List.of(
                (ctx, t, args) -> {
                    assertEquals("alice", ctx.principalId());
                    assertEquals("id", ctx.invocationId());
                    assertThrows(
                        UnsupportedOperationException.class,
                        () -> ((List<?>) args.get("value")).clear());
                    authorizations.incrementAndGet();
                    return ToolAuthorizationPolicy.Decision.allow();
                }),
            List.of(
                (ctx, t) -> {
                    permits.incrementAndGet();
                    return new ToolRateLimiter.Decision(true, null);
                }),
            List.of(listener),
            ToolRetryPolicy.safeDefault());
        assertTrue(call(executor).success());
        assertEquals(3, service.calls);
        assertEquals(3, authorizations.get());
        assertEquals(3, permits.get());
        assertEquals(1, before.get());
        assertEquals(1, after.get());
        assertEquals(1, events.starts);
        assertEquals(1, events.finishes);
        assertEquals(3, events.attempts);
        assertEquals(3, events.finalAttempts);
    }

    @Test
    void authorizationRevocationStopsNextAttempt() {
        AtomicInteger auth = new AtomicInteger();
        var executor = executor(
            "transientFailure",
            List.of(
                (ctx, t, args) -> auth.incrementAndGet() == 1
                    ? ToolAuthorizationPolicy.Decision.allow()
                    : ToolAuthorizationPolicy.Decision.deny("revoked")),
            List.of(),
            List.of(),
            ToolRetryPolicy.safeDefault());
        assertEquals("TOOL_ACCESS_DENIED", call(executor).errorDetail().code());
        assertEquals(1, service.calls);
        assertEquals(1, events.attempts);
    }

    @Test
    void deniesBeforeBindingAndListeners() {
        AtomicInteger before = new AtomicInteger();
        var executor = executor(
            "success",
            List.of((ctx, t, args) -> ToolAuthorizationPolicy.Decision.deny("denied")),
            List.of(),
            List.of(
                new ToolExecutionListener() {

                    @Override
                    public void beforeExecution(ToolInvocation i) {
                        before.incrementAndGet();
                    }
                }),
            ToolRetryPolicy.safeDefault());
        var result = executor.execute(new ToolInvocation("tool", "{\"value\":3}"));
        assertEquals("TOOL_ACCESS_DENIED", result.errorDetail().code());
        assertEquals(0, service.calls);
        assertEquals(0, before.get());
        assertEquals(1, events.finishes);
        assertEquals(0, events.attempts);
    }

    @Test
    void rateLimitReturnsDelayWithoutBusinessOrRetry() {
        var executor = executor(
            "success",
            List.of(),
            List.of((ctx, t) -> new ToolRateLimiter.Decision(false, 250L)),
            List.of(),
            ToolRetryPolicy.safeDefault());
        var result = call(executor);
        assertEquals("TOOL_RATE_LIMITED", result.errorDetail().code());
        assertEquals(250L, result.errorDetail().retryAfterMillis());
        assertEquals(0, service.calls);
        assertEquals(0, events.attempts);
        assertEquals(1, events.finishes);
    }

    @Test
    void infrastructureExceptionsFailClosed() {
        var executor = executor(
            "success",
            List.of(
                (ctx, t, args) -> {
                    throw new IllegalStateException("secret");
                }),
            List.of(),
            List.of(),
            ToolRetryPolicy.safeDefault());
        assertEquals(ToolError.Category.INTERNAL, call(executor).errorDetail().category());
        assertEquals(0, service.calls);
    }

    @Test
    void unknownBusinessExceptionsAreSanitizedAndNotRetried() {
        var executor = executor("secret", List.of(), List.of(), List.of(), ToolRetryPolicy.safeDefault());
        var result = call(executor);
        assertEquals(ToolError.Category.INTERNAL, result.errorDetail().category());
        assertFalse(result.error().contains("secret"));
        assertEquals(1, service.calls);
    }

    @Test
    void serializationFailureIsNeverMappedAsTransient() {
        var executor = executor(
            "serialization",
            List.of(),
            List.of(),
            List.of(),
            ToolRetryPolicy.safeDefault());
        assertEquals(ToolError.Category.INTERNAL, call(executor).errorDetail().category());
        assertEquals(1, service.calls);
    }

    @Test
    void evenCustomRetryCannotReplaySerializationFailure() {
        var executor = executor("serialization", List.of(), List.of(), List.of(), (t, c, o, a, e) -> true);
        assertEquals(ToolError.Category.INTERNAL, call(executor).errorDetail().category());
        assertEquals(1, service.calls);
    }

    @Test
    void mapperFailureStillFinishesAttemptObservation() {
        ToolExceptionMapper broken = (cause, ctx) -> {
            throw new IllegalStateException();
        };
        var executor = new ToolExecutor(
            registry,
            context,
            ToolExecutor.createObjectMapper(),
            List.of(),
            List.of(),
            List.of(),
            List.of(broken),
            List.of(events),
            ToolRetryPolicy.safeDefault(),
            (n, s) -> ToolExecutionOptions.defaults());
        registry.register(
            new ToolDefinition(
                "tool",
                "desc",
                "cat",
                "service",
                "secret",
                List.of(new ParameterDefinition("value", "", "List", true)),
                true),
            service,
            null,
            null,
            null);
        assertEquals(ToolError.Category.INTERNAL, call(executor).errorDetail().category());
        assertEquals(1, events.attempts);
        assertEquals(1, events.finishes);
    }

    @Test
    void limiterFailureStopsBusiness() {
        var executor = executor(
            "success",
            List.of(),
            List.of(
                (ctx, t) -> {
                    throw new IllegalStateException();
                }),
            List.of(),
            ToolRetryPolicy.safeDefault());
        assertEquals(ToolError.Category.INTERNAL, call(executor).errorDetail().category());
        assertEquals(0, service.calls);
    }

    @Test
    void disabledToolDoesNotEnterBusiness() {
        var executor = executor("success", List.of(), List.of(), List.of(), ToolRetryPolicy.safeDefault());
        registry.unregister("tool");
        registry.register(
            new ToolDefinition(
                "tool",
                "desc",
                "cat",
                "service",
                "success",
                List.of(new ParameterDefinition("value", "", "List", true)),
                false),
            service,
            null,
            null,
            null);
        assertEquals("TOOL_DISABLED", call(executor).errorDetail().code());
        assertEquals(0, service.calls);
        assertEquals(1, events.finishes);
    }

    @Test
    void interruptionDuringBackoffStopsAndPreservesFlag() throws Exception {
        var attempted = new CountDownLatch(1);
        var executor = new ToolExecutor(
            registry,
            context,
            ToolExecutor.createObjectMapper(),
            List.of(),
            List.of(),
            List.of(),
            List.of(
                (cause, ctx) -> Optional.of(
                    new ToolError(
                        "TEMP",
                        ToolError.Category.TRANSIENT,
                        "later",
                        ctx.invocationId()))),
            List.of(
                new ToolObservation() {

                    @Override
                    public void onAttemptFinished(
                        ToolCallContext c,
                        String n,
                        int a,
                        long e,
                        ToolResult r) {
                        attempted.countDown();
                    }
                }),
            ToolRetryPolicy.safeDefault(),
            (n, s) -> new ToolExecutionOptions(
                ToolExecutionOptions.Effect.READ_ONLY, 3, 30000));
        registry.register(
            new ToolDefinition(
                "tool",
                "desc",
                "cat",
                "service",
                "secret",
                List.of(new ParameterDefinition("value", "", "List", true)),
                true),
            service,
            null,
            null,
            null);
        ExecutorService workerPool = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> worker = workerPool.submit(
                () -> {
                    call(executor);
                    return Thread.currentThread().isInterrupted();
                });
            assertTrue(attempted.await(3, TimeUnit.SECONDS));
            workerPool.shutdownNow();
            assertTrue(worker.get(3, TimeUnit.SECONDS));
            assertEquals(1, service.calls);
        } finally {
            workerPool.shutdownNow();
            assertTrue(
                workerPool.awaitTermination(3, TimeUnit.SECONDS),
                "Test worker must terminate even when an assertion fails");
        }
    }

    @Test
    void retryPolicyCannotExceedHardBound() {
        var executor = executor("secret", List.of(), List.of(), List.of(), (t, c, o, a, e) -> true);
        assertFalse(call(executor).success());
        assertEquals(3, service.calls);
    }

    @Test
    void threadInterruptionStopsRetry() {
        var executor = executor(
            "transientFailure",
            List.of(),
            List.of(),
            List.of(),
            (t, c, o, a, e) -> {
                Thread.currentThread().interrupt();
                return true;
            });
        assertFalse(call(executor).success());
        assertEquals(1, service.calls);
        assertTrue(Thread.currentThread().isInterrupted());
    }

    @Test
    void observationAndFinalListenerExceptionsDoNotReplaceSuccess() {
        ToolExecutionListener listener = new ToolExecutionListener() {

            @Override
            public void afterExecution(ToolInvocation i, ToolResult r) {
                throw new IllegalStateException();
            }
        };
        events.fail = true;
        var executor = executor(
            "success",
            List.of(),
            List.of(),
            List.of(listener),
            ToolRetryPolicy.safeDefault());
        assertTrue(call(executor).success());
        assertEquals(1, service.calls);
    }

    @Test
    void beforeListenerFailurePreventsBusiness() {
        var executor = executor(
            "success",
            List.of(),
            List.of(),
            List.of(
                new ToolExecutionListener() {

                    @Override
                    public void beforeExecution(ToolInvocation i) {
                        throw new IllegalStateException();
                    }
                }),
            ToolRetryPolicy.safeDefault());
        assertFalse(call(executor).success());
        assertEquals(0, service.calls);
        assertEquals(0, events.attempts);
    }

    @Test
    void missingDisabledAndMalformedCallsAreObserved() {
        var executor = executor("success", List.of(), List.of(), List.of(), ToolRetryPolicy.safeDefault());
        assertEquals(
            "TOOL_NOT_FOUND",
            executor.execute(new ToolInvocation("missing", "{}")).errorDetail().code());
        assertEquals(
            "TOOL_ARGUMENT_INVALID",
            executor.execute(new ToolInvocation("tool", "[]")).errorDetail().code());
        assertEquals(2, events.finishes);
        assertEquals(0, events.attempts);
    }

    @Test
    void unsafeRetryConfigurationFails() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new ToolExecutionOptions(ToolExecutionOptions.Effect.UNKNOWN, 2, 0));
        assertThrows(
            IllegalArgumentException.class,
            () -> new ToolExecutionOptions(ToolExecutionOptions.Effect.NON_IDEMPOTENT, 2, 0));
    }

    @Test
    void contextMakesDeepSnapshotsAndChildrenKeepIdentity() {
        Map<String, Object> attrs = new HashMap<>();
        List<String> values = new ArrayList<>(List.of("original"));
        attrs.put("x", values);
        var parent = new ToolCallContext("p", "MCP", "alice", "t", attrs);
        values.clear();
        assertEquals(List.of("original"), parent.attributes().get("x"));
        assertThrows(
            UnsupportedOperationException.class,
            () -> ((List<?>) parent.attributes().get("x")).clear());
        var child = parent.child("AGENT");
        assertNotEquals(parent.invocationId(), child.invocationId());
        assertEquals("p", child.attributes().get("parentInvocationId"));
        assertEquals("alice", child.principalId());
        assertThrows(
            IllegalArgumentException.class,
            () -> new ToolCallContext(
                null, "JAVA", null, null, Map.of("active", new Object())));
    }

    /** 工具执行契约的测试夹具与回归验证。 */
    static class Events implements ToolObservation {

        int starts, finishes, attempts, finalAttempts;

        boolean fail;

        @Override
        public void onCallStarted(ToolCallContext c, String n) {
            starts++;
            if (fail) {
                throw new IllegalStateException();
            }
        }

        @Override
        public void onAttemptFinished(ToolCallContext c, String n, int a, long time, ToolResult r) {
            attempts++;
            if (fail) {
                throw new IllegalStateException();
            }
        }

        @Override
        public void onCallFinished(ToolCallContext c, String n, long time, int a, ToolResult r) {
            finishes++;
            finalAttempts = a;
            if (fail) {
                throw new IllegalStateException();
            }
        }
    }
}
