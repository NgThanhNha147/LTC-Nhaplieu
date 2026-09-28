package com.example.dynamicform.schema;

import com.example.dynamicform.common.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class IdentifierPolicyTest {
    private final IdentifierPolicy policy = new IdentifierPolicy();

    @Test
    void normalizesLogicalNamesToSafePostgresIdentifiers() {
        assertThat(policy.normalize("certificateSerial")).isEqualTo("certificate_serial");
        assertThat(policy.normalize("Số thửa đất")).isEqualTo("so_thua_dat");
    }

    @Test
    void rejectsInjectionAndReservedWords() {
        assertThatThrownBy(() -> policy.requireValid("name;drop_table", "column")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> policy.requireValid("select", "column")).isInstanceOf(ApiException.class);
    }
}
