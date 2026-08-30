package com.passage.agent.agent.research;

import com.passage.agent.agent.policy.ToolAuditSink;
import com.passage.agent.agent.policy.ToolPolicy;
import com.passage.agent.agent.policy.ToolPolicyGateway;
import com.passage.agent.agent.tool.ToolAdapter;
import com.passage.agent.agent.tool.ToolCallRequest;
import com.passage.agent.agent.tool.ToolCallResult;
import com.passage.agent.agent.tool.ToolId;
import com.passage.agent.agent.tool.ToolRegistry;
import com.passage.agent.model.entity.ResearchSourceRecord;
import com.passage.agent.service.ResearchSourceService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GatewayResearchUseCaseTest {
    @Test
    void readsOnlyRegisteredSearchUrlAndPersistsItsVerifiedSource() {
        ResearchSourceService sources = mock(ResearchSourceService.class);
        ResearchSourceRecord stored = new ResearchSourceRecord();
        stored.setId(42L);
        when(sources.saveOrReuse(eq("research-run"), any())).thenReturn(stored);
        AtomicInteger toolCalls = new AtomicInteger();
        GatewayResearchUseCase useCase = new GatewayResearchUseCase(gateway(request -> {
            toolCalls.incrementAndGet();
            assertThat(request.input()).isEqualTo("https://example.com/registered");
            return result("https://example.com/registered", "Evidence");
        }), sources);

        ResearchBundle bundle = useCase.execute("research-run", request("https://example.com/registered"));

        assertThat(toolCalls).hasValue(1);
        assertThat(bundle.sources()).singleElement().satisfies(source -> {
            assertThat(source.sourceId()).isEqualTo("42");
            assertThat(source.query()).isEqualTo("climate evidence");
            assertThat(source.status()).isEqualTo(ResearchSourceStatus.VERIFIED);
        });
        assertThat(bundle.unresolvedClaims()).isEmpty();
        verify(sources).saveOrReuse(eq("research-run"), any());
    }

    @Test
    void doesNotCallAnyToolWhenWebReaderIsNotAuthorized() {
        ResearchSourceService sources = mock(ResearchSourceService.class);
        GatewayResearchUseCase useCase = new GatewayResearchUseCase(gateway(request -> {
            throw new AssertionError("Tool must not be called");
        }), sources);

        ResearchBundle bundle = useCase.execute("research-run", new ResearchRequest("climate evidence",
                List.of(new RegisteredSearchResult("search-1", "https://example.com/registered", "Registered", null)), Set.of(), 1));

        assertThat(bundle.sources()).isEmpty();
        assertThat(bundle.unresolvedClaims()).containsExactly("WEB_READER is not authorized");
        verify(sources, never()).saveOrReuse(any(), any());
    }

    @Test
    void toolFailureProducesNoCitationOrPersistedSource() {
        ResearchSourceService sources = mock(ResearchSourceService.class);
        GatewayResearchUseCase useCase = new GatewayResearchUseCase(gateway(request -> {
            throw new IllegalStateException("unreachable");
        }), sources);

        ResearchBundle bundle = useCase.execute("research-run", request("https://example.com/registered"));

        assertThat(bundle.sources()).isEmpty();
        assertThat(bundle.unresolvedClaims()).containsExactly("Web Reader failed for registered result search-1");
        verify(sources, never()).saveOrReuse(any(), any());
    }

    @Test
    void reusesPersistedSourceBeforeCallingTheReaderAgain() {
        ResearchSourceService sources = mock(ResearchSourceService.class);
        ResearchSourceRecord stored = new ResearchSourceRecord();
        stored.setId(42L);
        stored.setCanonicalUrl("https://example.com/registered");
        stored.setTitle("Stored evidence");
        stored.setFetchedAt(java.time.LocalDateTime.of(2026, 8, 29, 0, 0));
        stored.setContentHash("stored-hash");
        stored.setSummary("Stored summary");
        stored.setSearchQuery("climate evidence");
        stored.setStatus("VERIFIED");
        when(sources.listByRunId("research-run")).thenReturn(List.of(stored));
        GatewayResearchUseCase useCase = new GatewayResearchUseCase(gateway(request -> {
            throw new AssertionError("Persisted source must be reused before a Tool call");
        }), sources);

        ResearchBundle bundle = useCase.execute("research-run", request("https://example.com/registered"));

        assertThat(bundle.sources()).singleElement().extracting(ResearchSource::sourceId).isEqualTo("42");
        verify(sources, never()).saveOrReuse(any(), any());
    }

    private static ResearchRequest request(String url) {
        return new ResearchRequest("climate evidence", List.of(new RegisteredSearchResult("search-1", url, "Registered", null)),
                Set.of(ToolId.WEB_READER), 1);
    }

    private static ToolPolicyGateway gateway(java.util.function.Function<ToolCallRequest, ToolCallResult> action) {
        ToolAdapter adapter = new ToolAdapter() {
            @Override public ToolId id() { return ToolId.WEB_READER; }
            @Override public ToolCallResult execute(ToolCallRequest request, ToolPolicy policy) { return action.apply(request); }
        };
        return new ToolPolicyGateway(new ToolRegistry(List.of(adapter)), ToolPolicy.strictDefaults(), event -> { });
    }

    private static ToolCallResult result(String url, String summary) {
        return new ToolCallResult(ToolId.WEB_READER,
                List.of(new ToolCallResult.SourceCandidate(url, "Evidence", "Example", summary, "content-hash")), Duration.ZERO);
    }
}
