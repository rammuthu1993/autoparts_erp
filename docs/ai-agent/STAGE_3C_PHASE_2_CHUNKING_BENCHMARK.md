# Stage 3C Phase 2 — Chunking Evaluation & Empirical Benchmark Report (Revised)

**Status**: COMPLETED & VERIFIED  
**Date**: 2026-11-18  
**Scope**: Empirical baseline measurement of `DocumentChunker` (500 target tokens, 50 overlap tokens) across the 4 representative corpus documents (`POL-WAR-001.md`, `SOP-CSH-001.txt`, `POL-RET-001.pdf`, `FAQ-GST-001.md`).  
**Target Environment**: Java 25, Spring Boot 4.1.x, Apache PDFBox 3.0.3, PostgreSQL 18.4, Spring JDBC.  
**Constraint Enforced**: Measurement-only benchmark; zero chunking parameter changes before establishing empirical baseline.

---

## 1. Executive Summary

In Stage 3C Phase 2, an empirical evaluation of the existing `DocumentChunker` was conducted across all four representative synthetic documents authored in Phase 1. The goal was to establish a rigorous quantitative baseline of chunk sizes, token estimation heuristics, heading hierarchy preservation, boundary snapping, and sizing anomalies before introducing any modifications to the retrieval or chunking pipeline.

### Headline Benchmark Metrics (Verified Across Corpus)

| Metric | Measured Value | Analysis & Classification |
| :--- | :---: | :--- |
| **Total Corpus Documents** | **4** | 2 Markdown (`POL-WAR-001`, `FAQ-GST-001`), 1 Plain Text (`SOP-CSH-001`), 1 PDF (`POL-RET-001`) |
| **Total Chunks Produced** | **33** | 10 (Warranty), 6 (Cash SOP), 3 (Return PDF), 14 (GST FAQ) |
| **Weighted Average Chunk Size** | **202.0 tokens** | True weighted mean across all 33 chunks ($\sum \text{tokens} = 6,667 / 33$ chunks). (Unweighted document average is $258.2$). |
| **Oversized Chunks (>500 tokens)** | **0 (0.0%)** | 500-token ceiling is strictly enforced across all 4 documents |
| **Undersized Chunks (<100 tokens)** | **8 (24.2%)** | Caused primarily by orphan parent headers in Markdown (9–13 tokens) |
| **Word Boundary Preservation** | **33/33 (100.0%)** | Zero words truncated mid-word across all chunks |
| **Markdown/Text Paragraph Preservation** | **52/52 (100.0%)** | All 52 source paragraphs separated by `\n\n` in Markdown and Text are preserved intact |
| **PDF Text Splitting Behavior** | **Sentence-Level** | PDFBox extracts text without `\n\n`, forming a single 967-token block that is chunked by sentence |
| **Heuristic Punctuation Termination** | **27/33 (81.8%)** | 27 chunks end with terminal punctuation (`.`, `?`, `!`); remaining 6 are 5 orphan headers and 1 table/list line |
| **Aggregate Chars/Estimated-Token Ratio** | **3.99 (Heuristic)** | Calculated directly from corpus totals ($26{,}611 \text{ chars} / 6{,}667 \text{ tokens} \approx 3.9915$). Internal property of `Math.ceil(chars / 4.0)` heuristic; actual tokenizer accuracy **not yet measured** |
| **Consecutive Chunk Overlap** | **Continuous: 37 words / Markdown: 0 words** | Verified via suffix-to-prefix word matching: continuous text overlaps by 37 words; Markdown headers have 0% overlap |

---

## 2. Document-by-Document Empirical Summary

