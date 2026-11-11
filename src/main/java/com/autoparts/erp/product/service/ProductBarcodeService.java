package com.autoparts.erp.product.service;

import com.autoparts.erp.auth.security.annotation.SalesAccess;
import com.autoparts.erp.product.dto.ProductResponse;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ProductBarcodeService {

    private static final int WIDTH = 500;
    private static final int HEIGHT = 150;

    private final ProductService productService;

    public ProductBarcodeService(ProductService productService) {
        this.productService = productService;
    }

    @SalesAccess
    public ProductResponse getBarcodeDetails(UUID uuid) {
        return productService.findByUuid(uuid);
    }

    @SalesAccess
    public byte[] generatePng(UUID uuid) {
        ProductResponse product = productService.findByUuid(uuid);

        if (product.barcode() == null || product.barcode().isBlank()) {
            throw new IllegalStateException("Barcode is not configured for this product");
        }

        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
            hints.put(EncodeHintType.MARGIN, 2);

            BitMatrix matrix = new MultiFormatWriter().encode(
                    product.barcode(),
                    BarcodeFormat.CODE_128,
                    WIDTH,
                    HEIGHT,
                    hints
            );

            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                MatrixToImageWriter.writeToStream(matrix, "PNG", output);
                return output.toByteArray();
            }
        } catch (WriterException | IOException ex) {
            throw new IllegalStateException("Could not generate product barcode", ex);
        }
    }
}
