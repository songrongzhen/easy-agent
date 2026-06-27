package io.github.songrongzhen.easyagent.llm.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class OpenAiCompatibleApi {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatCompletionRequest(
            @JsonProperty("model") String model,
            @JsonProperty("messages") List<ChatMessage> messages,
            @JsonProperty("temperature") Double temperature,
            @JsonProperty("top_p") Double topP,
            @JsonProperty("max_tokens") Integer maxTokens,
            @JsonProperty("tools") List<ToolDef> tools,
            @JsonProperty("stream") Boolean stream
    ) {
        public ChatCompletionRequest {
            if (stream == null) stream = false;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatMessage(
            @JsonProperty("role") String role,
            @JsonProperty("content") String content,
            @JsonProperty("tool_calls") List<ToolCall> toolCalls,
            @JsonProperty("tool_call_id") String toolCallId
    ) {
        public static ChatMessage system(String content) {
            return new ChatMessage("system", content, null, null);
        }
        public static ChatMessage user(String content) {
            return new ChatMessage("user", content, null, null);
        }
        public static ChatMessage assistant(String content) {
            return new ChatMessage("assistant", content, null, null);
        }
        public static ChatMessage assistantWithToolCalls(String content, List<ToolCall> toolCalls) {
            return new ChatMessage("assistant", content, toolCalls, null);
        }
        public static ChatMessage toolResult(String toolCallId, String content) {
            return new ChatMessage("tool", content, null, toolCallId);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ToolDef(
            @JsonProperty("type") String type,
            @JsonProperty("function") FunctionDef function
    ) {
        public static ToolDef function(String name, String description, Map<String, Object> parameters) {
            return new ToolDef("function", new FunctionDef(name, description, parameters));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FunctionDef(
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("parameters") Map<String, Object> parameters
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ToolCall(
            @JsonProperty("id") String id,
            @JsonProperty("type") String type,
            @JsonProperty("function") FunctionCall function
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FunctionCall(
            @JsonProperty("name") String name,
            @JsonProperty("arguments") String arguments
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatCompletionResponse(
            @JsonProperty("id") String id,
            @JsonProperty("model") String model,
            @JsonProperty("choices") List<Choice> choices,
            @JsonProperty("usage") Usage usage
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(
            @JsonProperty("index") int index,
            @JsonProperty("message") ChatMessage message,
            @JsonProperty("delta") ChatMessage delta,
            @JsonProperty("finish_reason") String finishReason
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("prompt_tokens") long promptTokens,
            @JsonProperty("completion_tokens") long completionTokens,
            @JsonProperty("total_tokens") long totalTokens
    ) {}
}
