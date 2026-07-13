package com.uniremington.api.tramita.auth;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit test de la política de contraseñas (T009, RED antes de T010).
 * Reglas de research.md D6 (NIST SP 800-63B-4 §3.1.1.2 + límite real de BCrypt):
 * mínimo 15 caracteres, máximo 72 bytes UTF-8, nueva distinta de la actual,
 * y cada regla incumplida produce su propio mensaje identificable.
 */
class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    @DisplayName("una contraseña de 15+ caracteres y hasta 72 bytes es válida")
    void acceptsValidPassword() {
        assertThat(policy.validate("frase de paso valida")).isEmpty();
    }

    @Test
    @DisplayName("borde inferior: 15 caracteres exactos pasan")
    void acceptsExactMinimumLength() {
        assertThat(policy.validate("a".repeat(15))).isEmpty();
    }

    @Test
    @DisplayName("menos de 15 caracteres se rechaza con su propio mensaje")
    void rejectsPasswordShorterThanMinimum() {
        List<String> violations = policy.validate("a".repeat(14));

        assertThat(violations).containsExactly(PasswordPolicy.MSG_TOO_SHORT);
    }

    @Test
    @DisplayName("borde superior: 72 bytes exactos pasan")
    void acceptsExactMaximumBytes() {
        assertThat(policy.validate("a".repeat(72))).isEmpty();
    }

    @Test
    @DisplayName("más de 72 bytes UTF-8 se rechaza con su propio mensaje")
    void rejectsPasswordOverMaximumBytes() {
        List<String> violations = policy.validate("a".repeat(73));

        assertThat(violations).containsExactly(PasswordPolicy.MSG_TOO_LONG);
    }

    @Test
    @DisplayName("no ASCII: pasa el conteo de caracteres pero excede el de bytes")
    void rejectsNonAsciiPassphraseThatFitsInCharsButNotInBytes() {
        // Frase realista para una usuaria hispanohablante: ñ y vocales acentuadas
        // ocupan 2 bytes en UTF-8, así que 72 caracteres NO garantizan 72 bytes.
        String passphrase = "mañana comeré ñoquis con salsa de años añejos y café según la ocasión";

        // Premisas del caso (si fallan, el ejemplo está mal construido, no la política)
        assertThat(passphrase.length()).isLessThanOrEqualTo(72);
        assertThat(passphrase.getBytes(UTF_8).length).isGreaterThan(72);

        assertThat(policy.validate(passphrase)).containsExactly(PasswordPolicy.MSG_TOO_LONG);
    }

    @Test
    @DisplayName("cambio de clave: nueva igual a la actual se rechaza")
    void rejectsNewPasswordEqualToCurrent() {
        String current = "frase de paso valida";

        List<String> violations = policy.validateChange(current, current);

        assertThat(violations).containsExactly(PasswordPolicy.MSG_SAME_AS_CURRENT);
    }

    @Test
    @DisplayName("cambio de clave: nueva distinta y válida no produce violaciones")
    void acceptsValidChange() {
        assertThat(policy.validateChange("frase de paso valida", "otra frase de paso nueva"))
                .isEmpty();
    }

    @Test
    @DisplayName("cada regla incumplida aporta su propio mensaje, acumulables")
    void reportsEachViolatedRuleWithItsOwnMessage() {
        // Demasiado corta y ademas igual a la actual: dos violaciones, dos mensajes
        String tooShort = "corta";

        List<String> violations = policy.validateChange(tooShort, tooShort);

        assertThat(violations).containsExactly(
                PasswordPolicy.MSG_TOO_SHORT,
                PasswordPolicy.MSG_SAME_AS_CURRENT);
    }
}
