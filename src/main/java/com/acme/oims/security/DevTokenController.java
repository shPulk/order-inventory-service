package com.acme.oims.security;

import com.acme.oims.common.BusinessRuleException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@RestController
@ConditionalOnProperty(name = "oims.security.dev-tokens", havingValue = "true")
public class DevTokenController {

    private static final Set<String> ROLES = Set.of("CUSTOMER", "WAREHOUSE_OPS", "ADMIN");
    private static final Duration TTL = Duration.ofHours(1);

    public record TokenRequest(@NotBlank String subject, @NotEmpty List<String> roles) {}
    public record TokenResponse(String token, String type, long expiresInSeconds) {}

    private final JwtEncoder encoder;

    public DevTokenController(JwtEncoder encoder) {
        this.encoder = encoder;
    }

    @PostMapping("/api/dev/token")
    public TokenResponse issue(@Valid @RequestBody TokenRequest req) {
        for (String role : req.roles) {
            if (!ROLES.contains(role)) {
                throw new BusinessRuleException("Unknown role:" + role);
            }
        }
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("oims-dev")
                .subject(req.subject())
                .issuedAt(now)
                .expiresAt(now.plus(TTL))
                .claim("roles", req.roles)
                .build();
        String token = encoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", TTL.toSeconds());
    }
}