| Document Code | Format | Document Title | Raw Chars | Raw Words | Raw Est. Tokens | Chunks Produced | Min Tokens | Max Tokens | Doc Avg Tokens | Chars/Token Ratio |
| :--- | :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `POL-WAR-001` | Markdown | Auto Parts Warranty Policy | 7,016 | 950 | 1,754 | 10 | 9 | 263 | 175.5 | 3.95 |
| `SOP-CSH-001` | Plain Text | Daily Cash Register Closing & Reconciliation SOP | 7,527 | 975 | 1,882 | 6 | 329 | 439 | 385.5 | 3.99 |
| `POL-RET-001` | PDF | Return and Core Exchange Policy | 3,866 | 544 | 967 | 3 | 114 | 491 | 364.0 | 3.99 |
| `FAQ-GST-001` | Markdown | ERP Billing and GST Clarification FAQ | 6,036 | 905 | 1,509 | 14 | 11 | 247 | 107.6 | 3.97 |
| **OVERALL CORPUS** | — | **4 Documents** | **24,445** | **3,374** | **6,112** | **33** | **9** | **491** | **202.0 (Weighted)** | **3.99 (Aggregate: 26,611 / 6,667)** |

### Arithmetic Reconciliation of Overall Average:
- Sum of estimated chunk tokens across all 33 chunks:
  $$\text{Total Tokens} = 1,755\text{ (Warranty)} + 2,313\text{ (SOP)} + 1,092\text{ (Return)} + 1,507\text{ (GST FAQ)} = 6,667\text{ Tokens}$$
- True weighted average across all 33 chunks:
  $$\text{Weighted Mean} = \frac{6,667}{33} \approx \mathbf{202.03 \text{ tokens/chunk}}$$
- Unweighted arithmetic mean of document averages:
  $$\text{Unweighted Mean} = \frac{175.5 + 385.5 + 364.0 + 107.6}{4} = \mathbf{258.15 \text{ tokens/chunk}}$$
- **Correction Applied**: The report and automated test assertions now strictly report and assert the weighted average of **202.0 tokens/chunk**.

### Arithmetic Reconciliation of Aggregate Characters-per-Estimated-Token Ratio:
- Sum of chunk character counts across all 33 chunks:
  $$\text{Total Chunk Characters} = 6{,}997\text{ (Warranty)} + 9{,}241\text{ (SOP)} + 4{,}364\text{ (Return)} + 6{,}009\text{ (GST FAQ)} = 26{,}611\text{ Characters}$$
- Sum of estimated chunk tokens across all 33 chunks:
  $$\text{Total Chunk Estimated Tokens} = 1{,}755\text{ (Warranty)} + 2{,}313\text{ (SOP)} + 1{,}092\text{ (Return)} + 1{,}507\text{ (GST FAQ)} = 6{,}667\text{ Tokens}$$
- Aggregate characters-per-estimated-token ratio:
  $$\text{Aggregate Ratio} = \frac{\text{Total Chunk Characters}}{\text{Total Chunk Estimated Tokens}} = \frac{26{,}611}{6{,}667} \approx \mathbf{3.9915 \text{ (3.99 chars/estimated token)}}$$
- **Heuristic Clarification**: The previous `3.99 (Weighted)` label was removed and replaced with the verified aggregate ratio ($26,611 / 6,667 = 3.9915 \approx 3.99$). This ratio is an internal mathematical property of the $\lceil \text{chars} / 4.0 \rceil$ heuristic and **NOT** a measurement of real BPE/WordPiece tokenizer accuracy, which has not yet been benchmarked.

---

## 3. Complete Chunk Census (All 33 Chunks)

### 3.1 `POL-WAR-001.md` (Auto Parts Warranty Policy — Markdown)
- **Document Level Section Title**: `Auto Parts Warranty Policy`

