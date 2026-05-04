package io.github.songrongzhen.easyagent.core.exception;

public class EasyAgentException extends RuntimeException {

    public EasyAgentException(String message) {
        super(message);
    }

    public EasyAgentException(String message, Throwable cause) {
        super(message, cause);
    }
}
