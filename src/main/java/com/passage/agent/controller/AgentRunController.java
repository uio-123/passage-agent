package com.passage.agent.controller;

import com.passage.agent.agent.workflow.ContentQualityContinuationResult;
import com.passage.agent.agent.workflow.ContentQualityContinuationService;
import com.passage.agent.agent.event.AgentEvent;
import com.passage.agent.agent.event.AgentRunSnapshot;
import com.passage.agent.common.BaseResponse;
import com.passage.agent.common.ResultUtils;
import com.passage.agent.exception.ErrorCode;
import com.passage.agent.exception.ThrowUtils;
import com.passage.agent.model.entity.User;
import com.passage.agent.service.ArticleService;
import com.passage.agent.service.AgentEventService;
import com.passage.agent.service.AgentRunService;
import com.passage.agent.service.AgentCheckpointService;
import com.passage.agent.service.AgentNodeExecutionService;
import com.passage.agent.service.AgentArticleArtifactService;
import com.passage.agent.service.AgentContextSnapshotService;
import com.passage.agent.service.UserService;
import com.passage.agent.manager.AgentEventSseManager;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.servlet.http.HttpServletRequest;

/** P3-only continuation endpoints; legacy /article creation and confirmation APIs remain unchanged. */
@RestController
@RequestMapping("/api/agent-runs")
public class AgentRunController {
    private final ContentQualityContinuationService continuation;
    private final UserService users;
    private final ArticleService articles;
    private final AgentEventService events;
    private final AgentRunService runs;
    private final AgentEventSseManager eventSse;
    private final AgentCheckpointService checkpoints;
    private final AgentNodeExecutionService nodeExecutions;
    private final AgentArticleArtifactService artifacts;
    private final AgentContextSnapshotService contextSnapshots;

    public AgentRunController(ContentQualityContinuationService continuation, UserService users, ArticleService articles) {
        this(continuation, users, articles, null, null, null, null, null, null, null);
    }

    @Autowired
    public AgentRunController(ContentQualityContinuationService continuation, UserService users, ArticleService articles,
                              AgentEventService events, AgentRunService runs, AgentEventSseManager eventSse,
                              AgentCheckpointService checkpoints, AgentNodeExecutionService nodeExecutions,
                              AgentArticleArtifactService artifacts, AgentContextSnapshotService contextSnapshots) {
        this.continuation = continuation; this.users = users; this.articles = articles;
        this.events = events; this.runs = runs; this.eventSse = eventSse; this.checkpoints = checkpoints; this.nodeExecutions = nodeExecutions; this.artifacts = artifacts; this.contextSnapshots = contextSnapshots;
    }

    @PostMapping("/{runId}/content-quality/continue")
    @Operation(summary = "确认 P3 正文质量并继续图片交付")
    public BaseResponse<ContentQualityContinuationResult> continueContentQuality(@PathVariable String runId,
                                                                                   HttpServletRequest request) {
        ThrowUtils.throwIf(runId == null || runId.isBlank(), ErrorCode.PARAMS_ERROR, "runId不能为空");
        User user = users.getLoginUser(request);
        articles.getArticleDetail(runId, user);
        return ResultUtils.success(continuation.continueDelivery(runId));
    }

    @GetMapping("/{runId}/events")
    @Operation(summary = "查询 Agent Run 的安全事件回放")
    public BaseResponse<AgentEventReplayResponse> replay(@PathVariable String runId,
                                                           @RequestParam(defaultValue = "0") long afterSequence,
                                                           HttpServletRequest request) {
        authorize(runId, request);
        return ResultUtils.success(new AgentEventReplayResponse(snapshot(runId), events.findAfter(runId, afterSequence, 200)));
    }

