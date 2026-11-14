package io.github.songrongzhen.easyagent.llm.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.songrongzhen.easyagent.llm.config.EasyAgentLlmProperties;
import io.github.songrongzhen.easyagent.llm.service.ChatMessage;
import io.github.songrongzhen.easyagent.llm.service.ChatResponse;
import io.github.songrongzhen.easyagent.llm.service.ToolCall;
import io.github.songrongzhen.easyagent.llm.service.ToolDescriptor;
import io.github.songrongzhen.easyagent.llm.service.Usage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 通过 OpenAI 兼容 HTTP 协议发送模型请求，直接使用完整工具 Schema。
 */
public class OpenAiCompatibleClient {

    /**
     * 当前组件的日志记录器。
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenAiCompatibleClient.class);

    /**
     * 当前边界使用的 JSON 编解码器。
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 模型兼容接口的服务地址。
     */
    private final String baseUrl;

    /**
     * 模型服务凭证。
     */
    private final String apiKey;

    /**
     * 模型名称。
     */
    private final String model;

    /**
     * 模型供应商的可读名称。
     */
    private final String providerName;

    /**
     * 模型生成参数配置。
     */
    private final EasyAgentLlmProperties.ChatOptions chatOptions;

    /**
     * 发送兼容模型请求的 HTTP 客户端。
     */
    private final HttpClient httpClient;

    /**
     * 创建 OpenAiCompatibleClient，保存调用所需的显式依赖。
     *
     * @param baseUrl      模型兼容接口的服务地址
     * @param apiKey       模型服务凭证
     * @param model        模型名称
     * @param providerName 模型供应商的可读名称
     * @param chatOptions  模型生成参数配置
     */
    public OpenAiCompatibleClient(String baseUrl, String apiKey, String model, String providerName,
        EasyAgentLlmProperties.ChatOptions chatOptions) {
        this(baseUrl, apiKey, model, providerName, chatOptions,
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build());
    }

