package com.uniremington.api.tramita.shared.config;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

/**
 * Plumbing de seguridad base (T013). El wiring del login (AuthenticationFilter,
 * converter, handlers — research.md D5) se agrega en la US1; el logout en la US4.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

    /**
     * DelegatingPasswordEncoder con BCrypt por defecto (research.md D6): el hash se
     * persiste con prefijo {bcrypt}, desacoplando los datos de un futuro cambio de
     * algoritmo (una migración a {argon2} no invalidaría los hashes existentes).
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /** Reloj único de la app: el throttling (D7) lo recibe inyectado y los tests lo simulan. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    // JsonMapper y no ObjectMapper: Boot 4 auto-configura Jackson 3 (features/json.adoc);
    // el ObjectMapper de Jackson 2 quedó deprecated y su bean ya no existe por defecto
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource, JsonMapper jsonMapper)
            throws Exception {
        http
                // CSRF para SPA: cookie XSRF-TOKEN legible por JS + deferred loading (D4)
                .csrf(csrf -> csrf.spa())
                // re-emite la cookie XSRF-TOKEN en cada respuesta; sin esto, el token
                // rotado al autenticar dejaría los POST post-login en 403 (D4)
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .anyRequest().authenticated())
                // sin sesión → 401 problem+json (RFC 7807, D10)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(problemJsonEntryPoint(jsonMapper)));
        return http.build();
    }

    private AuthenticationEntryPoint problemJsonEntryPoint(JsonMapper jsonMapper) {
        return (request, response, authException) -> {
            ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
            problem.setTitle("Autenticación requerida");
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            jsonMapper.writeValue(response.getWriter(), problem);
        };
    }

    /**
     * CORS con credenciales (research.md D9): allowlist explícita desde CorsProperties,
     * nunca comodín — allowCredentials=true lo prohíbe por spec.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsProperties.allowedOrigins());
        config.setAllowedMethods(List.of(HttpMethod.GET.name(), HttpMethod.POST.name()));
        config.setAllowedHeaders(List.of(HttpHeaders.CONTENT_TYPE, "X-XSRF-TOKEN"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
