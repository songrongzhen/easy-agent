package io.github.songrongzhen.easyagent.core.exception;

public class ToolExecutionException extends EasyAgentException {

    public ToolExecutionException(String toolName, Throwable cause) {
        super("Failed to execute tool: " + toolName, cause);
    }
}
