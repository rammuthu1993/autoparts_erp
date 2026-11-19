package com.autoparts.erp.ai.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.autoparts.erp.ai.rag.evaluation.LiveGroundedAnswerEvaluationService;
import com.autoparts.erp.ai.rag.evaluation.KnowledgeEvaluationCorpusService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;

/**
 * Explicitly opt-in, administrator-only endpoint. No arbitrary prompt or document
 * content can be supplied by the caller; the evaluation uses a fixed test dataset.
 */
@RestController
@RequestMapping("/api/v1/admin/ai-evaluation")
public class LiveGroundedAnswerEvaluationController {

    private final LiveGroundedAnswerEvaluationService evaluationService;
    private final KnowledgeEvaluationCorpusService corpusService;

    public LiveGroundedAnswerEvaluationController(
            LiveGroundedAnswerEvaluationService evaluationService,
            KnowledgeEvaluationCorpusService corpusService
    ) {
        this.evaluationService = evaluationService;
        this.corpusService = corpusService;
    }

    /** Returns the ingestion status of the fixed synthetic evaluation corpus. */
    @GetMapping("/corpus/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<KnowledgeEvaluationCorpusService.CorpusStatus> corpusStatus() {
        return ResponseEntity.ok(corpusService.getStatus());
    }

    /** Explicitly ingests the fixed synthetic corpus into the configured local database. */
    @PostMapping("/corpus/seed")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<KnowledgeEvaluationCorpusService.SeedReport> seedCorpus() {
        return ResponseEntity.ok(corpusService.seed());
    }

    @PostMapping("/live-provider/run")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LiveGroundedAnswerEvaluationService.Report> run(
            @Valid @RequestBody RunRequest request
    ) {
        corpusService.requireReady();
        return ResponseEntity.ok(evaluationService.run(request.confirmProviderCalls()));
    }

    public record RunRequest(
            @AssertTrue(message = "Set confirmProviderCalls=true to authorize live provider usage.")
            boolean confirmProviderCalls
    ) {}
}
