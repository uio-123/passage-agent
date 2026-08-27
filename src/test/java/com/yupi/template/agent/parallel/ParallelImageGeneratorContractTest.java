package com.yupi.template.agent.parallel;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.yupi.template.agent.context.StreamHandlerContext;
import com.yupi.template.agent.event.AgentStreamEvent;
import com.yupi.template.agent.fixture.ArticleWorkflowFixture;
import com.yupi.template.agent.tools.ImageGenerationTool;
import com.yupi.template.model.dto.article.ArticleState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract for the current image fan-out/fan-in boundary. A failed image must
 * not discard successful siblings, and the merged result must keep article order.
 */
class ParallelImageGeneratorContractTest {

    @AfterEach
    void clearStreamContext() {
        StreamHandlerContext.clear();
    }

    @Test
    void continuesAfterOneImageFailureAndReturnsSuccessfulImagesInPositionOrder() throws Exception {
        List<AgentStreamEvent> events = new ArrayList<>();
        StreamHandlerContext.set("image-contract-1", events::add);
        ParallelImageGenerator generator = new ParallelImageGenerator(new FakeImageTool());

        StateGraph graph = new StateGraph()
                .addNode("parallel_images", node_async(generator))
                .addEdge(START, "parallel_images")
                .addEdge("parallel_images", END);

        OverAllState result = graph.compile().invoke(Map.of(
                "imageRequirements", ArticleWorkflowFixture.imageRequirementsWithOneFailure()
        )).orElseThrow();

        @SuppressWarnings("unchecked")
        List<ArticleState.ImageResult> images = (List<ArticleState.ImageResult>) result.value("images").orElseThrow();
        assertThat(images).extracting(ArticleState.ImageResult::getPosition).containsExactly(1, 2);
        assertThat(images).extracting(ArticleState.ImageResult::getUrl)
                .containsExactly("https://images.example/1", "https://images.example/2");
        assertThat(events).extracting(AgentStreamEvent::taskId).containsOnly("image-contract-1");
        assertThat(events).extracting(AgentStreamEvent::sequence).containsExactly(1L, 2L);
    }

    private static final class FakeImageTool extends ImageGenerationTool {
        @Override
        public ImageGenerationResult generateImageDirect(
                String imageSource,
                String keywords,
                String prompt,
                Integer position,
                String type,
                String sectionTitle,
                String placeholderId
        ) {
            if (position == 3) {
                throw new IllegalStateException("simulated image provider failure");
            }
            ImageGenerationResult result = new ImageGenerationResult();
            result.setSuccess(true);
            result.setPosition(position);
            result.setUrl("https://images.example/" + position);
            result.setMethod(imageSource);
            result.setKeywords(keywords);
            result.setSectionTitle(sectionTitle);
            result.setPlaceholderId(placeholderId);
            return result;
        }
    }
}
