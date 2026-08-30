package com.passage.agent.agent.tool.web;

import java.net.URI;
import java.time.Duration;

/** Replaceable transport seam; production integration is deliberately deferred until a real provider is selected. */
@FunctionalInterface
public interface WebTransport {
    WebResponse fetch(URI uri, Duration connectTimeout, Duration readTimeout, int maxResponseBytes);
}
