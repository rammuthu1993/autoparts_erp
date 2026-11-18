package com.autoparts.erp.ai.rag.ingestion;

import java.io.IOException;

/**
 * Strategy interface for parsing supported document file types into plain text.
 */
public interface DocumentParser {

    /**
     * Checks if this parser supports the given format and content bytes.
     */
    boolean supports(String contentFormat, byte[] content);

    /**
     * Extracts text content from document raw bytes.
     *
     * @param content raw document byte array
     * @return extracted plain text
     * @throws IOException on parsing or format error
     */
    String extractText(byte[] content) throws IOException;
}