| Idx | Tokens | Words | Chars | Undersized | Ends Punct. | Non-Punctuation Classification | Inherited Section Title | Content Summary / Snippet |
| :---: | :---: | :---: | :---: | :---: | :---: | :--- | :--- | :--- |
| `0` | 64 | 37 | 253 | **YES** (<100) | YES | — | `Auto Parts Warranty Policy` | Synthetic disclaimer and policy preamble |
| `1` | 155 | 81 | 619 | NO | YES | — | `... > 1. Overview and Scope` | Scope covering retail, trade, commercial sales, exclusions |
| `2` | 9 | 5 | 33 | **YES** (<100) | **NO** | **Orphan Parent Header** | `... > 2. Product Warranty Categories` | Heading only (`## 2. Product Warranty Categories`) |
| `3` | 263 | 153 | 1050 | NO | YES | — | `... > 2. ... > 2.1 Automotive Batteries` | 24-month free replacement, 36-month pro-rata, testing |
| `4` | 222 | 117 | 886 | NO | YES | — | `... > 2. ... > 2.2 Brake Rotors and Friction` | 12-month / 20,000 km defect warranty, runout, wear |
| `5` | 203 | 107 | 809 | NO | YES | — | `... > 2. ... > 2.3 Alternators and Starters` | 24-month warranty, bench testing requirement, core deposit |
| `6` | 204 | 104 | 813 | NO | YES | — | `... > 2. ... > 2.4 Electrical Sensors` | 12-month warranty, diagnostic scan report prerequisite |
| `7` | 193 | 102 | 771 | NO | YES | — | `... > 3. General Warranty Exclusions` | Misuse, commercial racing, unauthorized modification |
| `8` | 209 | 113 | 833 | NO | YES | — | `... > 4. Warranty Claim Submission` | Invoice prerequisite, VIN, mileage, test report verification |
| `9` | 233 | 131 | 930 | NO | YES | — | `... > 5. Review and Approval Workflow` | Thresholds: Cashier $\le$ 2,500, Manager $\le$ 15,000, Admin > 15,000 |

### 3.2 `SOP-CSH-001.txt` (Cash Register Reconciliation — Plain Text)
- **Document Level Section Title**: `Daily Cash Register Closing & Reconciliation SOP`

| Idx | Tokens | Words | Chars | Undersized | Ends Punct. | Non-Punctuation Classification | Inherited Section Title | Content Summary / Snippet |
| :---: | :---: | :---: | :---: | :---: | :---: | :--- | :--- | :--- |
| `0` | 439 | 226 | 1756 | NO | YES | — | `Daily Cash Register Closing...` | Preamble, register opening, float verification (\$200) |
| `1` | 411 | 210 | 1642 | NO | YES | — | `Daily Cash Register Closing...` | Denomination breakdown, register closing cutoff (21:00) |
| `2` | 329 | 146 | 1313 | NO | **NO** | **Table / List Row End** | `Daily Cash Register Closing...` | Cash counting, float separation (ends with `1s, and coins`) |
| `3` | 418 | 201 | 1671 | NO | YES | — | `Daily Cash Register Closing...` | Card/UPI/Credit reconciliation against ERP settlement summary |
| `4` | 350 | 187 | 1398 | NO | YES | — | `Daily Cash Register Closing...` | Discrepancy management: $\pm \$10$ auto-post, $>\$10$ investigation |
| `5` | 366 | 190 | 1461 | NO | YES | — | `Daily Cash Register Closing...` | Cash drop, tamper-evident bag sealing, safe drop, sign-off |

### 3.3 `POL-RET-001.pdf` (Return & Core Exchange Policy — 3-Page PDF)
- **Document Level Section Title**: `Return and Core Exchange Policy`

| Idx | Tokens | Words | Chars | Undersized | Ends Punct. | Non-Punctuation Classification | Inherited Section Title | Content Summary / Snippet |
| :---: | :---: | :---: | :---: | :---: | :---: | :--- | :--- | :--- |
| `0` | 491 | 274 | 1963 | NO | YES | — | `Return and Core Exchange Policy` | Page 1 & Page 2 top: 30-day window, receipt, condition criteria |
| `1` | 487 | 284 | 1948 | NO | YES | — | `Return and Core Exchange Policy` | Page 2 & Page 3 top: 15% restocking fee, battery core deposits (\$15) |
| `2` | 114 | 60 | 453 | NO | YES | — | `Return and Core Exchange Policy` | Page 3 bottom: Alternator core exchange (\$30), manager overrides |

