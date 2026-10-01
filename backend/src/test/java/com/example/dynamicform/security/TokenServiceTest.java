package com.example.dynamicform.security;

import com.example.dynamicform.security.SecurityRepository.UserRow;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TokenServiceTest {
    @Test
    void issuesHs256TokenThatTheConfiguredDecoderCanRead() {
        SecretKey key = new SecretKeySpec(
                "test-secret-at-least-thirty-two-bytes-long".getBytes(StandardCharsets.UTF_8),
                "HmacSHA256");
        TokenService service = new TokenService(new NimbusJwtEncoder(new ImmutableSecret<>(key)));
        ReflectionTestUtils.setField(service, "accessTokenMinutes", 15L);
        ReflectionTestUtils.setField(service, "refreshTokenDays", 7L);

        UserRow user = new UserRow(UUID.randomUUID(), "tester", "hash", "Tester", null,
                "ACTIVE", 0, null, false, 0);
        String token = service.issueAccessToken(user, Set.of("DATA_ENTRY"), Set.of("RECORD_VIEW")).value();

        var decoded = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build()
                .decode(token);

        assertThat(decoded.getSubject()).isEqualTo("tester");
        assertThat(decoded.getClaimAsStringList("functions")).containsExactly("RECORD_VIEW");
    }
}
