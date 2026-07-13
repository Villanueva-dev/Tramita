package com.uniremington.api.tramita.auth;

import com.uniremington.api.tramita.auth.dto.ChangePasswordRequest;
import com.uniremington.api.tramita.auth.dto.CurrentUserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/auth/me (identidad de la sesión activa) y POST /api/auth/password
 * (cambio de contraseña, US2). Login y logout NO son endpoints de controller —
 * corren en el filter chain (research.md D5). El 401 sin sesión lo resuelve el
 * entry point; el 422/429 del cambio de clave, el GlobalExceptionHandler (D10).
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final AuthService authService;

    @GetMapping("/me")
    public CurrentUserResponse me(Authentication authentication) {
        // Mapeo a mano (sin MapStruct): solo los campos permitidos, jamás id ni hash
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException(
                        "La sesión referencia una cuenta que ya no existe"));
        return new CurrentUserResponse(user.getEmail(), user.isActive());
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest body, HttpServletRequest request) {
        authService.changePassword(authentication.getName(), body, request.getRemoteAddr());
        // Solo en éxito (cualquier excepción propaga antes): rotar el id de sesión
        // tras el cambio de credencial — OWASP Session Management, capa web (JD2-003/F12)
        request.changeSessionId();
    }
}
