package io.github.songrongzhen.easyagent.core.exception;

public class ToolNotFoundException extends EasyAgentException {

    public ToolNotFoundException(String toolName) {
        super("Tool not found: " + toolName);
    }
}
