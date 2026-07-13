package com.uniremington.api.tramita.shared.config;

import com.uniremington.api.tramita.auth.AppUserDetailsService;
import com.uniremington.api.tramita.auth.AuthFailureHandler;
import com.uniremington.api.tramita.auth.AuthSuccessHandler;
import com.uniremington.api.tramita.auth.JsonAuthenticationConverter;
import com.uniremington.api.tramita.auth.LoginAttemptService;
import com.uniremington.api.tramita.auth.LoginThrottlingFilter;
import com.uniremington.api.tramita.shared.exception.ProblemJsonWriter;
import java.time.Clock;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

/**
 * Seguridad de la app. El login corre DENTRO del filter chain (research.md D5):
 * AuthenticationFilter + JsonAuthenticationConverter + handlers — así la rotación del
 * id de sesión (anti session-fixation) y la persistencia del contexto las provee el
 * framework, no código propio. El logout se agrega en la US4.
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

    /**
     * DaoAuthenticationProvider con hideUserNotFoundExceptions en su default true:
     * el email inexistente ya llega como BadCredentialsException — primera capa del
     * 401 genérico anti-enumeración (D10).
     */
    @Bean
    AuthenticationManager authenticationManager(AppUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource,
            AuthenticationManager authenticationManager,
            JsonAuthenticationConverter jsonAuthenticationConverter,
            AuthSuccessHandler authSuccessHandler,
            AuthFailureHandler authFailureHandler,
            LoginAttemptService loginAttemptService,
            JsonMapper jsonMapper,
            ProblemJsonWriter problemJsonWriter) throws Exception {

        AuthenticationFilter loginFilter =
                new AuthenticationFilter(authenticationManager, jsonAuthenticationConverter);
        loginFilter.setRequestMatcher(
                PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/login"));
        loginFilter.setSuccessHandler(authSuccessHandler);
        loginFilter.setFailureHandler(authFailureHandler);
        // La sesión queda en la HttpSession, no en el request (D5) — sin guardado manual
        loginFilter.setSecurityContextRepository(new HttpSessionSecurityContextRepository());

        http
                // CSRF para SPA: cookie XSRF-TOKEN legible por JS + deferred loading (D4)
                .csrf(csrf -> csrf.spa())
                // materializa el token diferido → la cookie XSRF-TOKEN se emite en las
                // respuestas del chain (la primera, en el GET inicial del SPA). El login
                // NO rota el token — ver javadoc de CsrfCookieFilter (D4/JD3-004)
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .anyRequest().authenticated())
                // sin sesión → 401 problem+json (RFC 7807, D10)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(problemJsonEntryPoint(problemJsonWriter)))
                // el 429 corta ANTES de intentar autenticar (D7); el CSRF filter corre
                // antes que ambos por orden estándar del chain, preservando el 403
                .addFilterBefore(
                        new LoginThrottlingFilter(loginAttemptService, jsonMapper, problemJsonWriter),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterAt(loginFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private AuthenticationEntryPoint problemJsonEntryPoint(ProblemJsonWriter problemJsonWriter) {
        return (request, response, authException) -> {
            ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
            problem.setTitle("Autenticación requerida");
            problemJsonWriter.write(response, problem);
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
        // Retry-After no es CORS-safelisted: sin exponerlo, el fetch del SPA no puede
        // leerlo del 429 en el deploy cross-origin por subdominios (D3, JD3-001)
        config.setExposedHeaders(List.of(HttpHeaders.RETRY_AFTER));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
