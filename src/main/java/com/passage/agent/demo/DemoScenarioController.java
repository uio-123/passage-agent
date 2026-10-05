package com.passage.agent.demo;

import com.passage.agent.common.BaseResponse;
import com.passage.agent.common.ResultUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("demo")
@RequestMapping("/demo/scenarios")
public class DemoScenarioController {
    private final DemoScenarioService scenarios;

    public DemoScenarioController(DemoScenarioService scenarios) {
        this.scenarios = scenarios;
    }

    @PostMapping("/run")
    public BaseResponse<DemoScenarioResult> run(@RequestBody DemoScenarioRequest request) {
        if (request == null) throw new IllegalArgumentException("request must not be null");
        DemoScenarioType type;
        try {
            type = DemoScenarioType.valueOf(request.scenarioType());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("scenarioType is invalid", exception);
        }
        return ResultUtils.success(scenarios.run(request.scenarioId(), type));
    }

    public record DemoScenarioRequest(String scenarioId, String scenarioType) { }
}
