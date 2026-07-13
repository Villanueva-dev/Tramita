package com.uniremington.api.tramita.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Identidad de la Coordinación que se autentica (data-model.md). El schema lo posee
 * Flyway (V1.0.0); Hibernate solo valida. Esta entity nunca se expone en la API:
 * los DTOs de respuesta mapean a mano solo los campos permitidos.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    /** PK UUIDv4 generada por la app; nunca sale del servidor (el identificador público es el email). */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Identificador de login, siempre normalizado con {@link EmailNormalizer} antes de escribir o buscar. */
    @Column(nullable = false)
    private String email;

    /** Hash con prefijo {bcrypt} del DelegatingPasswordEncoder; jamás texto plano. */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /** Solo true puede autenticarse (FR-011); se chequea únicamente en el login. */
    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Timestamps por callbacks JPA locales (data-model.md): cero configuración global;
    // las columnas son NOT NULL, así que sin esto el INSERT explotaría en runtime.
    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
