package com.uniremington.api.tramita.auth;

import java.util.Locale;

/**
 * Regla única de normalización del email de login (data-model.md): trim + minúsculas.
 * La comparten el seeder y la búsqueda de usuario para que no puedan divergir.
 * Locale.ROOT evita sorpresas dependientes del locale del sistema (p. ej. la I turca).
 */
public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    public static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