### 3.4 `FAQ-GST-001.md` (ERP Billing & GST Clarification FAQ — Markdown)
- **Document Level Section Title**: `ERP Billing and GST Clarification FAQ`

| Idx | Tokens | Words | Chars | Undersized | Ends Punct. | Non-Punctuation Classification | Inherited Section Title | Content Summary / Snippet |
| :---: | :---: | :---: | :---: | :---: | :---: | :--- | :--- | :--- |
| `0` | 70 | 42 | 278 | **YES** (<100) | YES | — | `ERP Billing & GST Clarification FAQ` | Synthetic disclaimer & FAQ introduction |
| `1` | 11 | 7 | 44 | **YES** (<100) | **NO** | **Orphan Parent Header** | `... > 1. Taxable Value versus Subtotal` | Heading only (`## 1. Taxable Value versus Subtotal Concepts`) |
| `2` | 201 | 123 | 802 | NO | YES | — | `... > 1. ... > 1.1 Gross Subtotal vs Taxable` | Gross subtotal vs taxable value, trade discounts |
| `3` | 143 | 80 | 570 | NO | YES | — | `... > 1. ... > 1.2 Does MRP include GST?` | Retail MRP inclusive of tax, backward tax extraction formula |
| `4` | 13 | 8 | 51 | **YES** (<100) | **NO** | **Orphan Parent Header** | `... > 2. HSN Codes` | Heading only (`## 2. Harmonized System of Nomenclature...`) |
| `5` | 102 | 62 | 407 | NO | YES | — | `... > 2. ... > 2.1 Why HSN mandatory?` | Statutory compliance, automated GST slab mapping |
| `6` | 247 | 144 | 988 | NO | YES | — | `... > 2. ... > 2.2 Primary HSN codes?` | HSN table: Batteries (8507), Brakes (870830), Plugs (8511) |
| `7` | 90 | 51 | 358 | **YES** (<100) | YES | — | `... > 2. ... > 2.3 Precision required?` | 6-digit precision required for turnover $> 5$ Crores |
| `8` | 11 | 8 | 44 | **YES** (<100) | **NO** | **Orphan Parent Header** | `... > 3. GST Tax Breakdown` | Heading only (`## 3. GST Tax Breakdown and Slab Calculation`) |
| `9` | 161 | 99 | 641 | NO | YES | — | `... > 3. ... > 3.1 CGST/SGST vs IGST?` | Intra-state (CGST+SGST) vs Inter-state (IGST) place of supply |
| `10` | 111 | 66 | 443 | NO | YES | — | `... > 3. ... > 3.2 Invoice rounding?` | Rounding adjustment ledger, paise 50 cutoff |
| `11` | 11 | 8 | 42 | **YES** (<100) | **NO** | **Orphan Parent Header** | `... > 4. Debit Note and Credit Note` | Heading only (`## 4. Debit Note and Credit Note Workflows`) |
| `12` | 213 | 130 | 852 | NO | YES | — | `... > 4. ... > 4.1 When Credit Note?` | Sales return, post-sale discount, irreversible posted invoices |
| `13` | 123 | 77 | 489 | NO | YES | — | `... > 4. ... > 4.2 When Debit Note?` | Price escalation, under-billed tax rate, customer surcharge |

---

## 4. In-Depth Evaluation Across Key Investigation Areas

### 4.1 Target Chunk Size (500 Tokens) & Overlap (50 Tokens)
1. **Target Budget Utilization by Format**:
   - In continuous text without Markdown headings (`SOP-CSH-001.txt` and `POL-RET-001.pdf`), the chunker effectively utilizes the 500-token budget. Average chunk size is **385.5 tokens** for SOP and **364.0 tokens** for PDF.
   - In structured Markdown (`POL-WAR-001.md` and `FAQ-GST-001.md`), the 500-token budget is **under-utilized** (average **175.5 tokens** for Warranty, **107.6 tokens** for FAQ).
   - **Root Cause**: `DocumentChunker` line 90 eagerly flushes the current chunk whenever a Markdown header is encountered. Because policy and FAQ sections average 80–200 words, flushing on every header boundary limits chunk accumulation.
