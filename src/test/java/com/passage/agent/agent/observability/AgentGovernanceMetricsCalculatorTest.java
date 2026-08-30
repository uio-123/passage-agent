package com.passage.agent.agent.observability;
import com.passage.agent.model.entity.AgentLog;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class AgentGovernanceMetricsCalculatorTest {
 @Test void calculatesPercentilesAndDoesNotExposeSensitiveLogFields(){var fast=AgentLog.builder().agentName("writer").status("SUCCESS").durationMs(10).prompt("secret").inputData("hidden").build();var failed=AgentLog.builder().agentName("writer").status("FAILED").durationMs(100).errorMessage("private").build();var slow=AgentLog.builder().agentName("reviewer").status("SUCCESS").durationMs(200).outputData("hidden").build();var result=AgentGovernanceMetricsCalculator.calculate(List.of(fast,failed,slow));assertEquals(3,result.sampleCount());assertEquals(2,result.successCount());assertEquals(100,result.p50DurationMs());assertEquals(200,result.p95DurationMs());assertTrue(result.agents().stream().anyMatch(a->a.agentName().equals("writer")&&a.highFailure()));assertFalse(result.token().available());}
 @Test void returnsZeroForNoCompletedSamples(){var result=AgentGovernanceMetricsCalculator.calculate(List.of(AgentLog.builder().status("SUCCESS").build()));assertEquals(0,result.sampleCount());assertEquals(0,result.p95DurationMs());}
}
