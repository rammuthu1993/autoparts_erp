package com.autoparts.erp.ai.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.autoparts.erp.ai.model.AiEntityReference;

/**
 * Extracts business entity references, identifiers, and parameters from natural-language queries.
 */
@Service
public class AiEntityExtractionService {

    private static final Pattern PRODUCT_IDENTIFIER = Pattern.compile(
            "\\b(?=[A-Z0-9-]*[A-Z])(?=[A-Z0-9-]*\\d)[A-Z0-9]+(?:-[A-Z0-9]+)*\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PURCHASE_IDENTIFIER = Pattern.compile(
            "\\bPUR-\\d+\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern INVOICE_IDENTIFIER = Pattern.compile(
            "\\bINV-\\d+(?:-\\d+)*\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern CUSTOMER_IDENTIFIER = Pattern.compile(
            "\\bCUS(?:T-)?\\d+\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern SUPPLIER_IDENTIFIER = Pattern.compile(
            "\\bSUP(?:P-)?\\d+\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern ISO_DATE = Pattern.compile(
            "\\b\\d{4}-\\d{2}-\\d{2}\\b");

    private static final Pattern RESULT_LIMIT = Pattern.compile(
            "\\b(?:last|latest|top|recent)\\s+(\\d+)\\b", Pattern.CASE_INSENSITIVE);

    public List<String> extractProductCodes(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        List<String> codes = new ArrayList<>();
        Matcher matcher = PRODUCT_IDENTIFIER.matcher(text);
        while (matcher.find()) {
            String code = matcher.group().trim().toUpperCase(Locale.ROOT);
            if (!code.startsWith("PUR-") && !code.startsWith("INV-") && !code.startsWith("CUS") && !code.startsWith("SUP")) {
                if (!codes.contains(code)) {
                    codes.add(code);
                }
            }
        }
        return List.copyOf(codes);
    }

    public String extractPurchaseId(String text) {
        if (!StringUtils.hasText(text)) return null;
        Matcher m = PURCHASE_IDENTIFIER.matcher(text);
        return m.find() ? m.group().trim().toUpperCase(Locale.ROOT) : null;
    }

    public String extractInvoiceId(String text) {
        if (!StringUtils.hasText(text)) return null;
        Matcher m = INVOICE_IDENTIFIER.matcher(text);
        return m.find() ? m.group().trim().toUpperCase(Locale.ROOT) : null;
    }

    public String extractCustomerId(String text) {
        if (!StringUtils.hasText(text)) return null;
        Matcher m = CUSTOMER_IDENTIFIER.matcher(text);
        return m.find() ? m.group().trim().toUpperCase(Locale.ROOT) : null;
    }

    public String extractSupplierId(String text) {
        if (!StringUtils.hasText(text)) return null;
        Matcher m = SUPPLIER_IDENTIFIER.matcher(text);
        return m.find() ? m.group().trim().toUpperCase(Locale.ROOT) : null;
    }

    public List<String> extractDates(String text) {
        if (!StringUtils.hasText(text)) return List.of();
        List<String> dates = new ArrayList<>();
        Matcher m = ISO_DATE.matcher(text);
        while (m.find()) {
            dates.add(m.group());
        }
        return List.copyOf(dates);
    }

    public Integer extractLimit(String text, int defaultLimit) {
        if (!StringUtils.hasText(text)) return defaultLimit;
        Matcher m = RESULT_LIMIT.matcher(text);
        if (m.find()) {
            try {
                return Math.max(1, Math.min(100, Integer.parseInt(m.group(1))));
            } catch (NumberFormatException ignored) {}
        }
        return defaultLimit;
    }

    public List<AiEntityReference> extractEntities(String text) {
        List<AiEntityReference> entities = new ArrayList<>();
        for (String p : extractProductCodes(text)) {
            entities.add(AiEntityReference.ofProduct(p));
        }
        String cust = extractCustomerId(text);
        if (cust != null) entities.add(AiEntityReference.ofCustomer(cust));
        String supp = extractSupplierId(text);
        if (supp != null) entities.add(AiEntityReference.ofSupplier(supp));
        String inv = extractInvoiceId(text);
        if (inv != null) entities.add(AiEntityReference.ofInvoice(inv));
        String pur = extractPurchaseId(text);
        if (pur != null) entities.add(AiEntityReference.ofPurchase(pur));
        return List.copyOf(entities);
    }
}
