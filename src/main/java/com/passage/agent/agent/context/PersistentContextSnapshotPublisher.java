package com.passage.agent.agent.context;
import com.passage.agent.service.AgentContextSnapshotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
@Component @Slf4j public class PersistentContextSnapshotPublisher implements ContextSnapshotPublisher {
 private final AgentContextSnapshotService service; private final boolean enabled;
 public PersistentContextSnapshotPublisher(AgentContextSnapshotService service,@Value("${article.agent.observability.context-snapshots-enabled:false}") boolean enabled){this.service=service;this.enabled=enabled;}
 public void checkpointReady(String runId,String nodeId,long stateVersion,String checkpointId,String targetStatus){if(!enabled)return;try{service.append(runId,"CHECKPOINT_READY","节点 "+nodeId+" 已提交 checkpoint；目标状态 "+targetStatus,0,0);}catch(RuntimeException e){log.warn("Context snapshot publication failed without affecting checkpoint, runId={}",runId,e);}}
}
