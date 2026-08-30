package com.passage.agent.agent.context;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ContextSnapshotSanitizerTest {
 @Test void removesCredentialsAndBoundsSummaries(){String cleaned=ContextSnapshotSanitizer.sanitize("token=abc\napi-key: hidden\tstatus ok");assertFalse(cleaned.contains("abc"));assertFalse(cleaned.contains("hidden"));assertTrue(cleaned.contains("status ok"));assertEquals(1024,ContextSnapshotSanitizer.sanitize("x".repeat(1200)).length());}
}
