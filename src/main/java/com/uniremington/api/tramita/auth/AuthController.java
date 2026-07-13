package com.uniremington.api.tramita.auth;

import com.uniremington.api.tramita.auth.dto.CurrentUserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Solo GET /api/auth/me: identidad de la sesión activa para que el SPA verifique
 * la sesión al cargar. Login y logout NO son endpoints de controller — corren en
 * el filter chain (research.md D5). El 401 sin sesión lo resuelve el entry point.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;

    @GetMapping("/me")
    public CurrentUserResponse me(Authentication authentication) {
        // Mapeo a mano (sin MapStruct): solo los campos permitidos, jamás id ni hash
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException(
                        "La sesión referencia una cuenta que ya no existe"));
        return new CurrentUserResponse(user.getEmail(), user.isActive());
    }
}
