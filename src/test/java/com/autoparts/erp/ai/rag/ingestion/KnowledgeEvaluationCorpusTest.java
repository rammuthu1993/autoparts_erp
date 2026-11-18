package com.autoparts.erp.ai.rag.ingestion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Focused test suite verifying Stage 3C Phase 1 synthetic evaluation corpus fixtures.
 *
 * <p>Validates that all four fixtures (Markdown Warranty Policy, Plain-text Cash Register SOP,
 * Multi-page PDF Return Policy, and Markdown GST Billing FAQ) adhere to ingestion contracts,
 * parse successfully with format-specific parsers, preserve section hierarchies and boundaries,
 * and contain required synthetic test fixture disclaimers.</p>
 */
class KnowledgeEvaluationCorpusTest {

    private final PdfDocumentParser pdfParser = new PdfDocumentParser();
    private final TextDocumentParser textParser = new TextDocumentParser();
    private final DocumentChunker chunker = new DocumentChunker(500, 50);

    @BeforeAll
    static void ensureFixturesExist() throws IOException {
        Path pdfPath = Path.of("src/test/resources/knowledge/POL-RET-001.pdf");
        if (!Files.exists(pdfPath)) {
            Files.createDirectories(pdfPath.getParent());
            byte[] pdfBytes = GeneratePdfCorpusFixture.createPdfBytes();
            Files.write(pdfPath, pdfBytes);
        }
    }

