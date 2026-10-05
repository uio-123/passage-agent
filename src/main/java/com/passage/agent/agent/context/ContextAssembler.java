package com.passage.agent.agent.context;

/** Assembles only the context required by one agent role. */
public interface ContextAssembler {

    AgentContext assemble(ContextAssemblyRequest request);
}
