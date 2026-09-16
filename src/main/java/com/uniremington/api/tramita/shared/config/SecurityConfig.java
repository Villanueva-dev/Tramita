package com.uniremington.api.tramita.shared.config;

import com.uniremington.api.tramita.security.AppUserDetailsService;
import com.uniremington.api.tramita.security.AuthFailureHandler;
import com.uniremington.api.tramita.security.AuthSuccessHandler;
import com.uniremington.api.tramita.security.JsonAuthenticationConverter;
import com.uniremington.api.tramita.service.impl.LoginAttemptService;
import com.uniremington.api.tramita.security.LoginThrottlingFilter;
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
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
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
     * La ruta de captura pública, declarada UNA vez: el permitAll y la exclusión de
     * CSRF deben cubrir exactamente lo mismo. Dos literales separados podrían
     * divergir, y la divergencia peligrosa —una exclusión de CSRF más ancha que el
     * permitAll— no la detectaría ningún test de esta feature.
     */
    private static final PathPatternRequestMatcher PUBLIC_CAPTURE =
            PathPatternRequestMatcher.withDefaults()
                    .matcher(HttpMethod.POST, "/api/public/requests/*");

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
                //
                // La captura pública queda FUERA de CSRF, y solo ella (004, research.md
                // D2). CSRF protege contra que un sitio ajeno use la sesión del navegante
                // a sus espaldas: sin sesión no hay identidad que suplantar, de modo que
                // acá no protegería nada y solo agregaría un paso previo —pedir el
                // token— que puede fallarle a un estudiante sin cuenta.
                //
                // La exclusión está acotada a esta ruta y NO es precedente para ninguna
                // otra: en cuanto un endpoint dependa de una sesión, CSRF vuelve a ser
                // la defensa que corresponde.
                //
                // ⚠️ LO QUE PROTEGE ESTE CANAL —tope de tamaño del cuerpo y límite de
                // envíos por origen— TODAVÍA NO ESTÁ: llega en la US3 de esta misma
                // feature (PublicSubmissionThrottlingFilter). Hasta entonces la ruta
                // está abierta sin cota de tamaño ni de tasa, y eso es deuda conocida,
                // no un descuido: no debe llegar a producción así.
                .csrf(csrf -> csrf.spa()
                        .ignoringRequestMatchers(PUBLIC_CAPTURE))
                // materializa el token diferido → la cookie XSRF-TOKEN se emite en las
                // respuestas del chain (la primera, en el GET inicial del SPA). El login
                // NO rota el token — ver javadoc de CsrfCookieFilter (D4/JD3-004)
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        // Segundo y último endpoint abierto del sistema (004, FR-001)
                        .requestMatchers(PUBLIC_CAPTURE).permitAll()
                        .anyRequest().authenticated())
                // sin sesión → 401 problem+json (RFC 9457, D10)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(problemJsonEntryPoint(problemJsonWriter)))
                // Logout por filter chain, sin controller (T036). JD2-001: el default
                // redirige 302 y el contrato exige 204 terminal. JD3-004: CsrfLogoutHandler
                // borra aquí la cookie XSRF-TOKEN y este 204 no la re-emite — el SPA repite
                // el GET inicial antes del próximo login (contrato en quickstart.md)
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(
                                new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                        .invalidateHttpSession(true)
                        .deleteCookies("TRAMITA_SESSION"))
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
