package com.passage.agent.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Requires an explicit acknowledgement so the deterministic demo API cannot be enabled accidentally. */
@Component
@Profile("demo")
public class DemoProfileGuard {
    public DemoProfileGuard(@Value("${passage.demo.acknowledge-non-production:false}") boolean acknowledged) {
        if (!acknowledged) {
            throw new IllegalStateException("The demo profile is non-production and requires PASSAGE_DEMO_ACKNOWLEDGE_NON_PRODUCTION=true");
        }
    }
}
