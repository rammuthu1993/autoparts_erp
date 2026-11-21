PHASE 4 FOLLOW-UP CLASSIFICATION FIX

Copy the two files from this archive into the existing project root, preserving paths:
- src/main/java/com/autoparts/erp/ai/service/ErpAiIntentClassifier.java
- src/test/java/com/autoparts/erp/ai/service/ErpAiGraphNodesTest.java

Why:
1. The production classifier in the original project still routed field-specific product questions into generic PRODUCT_STOCK / PRODUCT_DETAILS. This replacement prioritizes minimum stock, available quantity, part number, and product-specific low-stock status.
2. The new graph-node context tests used literal backslash-n sequences in assistant responses. The context resolver expects actual line breaks to identify labeled Product code fields. The test fixtures now use Java newline escapes (\n).

This patch does not remove or modify LangGraph4j. It does not change database schema or ERP business rules.

After copying, run:
  .\mvnw.cmd clean test-compile
  .\mvnw.cmd "-Dtest=ErpAiIntentClassifierTest,ErpAiHybridIntentRouterTest,ErpAiGraphNodesTest" test
  .\mvnw.cmd clean verify
