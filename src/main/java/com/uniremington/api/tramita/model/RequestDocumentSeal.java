package com.uniremington.api.tramita.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * El registro permanente de que un documento formal salió del sistema (FR-001, FR-002).
 *
 * DE SOLO ANEXADO: se construye completo y se persiste una vez. No hay setters, todas las
 * columnas son {@code updatable = false}, y la garantía fuerte no depende de eso sino del
 * trigger {@code trg_document_seal_immutable} de la base (§VII, V4.1.0). La ausencia de
 * setters evita el error honesto; el trigger ataja al que lo intente por SQL.
 *
 * UN SELLO ES UNA FOTOGRAFÍA, y por eso el estado del trámite se copia en vez de
 * referenciarse: una clave foránea diría en qué estado está HOY aquella transición, no en
 * cuál estaba el trámite cuando el documento se emitió. Se copian las DOS columnas —código y
 * nombre— porque el pie impreso muestra el nombre legible: resolverlo en tiempo de
 * verificación haría que un simple renombre de estado convirtiera un documento legítimo en
 * uno «alterado».
 */
@Entity
@Table(name = "request_document_seal")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Getter
public class RequestDocumentSeal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", updatable = false)
    private Request request;

    /**
     * El código impreso en el documento, con el que cualquiera puede consultarlo sin sesión.
     * Único: es la llave del canal público.
     */
    @Column(name = "verification_code", nullable = false, updatable = false, length = 13)
    private String verificationCode;

    /** Huella del documento emitido, con el código ya impreso dentro (D2). */
    @Column(name = "document_sha256", nullable = false, updatable = false, length = 64)
    private String documentSha256;

    /** Con qué versión del formato se emitió, para no acusar de alterado a un formato viejo. */
    @Column(name = "format_version", nullable = false, updatable = false, length = 80)
    private String formatVersion;

    /** Revisión de los datos al emitir: el {@code @Version} de la solicitud (D7). */
    @Column(name = "request_version", nullable = false, updatable = false)
    private long requestVersion;

    @Column(name = "state_code", nullable = false, updatable = false, length = 50)
    private String stateCode;

    /** Nombre legible del estado al emitir, CONGELADO: es el texto que el pie imprime. */
    @Column(name = "state_name", nullable = false, updatable = false, length = 120)
    private String stateName;

    /** Quién pidió la emisión — siempre un usuario autenticado. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", updatable = false)
    private User actor;

    /**
     * Momento de la emisión.
     *
     * ⚠️ NO SE ASIGNA EN {@code @PrePersist}, a diferencia del resto de las marcas de tiempo
     * del chasis, y la diferencia es deliberada. El pie del documento IMPRIME esta fecha, y
     * la huella se calcula sobre el documento ya impreso: si la asignara el momento de
     * persistir, el valor guardado podría no ser el impreso, y al reconstruir el documento
     * para verificarlo los bytes no coincidirían. Quien emite fija el instante ANTES de
     * renderizar y lo usa para las dos cosas. En UTC, como manda la convención del chasis.
     */
    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt;
}
