package com.autoparts.erp.ai.rag.ingestion;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class KnowledgeDocumentIngestionTest {

    private PdfDocumentParser pdfParser;
    private TextDocumentParser textParser;
    private JdbcTemplate jdbcTemplate;
    private KnowledgeIngestionService ingestionService;

    @BeforeEach
    void setUp() {
        pdfParser = new PdfDocumentParser();
        textParser = new TextDocumentParser();
        jdbcTemplate = mock(JdbcTemplate.class);
        ingestionService = new KnowledgeIngestionService(
                jdbcTemplate,
                List.of(pdfParser, textParser),
                new DocumentChunker(500, 50),
                new ObjectMapper()
        );
    }

    private byte[] createSamplePdf(String text) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText(text);
                content.endText();
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    @Test
    @DisplayName("PDF parser extracts text and detects PDF magic bytes")
    void shouldExtractTextFromValidPdf() throws IOException {
        byte[] pdfBytes = createSamplePdf("Standard return policy allows 30 days for returns.");
        assertTrue(pdfParser.supports("PDF", pdfBytes));
        String text = pdfParser.extractText(pdfBytes);
        assertNotNull(text);
        assertTrue(text.contains("Standard return policy allows 30 days"));
    }

    @Test
    @DisplayName("PDF parser rejects missing %PDF- header magic bytes")
    void shouldRejectInvalidPdfMagicBytes() {
        byte[] fakePdf = "INVALID_PDF_HEADER_CONTENT".getBytes(StandardCharsets.UTF_8);
        assertFalse(pdfParser.supports("PDF", fakePdf));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> pdfParser.extractText(fakePdf));
        assertTrue(ex.getMessage().contains("missing %PDF- header magic bytes"));
    }

    @Test
    @DisplayName("IngestionRequest enforces 10 MB maximum file limit")
    void shouldRejectFilesExceeding10MbLimit() {
        byte[] largeBytes = new byte[10 * 1024 * 1024 + 1];
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new KnowledgeIngestionService.IngestionRequest(
                        "POL-RET-01",
                        "Return Policy",
                        "POLICY",
                        "PDF",
                        "PUBLIC",
                        "policy.pdf",
                        largeBytes,
                        Map.of(),
                        "admin"
                )
        );
        assertTrue(ex.getMessage().contains("exceeds 10 MB maximum limit"));
    }

    @Test
    @DisplayName("TextDocumentParser parses clean UTF-8 text and markdown")
    void shouldParseCleanUtf8TextAndMarkdown() throws IOException {
        String md = "# Return Policy\n\nCustomers may return parts within 30 days of invoice.";
        byte[] mdBytes = md.getBytes(StandardCharsets.UTF_8);

        assertTrue(textParser.supports("MARKDOWN", mdBytes));
        assertTrue(textParser.supports("TEXT", mdBytes));
        String extracted = textParser.extractText(mdBytes);
        assertEquals(md, extracted);
    }

    @Test
    @DisplayName("TextDocumentParser rejects binary files with null bytes")
    void shouldRejectBinaryFilesWithNullBytes() {
        byte[] binaryWithNull = new byte[]{'H', 'e', 'l', 'l', 'o', 0x00, 'W', 'o', 'r', 'l', 'd'};
        assertFalse(textParser.supports("TEXT", binaryWithNull));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> textParser.extractText(binaryWithNull));
        assertTrue(ex.getMessage().contains("non-UTF-8 bytes or binary null characters"));
    }

    @Test
    @DisplayName("KnowledgeIngestionService detects identical SHA-256 duplicate and returns idempotent result")
    void shouldDetectDuplicateDocumentBySha256() {
        String content = "Warranty period is 12 months for OEM parts.";
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        String expectedHash = DocumentChunker.computeSha256(bytes);
        UUID existingId = UUID.randomUUID();

        when(jdbcTemplate.queryForList(anyString(), eq("POL-WAR-01"))).thenReturn(List.of(
                Map.of(
                        "id", existingId,
                        "version", 1,
                        "file_hash_sha256", expectedHash
                )
        ));

        KnowledgeIngestionService.IngestionRequest request = new KnowledgeIngestionService.IngestionRequest(
                "POL-WAR-01",
                "Warranty Policy",
                "POLICY",
                "TEXT",
                "PUBLIC",
                "warranty.txt",
                bytes,
                Map.of("department", "Customer Service"),
                "admin"
        );

        KnowledgeIngestionService.IngestionResult result = ingestionService.ingestDocument(request);

        assertNotNull(result);
        assertTrue(result.isDuplicate());
        assertEquals(existingId, result.documentId());
        assertEquals(1, result.version());
        assertEquals("POL-WAR-01", result.documentCode());
        // Verify no insert occurred
        verify(jdbcTemplate, never()).update(startsWith("INSERT INTO knowledge_documents"), any(Object[].class));
    }

    @Test
    @DisplayName("Document metadata is normalized and preserved in request")
    void shouldNormalizeAndPreserveMetadata() {
        KnowledgeIngestionService.IngestionRequest request = new KnowledgeIngestionService.IngestionRequest(
                "  pol-ret-02  ",
                "  General Return Policy  ",
                " policy ",
                " text ",
                null, // should default to PUBLIC
                null,
                "Valid policy text".getBytes(StandardCharsets.UTF_8),
                Map.of("category", "Returns"),
                null
        );

        assertEquals("POL-RET-02", request.documentCode());
        assertEquals("General Return Policy", request.title());
        assertEquals("POLICY", request.documentType());
        assertEquals("TEXT", request.contentFormat());
        assertEquals("PUBLIC", request.requiredRole());
        assertEquals("document.text", request.fileName());
        assertEquals("SYSTEM", request.createdBy());
        assertEquals("Returns", request.metadata().get("category"));
    }

    @Test
    @DisplayName("IngestionRequest accepts FAQ as valid documentType")
    void shouldAcceptFaqDocumentType() {
        KnowledgeIngestionService.IngestionRequest request = new KnowledgeIngestionService.IngestionRequest(
                "FAQ-RET-01",
                "Customer Return FAQ",
                "FAQ",
                "TEXT",
                "PUBLIC",
                "faq.txt",
                "Q: How do returns work?\nA: Returns are processed in 30 days.".getBytes(StandardCharsets.UTF_8),
                Map.of(),
                "admin"
        );
        assertEquals("FAQ", request.documentType());
    }

    @Test
    @DisplayName("IngestionRequest rejects unsupported documentType")
    void shouldRejectUnsupportedDocumentType() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new KnowledgeIngestionService.IngestionRequest(
                        "POL-RET-03",
                        "Secret Policy",
                        "CONFIDENTIAL",
                        "TEXT",
                        "PUBLIC",
                        "policy.txt",
                        "Policy text".getBytes(StandardCharsets.UTF_8),
                        Map.of(),
                        "admin"
                )
        );
        assertTrue(ex.getMessage().contains("Unsupported documentType: CONFIDENTIAL"));
    }

    @Test
    @DisplayName("IngestionRequest rejects unsupported contentFormat")
    void shouldRejectUnsupportedContentFormat() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new KnowledgeIngestionService.IngestionRequest(
                        "POL-RET-04",
                        "Word Doc Policy",
                        "POLICY",
                        "DOCX",
                        "PUBLIC",
                        "policy.docx",
                        "Word text".getBytes(StandardCharsets.UTF_8),
                        Map.of(),
                        "admin"
                )
        );
        assertTrue(ex.getMessage().contains("Unsupported contentFormat: DOCX"));
    }

    @Test
    @DisplayName("IngestionRequest rejects extension mismatch with contentFormat")
    void shouldRejectExtensionMismatch() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new KnowledgeIngestionService.IngestionRequest(
                        "POL-RET-05",
                        "Policy Mismatch",
                        "POLICY",
                        "PDF",
                        "PUBLIC",
                        "policy.txt", // extension is .txt but format is PDF
                        "%PDF-1.4 dummy pdf bytes".getBytes(StandardCharsets.UTF_8),
                        Map.of(),
                        "admin"
                )
        );
        assertTrue(ex.getMessage().contains("does not match contentFormat"));
    }

    @Test
    @DisplayName("IngestionRequest rejects path traversal in fileName")
    void shouldRejectPathTraversalInFileName() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new KnowledgeIngestionService.IngestionRequest(
                        "POL-RET-06",
                        "Policy Traversal",
                        "POLICY",
                        "TEXT",
                        "PUBLIC",
                        "../../etc/passwd.txt",
                        "Valid text".getBytes(StandardCharsets.UTF_8),
                        Map.of(),
                        "admin"
                )
        );
        assertTrue(ex.getMessage().contains("path traversal"));
    }

    @Test
    @DisplayName("IngestionRequest rejects invalid documentCode characters")
    void shouldRejectInvalidDocumentCode() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new KnowledgeIngestionService.IngestionRequest(
                        "PO", // too short (< 3 chars)
                        "Short Code",
                        "POLICY",
                        "TEXT",
                        "PUBLIC",
                        "policy.txt",
                        "Valid text".getBytes(StandardCharsets.UTF_8),
                        Map.of(),
                        "admin"
                )
        );
        assertTrue(ex.getMessage().contains("documentCode must be 3-100"));
    }

    @Test
    @DisplayName("IngestionRequest rejects unsupported requiredRole")
    void shouldRejectUnsupportedRequiredRole() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new KnowledgeIngestionService.IngestionRequest(
                        "POL-RET-07",
                        "Role Policy",
                        "POLICY",
                        "TEXT",
                        "SUPERUSER", // not in VALID_ROLES
                        "policy.txt",
                        "Valid text".getBytes(StandardCharsets.UTF_8),
                        Map.of(),
                        "admin"
                )
        );
        assertTrue(ex.getMessage().contains("Unsupported requiredRole: SUPERUSER"));
    }

    @Test
    @DisplayName("ingestDocument throws IllegalArgumentException when metadata JSON serialization fails")
    void shouldThrowWhenMetadataSerializationFails() throws Exception {
        ObjectMapper mockMapper = mock(ObjectMapper.class);
        when(mockMapper.writeValueAsString(any())).thenThrow(new RuntimeException("Simulated JSON error"));

        KnowledgeIngestionService svc = new KnowledgeIngestionService(
                jdbcTemplate,
                List.of(textParser),
                new DocumentChunker(500, 50),
                mockMapper
        );

        when(jdbcTemplate.queryForList(anyString(), eq("POL-JSON-01"))).thenReturn(List.of());
        when(jdbcTemplate.queryForObject(contains("MAX(version)"), eq(Integer.class), eq("POL-JSON-01"))).thenReturn(0);

        KnowledgeIngestionService.IngestionRequest request = new KnowledgeIngestionService.IngestionRequest(
                "POL-JSON-01",
                "JSON Fail Policy",
                "POLICY",
                "TEXT",
                "PUBLIC",
                "policy.txt",
                "Valid policy text.".getBytes(StandardCharsets.UTF_8),
                Map.of("key", "value"),
                "admin"
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> svc.ingestDocument(request));
        assertTrue(ex.getMessage().contains("Failed to serialize document metadata to JSON"));
    }
}
