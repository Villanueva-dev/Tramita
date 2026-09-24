# Contexto operativo del asistente (Trámita)

> Fuente `TECHNICAL_REFERENCE`, sembrada como `VALIDATED` vía
> `V5.4.0__Seed_assistant_operational_context.sql`. El asistente **no lee este archivo en
> tiempo de ejecución**: `KnowledgeSearchServiceImpl` solo busca en `knowledge_chunk`/
> `knowledge_source` (Postgres). Este `.md` es la fuente humana legible del contenido que la
> migración inserta fragmento por fragmento (`chunk_order`). Si se edita este archivo, hay que
> crear una migración `V5.5.0` nueva que actualice los chunks — Flyway no re-ejecuta versiones ya
> aplicadas.

## Qué es Trámita

Motor de workflow configurable para dos trámites académicos de la Sede Cali (Universidad
Remington): **adición de créditos** (autorizar matrícula por encima del tope de créditos) y
**novedad de notas** (corregir/registrar nota tras el cierre del periodo). Es una bitácora de
seguimiento: el sistema **registra** el avance, no aprueba ni decide. Class y QF son cajas negras;
el sistema entrega el PDF formal y un humano lo asienta ahí.

## Estados por trámite (glosario operativo)

**Adición de créditos** (`ADICION_CREDITOS`): `REGISTRADA` → `EN_FACULTAD` →
`APROBADA_FACULTAD` → `EN_REGISTRO_CALI` → `EN_REGISTRO_NACIONAL` → `FINALIZADA`. Puede
`DEVOLVERSE` (vuelve a la Coordinación con motivo) o `RECHAZARSE` (la facultad la rechaza, p.ej.
extemporánea).

**Novedad de notas** (`NOVEDAD_NOTAS`): `REGISTRADA` → `EN_PREPARACION` (carpeta + firmas) →
`EN_FACULTAD` → `EN_REVISION_FINANCIERA` → `EN_REGISTRO_CONTROL` → `FINALIZADA`. No tiene estado
de rechazo propio: las devoluciones regresan a `EN_PREPARACION`.

## Qué puede responder el asistente

- Preguntas documentales con respaldo en fuentes `VALIDATED` (institucionales, evidencia de
  entrevista contrastada, o referencia técnica del propio sistema).
- Preguntas operativas ("¿cuántas solicitudes hay pendientes?", "¿cómo va el proceso?") usando
  `IRequestMetricsService`: totales, distribución por estado actual, tiempo promedio de ciclo,
  devoluciones — siempre agregado, **nunca por folio ni por estudiante**.
- Sin evidencia documental ni pregunta operativa reconocible: abstención segura
  (`grounded: false`, `"No encontré respaldo suficiente en las fuentes validadas disponibles."`).

## Qué NO hace el asistente (por diseño, no por bug)

- No cambia solicitudes, no ejecuta transiciones de workflow, no toca la base de datos ni el
  sistema de archivos directamente.
- No expone el detalle de una solicitud individual por folio en el chat (decisión deliberada:
  evitar enumeración de datos personales por texto libre; ese detalle vive en
  `GET /api/requests/{id}` con su propio control de acceso).
- No responde con autoridad normativa mientras no exista un Reglamento Estudiantil y
  procedimientos institucionales validados y cargados como fuente `OFFICIAL_INSTITUTIONAL`.

## Requisitos de runtime (por qué el chat puede quedar en blanco)

El chat depende de una cadena completa, en este orden. Si cualquier eslabón falla, la UI se ve
vacía sin error visible:

1. **Backend arriba** — Spring Boot debe arrancar sin excepciones. Causa más común de fallo:
   variables de entorno (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `APP_CORS_ALLOWED_ORIGINS`,
   `SEED_COORD_*`) no cargadas — `application.yml` no tiene defaults para secretos
   (fail-fast intencional). El error típico en el log es
   `'url' must start with "jdbc"` cuando `DB_URL` llegó vacía.
2. **Sesión autenticada** — `POST /api/auth/login` con las credenciales de
   `SEED_COORD_EMAIL`/`SEED_COORD_PASSWORD`, seguido de `GET /api/auth/me` en la misma sesión
   (cookie `TRAMITA_SESSION`, `HttpOnly`). Sin sesión válida, `/api/assistant` responde 401.
3. **CSRF** — cookie `XSRF-TOKEN` → header `X-XSRF-TOKEN` en toda mutación (login incluido,
   salvo el canal público de captura, que no lo requiere por diseño).
4. **`APP_AI_ENABLED=true`** — con evidencia documental pero el flag apagado o sin
   `OPENROUTER_API_KEY`, el backend responde `AiUnavailableException` (503), no una respuesta
   vacía.
5. **Perfil `dev`** en local — sin `SPRING_PROFILES_ACTIVE=dev`, la cookie de sesión lleva
   `Secure=true` y no viaja por `http://localhost`.

## Variables de entorno relevantes

| Variable | Rol |
|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Conexión JDBC a Postgres (puerto 5433 en dev) |
| `APP_CORS_ALLOWED_ORIGINS` | Allowlist del SPA, coma-separada, sin comodín |
| `SEED_COORD_EMAIL` / `SEED_COORD_PASSWORD` | Cuenta real de la Coordinación (seed idempotente) |
| `APP_AI_ENABLED` | Apaga/enciende el proveedor OpenRouter |
| `OPENROUTER_API_KEY` / `OPENROUTER_MODEL` | Credencial y modelo; nunca se exponen al frontend |
| `OPENROUTER_MAX_REQUESTS` / `OPENROUTER_WINDOW_SECONDS` | Límite por usuario autenticado (429 si se supera) |
| `OPENROUTER_CACHE_TTL_SECONDS` | Evita repetir la misma consulta al proveedor |

## Stack

Java 21 · Spring Boot 4.0.7 (Security 7, Data JPA, Validation, WebMVC) · PostgreSQL + Flyway
(Hibernate solo valida el esquema) · BCrypt · Testcontainers. Frontend: Next.js 16 · React 19 ·
TypeScript strict · Tailwind 4, sesión por cookie `HttpOnly` + CSRF *double-submit*.
