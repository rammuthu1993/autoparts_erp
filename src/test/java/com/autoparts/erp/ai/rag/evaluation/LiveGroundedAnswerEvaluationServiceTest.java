package com.autoparts.erp.ai.rag.evaluation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bsc.langgraph4j.RunnableConfig;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.autoparts.erp.ai.rag.citation.CitationValidator;
import com.autoparts.erp.ai.rag.model.RetrievedChunk;
import com.autoparts.erp.ai.security.ErpAiExecutionContext;
import com.autoparts.erp.ai.service.ErpAiGraphNodes;
import com.autoparts.erp.ai.service.ErpAiGraphState;

class LiveGroundedAnswerEvaluationServiceTest {

    @Test
    void shouldKeepLiveEvaluationDisabledByDefault() {
        ErpAiGraphNodes nodes = mock(ErpAiGraphNodes.class);
        LiveGroundedAnswerEvaluationService service = service(nodes, false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> service.run(true));

        assertEquals(409, exception.getStatusCode().value());
        verifyNoInteractions(nodes);
    }

    @Test
    void shouldRequireExplicitConfirmationBeforeProviderCalls() {
        ErpAiGraphNodes nodes = mock(ErpAiGraphNodes.class);
        LiveGroundedAnswerEvaluationService service = service(nodes, true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> service.run(false));

        assertEquals(400, exception.getStatusCode().value());
        verifyNoInteractions(nodes);
    }

    @Test
    void shouldRunFixedLiveEvaluationDatasetThroughGroundedGraphNodes() {
        ErpAiGraphNodes nodes = mock(ErpAiGraphNodes.class);
        stubRetrieval(nodes);
        stubGroundedGeneration(nodes);

        LiveGroundedAnswerEvaluationService service = service(nodes, true);
        LiveGroundedAnswerEvaluationService.Report report = service.run(true);

        assertEquals(8, report.caseCount());
        assertTrue(report.providerCalls() >= 5);
        assertEquals(0, report.metrics().unauthorizedCitationLeaks());
        LiveGroundedAnswerEvaluationService.CaseResult authorizedCashierCase = report.cases().stream()
                .filter(c -> "LIVE-005".equals(c.caseId()))
                .findFirst().orElseThrow();
        assertEquals(1, authorizedCashierCase.finalCitationCount());
        assertEquals(0, authorizedCashierCase.unauthorizedCitationLeaks());
        LiveGroundedAnswerEvaluationService.CaseResult roleBlockedCase = report.cases().stream()
                .filter(c -> "LIVE-006".equals(c.caseId()))
                .findFirst().orElseThrow();
        assertEquals(0, roleBlockedCase.finalCitationCount());
        assertEquals(0, roleBlockedCase.unauthorizedCitationLeaks());
        assertTrue(report.cases().stream().anyMatch(c -> "LIVE-006".equals(c.caseId())
                && !c.providerCalled()));
        assertTrue(report.cases().stream().anyMatch(c -> "LIVE-008".equals(c.caseId())
                && c.providerCalled()));
        verify(nodes, times(8)).retrieveKnowledge(any(ErpAiGraphState.class), any(RunnableConfig.class));
    }

    private static LiveGroundedAnswerEvaluationService service(ErpAiGraphNodes nodes, boolean enabled) {
        return new LiveGroundedAnswerEvaluationService(
                nodes, new CitationValidator(), enabled, "TEST_PROVIDER", "test-model",
                "45s", 0, "", "", "UNCONFIGURED");
    }

    private static void stubRetrieval(ErpAiGraphNodes nodes) {
        when(nodes.retrieveKnowledge(any(ErpAiGraphState.class), any(RunnableConfig.class)))
                .thenAnswer(invocation -> {
                    ErpAiGraphState state = invocation.getArgument(0);
                    RunnableConfig config = invocation.getArgument(1);
                    ErpAiExecutionContext context = config.metadata("executionContext")
                            .filter(ErpAiExecutionContext.class::isInstance)
                            .map(ErpAiExecutionContext.class::cast)
                            .orElseThrow();
                    String question = state.currentMessage().toLowerCase();

                    if (question.contains("opening cash float") && !context.hasRole("ROLE_CASHIER")) {
                        return Map.of("retrievedChunks", List.of());
                    }
                    if (question.contains("opening cash float")) {
                        return Map.of("retrievedChunks", List.of(sopChunk()));
                    }
                    if (question.contains("hsn") || question.contains("alternators")) {
                        return Map.of("retrievedChunks", List.of(gstChunk()));
                    }
                    if (question.contains("restocking")) {
                        return Map.of("retrievedChunks", List.of(returnFeeChunk()));
                    }
                    if (question.contains("return")) {
                        return Map.of("retrievedChunks", List.of(returnChunk()));
                    }
                    return Map.of("retrievedChunks", List.of(warrantyChunk()));
                });

        when(nodes.validateRetrievedContext(any(ErpAiGraphState.class), any(RunnableConfig.class)))
                .thenAnswer(invocation -> {
                    ErpAiGraphState state = invocation.getArgument(0);
                    return state.retrievedChunks().isEmpty()
                            ? Map.of("finalResponse",
                                    "I could not find any company policy or documentation covering this in the knowledge base.")
                            : Map.of();
                });
        when(nodes.validateCitations(any(ErpAiGraphState.class), any(RunnableConfig.class)))
                .thenReturn(Map.of());
    }

