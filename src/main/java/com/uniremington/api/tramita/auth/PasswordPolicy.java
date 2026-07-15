package com.uniremington.api.tramita.auth;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Política de contraseñas server-side (research.md D6): mínimo 15 caracteres
 * (NIST SP 800-63B-4 §3.1.1.2 — piso para contraseña como factor único), máximo
 * 72 BYTES en UTF-8 (BCrypt trunca en bytes, no en caracteres: la ñ y las vocales
 * acentuadas ocupan 2), sin reglas de composición ni rotación forzada.
 *
 * Cada regla incumplida produce su propio mensaje: los consumen el 422 del cambio
 * de clave (US2) y la retroalimentación del SPA (US3). Punto único de extensión
 * para la futura blocklist (no-conformidad consciente documentada en D6).
 */
@Component
public class PasswordPolicy {

    public static final int MIN_CHARS = 15;
    public static final int MAX_BYTES = 72;

    public static final String MSG_TOO_SHORT =
            "La contraseña debe tener al menos " + MIN_CHARS + " caracteres";
    public static final String MSG_TOO_LONG =
            "La contraseña no debe superar " + MAX_BYTES + " bytes en UTF-8"
                    + " (la ñ y las letras acentuadas cuentan doble)";
    public static final String MSG_SAME_AS_CURRENT =
            "La nueva contraseña debe ser distinta de la actual";

    /**
     * Reglas intrínsecas de una contraseña (las valida también el seeder — D8).
     *
     * @return lista de violaciones, una por regla incumplida; vacía si es válida
     */
    public List<String> validate(String password) {
        List<String> violations = new ArrayList<>();
        if (password.length() < MIN_CHARS) {
            violations.add(MSG_TOO_SHORT);
        }
        if (password.getBytes(UTF_8).length > MAX_BYTES) {
            violations.add(MSG_TOO_LONG);
        }
        return violations;
    }

    /**
     * Reglas del cambio de clave (US2): las intrínsecas sobre la nueva más
     * «nueva distinta de la actual».
     */
    public List<String> validateChange(String currentPassword, String newPassword) {
        List<String> violations = validate(newPassword);
        if (newPassword.equals(currentPassword)) {
            violations.add(MSG_SAME_AS_CURRENT);
        }
        return violations;
    }
}
