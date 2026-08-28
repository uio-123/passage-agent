package com.passage.agent.agent.llm;

import com.passage.agent.utils.GsonUtils;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.lang.reflect.Type;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract shared by deterministic test doubles and the production adapter.
 * It deliberately has no Spring context or network dependency.
 */
class AiModelPortContractTest {

    @Test
    void fakeSupportsNormalStreamingAndStructuredCalls() {
        FakeAiModelPort model = new FakeAiModelPort("plain", List.of("part-1", "part-2"), "[1,2,3]");

        assertThat(model.complete("normal prompt")).isEqualTo("plain");
        assertThat(model.stream("stream prompt").collectList().block()).containsExactly("part-1", "part-2");
        List<?> structuredResult = model.completeStructured("json prompt", List.class);
        assertThat(structuredResult).isEqualTo(List.of(1.0, 2.0, 3.0));
        assertThat(model.prompts()).containsExactly("normal prompt", "stream prompt", "json prompt");
    }

    private static final class FakeAiModelPort implements AiModelPort {
        private final String completion;
        private final List<String> chunks;
        private final String structuredCompletion;
        private final List<String> prompts = new java.util.ArrayList<>();

        private FakeAiModelPort(String completion, List<String> chunks, String structuredCompletion) {
            this.completion = completion;
            this.chunks = chunks;
            this.structuredCompletion = structuredCompletion;
        }

        @Override
        public String complete(String prompt) {
            prompts.add(prompt);
            return completion;
        }

        @Override
        public Flux<String> stream(String prompt) {
            prompts.add(prompt);
            return Flux.fromIterable(chunks);
        }

        @Override
        public <T> T completeStructured(String prompt, Type responseType) {
            prompts.add(prompt);
            return GsonUtils.fromJson(structuredCompletion, responseType);
        }

        private List<String> prompts() {
            return prompts;
        }
    }
}
