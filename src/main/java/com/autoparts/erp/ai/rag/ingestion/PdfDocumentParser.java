package com.autoparts.erp.ai.rag.ingestion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import com.autoparts.erp.common.exception.ClientValidationException;

/**
 * PDF parser implementation utilizing Apache PDFBox 3.x.
 *
 * <p>Validates PDF magic bytes (%PDF-) and extracts text in document reading order.</p>
 */
@Component
public class PdfDocumentParser implements DocumentParser {

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    @Override
    public boolean supports(String contentFormat, byte[] content) {
        if (contentFormat == null || !contentFormat.equalsIgnoreCase("PDF")) {
            return false;
        }
        return hasPdfMagicBytes(content);
    }

    public static boolean hasPdfMagicBytes(byte[] content) {
        if (content == null || content.length < PDF_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            if (content[i] != PDF_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String extractText(byte[] content) throws IOException {
        if (!hasPdfMagicBytes(content)) {
            throw new ClientValidationException("Invalid PDF document: missing %PDF- header magic bytes");
        }

        try (PDDocument document = Loader.loadPDF(content)) {
            if (document.isEncrypted()) {
                throw new ClientValidationException("Encrypted PDF documents are not supported for knowledge ingestion");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }
}
