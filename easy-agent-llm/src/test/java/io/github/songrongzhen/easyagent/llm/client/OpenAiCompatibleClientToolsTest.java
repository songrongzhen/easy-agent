package io.github.songrongzhen.easyagent.llm.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.songrongzhen.easyagent.llm.config.EasyAgentLlmProperties;
import io.github.songrongzhen.easyagent.llm.service.ChatMessage;
import io.github.songrongzhen.easyagent.llm.service.ToolDescriptor;
import io.github.songrongzhen.easyagent.llm.service.ToolParameter;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.TimeUnit;

/** 工具执行契约的测试夹具与回归验证。 */
class OpenAiCompatibleClientToolsTest {

    @Test
    void sendsCompleteSchemaInActualHttpBody() throws Exception {
        Map<String, Object> schema = Map.of(
            "type",
            "object",
            "properties",
            Map.of(
                "users",
                Map.of("type", "array", "items", Map.of("$ref", "#/$defs/User"))),
            "$defs",
            Map.of(
                "User",
                Map.of(
                    "type",
                    "object",
                    "properties",
                    Map.of("name", Map.of("type", "string")))));
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"choices\":[],\"model\":\"test\"}");
        List<String> bodies = new ArrayList<>();
        when(http.send(
            any(HttpRequest.class),
            ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
            .thenAnswer(
                invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    CompletableFuture<String> body = new CompletableFuture<>();
                    StringBuilder text = new StringBuilder();
                    request.bodyPublisher()
                        .orElseThrow()
                        .subscribe(
                            new Subscriber<ByteBuffer>() {

                                @Override
                                public void onSubscribe(Subscription s) {
                                    s.request(Long.MAX_VALUE);
                                }

                                @Override
                                public void onNext(ByteBuffer b) {
                                    byte[] bytes = new byte[b.remaining()];
                                    b.get(bytes);
                                    text.append(
                                        new String(
                                            bytes, StandardCharsets.UTF_8));
                                }

                                @Override
                                public void onError(Throwable e) {
                                    body.completeExceptionally(e);
                                }

                                @Override
                                public void onComplete() {
                                    body.complete(text.toString());
                                }
                            });
                    bodies.add(body.get(5, TimeUnit.SECONDS));
                    return response;
                });
        new OpenAiCompatibleClient(
            "http://example.invalid",
            null,
            "test",
            "test",
            new EasyAgentLlmProperties.ChatOptions(),
            http)
            .chatWithTools(
                List.of(ChatMessage.user("hello")),
                List.of(new ToolDescriptor("tool", "desc", List.of(), schema)));
        JsonNode actual = new ObjectMapper().readTree(bodies.get(0)).at("/tools/0/function/parameters");
        assertEquals(new ObjectMapper().valueToTree(schema), actual);
    }

    @Test
    void legacyDescriptorHasArrayItemsAndRequired() {
        var descriptor = new ToolDescriptor(
            "old", "desc", List.of(new ToolParameter("x", "array", "desc", true)));
        var json = new ObjectMapper().valueToTree(descriptor.inputSchema());
        assertTrue(json.at("/properties/x/items").isObject());
        assertEquals("x", json.at("/required/0").asText());
    }
}
