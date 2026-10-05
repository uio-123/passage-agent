package com.passage.agent.evaluation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvaluationDatasetContractTest {

    private static final Path DATASET = Path.of("evaluation", "datasets", "p5-v1", "samples.json");
    private static final Path MANIFEST = Path.of("evaluation", "datasets", "p5-v1", "dataset-manifest.json");

    @Test
    void p5V1HasFrozenSizeStrataAndSafeReplaySources() throws IOException {
        JsonObject root = read(DATASET);
        assertEquals("p5-v1", root.get("datasetVersion").getAsString());
        assertEquals(5, root.getAsJsonObject("rubrics").getAsJsonArray("p5-style-v1").size());
        assertEquals(5, root.getAsJsonObject("rubrics").getAsJsonArray("p5-readability-v1").size());

        JsonArray samples = root.getAsJsonArray("samples");
        assertEquals(30, samples.size());

        Set<String> ids = new HashSet<>();
        Map<String, Integer> domains = new HashMap<>();
        int research = 0;
        int images = 0;
        int revisions = 0;
        int faults = 0;
        int drafts = 0;

        for (JsonElement element : samples) {
            JsonObject sample = element.getAsJsonObject();
            String id = requiredString(sample, "id");
            assertTrue(id.matches("p5-(technology|education|emotion)-\\d{2}"), id);
            assertTrue(ids.add(id), "duplicate sample id: " + id);
            assertEquals("p5-v1", requiredString(sample, "datasetVersion"));
            assertEquals("zh-CN", requiredString(sample, "language"));
            assertFalse(requiredString(sample, "userGoal").isBlank());
            assertEquals("p5-style-v1", requiredString(sample, "styleRubric"));
            assertEquals("p5-readability-v1", requiredString(sample, "readabilityRubric"));

            String domain = requiredString(sample, "domain");
            domains.merge(domain, 1, Integer::sum);
            boolean requiresResearch = sample.get("requiresResearch").getAsBoolean();
            boolean requiresImages = sample.get("requiresImages").getAsBoolean();
            if (requiresResearch) research++;
            if (requiresImages) images++;
            if ("LOCAL_REVISION".equals(requiredString(sample, "expectedQualityOutcome"))) revisions++;
            if (!"NONE".equals(requiredString(sample, "faultScenario"))) faults++;
            if ("DRAFT".equals(requiredString(sample, "annotationStatus"))) drafts++;

            JsonObject outline = sample.getAsJsonObject("outlineRequirements");
            assertTrue(outline.getAsJsonArray("requiredSections").size() >= 3, id);
            assertTrue(outline.get("minSections").getAsInt() >= 3, id);
            assertTrue(outline.get("maxSections").getAsInt() >= outline.get("minSections").getAsInt(), id);

            JsonArray facts = sample.getAsJsonArray("referenceFacts");
            JsonArray sources = sample.getAsJsonArray("replaySources");
            JsonArray requiredCitations = sample.getAsJsonArray("requiredCitationFactIds");
            JsonArray expectedCitations = sample.getAsJsonObject("fakeResponses").getAsJsonArray("expectedCitations");
            if (requiresResearch) {
                assertEquals("RESEARCH", requiredString(sample, "expectedRoute"));
                assertTrue(facts.size() >= 3, id);
                assertTrue(sources.size() >= 2, id);
                assertEquals(facts.size(), requiredCitations.size(), id);
                validateResearchReferences(id, facts, sources, requiredCitations, expectedCitations);
            } else {
                assertEquals("NO_RESEARCH", requiredString(sample, "expectedRoute"));
                assertEquals(0, facts.size(), id);
                assertEquals(0, sources.size(), id);
                assertEquals(0, requiredCitations.size(), id);
                assertEquals(0, expectedCitations.size(), id);
            }

            assertTrue(sample.getAsJsonArray("expectedArtifacts").size() >= 2, id);
            assertEquals(0, sample.getAsJsonArray("annotatorIds").size(), id);
            assertNoSecretLikeFields(sample, id);
        }

        assertEquals(Map.of("TECHNOLOGY", 10, "EDUCATION", 10, "EMOTION", 10), domains);
        assertEquals(18, research);
        assertEquals(18, images);
        assertEquals(12, revisions);
        assertEquals(6, faults);
        assertEquals(30, drafts);
    }

    @Test
    void manifestPinsContentAndCannotClaimReleaseWhileDraftsRemain() throws IOException {
        JsonObject manifest = read(MANIFEST);
        assertEquals("p5-v1", requiredString(manifest, "datasetVersion"));
        assertEquals(30, manifest.get("sampleCount").getAsInt());
        assertEquals(sha256NormalizedText(Files.readString(DATASET, StandardCharsets.UTF_8)),
                requiredString(manifest, "sampleFileSha256"));
        assertFalse(manifest.get("releaseReady").getAsBoolean());
        assertFalse(manifest.getAsJsonArray("blockingReasons").isEmpty());
    }

    private static void validateResearchReferences(String sampleId, JsonArray facts, JsonArray sources,
                                                   JsonArray requiredCitations, JsonArray expectedCitations) {
        Set<String> sourceIds = new HashSet<>();
        for (JsonElement sourceElement : sources) {
            JsonObject source = sourceElement.getAsJsonObject();
            String sourceId = requiredString(source, "id");
            assertTrue(sourceIds.add(sourceId), sampleId + " duplicate source " + sourceId);
            assertTrue(requiredString(source, "url").matches("https://[a-z0-9-]+\\.invalid/.+"), sampleId);
            assertEquals("SYNTHETIC_REPLAY", requiredString(source, "sourceType"));
            assertTrue(requiredString(source, "contentSha256").matches("[a-f0-9]{64}"), sampleId);
            assertTrue(requiredString(source, "excerpt").length() <= 500, sampleId);
        }

        Set<String> factIds = new HashSet<>();
        for (JsonElement factElement : facts) {
            JsonObject fact = factElement.getAsJsonObject();
            String factId = requiredString(fact, "id");
            assertTrue(factIds.add(factId), sampleId + " duplicate fact " + factId);
            int weight = fact.get("weight").getAsInt();
            assertTrue(weight == 1 || weight == 2, sampleId);
            for (JsonElement allowed : fact.getAsJsonArray("allowedSourceIds")) {
                assertTrue(sourceIds.contains(allowed.getAsString()), sampleId + " unknown source for " + factId);
            }
        }

        assertEquals(factIds, strings(requiredCitations), sampleId + " citation facts");
        assertEquals(sourceIds, strings(expectedCitations), sampleId + " fixture citations");
    }

    private static void assertNoSecretLikeFields(JsonObject sample, String sampleId) {
        String normalized = sample.toString().toLowerCase();
        assertFalse(normalized.contains("authorization"), sampleId);
        assertFalse(normalized.contains("api-key"), sampleId);
        assertFalse(normalized.contains("cookie"), sampleId);
        assertFalse(normalized.matches(".*sk-[a-z0-9]{12,}.*"), sampleId);
    }

    private static Set<String> strings(JsonArray values) {
        Set<String> result = new HashSet<>();
        for (JsonElement value : values) result.add(value.getAsString());
        return result;
    }

    private static JsonObject read(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static String requiredString(JsonObject object, String field) {
        assertTrue(object.has(field), "missing field: " + field);
        String value = object.get(field).getAsString();
        assertFalse(value.isBlank(), "blank field: " + field);
        return value;
    }

    private static String sha256NormalizedText(String value) {
        try {
            byte[] normalized = value.replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8);
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalized));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
