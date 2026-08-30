package com.passage.agent.service;
import com.passage.agent.agent.context.ObservabilityContextSnapshot;
import java.util.List;
public interface AgentContextSnapshotService { ObservabilityContextSnapshot append(String runId,String stage,String summary,long tokenBefore,long tokenAfter); List<ObservabilityContextSnapshot> list(String runId); }