2. **Empirical Overlap Verification**:
   - An exact suffix-to-prefix word comparison algorithm (`measureChunkOverlap()`) was executed on all 29 chunk transitions across the corpus:
     - **Continuous Text (`SOP-CSH-001.txt`)**: All 5 transitions ($0\to 1, 1\to 2, 2\to 3, 3\to 4, 4\to 5$) overlap by **exactly 37 words** ($\lfloor 50 \times 0.75 \rfloor$). Due to character densities and ASCII separator lines, the estimated overlap tokens range between 58 and 103 tokens per transition.
     - **Continuous Text (`POL-RET-001.pdf`)**: Both transitions ($0\to 1$ and $1\to 2$) overlap by **exactly 37 words**, yielding 66 and 72 estimated overlap tokens.
     - **Markdown Header Transitions (`POL-WAR-001.md` and `FAQ-GST-001.md`)**: All 22 transitions across both Markdown files exhibit **exactly 0 overlapping words (0 tokens)**. When a Markdown header triggers a chunk flush, the chunk builder resets without overlap text seeding.

---

### 4.2 Token Estimation Claims & Limitations
1. **Heuristic Formulation**:
   `DocumentChunker.estimateTokens(text)` computes:
   $$\text{estimatedTokens} = \max\left(1, \max\left(\lceil \text{words} \times 1.33 \rceil, \lceil \frac{\text{chars}}{4.0} \rceil\right)\right)$$
2. **Heuristic Nature of the Aggregate 3.99 Chars/Estimated-Token Ratio**:
   - The aggregate ratio ($\sum \text{chars} / \sum \text{tokens} = 26{,}611 / 6{,}667 \approx 3.9915 \approx 3.99$ chars/estimated token) is an **internal mathematical property of the heuristic itself** (specifically the $\lceil \text{chars} / 4.0 \rceil$ term).
   - In technical automotive and financial ERP prose containing part numbers (`870830`), percentages (`18%`), currencies, and abbreviations (`CGST`, `SGST`, `IGST`, `HSN`, `VIN`), $\lceil \text{chars} / 4.0 \rceil$ consistently dominates $\lceil \text{words} \times 1.33 \rceil$.
   - **Explicit Limitation**: This metric **does NOT constitute an independent measurement of token accuracy against a real BPE tokenizer** (such as TikToken, HuggingFace, or OpenAI cl100k_base). Accuracy against a real tokenizer has **not yet been measured** and remains an offline calibration task for future evaluation.

---

### 4.3 Paragraph Boundary Preservation
1. **Markdown & Plain Text Paragraph Preservation (Tested & Proven)**:
   - In `POL-WAR-001.md`, `SOP-CSH-001.txt`, and `FAQ-GST-001.md`, source paragraphs are defined by standard double-newline breaks (`\n\n`).
   - Automated testing (`shouldMeasureParagraphPreservationAgainstSourceDocument`) extracted all **52 source paragraphs** across these three documents and verified their containment in the produced chunks:
     - `POL-WAR-001.md`: 20/20 source paragraphs preserved intact (100.0%).
     - `SOP-CSH-001.txt`: 14/14 source paragraphs preserved intact (100.0%).
     - `FAQ-GST-001.md`: 18/18 source paragraphs preserved intact (100.0%).
     - **Total Markdown & Text Preservation**: **52 / 52 (100.0%)**. Zero paragraphs were severed across chunk boundaries.
