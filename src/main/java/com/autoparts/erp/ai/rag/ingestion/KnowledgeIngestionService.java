package com.autoparts.erp.ai.rag.ingestion;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import tools.jackson.databind.ObjectMapper;

/**
 * Transactional ingestion service for authoritative ERP knowledge documents.
 *
 * <p>Validates file limits and magic byte signatures, handles SHA-256 deduplication,
 * extracts text via format-specific parsers, generates boundary-aware chunks, and
 * manages atomic version transitions with PostgreSQL concurrency protection.</p>
 */
@Service
public class KnowledgeIngestionService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestionService.class);
    public static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB

    public static final Set<String> VALID_DOCUMENT_TYPES = Set.of(
            "POLICY", "SOP", "MANUAL", "GUIDELINE", "HANDBOOK", "SPECIFICATION", "FAQ"
    );
    public static final Set<String> VALID_CONTENT_FORMATS = Set.of(
            "PDF", "TEXT", "MARKDOWN"
    );
    public static final Set<String> VALID_ROLES = Set.of(
            "ROLE_ADMIN", "ROLE_MANAGER", "ROLE_CASHIER", "PUBLIC"
    );
    private static final Pattern DOCUMENT_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{3,100}$");

    private final JdbcTemplate jdbcTemplate;
    private final List<DocumentParser> parsers;
    private final DocumentChunker chunker;
    private final ObjectMapper objectMapper;

    @Autowired
    public KnowledgeIngestionService(
            JdbcTemplate jdbcTemplate,
            List<DocumentParser> parsers,
            ObjectMapper objectMapper
    ) {
        this(jdbcTemplate, parsers, new DocumentChunker(500, 50), objectMapper);
    }

    public KnowledgeIngestionService(
            JdbcTemplate jdbcTemplate,
            List<DocumentParser> parsers,
            DocumentChunker chunker,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.parsers = parsers != null ? parsers : List.of();
        this.chunker = chunker != null ? chunker : new DocumentChunker(500, 50);
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public record IngestionRequest(
            String documentCode,
            String title,
            String documentType,
            String contentFormat,
            String requiredRole,
            String fileName,
            byte[] fileBytes,
            Map<String, Object> metadata,
            String createdBy
    ) {
        public IngestionRequest {
            if (!StringUtils.hasText(documentCode)) {
                throw new IllegalArgumentException("documentCode is required");
            }
            documentCode = documentCode.trim().toUpperCase(Locale.ROOT);
            if (!DOCUMENT_CODE_PATTERN.matcher(documentCode).matches()) {
                throw new IllegalArgumentException("documentCode must be 3-100 alphanumeric, underscore, or hyphen characters: " + documentCode);
            }

            if (!StringUtils.hasText(title)) {
                throw new IllegalArgumentException("title is required");
            }
            title = title.trim();

            if (!StringUtils.hasText(documentType)) {
                throw new IllegalArgumentException("documentType is required");
            }
            documentType = documentType.trim().toUpperCase(Locale.ROOT);
            if (!VALID_DOCUMENT_TYPES.contains(documentType)) {
                throw new IllegalArgumentException("Unsupported documentType: " + documentType + ". Allowed: " + VALID_DOCUMENT_TYPES);
            }

            if (!StringUtils.hasText(contentFormat)) {
                throw new IllegalArgumentException("contentFormat is required");
            }
            contentFormat = contentFormat.trim().toUpperCase(Locale.ROOT);
            if (!VALID_CONTENT_FORMATS.contains(contentFormat)) {
                throw new IllegalArgumentException("Unsupported contentFormat: " + contentFormat + ". Allowed: " + VALID_CONTENT_FORMATS);
            }

            if (fileBytes == null || fileBytes.length == 0) {
                throw new IllegalArgumentException("fileBytes cannot be empty");
            }
            if (fileBytes.length > MAX_FILE_SIZE_BYTES) {
                throw new IllegalArgumentException("File size exceeds 10 MB maximum limit: " + fileBytes.length + " bytes");
            }

            // Magic bytes and content integrity
            if ("PDF".equals(contentFormat)) {
                if (!PdfDocumentParser.hasPdfMagicBytes(fileBytes)) {
                    throw new IllegalArgumentException("Invalid PDF document: missing %PDF- header magic bytes");
                }
            } else if ("TEXT".equals(contentFormat) || "MARKDOWN".equals(contentFormat)) {
                if (!TextDocumentParser.isCleanUtf8(fileBytes)) {
                    throw new IllegalArgumentException("Invalid text document: content contains non-UTF-8 bytes or binary null characters");
                }
            }

            requiredRole = StringUtils.hasText(requiredRole) ? requiredRole.trim().toUpperCase(Locale.ROOT) : "PUBLIC";
            if (!VALID_ROLES.contains(requiredRole)) {
                throw new IllegalArgumentException("Unsupported requiredRole: " + requiredRole + ". Allowed: " + VALID_ROLES);
            }

            if (StringUtils.hasText(fileName)) {
                fileName = fileName.trim();
                if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
                    throw new IllegalArgumentException("fileName must not contain path traversal characters: " + fileName);
                }
            } else {
                fileName = "document." + contentFormat.toLowerCase(Locale.ROOT);
            }

            // Validate file extension matches content format
            String lowerFileName = fileName.toLowerCase(Locale.ROOT);
            boolean validExtension = switch (contentFormat) {
                case "PDF" -> lowerFileName.endsWith(".pdf");
                case "TEXT" -> lowerFileName.endsWith(".txt") || lowerFileName.endsWith(".text");
                case "MARKDOWN" -> lowerFileName.endsWith(".md") || lowerFileName.endsWith(".markdown");
                default -> false;
            };
            if (!validExtension) {
                throw new IllegalArgumentException("File extension of '" + fileName + "' does not match contentFormat '" + contentFormat + "'");
            }

            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
            createdBy = StringUtils.hasText(createdBy) ? createdBy.trim() : "SYSTEM";
        }
    }

    public record IngestionResult(
            UUID documentId,
            String documentCode,
            int version,
            int chunkCount,
            boolean isDuplicate,
            String message
    ) {}

    /**
     * Atomically ingests a document. If an active version exists with the identical
     * SHA-256 hash, returns existing documentId without modifications (idempotent).
     * If content differs, deactivates prior version and creates version n+1.
     */
    @Transactional
    public IngestionResult ingestDocument(IngestionRequest request) {
        log.info("Starting knowledge document ingestion. Code: {}, Format: {}, Size: {} bytes",
                request.documentCode(), request.contentFormat(), request.fileBytes().length);

        // 1. Concurrency control: acquire transactional advisory lock on document code hash
        acquireDocumentLock(request.documentCode());

        // 2. Select appropriate parser and extract text
        DocumentParser parser = findParser(request.contentFormat(), request.fileBytes());
        String extractedText;
        try {
            extractedText = parser.extractText(request.fileBytes());
        } catch (IOException e) {
            log.error("Failed to extract text from document: {}", request.fileName(), e);
            throw new IllegalArgumentException("Document text extraction failed: " + e.getMessage(), e);
        }

        if (!StringUtils.hasText(extractedText)) {
            throw new IllegalArgumentException("Extracted document text is empty: " + request.fileName());
        }

        // 3. Compute raw file hash
        String fileHash = DocumentChunker.computeSha256(request.fileBytes());

        // 4. Check for existing active version with identical hash (idempotency check)
        List<Map<String, Object>> activeDocs = jdbcTemplate.queryForList(
                "SELECT id, version, file_hash_sha256 FROM knowledge_documents WHERE document_code = ? AND active = TRUE",
                request.documentCode()
        );

        if (!activeDocs.isEmpty()) {
            Map<String, Object> existing = activeDocs.getFirst();
            String existingHash = (String) existing.get("file_hash_sha256");
            int existingVersion = ((Number) existing.get("version")).intValue();
            UUID existingId = (UUID) existing.get("id");

            if (fileHash.equalsIgnoreCase(existingHash)) {
                log.info("Document code {} already ingested with identical SHA-256 hash at version {}. Skipping re-ingestion.",
                        request.documentCode(), existingVersion);
                return new IngestionResult(existingId, request.documentCode(), existingVersion, 0, true,
                        "Document already active with identical content hash at version " + existingVersion);
            }
        }

        // 5. Generate boundary-aware chunks
        List<DocumentChunker.ChunkDraft> chunkDrafts = chunker.chunk(extractedText, request.title());
        if (chunkDrafts.isEmpty()) {
            throw new IllegalArgumentException("Document produced 0 content chunks: " + request.fileName());
        }

        // 6. Calculate next version number
        Integer maxVersion = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(version), 0) FROM knowledge_documents WHERE document_code = ?",
                Integer.class,
                request.documentCode()
        );
        int nextVersion = (maxVersion != null ? maxVersion : 0) + 1;

        // 7. Deactivate prior version and its chunks atomically
        jdbcTemplate.update(
                "UPDATE knowledge_documents SET active = FALSE, updated_at = CURRENT_TIMESTAMP WHERE document_code = ? AND active = TRUE",
                request.documentCode()
        );
        jdbcTemplate.update(
                "UPDATE knowledge_chunks SET active = FALSE WHERE document_id IN (SELECT id FROM knowledge_documents WHERE document_code = ? AND active = FALSE)",
                request.documentCode()
        );

        // 8. Insert new active document record
        UUID newDocumentId = UUID.randomUUID();
        String metadataJson;
        try {
            metadataJson = objectMapper.writeValueAsString(request.metadata());
        } catch (Exception e) {
            log.error("Failed to serialize metadata for document: {}", request.documentCode(), e);
            throw new IllegalArgumentException("Failed to serialize document metadata to JSON: " + e.getMessage(), e);
        }

        jdbcTemplate.update(
                """
                INSERT INTO knowledge_documents (
                    id, document_code, title, document_type, content_format, version,
                    required_role, active, file_name, file_hash_sha256, file_size_bytes,
                    metadata, created_by, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?, ?, ?::jsonb, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                newDocumentId,
                request.documentCode(),
                request.title(),
                request.documentType(),
                request.contentFormat(),
                nextVersion,
                request.requiredRole(),
                request.fileName(),
                fileHash,
                request.fileBytes().length,
                metadataJson,
                request.createdBy()
        );

        // 9. Batch insert passage chunks
        String chunkInsertSql = """
                INSERT INTO knowledge_chunks (
                    document_id, chunk_index, section_title, chunk_text, token_count,
                    chunk_hash_sha256, active, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, TRUE, CURRENT_TIMESTAMP)
                """;

        List<Object[]> batchArgs = chunkDrafts.stream()
                .map(draft -> new Object[] {
                        newDocumentId,
                        draft.chunkIndex(),
                        draft.sectionTitle(),
                        draft.text(),
                        draft.estimatedTokens(),
                        draft.sha256Hex()
                })
                .toList();

        jdbcTemplate.batchUpdate(chunkInsertSql, batchArgs);

        log.info("Document successfully ingested. ID: {}, Code: {}, Version: {}, Chunks: {}",
                newDocumentId, request.documentCode(), nextVersion, chunkDrafts.size());

        return new IngestionResult(newDocumentId, request.documentCode(), nextVersion, chunkDrafts.size(), false,
                "Document successfully ingested at version " + nextVersion);
    }

    private DocumentParser findParser(String format, byte[] content) {
        for (DocumentParser parser : parsers) {
            if (parser.supports(format, content)) {
                return parser;
            }
        }
        throw new IllegalArgumentException("No parser registered capable of processing format '" + format + "'");
    }

    private void acquireDocumentLock(String documentCode) {
        try {
            jdbcTemplate.execute(
                    "SELECT pg_advisory_xact_lock(hashtext(?)::bigint)",
                    (PreparedStatementCallback<Void>) ps -> {
                        ps.setString(1, documentCode);
                        ps.execute();
                        return null;
                    }
            );
        } catch (DataAccessException e) {
            log.error("Failed to acquire advisory lock for document: {}", documentCode, e);
            throw new IllegalStateException("Failed to acquire advisory lock for documentCode: " + documentCode, e);
        }
    }
}
