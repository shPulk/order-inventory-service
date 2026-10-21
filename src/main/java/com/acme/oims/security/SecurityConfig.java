package com.acme.oims.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    // AntPathRequestMatcher on purpose: with the H2 console servlet registered,
    // string patterns in requestMatchers() fail with an "ambiguous servlet" error.
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s ->
                        s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(
                                AntPathRequestMatcher.antMatcher("/actuator/health/**")
                        ).permitAll()
                        .requestMatchers(
                                AntPathRequestMatcher.antMatcher("/actuator/metrics/**")
                        ).permitAll()
                        .requestMatchers(
                                AntPathRequestMatcher.antMatcher("/actuator/info")
                        ).permitAll()
                        .requestMatchers(
                                AntPathRequestMatcher.antMatcher("/v3/api-docs/**")
                        ).permitAll()
                        .requestMatchers(
                                AntPathRequestMatcher.antMatcher("/swagger-ui/**")
                        ).permitAll()
                        .requestMatchers(
                                AntPathRequestMatcher.antMatcher("/swagger-ui.html")
                        ).permitAll()
                        .requestMatchers(
                                AntPathRequestMatcher.antMatcher("/h2-console/**")
                        ).permitAll()
                        .requestMatchers(
                                AntPathRequestMatcher.antMatcher("/api/dev/token")
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(o ->
                        o.jwt(j ->
                                j.jwtAuthenticationConverter(jwtConverter())));

        return http.build();
    }

    private JwtAuthenticationConverter jwtConverter() {
        JwtGrantedAuthoritiesConverter authorities =
                new JwtGrantedAuthoritiesConverter();

        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(authorities);

        return converter;
    }

    @Bean
    SecretKey jwtKey(
            @Value("${oims.security.jwt-secret}") String secret) {

        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);

        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "oims.security.jwt-secret must be at least 32 bytes"
            );
        }

        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtKey) {
        return NimbusJwtDecoder.withSecretKey(jwtKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtKey) {
        return new NimbusJwtEncoder(
                new ImmutableSecret<>(jwtKey)
        );
    }
}