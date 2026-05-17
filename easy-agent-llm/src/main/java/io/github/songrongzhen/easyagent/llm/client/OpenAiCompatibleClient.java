package io.github.songrongzhen.easyagent.llm.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.songrongzhen.easyagent.llm.config.EasyAgentLlmProperties;
import io.github.songrongzhen.easyagent.llm.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

public class OpenAiCompatibleClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleClient.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final String providerName;
    private final EasyAgentLlmProperties.ChatOptions chatOptions;
    private final HttpClient httpClient;

    public OpenAiCompatibleClient(String baseUrl, String apiKey, String model,
                                   String providerName, EasyAgentLlmProperties.ChatOptions chatOptions) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.providerName = providerName;
        this.chatOptions = chatOptions;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public io.github.songrongzhen.easyagent.llm.service.ChatResponse chat(List<ChatMessage> messages) {
        List<OpenAiCompatibleApi.ChatMessage> apiMessages = convertMessages(messages);
        OpenAiCompatibleApi.ChatCompletionRequest request = new OpenAiCompatibleApi.ChatCompletionRequest(
                model, apiMessages, chatOptions.getTemperature(), chatOptions.getTopP(),
                chatOptions.getMaxTokens(), null, false
        );

        OpenAiCompatibleApi.ChatCompletionResponse response = sendRequest(request);
        return convertResponse(response);
    }

    public io.github.songrongzhen.easyagent.llm.service.ChatResponse chatWithTools(
            List<ChatMessage> messages, List<ToolDescriptor> tools) {
        List<OpenAiCompatibleApi.ChatMessage> apiMessages = convertMessages(messages);
        List<OpenAiCompatibleApi.ToolDef> apiTools = convertTools(tools);

        OpenAiCompatibleApi.ChatCompletionRequest request = new OpenAiCompatibleApi.ChatCompletionRequest(
                model, apiMessages, chatOptions.getTemperature(), chatOptions.getTopP(),
                chatOptions.getMaxTokens(), apiTools, false
        );

        OpenAiCompatibleApi.ChatCompletionResponse response = sendRequest(request);
        return convertResponse(response);
    }

    private OpenAiCompatibleApi.ChatCompletionResponse sendRequest(OpenAiCompatibleApi.ChatCompletionRequest request) {
        try {
            String requestBody = OBJECT_MAPPER.writeValueAsString(request);

            HttpRequest.Builder httpRequestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody));

            if (apiKey != null && !apiKey.isBlank()) {
                httpRequestBuilder.header("Authorization", "Bearer " + apiKey);
            }

            HttpRequest httpRequest = httpRequestBuilder.build();
            HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() != 200) {
                log.error("LLM API call failed with status {}: {}", httpResponse.statusCode(), httpResponse.body());
                throw new RuntimeException(providerName + " API call failed: " + httpResponse.statusCode());
            }

            return OBJECT_MAPPER.readValue(httpResponse.body(), OpenAiCompatibleApi.ChatCompletionResponse.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize/deserialize LLM request/response", e);
        } catch (Exception e) {
            if (e instanceof RuntimeException) throw (RuntimeException) e;
            throw new RuntimeException(providerName + " API call failed", e);
        }
    }

    private List<OpenAiCompatibleApi.ChatMessage> convertMessages(List<ChatMessage> messages) {
        return messages.stream()
                .map(msg -> new OpenAiCompatibleApi.ChatMessage(
                        msg.role().name().toLowerCase(), msg.content(), null, null))
                .toList();
    }

    private List<OpenAiCompatibleApi.ToolDef> convertTools(List<ToolDescriptor> tools) {
        return tools.stream()
                .map(tool -> {
                    Map<String, Object> parameters = new LinkedHashMap<>();
                    Map<String, Object> properties = new LinkedHashMap<>();
                    List<String> required = new ArrayList<>();

                    for (ToolParameter param : tool.parameters()) {
                        Map<String, Object> propDef = new LinkedHashMap<>();
                        propDef.put("type", param.type());
                        propDef.put("description", param.description());
                        properties.put(param.name(), propDef);
                        if (param.required()) {
                            required.add(param.name());
                        }
                    }

                    parameters.put("type", "object");
                    parameters.put("properties", properties);
                    parameters.put("required", required);

                    return OpenAiCompatibleApi.ToolDef.function(tool.name(), tool.description(), parameters);
                })
                .toList();
    }

    private io.github.songrongzhen.easyagent.llm.service.ChatResponse convertResponse(
            OpenAiCompatibleApi.ChatCompletionResponse response) {
        if (response.choices() == null || response.choices().isEmpty()) {
            return io.github.songrongzhen.easyagent.llm.service.ChatResponse.of("", model);
        }

        OpenAiCompatibleApi.Choice choice = response.choices().get(0);
        OpenAiCompatibleApi.ChatMessage message = choice.message();

        if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
            List<ToolCall> toolCalls = message.toolCalls().stream()
                    .map(tc -> new ToolCall(tc.id(), tc.function().name(), tc.function().arguments()))
                    .toList();
            return io.github.songrongzhen.easyagent.llm.service.ChatResponse.ofToolCalls(toolCalls, response.model());
        }

        Usage usage = null;
        if (response.usage() != null) {
            usage = new Usage(
                    response.usage().promptTokens(),
                    response.usage().completionTokens(),
                    response.usage().totalTokens()
            );
        }

        return new io.github.songrongzhen.easyagent.llm.service.ChatResponse(
                message.content(), "assistant", response.model(), List.of(), usage
        );
    }

    public boolean isAvailable() {
        return true;
    }

    public String getProviderName() {
        return providerName;
    }
}
