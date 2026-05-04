package io.github.songrongzhen.easyagent.core.registry;

import io.github.songrongzhen.easyagent.core.model.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();

    public void register(ToolDefinition definition) {
        if (definition == null || definition.name() == null || definition.name().isBlank()) {
            log.warn("Attempted to register a tool with null or blank name, ignoring");
            return;
        }
        ToolDefinition existing = tools.putIfAbsent(definition.name(), definition);
        if (existing != null) {
            log.warn("Tool '{}' already registered, overwriting with new definition", definition.name());
            tools.put(definition.name(), definition);
        } else {
            log.info("Registered tool: {} - {}", definition.name(), definition.description());
        }
    }

    public void unregister(String toolName) {
        ToolDefinition removed = tools.remove(toolName);
        if (removed != null) {
            log.info("Unregistered tool: {}", toolName);
        }
    }

    public ToolDefinition getTool(String toolName) {
        return tools.get(toolName);
    }

    public Collection<ToolDefinition> getAllTools() {
        return Collections.unmodifiableCollection(tools.values());
    }

    public Collection<ToolDefinition> getEnabledTools() {
        return tools.values().stream()
                .filter(ToolDefinition::enabled)
                .toList();
    }

    public Collection<ToolDefinition> getToolsByCategory(String category) {
        return tools.values().stream()
                .filter(t -> category.equals(t.category()))
                .toList();
    }

    public boolean contains(String toolName) {
        return tools.containsKey(toolName);
    }

    public int size() {
        return tools.size();
    }

    public void clear() {
        tools.clear();
        log.info("Tool registry cleared");
    }
}
