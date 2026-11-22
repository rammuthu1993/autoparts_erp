package com.autoparts.erp.ai.rag.ingestion;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

import com.autoparts.erp.common.exception.ClientValidationException;

/**
 * Text and Markdown document parser.
 *
 * <p>Validates strict UTF-8 encoding and verifies absence of binary control characters (e.g. null bytes).</p>
 */
@Component
public class TextDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String contentFormat, byte[] content) {
        if (contentFormat == null) {
            return false;
        }
        String format = contentFormat.trim().toUpperCase();
        if (!format.equals("TEXT") && !format.equals("MARKDOWN")) {
            return false;
        }
        return isCleanUtf8(content);
    }

    public static boolean isCleanUtf8(byte[] content) {
        if (content == null || content.length == 0) {
            return false;
        }
        // Reject binary files with null bytes
        for (byte b : content) {
            if (b == 0x00) {
                return false;
            }
        }
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPORT);
        decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(ByteBuffer.wrap(content));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    @Override
    public String extractText(byte[] content) throws IOException {
        if (content == null || content.length == 0) {
            throw new ClientValidationException("Document content cannot be empty");
        }
        if (!isCleanUtf8(content)) {
            throw new ClientValidationException("Invalid text document: content contains non-UTF-8 bytes or binary null characters");
        }
        return new String(content, StandardCharsets.UTF_8);
    }
}