    @GetMapping(value = "/{runId}/events/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "订阅 Agent Run 的可重连事件流")
    public SseEmitter stream(@PathVariable String runId,
                             @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
                             HttpServletRequest request) throws java.io.IOException {
        authorize(runId, request);
        long afterSequence = parseLastEventId(lastEventId);
        SseEmitter emitter = eventSse.subscribe(runId);
        eventSse.sendSnapshot(emitter, snapshot(runId));
        for (AgentEvent event : events.findAfter(runId, afterSequence, 200)) {
            emitter.send(SseEmitter.event().id(Long.toString(event.sequence())).name("agent-event").data(event));
        }
        return emitter;
    }

    @GetMapping("/{runId}/detail")
    @Operation(summary = "查询 Agent Run 安全运行视图")
    public BaseResponse<AgentRunDetailResponse> detail(@PathVariable String runId, HttpServletRequest request) {
        authorize(runId, request);
        var root = runs.getByRunId(runId);
        var runRecords = runs.list(com.mybatisflex.core.query.QueryWrapper.create().eq("rootRunId", root.getRootRunId()));
        var checkpointRecords = checkpoints.list(com.mybatisflex.core.query.QueryWrapper.create().eq("runId", runId));
        var nodeRecords = nodeExecutions.list(com.mybatisflex.core.query.QueryWrapper.create().eq("runId", runId));
        return ResultUtils.success(new AgentRunDetailResponse(
                runRecords.stream().map(item -> new RunView(item.getRunId(), item.getParentRunId(), item.getStatus(), item.getCurrentNode())).toList(),
                checkpointRecords.stream().map(item -> new CheckpointView(item.getCheckpointId(), item.getNodeId(), item.getStateVersion(), item.getStatus())).toList(),
                nodeRecords.stream().map(item -> new NodeView(item.getNodeId(), item.getStateVersion(), item.getStatus())).toList()));
    }

    @GetMapping("/{runId}/artifacts")
    @Operation(summary = "查询已登记的 Agent Artifact Manifest")
    public BaseResponse<ArtifactManifestResponse> artifactManifest(@PathVariable String runId, HttpServletRequest request) {
        authorize(runId, request);
        var versions = artifacts.listVersions(runId).stream().map(version -> new ArticleVersionView(
                version.getVersion(), version.getParentVersion(), version.getChangeReason(), version.getCreateTime(),
                artifacts.listArtifacts(runId, version.getVersion()).stream().map(artifact -> new ArtifactView(
                        artifact.getArtifactId(), artifact.getArtifactType(), artifact.getLocation(), artifact.getSha256(),
                        false, "已登记，尚未有可下载对象")).toList())).toList();
        return ResultUtils.success(new ArtifactManifestResponse(versions));
    }
    @GetMapping("/{runId}/context-snapshots")
    @Operation(summary = "查询安全 Agent Context Snapshot")
    public BaseResponse<java.util.List<com.passage.agent.agent.context.ObservabilityContextSnapshot>> contextSnapshots(@PathVariable String runId,HttpServletRequest request){authorize(runId,request);return ResultUtils.success(contextSnapshots.list(runId));}

    private void authorize(String runId, HttpServletRequest request) {
        ThrowUtils.throwIf(runId == null || runId.isBlank(), ErrorCode.PARAMS_ERROR, "runId不能为空");
        articles.getArticleDetail(runId, users.getLoginUser(request));
        if (events == null || runs == null || eventSse == null || checkpoints == null || nodeExecutions == null || artifacts == null || contextSnapshots == null) throw new IllegalStateException("Agent event APIs are not configured");
    }

    private AgentRunSnapshot snapshot(String runId) {
        var run = runs.getByRunId(runId);
        if (run == null) throw new IllegalStateException("Agent run does not exist: " + runId);
        return new AgentRunSnapshot(run.getRunId(), run.getRootRunId(), run.getParentRunId(), run.getStatus(),
                run.getCurrentNode(), run.getStateVersion() == null ? 0L : run.getStateVersion(), run.getCheckpointId());
    }

    private long parseLastEventId(String value) {
        if (value == null || value.isBlank()) return 0L;
        try { long parsed = Long.parseLong(value); if (parsed < 0) throw new NumberFormatException(); return parsed; }
        catch (NumberFormatException exception) { throw new IllegalArgumentException("Last-Event-ID must be a non-negative sequence"); }
    }

    public record AgentEventReplayResponse(AgentRunSnapshot snapshot, java.util.List<AgentEvent> events) { }
    public record AgentRunDetailResponse(java.util.List<RunView> runs, java.util.List<CheckpointView> checkpoints, java.util.List<NodeView> nodes) { }
    public record RunView(String runId, String parentRunId, String status, String currentNode) { }
    public record CheckpointView(String checkpointId, String nodeId, Long stateVersion, String status) { }
    public record NodeView(String nodeId, Long stateVersion, String status) { }
    public record ArtifactManifestResponse(java.util.List<ArticleVersionView> versions) { }
    public record ArticleVersionView(Integer version, Integer parentVersion, String changeReason, java.time.LocalDateTime createdAt,
                                     java.util.List<ArtifactView> artifacts) { }
    public record ArtifactView(String artifactId, String artifactType, String location, String sha256, boolean downloadable, String downloadStatus) { }
}
