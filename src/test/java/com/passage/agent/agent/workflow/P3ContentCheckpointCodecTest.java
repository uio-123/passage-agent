package com.passage.agent.agent.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class P3ContentCheckpointCodecTest {
    @Test void roundTripsOnlyTheAllowListedRecoveryFields() {
        P3ContentCheckpointCodec codec = new P3ContentCheckpointCodec(new ObjectMapper());
        String json = codec.write(new P3ContentCheckpointSnapshot("run", P3ContentCheckpointSnapshot.MODE, 3,
                "markdown", 2, List.of("article-markdown-v2", "quality-report-v2"), "ACCEPT",
                new P3ContentDeliveryContext("Title", "Sub", "guide", List.of("PEXELS"))));
        assertThat(codec.read(json).artifactIds()).containsExactly("article-markdown-v2", "quality-report-v2");
        assertThat(codec.read(json).deliveryContext().enabledImageMethods()).containsExactly("PEXELS");
        assertThat(json).doesNotContain("prompt").doesNotContain("sourceSummary");
    }
    @Test void rejectsWrongModeOrDecision() {
        P3ContentDeliveryContext delivery = new P3ContentDeliveryContext("Title", null, "guide", List.of());
        assertThatIllegalArgumentException().isThrownBy(() -> new P3ContentCheckpointSnapshot("run", "LEGACY", 0, "markdown", 1, List.of(), "ACCEPT", delivery));
        assertThatIllegalArgumentException().isThrownBy(() -> new P3ContentCheckpointSnapshot("run", P3ContentCheckpointSnapshot.MODE, 0, "markdown", 1, List.of(), "REVISE", delivery));
    }
}
