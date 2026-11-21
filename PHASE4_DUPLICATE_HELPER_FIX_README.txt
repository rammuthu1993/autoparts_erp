Phase 4 test compilation fix

Copy/extract the src/ folder directly into the Auto Parts ERP project root:
D:\Projects\autoparts-erp

This replaces only:
src/test/java/com/autoparts/erp/ai/service/ErpAiGraphNodesTest.java

The file retains the existing classify(String, List<Map<String,String>>) helper near line 587 and removes the duplicate helper near line 837. All Phase 4 test methods are retained. The final class closing brace is present.

No production source, LangGraph4j dependency, graph workflow, or graph state is modified.
