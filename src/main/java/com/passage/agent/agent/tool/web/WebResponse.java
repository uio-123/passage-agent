package com.passage.agent.agent.tool.web;

import java.util.List;

public record WebResponse(int statusCode, String contentType, byte[] body, String redirectLocation) {
    public WebResponse {
        body = body == null ? new byte[0] : body.clone();
    }

    public boolean isRedirect() {
        return statusCode >= 300 && statusCode < 400;
    }
}
