package com.example.dynamicform.document;

import com.example.dynamicform.common.ApiException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Component
public class FileSignatureValidator {
    public void validate(String contentType, byte[] header) {
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        boolean valid = switch (type) {
            case "application/pdf" -> contains(header, "%PDF-".getBytes(StandardCharsets.US_ASCII));
            case "image/jpeg" -> startsWith(header, 0xFF, 0xD8, 0xFF);
            case "image/png" -> startsWith(header, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "image/webp" -> startsWith(header, 0x52, 0x49, 0x46, 0x46)
                    && startsWithAt(header, 8, 0x57, 0x45, 0x42, 0x50);
            case "image/tiff" -> startsWith(header, 0x49, 0x49, 0x2A, 0x00)
                    || startsWith(header, 0x4D, 0x4D, 0x00, 0x2A);
            default -> false;
        };
        if (!valid) {
            throw ApiException.badRequest("Nội dung file không khớp với định dạng được khai báo.");
        }
    }

    private boolean startsWith(byte[] value, int... expected) {
        return startsWithAt(value, 0, expected);
    }

    private boolean startsWithAt(byte[] value, int offset, int... expected) {
        if (value == null || value.length < offset + expected.length) return false;
        for (int i = 0; i < expected.length; i++) {
            if ((value[offset + i] & 0xFF) != expected[i]) return false;
        }
        return true;
    }

    private boolean contains(byte[] value, byte[] expected) {
        if (value == null || expected.length == 0 || value.length < expected.length) return false;
        for (int i = 0; i <= value.length - expected.length; i++) {
            boolean match = true;
            for (int j = 0; j < expected.length; j++) {
                if (value[i + j] != expected[j]) {
                    match = false;
                    break;
                }
            }
            if (match) return true;
        }
        return false;
    }
}

