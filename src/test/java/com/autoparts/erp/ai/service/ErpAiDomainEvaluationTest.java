package com.autoparts.erp.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Deterministic, offline evaluation of the ERP intent classifier.
 * This does not call an LLM, database, or live ERP endpoint.
 */
public class ErpAiDomainEvaluationTest {

    private static final String DATASET = "/ai-evaluation/erp-ai-domain-evaluation.json";
    private final ErpAiIntentClassifier classifier = new ErpAiIntentClassifier();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void evaluationDatasetContainsAtLeast150CasesAcrossAllTenDomains() throws IOException {
        EvaluationDataset dataset = loadDataset();
        assertTrue(dataset.scenarios().size() >= 150,
                "The Stage 5 evaluation dataset must contain at least 150 scenarios");

        Map<String, Long> domainCounts = dataset.scenarios().stream()
                .collect(Collectors.groupingBy(Scenario::domain, Collectors.counting()));
        assertEquals(10, domainCounts.size(), "All ten ERP domains must be represented");
        domainCounts.forEach((domain, count) -> assertTrue(count >= 10,
                () -> "Domain " + domain + " has insufficient scenarios: " + count));

        long distinctIds = dataset.scenarios().stream().map(Scenario::id).distinct().count();
        assertEquals(dataset.scenarios().size(), distinctIds, "Scenario IDs must be unique");
    }

    @TestFactory
    Stream<DynamicTest> classifiesEveryDomainScenario() throws IOException {
        return loadDataset().scenarios().stream().map(scenario -> DynamicTest.dynamicTest(
                scenario.id() + " [" + scenario.domain() + "] " + scenario.query(),
                () -> assertEquals(scenario.expectedIntent(), classifier.classify(scenario.query()).intent(),
                        () -> "Unexpected intent for " + scenario.id() + " query: " + scenario.query())));
    }

    @Test
    void rejectsReadOnlyPolicyMutationRequestsDeterministically() {
        List<String> mutations = List.of(
                "Create product GAP-000001",
                "Update product GAP-000001",
                "Delete product GAP-000001",
                "Adjust stock for GAP-000001",
                "Record payment for INV-2026-0001",
                "Post invoice INV-2026-0001",
                "Delete supplier Bosch"
        );
        for (String message : mutations) {
            assertEquals("UNSUPPORTED", classifier.classify(message).intent(),
                    "Mutation must be rejected: " + message);
        }
    }

    private EvaluationDataset loadDataset() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(DATASET)) {
            if (stream == null) {
                throw new IOException("Missing Stage 5 evaluation dataset: " + DATASET);
            }
            return objectMapper.readValue(stream, new TypeReference<EvaluationDataset>() { });
        }
    }

    public record EvaluationDataset(String datasetVersion, String description, List<Scenario> scenarios) { }

    public record Scenario(String id, String domain, String query, String expectedIntent) { }
}