    OpenAiCompatibleClient(String baseUrl, String apiKey, String model, String providerName,
        EasyAgentLlmProperties.ChatOptions chatOptions, HttpClient httpClient) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.providerName = providerName;
        this.chatOptions = chatOptions;
        this.httpClient = httpClient;
    }

    /**
     * 向兼容模型服务发送对话请求。
     *
     * @param messages 对话消息
     * @return 模型返回的对话或工具调用响应
     */
    public ChatResponse chat(List<ChatMessage> messages) {
        List<OpenAiCompatibleApi.ChatMessage> apiMessages = convertMessages(messages);
        OpenAiCompatibleApi.ChatCompletionRequest request = new OpenAiCompatibleApi.ChatCompletionRequest(model,
            apiMessages, chatOptions.getTemperature(), chatOptions.getTopP(), chatOptions.getMaxTokens(), null, false);
        OpenAiCompatibleApi.ChatCompletionResponse response = sendRequest(request);
        return convertResponse(response);
    }

    /**
     * 向兼容模型服务发送对话请求。
     *
     * @param messages 对话消息
     * @param tools    按工具名称保存的配置或运行时绑定
     * @return 模型返回的对话或工具调用响应
     */
    public ChatResponse chatWithTools(List<ChatMessage> messages, List<ToolDescriptor> tools) {
        List<OpenAiCompatibleApi.ChatMessage> apiMessages = convertMessages(messages);
        List<OpenAiCompatibleApi.ToolDef> apiTools = convertTools(tools);
        OpenAiCompatibleApi.ChatCompletionRequest request = new OpenAiCompatibleApi.ChatCompletionRequest(model,
            apiMessages, chatOptions.getTemperature(), chatOptions.getTopP(), chatOptions.getMaxTokens(), apiTools,
            false);
        OpenAiCompatibleApi.ChatCompletionResponse response = sendRequest(request);
        return convertResponse(response);
    }

    private OpenAiCompatibleApi.ChatCompletionResponse sendRequest(OpenAiCompatibleApi.ChatCompletionRequest request) {
        try {
            String requestBody = OBJECT_MAPPER.writeValueAsString(request);
            HttpRequest.Builder httpRequestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/chat/completions")).header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(120)).POST(HttpRequest.BodyPublishers.ofString(requestBody));
            if (apiKey != null && !apiKey.isBlank()) {
                httpRequestBuilder.header("Authorization", "Bearer " + apiKey);
            }
            HttpRequest httpRequest = httpRequestBuilder.build();
            HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
                LOGGER.error("LLM API call failed with status {}: {}", httpResponse.statusCode(), httpResponse.body());
                throw new RuntimeException(
                    providerName + " API call failed: " + httpResponse.statusCode() + ", body: " + httpResponse.body());
            }
            return OBJECT_MAPPER.readValue(httpResponse.body(), OpenAiCompatibleApi.ChatCompletionResponse.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize/deserialize LLM request/response", e);
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException(providerName + " API call failed", e);
        }
    }

    private List<OpenAiCompatibleApi.ChatMessage> convertMessages(List<ChatMessage> messages) {
        return messages.stream().map(msg -> new OpenAiCompatibleApi.ChatMessage(msg.role().name().toLowerCase(),
            msg.content(), msg.hasToolCalls() ? convertServiceToolCalls(msg.toolCalls()) : null, msg.toolCallId()))
            .toList();
    }

    private List<OpenAiCompatibleApi.ToolDef> convertTools(List<ToolDescriptor> tools) {
        return tools.stream()
            .map(tool -> OpenAiCompatibleApi.ToolDef.function(tool.name(), tool.description(), tool.inputSchema()))
            .toList();
    }

    private ChatResponse convertResponse(OpenAiCompatibleApi.ChatCompletionResponse response) {
        if (response.choices() == null || response.choices().isEmpty()) {
            return ChatResponse.of("", model);
        }
        OpenAiCompatibleApi.Choice choice = response.choices().get(0);
        OpenAiCompatibleApi.ChatMessage message = choice.message();
        if (message == null) {
            return ChatResponse.of("", model);
        }
        Usage usage = convertUsage(response.usage());
        String responseModel = response.model() != null ? response.model() : model;
        if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
            List<ToolCall> toolCalls = message.toolCalls().stream().filter(tc -> tc.function() != null)
                .map(tc -> new ToolCall(tc.id(), tc.function().name(), tc.function().arguments())).toList();
            return new ChatResponse(message.content(), "assistant", responseModel, toolCalls, usage);
        }
        return new ChatResponse(message.content(), "assistant", responseModel, List.of(), usage);
    }

    /**
     * 查询available。
     *
     * @return 当前available
     */
    public boolean isAvailable() {
        return true;
    }

    /**
     * 读取模型供应商的可读名称。
     *
     * @return 当前模型供应商的可读名称
     */
    public String getProviderName() {
        return providerName;
    }

    /**
     * 向兼容模型服务发送对话请求。
     *
     * @param messages 对话消息
     * @param consumer 按顺序接收流式文本片段的回调
     */
    public void chatStream(List<ChatMessage> messages, Consumer<String> consumer) {
        List<OpenAiCompatibleApi.ChatMessage> apiMessages = convertMessages(messages);
        // 流式请求使用相同消息转换逻辑，并显式开启 SSE 响应。
        OpenAiCompatibleApi.ChatCompletionRequest request = new OpenAiCompatibleApi.ChatCompletionRequest(
            model, apiMessages, chatOptions.getTemperature(), chatOptions.getTopP(),
            chatOptions.getMaxTokens(), null, true);
        try {
            String requestBody = OBJECT_MAPPER.writeValueAsString(request);
            HttpRequest.Builder httpRequestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/chat/completions")).header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(120)).POST(HttpRequest.BodyPublishers.ofString(requestBody));
            if (apiKey != null && !apiKey.isBlank()) {
                httpRequestBuilder.header("Authorization", "Bearer " + apiKey);
            }
            HttpRequest httpRequest = httpRequestBuilder.build();
            // SSE流式响应处理
            HttpResponse<Stream<String>> httpResponse = httpClient.send(httpRequest,
                HttpResponse.BodyHandlers.ofLines());
            if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
                String errorBody = httpResponse.body().collect(Collectors.joining("\n"));
                throw new RuntimeException(
                    providerName + " streaming failed: " + httpResponse.statusCode() + ", body: " + errorBody);
            }
            httpResponse.body().forEach(line -> handleStreamLine(line, consumer));
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException(providerName + " streaming failed", e);
        }
    }

    private List<OpenAiCompatibleApi.ToolCall> convertServiceToolCalls(List<ToolCall> toolCalls) {
        return toolCalls.stream().map(toolCall -> new OpenAiCompatibleApi.ToolCall(toolCall.id(), "function",
            new OpenAiCompatibleApi.FunctionCall(toolCall.name(), toolCall.arguments()))).toList();
    }

    private Usage convertUsage(OpenAiCompatibleApi.Usage usage) {
        if (usage == null) {
            return null;
        }
        return new Usage(usage.promptTokens(), usage.completionTokens(), usage.totalTokens());
    }

    private void handleStreamLine(String line, Consumer<String> consumer) {
        String trimmedLine = line == null ? "" : line.trim();
        if (trimmedLine.isEmpty() || !trimmedLine.startsWith("data:")) {
            return;
        }
        String data = trimmedLine.substring("data:".length()).trim();
        if ("[DONE]".equals(data)) {
            consumer.accept(null);
            return;
        }
        try {
            OpenAiCompatibleApi.ChatCompletionResponse response = OBJECT_MAPPER.readValue(data,
                OpenAiCompatibleApi.ChatCompletionResponse.class);
            if (response.choices() == null || response.choices().isEmpty()) {
                return;
            }
            OpenAiCompatibleApi.ChatMessage delta = response.choices().get(0).delta();
            if (delta == null || delta.content() == null || delta.content().isEmpty()) {
                return;
            }
            consumer.accept(delta.content());
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse " + providerName + " streaming line: " + line, e);
        }
    }
}
