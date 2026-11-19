# Auto Parts ERP - Stage 3C Phase 5 evaluation corpus loader patch

This patch is based on `autoparts-erp(20261119-053107).zip`.

## Files

- Adds `src/main/java/com/autoparts/erp/ai/rag/evaluation/KnowledgeEvaluationCorpusService.java`.
- Updates `src/main/java/com/autoparts/erp/ai/controller/LiveGroundedAnswerEvaluationController.java` with ADMIN-only corpus status/seed endpoints and a corpus-readiness guard before paid live evaluation calls.
- Adds two disabled-by-default settings to `src/main/resources/application.properties`.

No database migration is required. The existing `KnowledgeIngestionService` writes the documents and chunks into the existing knowledge tables.

## Apply

Extract this ZIP directly into `D:\Projects\autoparts-erp` and allow the existing controller and `application.properties` to be updated. Back up those two existing files first if you have uncommitted local changes.

## Enable only in local development CMD

From `D:\Projects\autoparts-erp`:

```cmd
set AI_LIVE_EVALUATION_CORPUS_ENABLED=true
set AI_LIVE_EVALUATION_CORPUS_PATH=src/test/resources/knowledge
set AI_LIVE_EVALUATION_ENABLED=true
```

Set your provider key in the same terminal if not already set:

```cmd
set SARVAM_API_KEY=YOUR_ACTUAL_SARVAM_API_KEY
```

Start the application:

```cmd
.\mvnw.cmd spring-boot:run
```

## Seed and verify

Use an ADMIN JWT in Postman.

1. Check current status:
   - `GET http://localhost:8080/api/v1/admin/ai-evaluation/corpus/status`
2. Explicitly seed the synthetic local corpus:
   - `POST http://localhost:8080/api/v1/admin/ai-evaluation/corpus/seed`
   - No request body is required.
3. Check status again. Expect four active documents and a positive active chunk count. Expected roles:
   - `POL-WAR-001`: `PUBLIC`
   - `POL-RET-001`: `PUBLIC`
   - `FAQ-GST-001`: `ROLE_CASHIER`
   - `SOP-CSH-001`: `ROLE_CASHIER`
4. Only after corpus status is ready, run:
   - `POST http://localhost:8080/api/v1/admin/ai-evaluation/live-provider/run`
   - Body: `{"confirmProviderCalls":true}`

The live evaluation endpoint now rejects the run before provider calls if any of the four expected active documents/chunks/roles are missing.

## Important

- This corpus is synthetic and explicitly marked `approvedForProduction=false` in document metadata.
- Do not enable the corpus loader or seed these documents in production.
- The loader uses the existing SHA-256 idempotency/versioning in `KnowledgeIngestionService`.
- The patch has been source-reviewed against the uploaded project archive but could not be compiled here: the project targets Java 25, while the available build environment is Java 21 and the uploaded archive does not contain the Maven wrapper. Run the project's Maven tests with your configured Java 25 after applying it.
