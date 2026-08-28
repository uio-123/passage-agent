package com.passage.agent.agent;

import com.passage.agent.agent.agents.ContentGeneratorAgent;
import com.passage.agent.agent.agents.ContentMergerAgent;
import com.passage.agent.agent.agents.ImageAnalyzerAgent;
import com.passage.agent.agent.agents.OutlineGeneratorAgent;
import com.passage.agent.agent.agents.TitleGeneratorAgent;
import com.passage.agent.agent.fixture.ArticleWorkflowFixture;
import com.passage.agent.agent.llm.AiModelPort;
import com.passage.agent.agent.parallel.ParallelImageGenerator;
import com.passage.agent.model.dto.article.ArticleState;
import com.passage.agent.model.enums.SseMessageTypeEnum;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression contract for the existing three human-in-the-loop creation phases.
 * It exercises the real StateGraph nodes with a deterministic model and never
 * starts Spring or accesses LiteLLM, a database, or image services.
 */
class ArticleCreationWorkflowContractTest {

    @Test
    void preservesTitleOutlineAndContentWorkflowAndLegacySseMessages() {
        ArticleWorkflowFixture.DeterministicArticleModel model = new ArticleWorkflowFixture.DeterministicArticleModel();
        ArticleAgentOrchestrator orchestrator = orchestratorUsing(model);
        ArticleState state = ArticleWorkflowFixture.legacyState("workflow-contract-1");
        List<String> messages = new ArrayList<>();

        orchestrator.executePhase1_GenerateTitles(state, messages::add);
        assertThat(state.getTitleOptions()).hasSize(1);
        assertThat(messages).containsExactly(SseMessageTypeEnum.AGENT1_COMPLETE.getValue());

        ArticleState.TitleOption selected = state.getTitleOptions().getFirst();
        ArticleState.TitleResult title = new ArticleState.TitleResult();
        title.setMainTitle(selected.getMainTitle());
        title.setSubTitle(selected.getSubTitle());
        state.setTitle(title);

        orchestrator.executePhase2_GenerateOutline(state, messages::add);
        assertThat(state.getOutline().getSections())
                .extracting(ArticleState.OutlineSection::getTitle)
                .containsExactly("什么是 Agent");
        assertThat(messages).contains("AGENT2_STREAMING:{\"sections\":[", "AGENT2_COMPLETE");

        orchestrator.executePhase3_GenerateContent(state, messages::add);
        assertThat(state.getContent()).isEqualTo("## 什么是 Agent\n它由模型、状态和工具组成。\n");
        assertThat(state.getFullContent()).isEqualTo(state.getContent());
        assertThat(state.getImageRequirements()).isEmpty();
        assertThat(state.getImages()).isEmpty();
        assertThat(messages).contains(
                "AGENT3_STREAMING:## 什么是 Agent\n",
                "AGENT3_COMPLETE",
                "AGENT4_COMPLETE",
                "AGENT5_COMPLETE",
                "MERGE_COMPLETE"
        );
        assertThat(model.prompts()).hasSize(4);
    }

    private ArticleAgentOrchestrator orchestratorUsing(AiModelPort model) {
        ArticleAgentOrchestrator orchestrator = new ArticleAgentOrchestrator();
        ReflectionTestUtils.setField(orchestrator, "titleGeneratorAgent", new TitleGeneratorAgent(model));
        ReflectionTestUtils.setField(orchestrator, "outlineGeneratorAgent", new OutlineGeneratorAgent(model));
        ReflectionTestUtils.setField(orchestrator, "contentGeneratorAgent", new ContentGeneratorAgent(model));
        ReflectionTestUtils.setField(orchestrator, "imageAnalyzerAgent", new ImageAnalyzerAgent(model));
        ReflectionTestUtils.setField(orchestrator, "parallelImageGenerator", new ParallelImageGenerator(null));
        ReflectionTestUtils.setField(orchestrator, "contentMergerAgent", new ContentMergerAgent());
        return orchestrator;
    }

}
