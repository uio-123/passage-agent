package com.passage.agent.agent.tool;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

/** Closed registry: no tool is executable unless the application registers it explicitly. */
public final class ToolRegistry {
    private final Map<ToolId, ToolAdapter> adapters;

    public ToolRegistry(Collection<? extends ToolAdapter> adapters) {
        EnumMap<ToolId, ToolAdapter> registered = new EnumMap<>(ToolId.class);
        if (adapters != null) {
            for (ToolAdapter adapter : adapters) {
                if (adapter == null || registered.putIfAbsent(adapter.id(), adapter) != null) {
                    throw new IllegalArgumentException("Tool registry contains an invalid or duplicate adapter");
                }
            }
        }
        this.adapters = Map.copyOf(registered);
    }

    public ToolAdapter required(ToolId toolId) {
        ToolAdapter adapter = adapters.get(toolId);
        if (adapter == null) {
            throw new IllegalArgumentException("No adapter is registered for tool: " + toolId);
        }
        return adapter;
    }
}
