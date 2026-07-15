package com.uniremington.api.tramita.auth;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Puente entre la tabla users y Spring Security. Una cuenta inactiva se mapea a
 * disabled: el DaoAuthenticationProvider produce DisabledException (FR-011), que el
 * AuthFailureHandler aplana al mismo 401 genérico que cualquier otra falla (FR-002).
 */
@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) {
        String normalized = EmailNormalizer.normalize(email);
        User user = userRepository.findByEmail(normalized)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No existe cuenta para el email indicado"));
        // Sin roles: un único actor (la Coordinación) — authorities vacías a propósito
        return org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .disabled(!user.isActive())
                .authorities(List.of())
                .build();
    }
}
