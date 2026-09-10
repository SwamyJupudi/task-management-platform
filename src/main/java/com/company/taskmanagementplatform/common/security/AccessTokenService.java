package com.company.taskmanagementplatform.common.security;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Mints and reads the access token.
 *
 * <p>The token carries issuer, audience, subject, issue time, expiry and an id, and deliberately
 * nothing else. No address, because it would put personal data into something that travels in
 * headers and gets pasted into bug reports. No roles or permissions, because they differ per
 * workspace and must revoke at once, so a fifteen-minute copy of them would be both bulky and wrong
 * for up to fifteen minutes.
 *
 * <p>The issue time earns its place: it is compared against the moment the password last changed, so
 * changing a password refuses every token minted before it.
 */
@Service
public class AccessTokenService {

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final SecurityProperties.Jwt properties;
    private final Clock clock;

    AccessTokenService(JwtEncoder encoder, JwtDecoder decoder, SecurityProperties properties, Clock clock) {
        this.encoder = encoder;
        this.decoder = decoder;
        this.properties = properties.jwt();
        this.clock = clock;
    }

    public IssuedAccessToken issue(UUID userId) {
        // Truncated to the second because that is the resolution a JWT timestamp
        // has. Leaving sub-second precision on would make the value written differ
        // from the value read back.
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(properties.accessTtl());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .subject(userId.toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        return new IssuedAccessToken(value, expiresAt, properties.accessTtl().toSeconds());
    }

    /**
     * Verifies the signature, the issuer, the audience and the expiry.
     *
     * @throws org.springframework.security.oauth2.jwt.JwtException if any of those fail
     */
    public Jwt parse(String tokenValue) {
        return decoder.decode(tokenValue);
    }

    /**
     * @param value the encoded token, returned to the caller once and never stored
     * @param expiresAt when it stops being accepted
     * @param expiresInSeconds the same, expressed the way a client wants to consume it
     */
    public record IssuedAccessToken(String value, Instant expiresAt, long expiresInSeconds) {}
}
