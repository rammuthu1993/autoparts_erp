Phase 4 product-search prefix fix

Replace:
src/main/java/com/autoparts/erp/ai/service/ErpAiIntentClassifier.java

This change adds removal of leading "matching" and "containing" qualifiers
during iterative search-text cleanup. It preserves the existing classifier
intent rules and does not modify LangGraph4j or tests.

Expected:
"Find products matching brake pad" -> PRODUCT_SEARCH, searchText "brake pad"

Apply by extracting this ZIP into the project root:
D:\Projects\autoparts-erp

Then run:
.\mvnw.cmd "-Dtest=ErpAiIntentClassifierTest" test
.\mvnw.cmd clean test
