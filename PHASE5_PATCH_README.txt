PHASE 5 KICKOFF PATCH

Apply from D:\Projects\autoparts-erp by extracting this ZIP and allowing files to merge/overwrite.

Files included:
- src/main/java/com/autoparts/erp/ai/service/ErpAiIntentClassifier.java
  Based on the latest ErpAiIntentClassifier.java uploaded in this conversation. Includes the Phase 4 matching/containing/with search qualifier cleanup plus deterministic routing corrections exposed by the 150-case Phase 5 dataset and read-only mutation rejection for product/stock/payment/price operations.
- src/test/java/com/autoparts/erp/ai/service/ErpAiDomainEvaluationTest.java
- src/test/java/com/autoparts/erp/ai/service/ErpAiConcurrencyLoadTest.java
- src/test/java/com/autoparts/erp/ai/security/ErpAiSqlInjectionDefenseTest.java
- src/test/resources/ai-evaluation/erp-ai-domain-evaluation.json
- docs/ai-agent/EVALUATION_REPORT.md
- docs/ai-agent/IMPLEMENTATION_STATUS.md

No Flyway migration is added or modified. LangGraph4j is unchanged. No existing test source is overwritten.

IMPORTANT:
The classifier source is based on the latest individual classifier file supplied in this conversation. The rest of the patch is additive. This environment has Java 21, while the project targets Java 25, so the full Maven/JUnit suite was not run here. Apply to a backup or clean Git worktree, inspect the diff, then run the commands in EVALUATION_REPORT.md on the Java 25 Windows environment.
