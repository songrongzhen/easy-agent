package io.github.songrongzhen.easyagent.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "easy-agent.mcp")
public class EasyAgentMcpProperties {

    private boolean enabled = true;

    private TransportType transportType = TransportType.SSE;

    private String sseEndpoint = "/mcp/sse";

    private String messageEndpoint = "/mcp/messages";

    private String serverName = "easy-agent-mcp-server";

    private String serverVersion = "0.1.0";

    public enum TransportType {
        SSE,
        STDIO
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public TransportType getTransportType() { return transportType; }
    public void setTransportType(TransportType transportType) { this.transportType = transportType; }
    public String getSseEndpoint() { return sseEndpoint; }
    public void setSseEndpoint(String sseEndpoint) { this.sseEndpoint = sseEndpoint; }
    public String getMessageEndpoint() { return messageEndpoint; }
    public void setMessageEndpoint(String messageEndpoint) { this.messageEndpoint = messageEndpoint; }
    public String getServerName() { return serverName; }
    public void setServerName(String serverName) { this.serverName = serverName; }
    public String getServerVersion() { return serverVersion; }
    public void setServerVersion(String serverVersion) { this.serverVersion = serverVersion; }
}
