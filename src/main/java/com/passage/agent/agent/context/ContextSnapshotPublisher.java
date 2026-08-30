package com.passage.agent.agent.context;
public interface ContextSnapshotPublisher { void checkpointReady(String runId,String nodeId,long stateVersion,String checkpointId,String targetStatus); }
