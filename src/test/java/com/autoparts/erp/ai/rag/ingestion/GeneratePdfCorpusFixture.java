package com.autoparts.erp.ai.rag.ingestion;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Utility to generate the synthetic multi-page PDF fixture for POL-RET-001.
 */
public final class GeneratePdfCorpusFixture {

    private GeneratePdfCorpusFixture() {}

    public static byte[] createPdfBytes() throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDFont titleFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDFont headingFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDFont bodyFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont italicFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

            // PAGE 1: Overview, Window, and Condition
            PDPage page1 = new PDPage(PDRectangle.A4);
            document.addPage(page1);
            try (PDPageContentStream cs = new PDPageContentStream(document, page1)) {
                float y = 780;
                y = writeLine(cs, titleFont, 16, 50, y, "Return & Core Exchange Policy");
                y = writeLine(cs, bodyFont, 10, 50, y - 5, "Document Code: POL-RET-001 | Version: 1 | Classification: PUBLIC");
                y = writeLine(cs, italicFont, 9, 50, y - 10, "SYNTHETIC TEST FIXTURE: This document is a synthetic test fixture created");
                y = writeLine(cs, italicFont, 9, 50, y, "exclusively for automated RAG evaluation. It does not constitute official company return terms.");

                y -= 25;
                y = writeLine(cs, headingFont, 12, 50, y, "Section 1: Standard Return Window and Eligibility");
                y = writeLines(cs, bodyFont, 10, 50, y - 5, List.of(
                        "Customers may return eligible automotive replacement parts within 30 calendar days",
                        "from the date of purchase indicated on the original sales invoice.",
                        "All returns require presentation of the original computer-generated tax invoice (INV).",
                        "Returns requested between 31 and 60 days require branch manager authorization and are",
                        "eligible exclusively for store credit or product exchange. Returns beyond 60 days are strictly",
                        "refused across all retail branches and commercial trade accounts."
                ));

                y -= 25;
                y = writeLine(cs, headingFont, 12, 50, y, "Section 2: Product Condition and Packaging Requirements");
                y = writeLines(cs, bodyFont, 10, 50, y - 5, List.of(
                        "Returned components must be in brand-new, uninstalled, and resalable condition.",
                        "Parts showing tool marks, grease, stripped bolt threads, or fluid contamination will be rejected.",
                        "All original manufacturer packaging, including branded boxes, internal plastic wrap, and protective",
                        "caps must be intact with legible, undamaged product barcodes.",
                        "Electrical sensor units with broken tamper-evident seals cannot be returned once opened."
                ));
            }

            // PAGE 2: Restocking Fees and Battery Core Deposits
            PDPage page2 = new PDPage(PDRectangle.A4);
            document.addPage(page2);
            try (PDPageContentStream cs = new PDPageContentStream(document, page2)) {
                float y = 780;
                y = writeLine(cs, titleFont, 14, 50, y, "Return & Core Exchange Policy (Page 2)");
                y = writeLine(cs, italicFont, 9, 50, y - 5, "SYNTHETIC TEST FIXTURE: Created exclusively for automated RAG testing.");

                y -= 25;
                y = writeLine(cs, headingFont, 12, 50, y, "Section 3: Restocking Fees and Exceptions");
                y = writeLines(cs, bodyFont, 10, 50, y - 5, List.of(
                        "Standard in-stock merchandise returned in unopened original packaging within 30 days is",
                        "subject to a zero percent (0%) restocking fee with full refund to original payment method.",
                        "Non-stocked special order items procured from regional automotive distribution centers incur",
                        "a mandatory fifteen percent (15%) restocking fee to cover reverse freight and processing.",
                        "Restocking fees are entirely waived if the return is necessitated by a verifiable branch cataloging",
                        "error, incorrect part dispatch, or warehouse fulfillment mistake."
                ));

                y -= 25;
                y = writeLine(cs, headingFont, 12, 50, y, "Section 4: Battery Core Deposits and Returns");
                y = writeLines(cs, bodyFont, 10, 50, y - 5, List.of(
                        "In compliance with environmental hazardous waste handling standards, a statutory core deposit",
                        "of exactly 750 currency units is added to the sale of every automotive lead-acid and AGM battery.",
                        "Customers may redeem the full 750-unit core deposit by surrendering a spent automotive battery",
                        "within 45 calendar days of the original purchase invoice date.",
                        "The returned spent battery must have an intact outer casing without structural cracks, acid leaks,",
                        "or melted terminal posts. Motorcycle and commercial truck batteries cannot be exchanged for",
                        "passenger automotive battery core deposits."
                ));
            }

            // PAGE 3: Alternator Core Exchange and Non-Returnable Items
            PDPage page3 = new PDPage(PDRectangle.A4);
            document.addPage(page3);
            try (PDPageContentStream cs = new PDPageContentStream(document, page3)) {
                float y = 780;
                y = writeLine(cs, titleFont, 14, 50, y, "Return & Core Exchange Policy (Page 3)");
                y = writeLine(cs, italicFont, 9, 50, y - 5, "SYNTHETIC TEST FIXTURE: Created exclusively for automated RAG testing.");

                y -= 25;
                y = writeLine(cs, headingFont, 12, 50, y, "Section 5: Alternator and Starter Core Exchange");
                y = writeLines(cs, bodyFont, 10, 50, y - 5, List.of(
                        "All remanufactured alternators and starter motors include a mandatory core charge of 1,200",
                        "currency units applied at the point of sale.",
                        "To qualify for a core refund, the customer must return the replaced unit within 30 calendar days.",
                        "Core Acceptance Criteria:",
                        "1. The old core must be like-for-like, matching the exact ampere rating and frame family.",
                        "2. Cast aluminum end-housings, mounting brackets, and drive pulleys must be free of cracks.",
                        "3. Disassembled units, cores with stripped terminal studs, or units with burned copper windings",
                        "   resulting from prolonged stall conditions will be rejected with zero refund."
                ));

                y -= 25;
                y = writeLine(cs, headingFont, 12, 50, y, "Section 6: Non-Returnable Items");
                y = writeLines(cs, bodyFont, 10, 50, y - 5, List.of(
                        "The following automotive categories are strictly non-returnable and non-refundable once sold:",
                        "- Electronic control modules (ECUs, powertrain controllers) once seal is broken.",
                        "- Custom cut-to-length hoses, vacuum lines, and electrical wiring looms.",
                        "- Chemical fluids, engine oils, gear lubes, coolant concentrates, and aerosol cleaners.",
                        "- Special tools, diagnostic scanners, and clearance items marked Final Sale."
                ));
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        }
    }

    private static float writeLine(PDPageContentStream cs, PDFont font, float fontSize, float x, float y, String text) throws IOException {
        cs.beginText();
        cs.setFont(font, fontSize);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
        return y - (fontSize + 4);
    }

    private static float writeLines(PDPageContentStream cs, PDFont font, float fontSize, float x, float startY, List<String> lines) throws IOException {
        float currentY = startY;
        for (String line : lines) {
            currentY = writeLine(cs, font, fontSize, x, currentY, line);
        }
        return currentY;
    }

    public static void main(String[] args) throws IOException {
        Path target = Path.of("src/test/resources/knowledge/POL-RET-001.pdf");
        Files.createDirectories(target.getParent());
        byte[] bytes = createPdfBytes();
        Files.write(target, bytes);
        System.out.println("Generated PDF fixture: " + target + " (" + bytes.length + " bytes)");
    }
}
