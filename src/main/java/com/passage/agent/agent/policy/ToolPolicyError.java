package com.passage.agent.agent.policy;

/** Stable, non-sensitive reason codes suitable for audit and later persistence. */
public enum ToolPolicyError {
    UNAUTHORIZED_TOOL,
    UNKNOWN_TOOL,
    UNSAFE_URL,
    UNSAFE_NETWORK_ADDRESS,
    REDIRECT_LIMIT,
    RESPONSE_TOO_LARGE,
    UNSUPPORTED_CONTENT_TYPE,
    RESPONSE_TEXT_TOO_LARGE,
    HTTP_CLIENT_ERROR,
    HTTP_SERVER_ERROR,
    TIMEOUT,
    TRANSPORT_FAILURE
}
