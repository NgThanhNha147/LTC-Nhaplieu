package com.example.dynamicform.document;

import com.example.dynamicform.common.ApiException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileSignatureValidatorTest {
    private final FileSignatureValidator validator = new FileSignatureValidator();

    @Test
    void acceptsPdfSignature() {
        validator.validate("application/pdf", "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void acceptsPngSignature() {
        validator.validate("image/png", new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        });
    }

    @Test
    void rejectsContentThatDoesNotMatchDeclaredType() {
        assertThatThrownBy(() -> validator.validate("application/pdf", "not-a-pdf".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void acceptsWebpSignature() {
        byte[] bytes = new byte[] { 0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50 };
        assertThatCode(() -> validator.validate("image/webp", bytes)).doesNotThrowAnyException();
    }
}
