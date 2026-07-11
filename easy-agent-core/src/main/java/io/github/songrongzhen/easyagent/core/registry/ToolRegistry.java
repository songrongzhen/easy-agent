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
        registerOrFail(definition);
    }

    public void register(ToolDefinition definition, ConflictStrategy strategy) {
        if (strategy == null) {
            register(definition);
            return;
        }

        switch (strategy) {
            case FAIL_FAST -> registerOrFail(definition);
            case KEEP_EXISTING -> registerIfAbsent(definition);
            case REPLACE_EXISTING -> registerOrReplace(definition);
        }
    }

    public void registerIfAbsent(ToolDefinition definition) {
        if (!isValid(definition)) {
            return;
        }
        ToolDefinition existing = tools.putIfAbsent(definition.name(), definition);
        if (existing != null) {
            log.info("Tool '{}' already registered, keeping existing definition from {}",
                    definition.name(), safeSource(existing));
            return;
        }
        log.info("Registered tool: {} - {} [{}]",
                definition.name(), definition.description(), safeSource(definition));
    }

    public void registerOrReplace(ToolDefinition definition) {
        if (!isValid(definition)) {
            return;
        }
        ToolDefinition existing = tools.put(definition.name(), definition);
        if (existing != null) {
            log.warn("Tool '{}' already registered, replacing {} with {}",
                    definition.name(), safeSource(existing), safeSource(definition));
        } else {
            log.info("Registered tool: {} - {} [{}]",
                    definition.name(), definition.description(), safeSource(definition));
        }
    }

    public void registerOrFail(ToolDefinition definition) {
        if (!isValid(definition)) {
            return;
        }
        ToolDefinition existing = tools.putIfAbsent(definition.name(), definition);
        if (existing != null) {
            throw new IllegalStateException("Tool '" + definition.name() + "' already registered from "
                    + safeSource(existing) + ", new source: " + safeSource(definition));
        }
        log.info("Registered tool: {} - {} [{}]", definition.name(), definition.description(), safeSource(definition));
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

    private boolean isValid(ToolDefinition definition) {
        if (definition == null || definition.name() == null || definition.name().isBlank()) {
            log.warn("Attempted to register a tool with null or blank name, ignoring");
            return false;
        }
        return true;
    }

    private String safeSource(ToolDefinition definition) {
        if (definition == null || definition.source() == null || definition.source().isBlank()) {
            return "unknown";
        }
        return definition.source();
    }

    public enum ConflictStrategy {
        FAIL_FAST,
        KEEP_EXISTING,
        REPLACE_EXISTING
    }
}
