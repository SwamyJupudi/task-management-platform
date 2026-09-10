package com.company.taskmanagementplatform.common.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * The cryptographic beans of the identity phase, and the checks that stop a misconfigured one from
 * reaching production.
 */
@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityBeansConfig {

    /** HS256 needs a key of at least 256 bits, and a shorter one is a silent weakness. */
    private static final int MINIMUM_SECRET_BYTES = 32;

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * BCrypt at strength 12, behind a delegating encoder.
     *
     * <p>The delegation is the point. Every stored hash is written with an algorithm prefix, so
     * moving to Argon2id later is a rehash on next login rather than a password reset for everyone.
     * The factory default is not used because it fixes BCrypt at strength 10.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        String defaultAlgorithm = "bcrypt";
        Map<String, PasswordEncoder> encoders = new HashMap<>();
        encoders.put(defaultAlgorithm, new BCryptPasswordEncoder(12));
        return new DelegatingPasswordEncoder(defaultAlgorithm, encoders);
    }

    @Bean
    public SecretKey jwtSigningKey(SecurityProperties properties) {
        String secret = properties.jwt().secret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "app.security.jwt.secret is not set. Supply it from the environment; there is no default.");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException("app.security.jwt.secret must be at least " + MINIMUM_SECRET_BYTES
                    + " bytes for HS256. It is " + keyBytes.length + ".");
        }
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return NimbusJwtEncoder.withSecretKey(jwtSigningKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * Verifies signature, expiry, issuer and audience.
     *
     * <p>The last two matter more than they look. Without them a token minted by any other system
     * that happens to share the secret would be accepted here.
     */
    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSigningKey, SecurityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        String expectedAudience = properties.jwt().audience();
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
                JwtClaimNames.AUD, audience -> audience != null && audience.contains(expectedAudience));

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtIssuerValidator(properties.jwt().issuer()),
                audienceValidator));
        return decoder;
    }
}
