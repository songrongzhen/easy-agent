package io.github.songrongzhen.easyagent.mcp.server;

import io.github.songrongzhen.easyagent.core.model.ToolDefinition;
import io.github.songrongzhen.easyagent.mcp.config.EasyAgentMcpProperties;

import java.util.Collection;
import java.util.List;

public class McpToolExposurePolicy {

    private final EasyAgentMcpProperties.ToolExposure toolExposure;

    public McpToolExposurePolicy(EasyAgentMcpProperties.ToolExposure toolExposure) {
        this.toolExposure = toolExposure;
    }

    public boolean isAllowed(ToolDefinition toolDefinition) {
        if (toolDefinition == null) {
            return false;
        }
        return isAllowed(toolDefinition.name(), toolDefinition.source(), toolDefinition.category());
    }

    public boolean isAllowed(String toolName) {
        return isAllowed(toolName, null, null);
    }

    public boolean isAllowed(String toolName, String source, String category) {
        if (toolName == null || toolName.isBlank()) {
            return false;
        }
        if (toolExposure == null || !toolExposure.isEnabled()) {
            return true;
        }
        if (contains(toolExposure.getBlockedTools(), toolName)) {
            return false;
        }
        if (!toolExposure.getAllowedTools().isEmpty() && !contains(toolExposure.getAllowedTools(), toolName)) {
            return false;
        }
        if (contains(toolExposure.getBlockedSources(), source)) {
            return false;
        }
        if (!toolExposure.getAllowedSources().isEmpty() && !contains(toolExposure.getAllowedSources(), source)) {
            return false;
        }
        if (contains(toolExposure.getBlockedCategories(), category)) {
            return false;
        }
        if (!toolExposure.getAllowedCategories().isEmpty() && !contains(toolExposure.getAllowedCategories(), category)) {
            return false;
        }
        return true;
    }

    public boolean isAllowed(String toolName, Collection<ToolDefinition> tools) {
        if (toolName == null) {
            return false;
        }
        for (ToolDefinition tool : tools) {
            if (tool != null && toolName.equals(tool.name())) {
                return isAllowed(tool);
            }
        }
        return false;
    }

    private boolean contains(List<String> values, String candidate) {
        if (values == null || values.isEmpty() || candidate == null) {
            return false;
        }
        return values.stream().filter(v -> v != null && !v.isBlank()).anyMatch(candidate::equals);
    }
}
