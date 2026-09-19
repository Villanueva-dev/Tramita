package com.uniremington.api.tramita.util;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * LA ZONA DE LA SEDE, en un solo lugar (revisión #34 M1).
 *
 * `issuedAt` se persiste en UTC —convención del chasis (research.md)—, pero el pie del
 * documento imprime la hora de Cali: Colombia es UTC−5, así que imprimir el instante UTC sin
 * convertir adelanta un día entre las 19:00 y las 23:59 locales. `DoFr100Renderer` ya hacía
 * esta conversión para el pie, con su propia constante privada; los DTO que exponen
 * `issuedAt` por la API (`PublicSealResponse`, `VerdictResponse`, `SealEntryResponse`)
 * devolvían el `LocalDateTime` UTC crudo, sin convertir — el contrato promete que el JSON es
 * «contrastable contra el pie impreso» y no lo era: una emisión a las 21:00 de Cali imprimía
 * `18/09/2026` en el papel y `2026-09-19T02:00` sin zona en el JSON, un día distinto.
 *
 * Esta clase es la ÚNICA fuente de la zona y de la conversión: `DoFr100Renderer` y
 * `DocumentSealServiceImpl` la usan los dos, en vez de cada uno declarar la suya.
 *
 * NO SE CAMBIA EL ALMACENAMIENTO. `RequestDocumentSeal.issuedAt` sigue siendo
 * {@link LocalDateTime} en UTC — la conversión es solo de presentación, en el borde de salida
 * (el pie del PDF, el JSON de la API), nunca en lo que se persiste.
 */
public final class CampusTime {

    /** América/Bogotá: la sede es Cali, y Colombia entera comparte esta zona. */
    public static final ZoneId CAMPUS_ZONE = ZoneId.of("America/Bogota");

    private CampusTime() {
    }

    /** Convierte un instante UTC, tal como se persiste, a la hora de la sede con su offset. */
    public static OffsetDateTime toCampus(LocalDateTime utc) {
        return utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(CAMPUS_ZONE).toOffsetDateTime();
    }
}
