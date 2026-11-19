package com.autoparts.erp.ai.rag.evaluation;

import java.util.List;
import java.util.Objects;

/**
 * Deterministic evaluator for human-annotated observations of validated, user-visible responses.
 * Citation counts describe citations remaining after validation; rejected model attempts are tracked separately.
 * It does not ask an LLM to judge another LLM and does not infer semantic support itself.
 */
public final class GroundedAnswerEvaluator {

    public GroundedAnswerEvaluator() {}

    public record CaseObservation(
            String caseId,
            String category,
            int factualClaimCount,
            int supportedClaimCount,
            /** Citations remaining in the validated, user-visible response (not raw model attempts). */
            int returnedCitationCount,
            int relevantCitationCount,
            int validCitationCount,
            int expectedEvidenceCount,
            int correctlyCitedEvidenceCount,
            int expectedAnswerFactCount,
            int evidenceSupportedFactCount,
            boolean abstentionRequired,
            boolean abstained,
            /** Unauthorized citations attempted by the model, whether or not validation exposed them. */
            int unauthorizedCitationCount,
            int unauthorizedCitationLeakCount,
            /** Fabricated citations attempted by the model and rejected by validation. */
            int fabricatedCitationCount
    ) {
        public CaseObservation {
            if (caseId == null || caseId.isBlank()) {
                throw new IllegalArgumentException("caseId must not be blank");
            }
            category = category == null ? "UNSPECIFIED" : category;
            requireRange("supportedClaimCount", supportedClaimCount, 0, factualClaimCount);
            requireRange("relevantCitationCount", relevantCitationCount, 0, returnedCitationCount);
            requireRange("validCitationCount", validCitationCount, 0, returnedCitationCount);
            requireRange("correctlyCitedEvidenceCount", correctlyCitedEvidenceCount, 0, expectedEvidenceCount);
            requireRange("evidenceSupportedFactCount", evidenceSupportedFactCount, 0, expectedAnswerFactCount);
            requireNonNegative("factualClaimCount", factualClaimCount);
            requireNonNegative("returnedCitationCount", returnedCitationCount);
            requireNonNegative("expectedEvidenceCount", expectedEvidenceCount);
            requireNonNegative("expectedAnswerFactCount", expectedAnswerFactCount);
            requireNonNegative("unauthorizedCitationCount", unauthorizedCitationCount);
            requireNonNegative("unauthorizedCitationLeakCount", unauthorizedCitationLeakCount);
            requireNonNegative("fabricatedCitationCount", fabricatedCitationCount);
            if (unauthorizedCitationLeakCount > unauthorizedCitationCount) {
                throw new IllegalArgumentException("unauthorized citation leaks cannot exceed unauthorized citation attempts");
            }
        }
    }

    public record Metrics(
            int caseCount,
            int factualClaimCount,
            int supportedClaimCount,
            double answerSupportRate,
            double unsupportedClaimRate,
            int returnedCitationCount,
            int relevantCitationCount,
            double citationPrecision,
            int expectedEvidenceCount,
            int correctlyCitedEvidenceCount,
            double citationRecall,
            int validCitationCount,
            double citationValidityRate,
            int expectedAnswerFactCount,
            int evidenceSupportedFactCount,
            double evidenceCoverage,
            int abstentionDecisionCount,
            int correctAbstentionDecisions,
            double abstentionAccuracy,
            int unauthorizedCitationCount,
            int unauthorizedCitationLeakCount,
            int fabricatedCitationCount,
            int rejectedCitationAttemptCount
    ) {}

    public Metrics evaluate(List<CaseObservation> observations) {
        Objects.requireNonNull(observations, "observations must not be null");
        int claims = 0, supported = 0, citations = 0, relevant = 0, valid = 0;
        int expectedEvidence = 0, citedEvidence = 0, expectedFacts = 0, supportedFacts = 0;
        int correctAbstentions = 0, unauthorized = 0, unauthorizedLeaks = 0, fabricated = 0;

        for (CaseObservation observation : observations) {
            Objects.requireNonNull(observation, "observation must not be null");
            claims += observation.factualClaimCount();
            supported += observation.supportedClaimCount();
            citations += observation.returnedCitationCount();
            relevant += observation.relevantCitationCount();
            valid += observation.validCitationCount();
            expectedEvidence += observation.expectedEvidenceCount();
            citedEvidence += observation.correctlyCitedEvidenceCount();
            expectedFacts += observation.expectedAnswerFactCount();
            supportedFacts += observation.evidenceSupportedFactCount();
            if (observation.abstentionRequired() == observation.abstained()) {
                correctAbstentions++;
            }
            unauthorized += observation.unauthorizedCitationCount();
            unauthorizedLeaks += observation.unauthorizedCitationLeakCount();
            fabricated += observation.fabricatedCitationCount();
        }

        int caseCount = observations.size();
        return new Metrics(
                caseCount, claims, supported, rate(supported, claims), rate(claims - supported, claims),
                citations, relevant, rate(relevant, citations), expectedEvidence, citedEvidence,
                rate(citedEvidence, expectedEvidence), valid, rate(valid, citations),
                expectedFacts, supportedFacts, rate(supportedFacts, expectedFacts),
                caseCount, correctAbstentions, rate(correctAbstentions, caseCount), unauthorized, unauthorizedLeaks, fabricated,
                unauthorized + fabricated
        );
    }

    /** All zero-denominator rates are 0.0 and must be interpreted with their accompanying counts. */
    private static double rate(int numerator, int denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }

    private static void requireNonNegative(String name, int value) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }

    private static void requireRange(String name, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max);
        }
    }
}