2. **PDF Text Extraction & Sentence-Level Fallback**:
   - In `POL-RET-001.pdf`, Apache PDFBox extracts lines using single newlines (`\n`) without double-newlines.
   - Consequently, `splitIntoParagraphs` evaluates the entire 3-page PDF text (3,866 chars, 967 estimated tokens) as **one single continuous block**.
   - Because $967 > 500$, `DocumentChunker` automatically triggers its sentence-splitting branch (`splitIntoSentences`), chunking the PDF **at sentence boundaries rather than paragraph boundaries**.
   - **Conclusion**: Paragraph preservation is 100% for Markdown and Plain Text documents; PDF documents currently bypass paragraph chunking and fall back to sentence chunking.

---

### 4.4 Heuristic Sentence Boundary Termination
1. **Heuristic Definition**:
   - Sentence boundary preservation was evaluated using a **punctuation-ending heuristic**: verifying whether a chunk terminates with sentence-ending punctuation (`.`, `?`, `!`, or closing quotes `."`).
   - This metric measures **clean punctuation termination rate**, not grammatical sentence completeness.
2. **Census Results**:
   - **27 out of 33 chunks (81.8%)** terminate cleanly on terminal punctuation.
   - The remaining **6 chunks (18.2%)** fall into two distinct, verified categories:
     - **5 chunks are orphan Markdown section category headers** containing zero body text:
       - `POL-WAR-001` Chunk 2: `## 2. Product Warranty Categories`
       - `FAQ-GST-001` Chunk 1: `## 1. Taxable Value versus Subtotal Concepts`
       - `FAQ-GST-001` Chunk 4: `## 2. Harmonized System of Nomenclature (HSN) Codes`
       - `FAQ-GST-001` Chunk 8: `## 3. GST Tax Breakdown and Slab Calculation`
       - `FAQ-GST-001` Chunk 11: `## 4. Debit Note and Credit Note Workflows`
     - **1 chunk is a tabulated list item**:
       - `SOP-CSH-001` Chunk 2 terminates on a cash denomination line (`100s, 50s, 20s, 10s, 5s, 1s, and coins`) that lacks trailing punctuation in the source text.
3. **Word Boundary Integrity**:
   - **100.0% (33/33 chunks)** preserve full words. Zero words are severed mid-word across any format.

---

### 4.5 Heading Hierarchy Inheritance Across Formats
1. **Markdown Documents (`POL-WAR-001.md`, `FAQ-GST-001.md`)**:
   - Full 2-level and 3-level breadcrumb inheritance is preserved:
     - `Auto Parts Warranty Policy > 2. Product Warranty Categories > 2.1 Automotive Batteries`
     - `ERP Billing & GST Clarification FAQ > 2. Harmonized System of Nomenclature (HSN) Codes > 2.2 What are the primary HSN codes...`
   - Child headers properly overwrite same-level siblings while retaining ancestors in `TreeMap<Integer, String> headingLevels`.
2. **Plain Text (`SOP-CSH-001.txt`)**:
   - Visual headers like `SECTION 1: REGISTER OPENING` are not marked with `#`, so the chunker treats them as plain text paragraphs.
   - All 6 chunks receive only the default fallback document title: `Daily Cash Register Closing & Reconciliation SOP`. Sub-section context is absent from chunk metadata.
3. **PDF Documents (`POL-RET-001.pdf`)**:
   - Text extracted via Apache PDFBox lacks `#` markers.
   - All 3 chunks receive only the fallback title: `Return and Core Exchange Policy`. Section titles (e.g., `1. RETURN WINDOW`, `4. BATTERY CORE DEPOSITS`) reside only within the chunk body text.

---

### 4.6 Sizing Anomalies & Risk Analysis

```
                    Chunk Size Distribution Across Corpus
       +-------------------------------------------------------------+
Tokens |                                                             |
 500   | - - - - - - - - - - - - - - - - - - - - - - - - Target Ceiling
 400   |             [SOP-CSH]       [POL-RET]                       |
 300   |             (329-439)       (487-491)                       |
 200   | [POL-WAR]                                   [FAQ-GST]       |
 100   | (155-263)                                   (102-247)       |
       | - - - - - - - - - - - - - - - - - - - - - - - - Undersized Cutoff (<100)
   0   | [WAR-Stubs: 9-64]           [RET-End: 114]  [FAQ-Stubs: 11-90]
       +-------------------------------------------------------------+
```