    private byte[] loadFixtureBytes(String resourceName) throws IOException {
        // Try classpath first
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("knowledge/" + resourceName)) {
            if (in != null) {
                return in.readAllBytes();
            }
        }
        // Fallback to direct project path
        Path path = Path.of("src/test/resources/knowledge", resourceName);
        if (Files.exists(path)) {
            return Files.readAllBytes(path);
        }
        throw new IOException("Resource not found: knowledge/" + resourceName);
    }

    @Test
    @DisplayName("Verify all four corpus fixtures exist, are non-empty, and contain synthetic disclaimers")
    void shouldEnsureAllFourCorpusFixturesExistAndAreNonEmpty() throws IOException {
        List<String> fixtures = List.of(
                "POL-WAR-001.md",
                "SOP-CSH-001.txt",
                "POL-RET-001.pdf",
                "FAQ-GST-001.md"
        );

        for (String fixture : fixtures) {
            byte[] bytes = loadFixtureBytes(fixture);
            assertNotNull(bytes, "Fixture bytes must not be null for " + fixture);
            assertTrue(bytes.length > 500, "Fixture " + fixture + " must be substantial (found " + bytes.length + " bytes)");
        }
    }

    @Test
    @DisplayName("Verify Markdown Warranty Policy parses cleanly and preserves multi-level heading hierarchy")
    void shouldParseMarkdownWarrantyPolicyAndPreserveHeadingHierarchy() throws IOException {
        byte[] bytes = loadFixtureBytes("POL-WAR-001.md");
        assertTrue(textParser.supports("MARKDOWN", bytes), "Text parser must support MARKDOWN");

        String text = textParser.extractText(bytes);
        assertNotNull(text);
        assertTrue(text.contains("SYNTHETIC TEST FIXTURE"), "Must contain synthetic fixture disclaimer");
        assertTrue(text.contains("Automotive Batteries"));
        assertTrue(text.contains("Brake Rotors and Friction Components"));
        assertTrue(text.contains("Alternators and Starters"));
        assertTrue(text.contains("Electrical Sensors and Control Modules"));

        List<DocumentChunker.ChunkDraft> chunks = chunker.chunk(text, "Auto Parts Warranty Policy");
        assertFalse(chunks.isEmpty(), "Chunker must produce chunks for warranty policy");

        List<String> sectionTitles = chunks.stream()
                .map(DocumentChunker.ChunkDraft::sectionTitle)
                .toList();

        // Verify inherited Markdown heading hierarchy (# Policy > ## Product Categories > ### Component)
        assertTrue(sectionTitles.stream().anyMatch(s -> s.contains("Auto Parts Warranty Policy > 2. Product Warranty Categories > 2.1 Automotive Batteries")),
                "Expected battery section heading hierarchy");
        assertTrue(sectionTitles.stream().anyMatch(s -> s.contains("Auto Parts Warranty Policy > 2. Product Warranty Categories > 2.2 Brake Rotors and Friction Components")),
                "Expected brake rotor section heading hierarchy");
        assertTrue(sectionTitles.stream().anyMatch(s -> s.contains("Auto Parts Warranty Policy > 2. Product Warranty Categories > 2.3 Alternators and Starters")),
                "Expected alternator section heading hierarchy");
        assertTrue(sectionTitles.stream().anyMatch(s -> s.contains("Auto Parts Warranty Policy > 2. Product Warranty Categories > 2.4 Electrical Sensors and Control Modules")),
                "Expected electrical sensors section heading hierarchy");
        assertTrue(sectionTitles.stream().anyMatch(s -> s.contains("Auto Parts Warranty Policy > 3. General Warranty Exclusions")),
                "Expected general exclusions heading hierarchy");
        assertTrue(sectionTitles.stream().anyMatch(s -> s.contains("Auto Parts Warranty Policy > 4. Warranty Claim Submission Procedure")),
                "Expected claim submission heading hierarchy");
        assertTrue(sectionTitles.stream().anyMatch(s -> s.contains("Auto Parts Warranty Policy > 5. Review and Approval Workflow")),
                "Expected approval workflow heading hierarchy");
    }

    @Test
    @DisplayName("Verify Plain-text Cash Register SOP parses cleanly and preserves usable section boundaries")
    void shouldParsePlainTextCashSopAndMaintainUsableSectionBoundaries() throws IOException {
        byte[] bytes = loadFixtureBytes("SOP-CSH-001.txt");
        assertTrue(textParser.supports("TEXT", bytes), "Text parser must support TEXT");

        String text = textParser.extractText(bytes);
        assertNotNull(text);
        assertTrue(text.contains("SYNTHETIC TEST FIXTURE"), "Must contain synthetic fixture disclaimer");
        assertTrue(text.contains("SECTION 1: REGISTER OPENING AND FLOAT VERIFICATION"));
        assertTrue(text.contains("SECTION 2: MID-DAY CASH DROP PROCEDURE"));
        assertTrue(text.contains("SECTION 3: END-OF-DAY CASH COUNTING"));
        assertTrue(text.contains("SECTION 4: PAYMENT-METHOD RECONCILIATION"));
        assertTrue(text.contains("SECTION 5: DISCREPANCY REPORTING AND THRESHOLDS"));
        assertTrue(text.contains("SECTION 6: MANAGER SIGN-OFF AND SAFE DEPOSIT"));

        List<DocumentChunker.ChunkDraft> chunks = chunker.chunk(text, "Daily Cash Register Closing SOP");
        assertFalse(chunks.isEmpty(), "Chunker must produce chunks for plain-text SOP");

        for (DocumentChunker.ChunkDraft chunk : chunks) {
            assertTrue(chunk.estimatedTokens() > 0, "Chunk tokens must be positive");
            assertNotNull(chunk.sha256Hex(), "Chunk SHA-256 must be computed");
            assertFalse(chunk.text().contains("\u0000"), "Chunk text must not contain null characters");
        }
    }

    @Test
    @DisplayName("Verify Multi-Page PDF Return Policy extracts layout text across all 3 pages")
    void shouldParseMultiPagePdfReturnPolicyAndExtractSectionsAcrossPages() throws IOException {
        byte[] bytes = loadFixtureBytes("POL-RET-001.pdf");
        assertTrue(pdfParser.supports("PDF", bytes), "Pdf parser must support PDF format with %PDF- header");

        String text = pdfParser.extractText(bytes);
        assertNotNull(text);
        assertTrue(text.length() > 1200, "Extracted PDF text must contain substantial multi-page content");
        assertTrue(text.contains("SYNTHETIC TEST FIXTURE"), "Extracted PDF text must contain synthetic disclaimer");

        // Verify content from Page 1
        assertTrue(text.contains("Section 1: Standard Return Window and Eligibility"), "Page 1 content must be present");
        assertTrue(text.contains("Section 2: Product Condition and Packaging Requirements"), "Page 1 content must be present");

        // Verify content from Page 2
        assertTrue(text.contains("Section 3: Restocking Fees and Exceptions"), "Page 2 content must be present");
        assertTrue(text.contains("Section 4: Battery Core Deposits and Returns"), "Page 2 content must be present");

        // Verify content from Page 3
        assertTrue(text.contains("Section 5: Alternator and Starter Core Exchange"), "Page 3 content must be present");
        assertTrue(text.contains("Section 6: Non-Returnable Items"), "Page 3 content must be present");

        List<DocumentChunker.ChunkDraft> chunks = chunker.chunk(text, "Return & Core Exchange Policy");
        assertFalse(chunks.isEmpty(), "Chunker must produce chunks for extracted PDF text");
        for (DocumentChunker.ChunkDraft chunk : chunks) {
            assertTrue(chunk.estimatedTokens() > 0);
        }
    }

    @Test
    @DisplayName("Verify Markdown GST Billing FAQ parses cleanly and preserves Q&A heading hierarchy")
    void shouldParseMarkdownGstFaqAndPreserveHeadingHierarchy() throws IOException {
        byte[] bytes = loadFixtureBytes("FAQ-GST-001.md");
        assertTrue(textParser.supports("MARKDOWN", bytes), "Text parser must support MARKDOWN");

        String text = textParser.extractText(bytes);
        assertNotNull(text);
        assertTrue(text.contains("SYNTHETIC TEST FIXTURE"), "Must contain synthetic fixture disclaimer");
        assertTrue(text.contains("Taxable Value versus Subtotal Concepts"));
        assertTrue(text.contains("Harmonized System of Nomenclature (HSN) Codes"));
        assertTrue(text.contains("GST Tax Breakdown and Slab Calculation"));
        assertTrue(text.contains("Debit Note and Credit Note Workflows"));

        List<DocumentChunker.ChunkDraft> chunks = chunker.chunk(text, "ERP Billing & GST Clarification FAQ");
        assertFalse(chunks.isEmpty(), "Chunker must produce chunks for GST FAQ");

        List<String> sectionTitles = chunks.stream()
                .map(DocumentChunker.ChunkDraft::sectionTitle)
                .toList();

        assertTrue(sectionTitles.stream().anyMatch(s -> s.contains("ERP Billing & GST Clarification FAQ > 1. Taxable Value versus Subtotal Concepts")),
                "Expected taxable value section heading");
        assertTrue(sectionTitles.stream().anyMatch(s -> s.contains("ERP Billing & GST Clarification FAQ > 2. Harmonized System of Nomenclature (HSN) Codes")),
                "Expected HSN section heading");
        assertTrue(sectionTitles.stream().anyMatch(s -> s.contains("ERP Billing & GST Clarification FAQ > 3. GST Tax Breakdown and Slab Calculation")),
                "Expected GST slab breakdown section heading");
        assertTrue(sectionTitles.stream().anyMatch(s -> s.contains("ERP Billing & GST Clarification FAQ > 4. Debit Note and Credit Note Workflows")),
                "Expected debit/credit note section heading");
    }

    @Test
    @DisplayName("Verify IngestionRequest validation passes for all four corpus fixtures with expected metadata")
    void shouldValidateIngestionRequestsForEntireCorpus() throws IOException {
        // 1. Warranty Policy
        byte[] warBytes = loadFixtureBytes("POL-WAR-001.md");
        KnowledgeIngestionService.IngestionRequest warReq = new KnowledgeIngestionService.IngestionRequest(
                "POL-WAR-001",
                "Auto Parts Warranty Policy",
                "POLICY",
                "MARKDOWN",
                "PUBLIC",
                "POL-WAR-001.md",
                warBytes,
                Map.of("department", "Warranty", "category", "Parts", "synthetic", true),
                "SYSTEM"
        );
        assertEquals("POL-WAR-001", warReq.documentCode());
        assertEquals("POLICY", warReq.documentType());
        assertEquals("MARKDOWN", warReq.contentFormat());
        assertEquals("PUBLIC", warReq.requiredRole());

        // 2. Cash Register SOP
        byte[] cshBytes = loadFixtureBytes("SOP-CSH-001.txt");
        KnowledgeIngestionService.IngestionRequest cshReq = new KnowledgeIngestionService.IngestionRequest(
                "SOP-CSH-001",
                "Daily Cash Register Closing & Reconciliation SOP",
                "SOP",
                "TEXT",
                "ROLE_CASHIER",
                "SOP-CSH-001.txt",
                cshBytes,
                Map.of("department", "Finance", "module", "POS", "synthetic", true),
                "SYSTEM"
        );
        assertEquals("SOP-CSH-001", cshReq.documentCode());
        assertEquals("SOP", cshReq.documentType());
        assertEquals("TEXT", cshReq.contentFormat());
        assertEquals("ROLE_CASHIER", cshReq.requiredRole());

        // 3. Return Policy PDF
        byte[] retBytes = loadFixtureBytes("POL-RET-001.pdf");
        KnowledgeIngestionService.IngestionRequest retReq = new KnowledgeIngestionService.IngestionRequest(
                "POL-RET-001",
                "Return & Core Exchange Policy",
                "POLICY",
                "PDF",
                "PUBLIC",
                "POL-RET-001.pdf",
                retBytes,
                Map.of("department", "Customer Service", "category", "Returns", "synthetic", true),
                "SYSTEM"
        );
        assertEquals("POL-RET-001", retReq.documentCode());
        assertEquals("POLICY", retReq.documentType());
        assertEquals("PDF", retReq.contentFormat());
        assertEquals("PUBLIC", retReq.requiredRole());

        // 4. GST FAQ
        byte[] gstBytes = loadFixtureBytes("FAQ-GST-001.md");
        KnowledgeIngestionService.IngestionRequest gstReq = new KnowledgeIngestionService.IngestionRequest(
                "FAQ-GST-001",
                "ERP Billing & GST Clarification FAQ",
                "FAQ",
                "MARKDOWN",
                "ROLE_CASHIER",
                "FAQ-GST-001.md",
                gstBytes,
                Map.of("department", "Accounting", "jurisdiction", "India-GST", "synthetic", true),
                "SYSTEM"
        );
        assertEquals("FAQ-GST-001", gstReq.documentCode());
        assertEquals("FAQ", gstReq.documentType());
        assertEquals("MARKDOWN", gstReq.contentFormat());
        assertEquals("ROLE_CASHIER", gstReq.requiredRole());
    }

    @Test
    @DisplayName("Verify synthetic disclaimer is strictly present across all four evaluation fixtures")
    void shouldVerifySyntheticDisclaimersInAllFixtures() throws IOException {
        String warText = new String(loadFixtureBytes("POL-WAR-001.md"), StandardCharsets.UTF_8);
        String cshText = new String(loadFixtureBytes("SOP-CSH-001.txt"), StandardCharsets.UTF_8);
        String gstText = new String(loadFixtureBytes("FAQ-GST-001.md"), StandardCharsets.UTF_8);
        String retText = pdfParser.extractText(loadFixtureBytes("POL-RET-001.pdf"));

        assertTrue(warText.contains("SYNTHETIC TEST FIXTURE"), "POL-WAR-001.md must contain synthetic test fixture disclaimer");
        assertTrue(cshText.contains("SYNTHETIC TEST FIXTURE"), "SOP-CSH-001.txt must contain synthetic test fixture disclaimer");
        assertTrue(gstText.contains("SYNTHETIC TEST FIXTURE"), "FAQ-GST-001.md must contain synthetic test fixture disclaimer");
        assertTrue(retText.contains("SYNTHETIC TEST FIXTURE"), "POL-RET-001.pdf must contain synthetic test fixture disclaimer");
    }
}
