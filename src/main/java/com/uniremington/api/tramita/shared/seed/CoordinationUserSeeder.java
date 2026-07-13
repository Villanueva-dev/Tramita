package com.uniremington.api.tramita.shared.seed;

import com.uniremington.api.tramita.auth.EmailNormalizer;
import com.uniremington.api.tramita.auth.PasswordPolicy;
import com.uniremington.api.tramita.auth.User;
import com.uniremington.api.tramita.auth.UserRepository;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Provisión boot-time e idempotente de la cuenta de la Coordinación (research.md D8).
 * Credenciales por variables de entorno — nunca en git; sin defaults: si falta una,
 * el arranque falla (fail-fast, patrón Convenia). La rotación posterior de la clave
 * la hace la propia Coordinación vía cambio de contraseña (US2).
 */
@Slf4j
@Component
public class CoordinationUserSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final String seedEmail;
    private final String seedPassword;

    public CoordinationUserSeeder(UserRepository userRepository, PasswordPolicy passwordPolicy,
            PasswordEncoder passwordEncoder,
            @Value("${SEED_COORD_EMAIL}") String seedEmail,
            @Value("${SEED_COORD_PASSWORD}") String seedPassword) {
        this.userRepository = userRepository;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.seedEmail = seedEmail;
        this.seedPassword = seedPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Misma regla de normalización que usará el login: minúsculas por construcción
        String email = EmailNormalizer.normalize(seedEmail);

        if (userRepository.existsByEmail(email)) {
            log.info("Cuenta de la Coordinación ya provisionada; seeder sin cambios (idempotente)");
            return;
        }

        // Sin puertas traseras (D8): la cuenta seed ES la cuenta real de la Coordinación
        // y nace bajo la misma política que valida el cambio de clave. Fail-fast.
        List<String> violations = passwordPolicy.validate(seedPassword);
        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                    "SEED_COORD_PASSWORD viola la política de contraseñas: "
                            + String.join(" · ", violations));
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(seedPassword));
        user.setActive(true);
        userRepository.save(user);
        log.info("Cuenta de la Coordinación provisionada para {}", email);
    }
}