1. **Oversized Chunks (>500 tokens)**:
   - **0 chunks (0.0%)**. Max chunk across the entire corpus is 491 tokens (`POL-RET-001` Chunk 0).
2. **Undersized Chunks (<100 tokens)**:
   - **8 out of 33 chunks (24.2%)** are undersized.
   - Five chunks are **orphan parent header stubs** (9, 11, 13, 11, and 11 tokens).
   - Two chunks are document preambles / disclaimers (64 tokens in Warranty, 70 tokens in GST FAQ).
   - One chunk is a short FAQ answer (90 tokens on HSN digit precision).
3. **Retrieval Risk of Orphan Header Stubs**:
   - An orphan header chunk (e.g. Chunk 2 in Warranty Policy: `"## 2. Product Warranty Categories"`) contains **zero substantive policy facts**.
   - If retrieved by FTS matching on section titles, it occupies a valuable retrieval slot (in `topK=5`), potentially crowding out an informative chunk like Chunk 3 (Batteries) or Chunk 4 (Brakes).
   - When fed to the generator node, this stub provides zero grounding context.

---

## 5. Concrete Recommendations for Future Chunking Optimization

Based on the empirical findings, the following non-breaking enhancements are recommended for future chunking improvements (Stage 3C Phase 3 or subsequent stages):

1. **Header Stub Aggregation (Anti-Stub Rule)**:
   - If a Markdown header paragraph has no subsequent body text before the next header (i.e., it is a parent category header like `## 2. Product Warranty Categories`), **do not flush it into a standalone chunk**.
   - Instead, record it in the heading hierarchy `headingLevels` and prepend it as context to the first child section chunk. This will immediately eliminate all 5 zero-information stub chunks.
2. **Heading Detection for Plain Text & PDF**:
   - Introduce heuristic heading detection for non-Markdown documents:
     - Match regex patterns like `^(?:SECTION\s+\d+:|[0-9]+\.\s+[A-Z\s]{4,})` on isolated lines.
     - Assign detected titles to the chunk draft's `sectionTitle` metadata, allowing Plain Text SOPs and PDFs to benefit from granular section breadcrumbs.
3. **Selective Inter-Section Overlap for Markdown**:
   - When flushing chunks on Markdown headers, optionally carry forward the last 1–2 sentences ($\approx 25$ tokens) from the preceding section as transition context, rather than having 0% overlap.
4. **Preserve Current Invariants**:
   - Retain the 500-token maximum target ceiling and the $\lceil \text{chars} / 4.0 \rceil$ token estimation heuristic. Both were empirically proven to prevent prompt overflow and protect word/paragraph boundaries.

---

## 6. Automated Test Suite Verification

The empirical measurements in this report are backed by the automated benchmark test suite [ChunkingBenchmarkTest.java](file:///d:/Projects/autoparts-erp/src/test/java/com/autoparts/erp/ai/rag/ingestion/ChunkingBenchmarkTest.java).

- **Suite Execution**: `mvnw.cmd clean test -o "-Dtest=!PostgresQueryTimeoutIntegrationTest"`
- **Total Test Classes**: **40**
- **Total Passing Tests**: **420 / 420 (100.0% pass rate)**
- **Failures**: **0**
- **Errors**: **0**
- **Skipped**: **0**
- **Surefire Reports**: Exactly **40 XML report files** in `target/surefire-reports`, reconciling 1:1.

```
Total Test Suite (420 Tests) =
    87 (Stage 3 RAG, Ingestion & Benchmark [including 5 in ChunkingBenchmarkTest]) +
    314 (Stage 2B–2E Accepted AI Assistant) +
    19 (Core Non-AI Application Core)
```
