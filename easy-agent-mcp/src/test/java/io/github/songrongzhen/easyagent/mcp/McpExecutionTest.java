package io.github.songrongzhen.easyagent.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.songrongzhen.easyagent.core.config.ToolExecutionOptions;
import io.github.songrongzhen.easyagent.core.executor.ToolExecutor;
import io.github.songrongzhen.easyagent.core.model.ParameterDefinition;
import io.github.songrongzhen.easyagent.core.model.ToolCallContext;
import io.github.songrongzhen.easyagent.core.model.ToolDefinition;
import io.github.songrongzhen.easyagent.core.registry.ToolRegistry;
import io.github.songrongzhen.easyagent.core.spi.ToolAuthorizationPolicy;
import io.github.songrongzhen.easyagent.core.spi.ToolRetryPolicy;
import io.github.songrongzhen.easyagent.mcp.config.EasyAgentMcpProperties;
import io.github.songrongzhen.easyagent.mcp.context.McpCallContextResolver;
import io.github.songrongzhen.easyagent.mcp.controller.McpController;
import io.github.songrongzhen.easyagent.mcp.protocol.McpProtocol;
import io.github.songrongzhen.easyagent.mcp.server.EasyAgentMcpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 工具执行契约的测试夹具与回归验证。 */
class McpExecutionTest {

    /** 工具执行契约的测试夹具与回归验证。 */
    public static class Service {

        int calls;

        public String run(String value) {
            calls++;
            return value;
        }
    }

    GenericApplicationContext context;

    ToolRegistry tools;

    ToolExecutor executor;

    Service service;

    EasyAgentMcpProperties properties;

    List<ToolCallContext> seen;

    @BeforeEach
    void setup() {
        context = new GenericApplicationContext();
        context.refresh();
        tools = new ToolRegistry();
        service = new Service();
        seen = new ArrayList<>();
        properties = new EasyAgentMcpProperties();
        executor = new ToolExecutor(
            tools,
            context,
            ToolExecutor.createObjectMapper(),
            List.of(),
            List.of(
                (ctx, t, args) -> {
                    seen.add(ctx);
                    return "alice".equals(ctx.principalId())
                        ? ToolAuthorizationPolicy.Decision.allow()
                        : ToolAuthorizationPolicy.Decision.deny("denied");
                }),
            List.of(),
            List.of(),
            List.of(),
            ToolRetryPolicy.safeDefault(),
            (n, s) -> ToolExecutionOptions.defaults());
        tools.register(
            new ToolDefinition(
                "tool",
                "description",
                "allowed",
                "provider",
                0,
                "service",
                "run",
                List.of(new ParameterDefinition("value", "", "String", true)),
                "1",
                true),
            service,
            null,
            null,
            null);
    }

    @AfterEach
    void close() {
        context.close();
    }

    McpProtocol.JsonRpcRequest request(String method, Map<String, Object> params) {
        return new McpProtocol.JsonRpcRequest("2.0", 1, method, params);
    }

    @Test
    void categoryAndSourceAllowListsRetainToolsAndBlacklistWins() {
        properties.getToolExposure().setEnabled(true);
        properties.getToolExposure().setAllowedCategories(List.of("allowed"));
        properties.getToolExposure().setAllowedSources(List.of("provider"));
        var server = new EasyAgentMcpServer(properties, tools, executor);
        var list = (McpProtocol.ListToolsResult) server.handleRequest(request("tools/list", Map.of())).result();
        assertEquals(1, list.tools().size());
        properties.getToolExposure().setBlockedTools(List.of("tool"));
        assertTrue(
            ((McpProtocol.ListToolsResult) server.handleRequest(request("tools/list", Map.of())).result())
                .tools()
                .isEmpty());
        var result = (McpProtocol.CallToolResult) server.handleRequest(
            request(
                "tools/call",
                Map.of(
                    "name",
                    "tool",
                    "arguments",
                    Map.of("value", "x"))))
            .result();
        assertTrue(result.isError());
        assertEquals(0, service.calls);
    }

    @Test
    void contextAndStructuredErrorsPropagateThroughServer() throws Exception {
        var server = new EasyAgentMcpServer(properties, tools, executor);
        var req = request(
            "tools/call",
            Map.of(
                "name",
                "tool",
                "arguments",
                Map.of("value", "x", "principalId", "alice")));
        var denied = (McpProtocol.CallToolResult) server.handleRequest(req).result();
        assertTrue(denied.isError());
        assertEquals(
            "TOOL_ACCESS_DENIED",
            new ObjectMapper().readTree(denied.content().get(0).text()).path("code").asText());
        assertEquals(0, service.calls);
        var trusted = new ToolCallContext("trusted", "MCP", "alice", "tenant", Map.of());
        assertFalse(
            ((McpProtocol.CallToolResult) server.handleRequest(req, trusted).result())
                .isError());
        assertEquals(1, service.calls);
        assertEquals("tenant", seen.get(1).tenantId());
    }

    @Test
    void controllerUsesHostResolverAndAuthenticationFailureIs401() throws Exception {
        var server = new EasyAgentMcpServer(properties, tools, executor);
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
            + "\"params\":{\"name\":\"tool\",\"arguments\":{\"value\":\"x\"}}}";
        var request = new MockHttpServletRequest();
        request.setUserPrincipal(() -> "alice");
        var controller = new McpController(
            server,
            properties,
            r -> new ToolCallContext(
                null,
                "MCP",
                r.getUserPrincipal().getName(),
                null,
                Map.of()));
        assertEquals(
            HttpStatus.OK, controller.handleHttpMcp(body, headers, request).getStatusCode());
        assertEquals(1, service.calls);
        controller = new McpController(
            server,
            properties,
            r -> {
                throw new McpCallContextResolver.AuthenticationException();
            });
        assertEquals(
            HttpStatus.UNAUTHORIZED,
            controller.handleHttpMcp(body, headers, request).getStatusCode());
        assertEquals(1, service.calls);
        controller = new McpController(server, properties);
        headers.setBearerAuth("unverified");
        controller.handleHttpMcp(body, headers, request);
        assertNull(seen.get(1).principalId());
        assertEquals(1, service.calls);
    }

    @Test
    void malformedProtocolParamsStayProtocolErrors() {
        var response = new EasyAgentMcpServer(properties, tools, executor)
            .handleRequest(request("tools/call", Map.of("name", 1)));
        assertNotNull(response.error());
        assertNull(response.result());
    }

    @Test
    void legacySchemaConstructorStillProducesMap() {
        var tool = new McpProtocol.Tool(
            "old",
            "desc",
            new McpProtocol.JsonSchema(
                "object",
                Map.of("x", new McpProtocol.PropertyDef("string", "desc")),
                List.of("x")));
        assertEquals("object", tool.inputSchema().get("type"));
    }
}
