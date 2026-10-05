package com.passage.agent.demo;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DemoScenarioControllerTest {
    @Test
    void exposesOnlyStructuredDeterministicScenarioResult() throws Exception {
        DemoScenarioService service = mock(DemoScenarioService.class);
        when(service.run("smoke-1", DemoScenarioType.RECOVERABLE_FAULT)).thenReturn(
                new DemoScenarioResult("smoke-1", DemoScenarioType.RECOVERABLE_FAULT, "demo-run", "COMPLETED",
                        1, 3, 8, true, 1, 1, 0, false));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new DemoScenarioController(service)).build();

        mvc.perform(post("/demo/scenarios/run").contentType("application/json")
                        .content("{\"scenarioId\":\"smoke-1\",\"scenarioType\":\"RECOVERABLE_FAULT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.runStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.artifactCount").value(3))
                .andExpect(jsonPath("$.data.recoverySucceeded").value(true))
                .andExpect(jsonPath("$.data.nodeRetryCount").value(1))
                .andExpect(jsonPath("$.data.duplicateExternalSideEffects").value(0));
    }
}
