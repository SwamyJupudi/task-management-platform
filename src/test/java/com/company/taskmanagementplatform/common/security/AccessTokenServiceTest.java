package com.company.taskmanagementplatform.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

import com.company.taskmanagementplatform.support.TestSecurityProperties;

class AccessTokenServiceTest {

    /**
     * Anchored to the present rather than to a literal date.
     *
     * <p>The decoder checks expiry against the real system clock, which is correct in production and
     * cannot be pointed at a fixed instant from here. A hardcoded moment would therefore make every
     * token either permanently expired or permanently valid, depending on when the suite ran. Only the
     * issuing side takes the fixed clock, which is enough to assert the arithmetic exactly.
     */
    private static final Instant NOW = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

    private final SecurityBeansConfig beans = new SecurityBeansConfig();

    @Test
    void issuesATokenThatItCanReadBack() {
        UUID userId = UUID.randomUUID();
        AccessTokenService service = service(TestSecurityProperties.defaults(), NOW);

        Jwt parsed = service.parse(service.issue(userId).value());

        assertThat(parsed.getSubject()).isEqualTo(userId.toString());
        // Read as a string, not through getIssuer(), which insists the claim be a URL.
        // The issuer here is a plain name; the validator compares it as a string.
        assertThat(parsed.getClaimAsString("iss")).isEqualTo(TestSecurityProperties.ISSUER);
        assertThat(parsed.getAudience()).containsExactly(TestSecurityProperties.AUDIENCE);
    }

    @Test
    void carriesNoPersonalDataAndNoPermissions() {
        // The whole design rests on this. If a permission ever appears in the token,
        // revocation silently stops being immediate.
        AccessTokenService service = service(TestSecurityProperties.defaults(), NOW);
        Jwt parsed = service.parse(service.issue(UUID.randomUUID()).value());

        assertThat(parsed.getClaims())
                .containsOnlyKeys("iss", "aud", "sub", "iat", "exp", "jti");
    }

    @Test
    void givesEveryTokenItsOwnIdentifier() {
        AccessTokenService service = service(TestSecurityProperties.defaults(), NOW);
        UUID userId = UUID.randomUUID();

        String first = service.parse(service.issue(userId).value()).getId();
        String second = service.parse(service.issue(userId).value()).getId();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void expiresAfterTheConfiguredLifetime() {
        AccessTokenService service = service(TestSecurityProperties.withAccessTtl(Duration.ofMinutes(15)), NOW);

        AccessTokenService.IssuedAccessToken issued = service.issue(UUID.randomUUID());

        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(issued.expiresInSeconds()).isEqualTo(900);
    }

    @Test
    void refusesATokenThatHasExpired() {
        // Issued an hour ago with a fifteen-minute life, so it is already stale by
        // the time the decoder looks at it.
        AccessTokenService issuer = service(
                TestSecurityProperties.withAccessTtl(Duration.ofMinutes(15)), NOW.minus(Duration.ofHours(1)));
        String token = issuer.issue(UUID.randomUUID()).value();

        AccessTokenService now = service(TestSecurityProperties.defaults(), NOW);

        assertThatThrownBy(() -> now.parse(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void refusesATokenSignedWithAnotherKey() {
        SecurityProperties otherKey = new SecurityProperties(
                new SecurityProperties.Jwt(
                        TestSecurityProperties.ISSUER,
                        TestSecurityProperties.AUDIENCE,
                        "a-completely-different-signing-key-of-length",
                        Duration.ofMinutes(15)),
                TestSecurityProperties.defaults().cookie(),
                TestSecurityProperties.defaults().password(),
                TestSecurityProperties.defaults().lockout(),
                TestSecurityProperties.defaults().tokens(),
                true);

        String foreign = service(otherKey, NOW).issue(UUID.randomUUID()).value();
        AccessTokenService ours = service(TestSecurityProperties.defaults(), NOW);

        assertThatThrownBy(() -> ours.parse(foreign)).isInstanceOf(JwtException.class);
    }

    @Test
    void refusesATokenMintedForAnotherAudience() {
        // Without this check, anything sharing the secret could mint tokens we honour.
        SecurityProperties otherAudience = new SecurityProperties(
                new SecurityProperties.Jwt(
                        TestSecurityProperties.ISSUER,
                        "some-other-service",
                        TestSecurityProperties.SECRET,
                        Duration.ofMinutes(15)),
                TestSecurityProperties.defaults().cookie(),
                TestSecurityProperties.defaults().password(),
                TestSecurityProperties.defaults().lockout(),
                TestSecurityProperties.defaults().tokens(),
                true);

        String foreign = service(otherAudience, NOW).issue(UUID.randomUUID()).value();
        AccessTokenService ours = service(TestSecurityProperties.defaults(), NOW);

        assertThatThrownBy(() -> ours.parse(foreign)).isInstanceOf(JwtException.class);
    }

    @Test
    void refusesATokenFromAnotherIssuer() {
        SecurityProperties otherIssuer = new SecurityProperties(
                new SecurityProperties.Jwt(
                        "somebody-else",
                        TestSecurityProperties.AUDIENCE,
                        TestSecurityProperties.SECRET,
                        Duration.ofMinutes(15)),
                TestSecurityProperties.defaults().cookie(),
                TestSecurityProperties.defaults().password(),
                TestSecurityProperties.defaults().lockout(),
                TestSecurityProperties.defaults().tokens(),
                true);

        String foreign = service(otherIssuer, NOW).issue(UUID.randomUUID()).value();
        AccessTokenService ours = service(TestSecurityProperties.defaults(), NOW);

        assertThatThrownBy(() -> ours.parse(foreign)).isInstanceOf(JwtException.class);
    }

    @Test
    void refusesASecretShorterThanTheAlgorithmNeeds() {
        SecurityProperties weak = new SecurityProperties(
                new SecurityProperties.Jwt("i", "a", "too-short", Duration.ofMinutes(15)),
                TestSecurityProperties.defaults().cookie(),
                TestSecurityProperties.defaults().password(),
                TestSecurityProperties.defaults().lockout(),
                TestSecurityProperties.defaults().tokens(),
                true);

        assertThatThrownBy(() -> beans.jwtSigningKey(weak))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void refusesAMissingSecretRatherThanInventingOne() {
        SecurityProperties missing = new SecurityProperties(
                new SecurityProperties.Jwt("i", "a", null, Duration.ofMinutes(15)),
                TestSecurityProperties.defaults().cookie(),
                TestSecurityProperties.defaults().password(),
                TestSecurityProperties.defaults().lockout(),
                TestSecurityProperties.defaults().tokens(),
                true);

        assertThatThrownBy(() -> beans.jwtSigningKey(missing)).isInstanceOf(IllegalStateException.class);
    }

    private AccessTokenService service(SecurityProperties properties, Instant now) {
        javax.crypto.SecretKey key = beans.jwtSigningKey(properties);
        return new AccessTokenService(
                beans.jwtEncoder(key),
                beans.jwtDecoder(key, properties),
                properties,
                Clock.fixed(now, ZoneOffset.UTC));
    }
}