    private static void stubGroundedGeneration(ErpAiGraphNodes nodes) {
        when(nodes.generateGroundedResponseWithUsage(any(ErpAiGraphState.class)))
                .thenAnswer(invocation -> {
                    ErpAiGraphState state = invocation.getArgument(0);
                    String q = state.currentMessage().toLowerCase();
                    if (q.contains("turbocharger")) {
                        return new ErpAiGraphNodes.GroundedGenerationResult(
                                "I do not have sufficient authorized documentation to answer this question.",
                                true, 100L, 25L, 125L, "test-model");
                    }
                    String response;
                    if (q.contains("opening cash float")) {
                        response = "The standard opening cash float is exactly 5,000 currency units "
                                + sopChunk().formatCitation() + ".";
                    } else if (q.contains("hsn") || q.contains("alternators")) {
                        response = "Alternators use HSN 8511 " + gstChunk().formatCitation()
                                + ". The stated GST rate is 18% " + gstChunk().formatCitation() + ".";
                    } else if (q.contains("restocking")) {
                        response = "Non-stocked special-order items incur a 15 percent restocking fee "
                                + returnFeeChunk().formatCitation()
                                + ". The fee is waived for a cataloging error "
                                + returnFeeChunk().formatCitation() + ".";
                    } else if (q.contains("return")) {
                        response = "Eligible replacement parts may be returned within 30 calendar days "
                                + returnChunk().formatCitation()
                                + ". The original computer-generated tax invoice is required "
                                + returnChunk().formatCitation() + ".";
                    } else {
                        response = "Automotive batteries are covered for 24 months "
                                + warrantyChunk().formatCitation()
                                + ". Free replacement applies during the first 12 months "
                                + warrantyChunk().formatCitation()
                                + ". Months 13 to 24 receive a 50% pro-rata credit "
                                + warrantyChunk().formatCitation() + ".";
                    }
                    return new ErpAiGraphNodes.GroundedGenerationResult(
                            response, true, 120L, 40L, 160L, "test-model");
                });
    }

    private static RetrievedChunk warrantyChunk() {
        return new RetrievedChunk(1, UUID.randomUUID(), "POL-WAR-001", "Auto Parts Warranty Policy", 1,
                "Auto Parts Warranty Policy > 2. Product Warranty Categories > 2.1 Automotive Batteries",
                "Automotive batteries are covered for 24 months. Defects in the first 12 months receive free replacement. Months 13 to 24 receive a 50% pro-rata credit.",
                80, 0.99);
    }

    private static RetrievedChunk returnChunk() {
        return new RetrievedChunk(2, UUID.randomUUID(), "POL-RET-001", "Return & Core Exchange Policy", 1,
                "Return & Core Exchange Policy > Section 1: Standard Return Window and Eligibility",
                "Eligible automotive replacement parts may be returned within 30 calendar days. The original computer-generated tax invoice is required.",
                60, 0.99);
    }

    private static RetrievedChunk returnFeeChunk() {
        return new RetrievedChunk(3, UUID.randomUUID(), "POL-RET-001", "Return & Core Exchange Policy", 1,
                "Return & Core Exchange Policy > Section 3: Restocking Fees and Exceptions",
                "Non-stocked special order items incur a mandatory fifteen percent restocking fee. Fees are waived for branch cataloging error, incorrect part dispatch, or warehouse fulfillment mistake.",
                75, 0.99);
    }

    private static RetrievedChunk gstChunk() {
        return new RetrievedChunk(4, UUID.randomUUID(), "FAQ-GST-001", "ERP Billing & GST Clarification FAQ", 1,
                "ERP Billing & GST Clarification FAQ > 2. Harmonized System of Nomenclature (HSN) Codes > 2.2 What are the primary HSN codes used in our automotive parts inventory?",
                "HSN 8511 applies to electrical ignition and starting equipment including alternators. The standard GST slab is 18%.",
                65, 0.99);
    }

    private static RetrievedChunk sopChunk() {
        return new RetrievedChunk(5, UUID.randomUUID(), "SOP-CSH-001",
                "Daily Cash Register Closing & Reconciliation SOP", 1,
                "Daily Cash Register Closing & Reconciliation SOP > SECTION 1: REGISTER OPENING AND FLOAT VERIFICATION",
                "The standard opening cash float is fixed at exactly 5,000 currency units.",
                45, 0.99);
    }
}
