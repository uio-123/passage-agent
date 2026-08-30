package com.passage.agent.agent.research;

import com.passage.agent.agent.policy.ToolPolicyGateway;
import com.passage.agent.agent.tool.ToolCallRequest;
import com.passage.agent.agent.tool.ToolCallResult;
import com.passage.agent.agent.tool.ToolId;
import com.passage.agent.model.entity.ResearchSourceRecord;
import com.passage.agent.service.ResearchSourceService;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

/** E4 adapter: all actual reading still goes through the E2 gateway and E3 persistence service. */
public final class GatewayResearchUseCase implements ResearchUseCase {
    private final ToolPolicyGateway gateway;
    private final ResearchSourceService sources;

    public GatewayResearchUseCase(ToolPolicyGateway gateway, ResearchSourceService sources) {
        this.gateway = gateway;
        this.sources = sources;
    }

    @Override
    public ResearchBundle execute(String researchRunId, ResearchRequest request) {
        if (!request.allowedTools().contains(ToolId.WEB_READER)) {
            return new ResearchBundle(researchRunId, List.of(), List.of(), List.of("WEB_READER is not authorized"));
        }
        if (request.registeredSearchResults().isEmpty()) {
            return new ResearchBundle(researchRunId, List.of(), List.of(), List.of("No registered Search result is available"));
        }
        List<ResearchSource> persisted = new java.util.ArrayList<>();
        List<String> unresolved = new java.util.ArrayList<>();
        int remaining = request.callBudget();
        for (RegisteredSearchResult searchResult : request.registeredSearchResults()) {
            if (remaining-- == 0) break;
            ResearchSource existing = sources.listByRunId(researchRunId).stream()
                    .filter(source -> searchResult.canonicalUrl().equals(source.getCanonicalUrl()))
                    .findFirst().map(GatewayResearchUseCase::toResearchSource).orElse(null);
            if (existing != null) {
                persisted.add(existing);
                continue;
            }
            try {
                ToolCallResult result = gateway.execute(new ToolCallRequest(researchRunId, ToolId.WEB_READER,
                        searchResult.canonicalUrl(), Set.of(ToolId.WEB_READER), remaining + 1));
                for (ToolCallResult.SourceCandidate candidate : result.sources()) {
                    ResearchSource source = new ResearchSource(candidate.contentHash(), candidate.url(), candidate.title(),
                            candidate.publisher(), Instant.now(), candidate.contentHash(), boundedSummary(candidate.summary()),
                            request.query(), ResearchSourceStatus.VERIFIED);
                    ResearchSourceRecord saved = sources.saveOrReuse(researchRunId, source);
                    persisted.add(new ResearchSource(String.valueOf(saved.getId()), source.canonicalUrl(), source.title(),
                            source.publisher(), source.fetchedAt(), source.contentHash(), source.summary(), source.query(), source.status()));
                }
            } catch (RuntimeException exception) {
                unresolved.add("Web Reader failed for registered result " + searchResult.resultId());
            }
        }
        if (persisted.isEmpty() && unresolved.isEmpty()) unresolved.add("No readable source was returned");
        return new ResearchBundle(researchRunId, persisted, List.of(), unresolved);
    }

    private static String boundedSummary(String value) {
        return value.length() <= ResearchSource.MAX_SUMMARY_LENGTH ? value
                : value.substring(0, ResearchSource.MAX_SUMMARY_LENGTH);
    }

    private static ResearchSource toResearchSource(ResearchSourceRecord source) {
        return new ResearchSource(String.valueOf(source.getId()), source.getCanonicalUrl(), source.getTitle(), source.getPublisher(),
                source.getFetchedAt().atZone(ZoneId.systemDefault()).toInstant(), source.getContentHash(), source.getSummary(),
                source.getSearchQuery(), ResearchSourceStatus.valueOf(source.getStatus()));
    }
}
