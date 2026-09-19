# Tramita — Backend

Backend del MVP de **motor de workflow configurable** para trámites académicos de la
Universidad Remington (Sede Cali). Trabajo de grado — Ingeniería de Sistemas.

El dominio es un motor genérico donde dos trámites de profundidad distinta (adición de
créditos y novedad de notas) se configuran **por dato, no por código**: sus estados,
transiciones, responsables, reglas numéricas y hasta el formato que emiten son filas
versionadas en BD, y agregar un trámite ya contemplado es un `INSERT` — sin recompilar ni
desplegar. El chasis técnico (Spring Boot 4 / Java 21 / PostgreSQL / Flyway) reutiliza el
patrón del proyecto hermano `convenia/` — se copia el *plumbing* por capas, no las entidades.

La tesis se comprueba con un comando: ningún nombre de trámite ni de estado aparece en la
lógica del motor.

```bash
git grep -nE '"(FINALIZADA|DEVUELTA|ADICION_CREDITOS|NOVEDAD_NOTAS)"' main -- 'src/main/java/*.java'
# → una sola línea, y es el rótulo impreso del papel
```

---

## Qué está entregado

Seis features recorrieron el ciclo y están mergeadas en `main`. La tabla dice **qué sabe
hacer el sistema hoy**; **cuánto falta** no se escribe acá, se consulta en los
[milestones](../../milestones) — un milestone por sprint, un issue por sub-problema.

| Feature | Aporta | Issue |
|---------|--------|-------|
| [`001-auth-login`](specs/001-auth-login/) | Sesión stateful de la Coordinación: login, `/me`, cambio de clave, logout | #6 |
| [`002-workflow-engine`](specs/002-workflow-engine/) | El motor: registrar, avanzar, devolver y auditar sobre configuración en BD | #7, #8 |
| [`003-request-form-rules`](specs/003-request-form-rules/) | El formulario real como dato (asignaturas, notas, créditos) y reglas de negocio por trámite | #9 |
| [`004-public-request-capture`](specs/004-public-request-capture/) | El estudiante entrega el formato firmado desde un enlace público, sin cuenta | #18 |
| [`005-formal-document`](specs/005-formal-document/) | El sistema emite el DO-FR-100 diligenciado como PDF | #10 |
| [`006-verifiable-document-seal`](specs/006-verifiable-document-seal/) | Cada emisión queda sellada, verificable por dos canales, con historial | #11 |

```bash
gh api repos/:owner/:repo/milestones \
  --jq '.[] | "\(.title) — abiertos:\(.open_issues) cerrados:\(.closed_issues)"'
```

---

## Stack

| Capa | Tecnología |
|------|-----------|
| Lenguaje | Java 21 |
| Framework | Spring Boot 4.0.7 (WebMVC, Data JPA, Security 7, Validation) |
| Serialización | Jackson 3 vía `spring-boot-starter-json` (Boot 4 ya **no** lo trae en webmvc) |
| Persistencia | PostgreSQL + Flyway (`ddl-auto: validate` — Flyway es dueño del schema) |
| Documentos | Apache PDFBox 3.0.8 (render del DO-FR-100, sin plantilla externa) |
| Hashing | BCrypt vía `DelegatingPasswordEncoder` (hash con prefijo `{bcrypt}`) |
| Boilerplate | Lombok (sin MapStruct) |
| Build | Maven (wrapper `./mvnw`) + JaCoCo + Failsafe |
| Test | JUnit 5, Spring Security Test, Testcontainers (PostgreSQL) |

---

## Arquitectura

Organización **package-by-layer** (constitución §II, vigente desde v2.0.0). Base:
`com.uniremington.api.tramita` — 90 archivos `.java` en `src/main`.

```
tramita/
├── controller/   AuthController                # GET /me, POST /password
│                 WorkflowDefinitionController  # catálogo de trámites vigentes
│                 RequestController             # registrar / avanzar / bandeja / timeline
│                                               #   / documento / historial de emisiones
│                 PublicRequestController       # captura del formato SIN sesión
│                 SealController                # verificación exacta (con sesión)
│                 PublicSealController          # consulta del sello por código impreso
├── dto/          20 records de entrada y salida — sin entidades cruzando la frontera HTTP
├── model/        User
│                 WorkflowDefinition / WorkflowState / WorkflowTransition / WorkflowParameter
│                 Request (@Version) / RequestSubject
│                 RequestTransitionLog / RequestDocumentSeal    # ambas solo INSERT
├── repo/         6 interfaces Spring Data
├── security/     AppUserDetailsService # carga el usuario para Spring Security
│                 JsonAuthenticationConverter / AuthSuccessHandler / AuthFailureHandler
│                 LoginThrottlingFilter          # 429 anti-fuerza-bruta
│                 PublicSubmissionThrottlingFilter + CachedBodyRequest  # canal público
├── service/      Contratos: IAuthService, IWorkflowDefinitionService, IRequestService,
│                 IRequestBusinessRules, IWorkflowGuard, IDocumentService,
│                 IDocumentRenderer, IDocumentSealService
│   └── impl/     RequestServiceImpl            # EL MOTOR: advance() valida contra la
│                                               #   definición — no conoce ningún trámite
│                 RequestBusinessRulesImpl      # reglas por parámetro configurado
│                 DocumentServiceImpl / DoFr100Renderer   # el PDF del formato
│                 DocumentSealServiceImpl       # sellar, verificar, historial
│                 AuthServiceImpl / LoginAttemptService / PasswordPolicy
│                 SlidingWindowCounter / PublicSubmissionCounter
├── util/         EmailNormalizer, CampusTime, PdfTextEncoder, VerificationCodeGenerator
└── shared/
    ├── config/      SecurityConfig, CsrfCookieFilter, CorsProperties, PublicCaptureProperties
    ├── exception/   GlobalExceptionHandler, PublicCaptureExceptionHandler,
    │                ProblemJsonWriter (RFC 9457) + 7 excepciones de dominio
    ├── validation/  @AtMostOneDecimal          # la precisión de una nota, en la entrada
    └── seed/        CoordinationUserSeeder     # provisión idempotente de la cuenta real
```

Las interfaces llevan prefijo `I`. **`LoginThrottlingFilter`, `PublicSubmissionThrottlingFilter`
y `CsrfCookieFilter` no llevan estereotipo a propósito**: `SecurityConfig` los construye e
inserta en el chain a mano, y anotarlos haría que Spring Boot los auto-registre por duplicado
en la cadena del servlet container.

**Login y logout NO son endpoints de controller**: corren dentro del *filter chain* de Spring
Security (`AuthenticationFilter` + handlers). Así la rotación del id de sesión
(anti session-fixation) y la persistencia del contexto las provee el framework, no código
propio. Solo `/me` y `/password` son métodos de controller.

---

## Modelo de datos

**Nueve tablas**, todas creadas por Flyway (`V1.0.0` → `V4.1.0`, once migraciones).

**Auth** (`V1.0.0`): `users` — índice único funcional `uq_users_email_lower` sobre
`LOWER(email)`; la cuenta la provisiona `CoordinationUserSeeder` por env (nunca credenciales
en git).

**Motor de workflow** (`V2.0.0`, semilla `V2.1.0`, restricciones `V2.2.0`):

- `workflow_definition(code, version, …)` con `UNIQUE(code, version)` — **la versión es
  parte de la identidad**: editar un trámite es insertar la versión siguiente, y cada
  solicitud se rige por la definición con la que nació.
- `workflow_state` y `workflow_transition` — estados, transiciones, responsable de cada
  paso y si exige observación (`requires_note`): la "devolución" es un dato de la
  configuración, no un concepto del motor.
- `request` — datos del estudiante (minimizados, Ley 1581) y `version` de **locking
  optimista**: ante dos avances simultáneos solo prospera el que vio el estado vigente.
- `request_transition_log` — el **timeline inmutable**: solo recibe INSERT, y el trigger
  `trg_timeline_immutable` rechaza UPDATE/DELETE incluso con acceso directo por SQL.

**Formulario y reglas** (`V2.3.0`, `V3.0.0`–`V3.3.0`):

- `request_subject` — las asignaturas del trámite, con créditos y notas.
- `workflow_parameter(definition_id, parameter_key, parameter_value)` — los límites y la
  configuración del trámite como dato: `MAX_CREDITS`, `MIN_GRADE`/`MAX_GRADE`,
  `CAPTURES_CREDITS`, `PUBLIC_CAPTURE_ENABLED`, `DOCUMENT_TEMPLATE`. La FK apunta a la
  **versión** de la definición, así que publicar una v2 con otro tope no altera las
  solicitudes en curso.
- `workflow_transition.guard_key` — la definición declara el **nombre** de la regla que
  condiciona el paso; el motor lo resuelve contra las implementaciones de `IWorkflowGuard`
  y nunca conoce el trámite. `NULL` = sin guarda.

> ⚠️ `MAX_CREDITS = 21` es **provisional y no auditado**: los transcripts confirman que
> existe un tope, no cuál. El Reglamento Estudiantil (Acuerdo n.º 13 de 2023, art. 24 §1)
> fija los créditos adicionales en el 40 % del nivel. Corregirlo es un `UPDATE`, no un
> despliegue. `MIN_GRADE`/`MAX_GRADE` sí tienen respaldo: **0.0–5.0** con un decimal
> (art. 32), y esa precisión está además en la base (`ck_request_subject_grades_one_decimal`).

**Sello del documento** (`V4.0.0`, `V4.1.0`):

- `request_document_seal` — una fila por emisión, **solo anexado**, protegida por el trigger
  `trg_document_seal_immutable` (el mismo patrón del timeline). Guarda el código impreso, la
  huella SHA-256 del PDF, la versión del formato, la revisión de la solicitud, el estado
  **copiado** (código y nombre, no referenciado), quién emitió y cuándo.
- **No hay columna de documento**: el archivo nunca se almacena.

Detalle por feature: [`002`](specs/002-workflow-engine/data-model.md) ·
[`003`](specs/003-request-form-rules/data-model.md) ·
[`004`](specs/004-public-request-capture/data-model.md) ·
[`006`](specs/006-verifiable-document-seal/data-model.md).
La semilla de novedad de notas es **provisional** hasta validar la cadena con la Coordinación.

---

## Seguridad

Sesión **stateful** con cookie, sin JWT.

| Mecanismo | Implementación |
|-----------|----------------|
| Sesión | Cookie `TRAMITA_SESSION` `HttpOnly` + `Secure` + `SameSite=Strict`, expira a los 30 min de inactividad |
| CSRF | Double-submit (`csrf.spa()`): cookie `XSRF-TOKEN` legible por JS + header `X-XSRF-TOKEN` en cada POST |
| Fuerza bruta | Throttling `429` + `Retry-After` tras 5 fallos por (email+IP) en 15 min; el `429` corta **antes** de autenticar |
| Anti-enumeración | `401` genérico: email inexistente, clave errada y cuenta inactiva devuelven el mismo mensaje sin `detail` |
| Hashing | `DelegatingPasswordEncoder` con BCrypt por defecto (prefijo `{bcrypt}` → migrable sin invalidar hashes) |
| Política de clave | Mínimo 15 caracteres, máximo 72 bytes UTF-8, distinta de la actual; sin composición forzada |
| CORS | Allowlist explícita desde `APP_CORS_ALLOWED_ORIGINS` (nunca comodín, obligatorio con `allowCredentials`) |
| Errores | Siempre `application/problem+json` (RFC 9457) |

`/me` responde desde el **snapshot de la sesión** (email + active del `UserDetails` capturado
al autenticar), sin tocar la BD.

### Las dos rutas sin sesión

Todo lo demás cae en `anyRequest().authenticated()`. Solo dos matchers llevan `permitAll`, y
cada uno está declarado **una sola vez** para que el permiso y la exclusión de CSRF no puedan
divergir:

- **`POST /api/public/requests/*`** — la captura del formato. Es la única ruta excluida de
  CSRF (quien envía no tiene sesión de la que abusar). Va protegida por
  `PublicSubmissionThrottlingFilter`: **20 envíos por IP cada 15 min** y un cuerpo máximo de
  **256 KB**, calibrados contra el volumen real (30–40 solicitudes por semestre). El recibo
  **no devuelve identificador**: quien envía no obtiene una ventana al sistema.
- **`GET /api/public/seals/*`** — la consulta del sello por el código impreso. **No necesita
  exclusión de CSRF**: un `GET` no cambia estado y Spring Security no lo protege por diseño.
  Autoriza **por posesión** del código (64 bits en base 36, ≤13 caracteres) y solo afirma que
  el sello existe. Sin datos personales.

> ⚠️ **Al desplegar detrás de un proxy** hay que cambiar `server.forward-headers-strategy` a
> `NATIVE` y declarar el proxy en `server.tomcat.remoteip.internal-proxies`. Si no,
> `getRemoteAddr()` devuelve la IP del proxy y los envíos de toda la sede se cuentan contra
> una sola clave — justo el fallo que el límite existe para prevenir.

### Los dos canales de verificación del sello

No son dos versiones del mismo endpoint: responden preguntas distintas.

| | `GET /api/public/seals/{code}` | `POST /api/seals/verify` |
|---|---|---|
| Quién | Cualquiera con el papel en la mano | La Coordinación, con sesión |
| Recibe | El código impreso en el pie | El código **y la huella SHA-256** — nunca el archivo |
| Responde | `ISSUED` o `404`. Nada más | `INTACT` · `TAMPERED` · `NOT_VERIFIABLE` |
| Integridad | **No la evalúa**: sin huella no compara nada | Compara contra la huella **guardada al emitir**, sin regenerar el documento |

`NOT_VERIFIABLE` existe para no acusar en falso: cuando las huellas difieren, el sistema
primero busca si cambió el formato (`FORMAT_CHANGED`) o si avanzaron los datos
(`DATA_CHANGED`). Solo si ninguna explica la diferencia dice `TAMPERED`.

---

## Endpoints

Base: `/api`. Contratos formales en los `contracts/openapi.yaml` de cada feature; guía de
integración para el SPA en
[`specs/001-auth-login/integracion-frontend.md`](specs/001-auth-login/integracion-frontend.md).

### Con sesión

| Método | Path | CSRF | Éxito |
|--------|------|:----:|-------|
| `POST` | `/api/auth/login` | ✅ | `204` + cookie de sesión |
| `GET`  | `/api/auth/me` | — | `200` `{ email, active }` |
| `POST` | `/api/auth/password` | ✅ | `204` (rota el id de sesión) |
| `POST` | `/api/auth/logout` | ✅ | `204` (borra ambas cookies) |
| `GET`  | `/api/workflow-definitions` | — | `200` — trámites vigentes (insumo del formulario) |
| `POST` | `/api/requests` | ✅ | `201` + `Location` — nace en el estado inicial de su trámite |
| `GET`  | `/api/requests?search=` | — | `200` — localiza por cédula exacta o fragmento del nombre |
| `GET`  | `/api/requests/inbox` | — | `200` — las recientes, **sin documento de identidad** |
| `GET`  | `/api/requests/{id}` | — | `200` — detalle + transiciones disponibles |
| `POST` | `/api/requests/{id}/transitions` | ✅ | `200` — avanza o devuelve; `409` si no está definida o la rechaza una guarda |
| `GET`  | `/api/requests/{id}/timeline` | — | `200` — el recorrido del trámite, en orden cronológico |
| `GET`  | `/api/requests/{id}/document` | — | `200` `application/pdf` — el DO-FR-100 diligenciado y sellado |
| `GET`  | `/api/requests/{id}/seals` | — | `200` — historial de emisiones (lista vacía si nunca se pidió) |
| `POST` | `/api/seals/verify` | ✅ | `200` — veredicto de integridad |

### Sin sesión

| Método | Path | CSRF | Éxito |
|--------|------|:----:|-------|
| `POST` | `/api/public/requests/{definitionCode}` | excluido | `201` + recibo **sin identificador** |
| `GET`  | `/api/public/seals/{code}` | n/a (`GET`) | `200` `ISSUED` · `404` si no existe |

Recorridos con `curl`, incluida la **demo SC-005** (cargar un trámite nuevo por SQL con la app
corriendo y operarlo sin redeploy):
[`002`](specs/002-workflow-engine/quickstart.md) ·
[`003`](specs/003-request-form-rules/quickstart.md) ·
[`004`](specs/004-public-request-capture/quickstart.md) ·
[`005`](specs/005-formal-document/quickstart.md) ·
[`006`](specs/006-verifiable-document-seal/quickstart.md).

---

## Puesta en marcha

### Prerrequisitos

- Java 21 (`java -version`).
- Docker (para PostgreSQL local y para los tests de integración con Testcontainers).

### 1. Base de datos

```bash
docker run -d \
  --name tramita-postgres \
  -e POSTGRES_DB=tramita-db \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5433:5432 \
  -v tramita-pgdata:/var/lib/postgresql/data \
  postgres:16

# En arranques posteriores basta con:
docker start tramita-postgres && docker ps
```

Publica en el **5433** del host, no en el 5432.

### 2. Variables de entorno

Copiar `.env.example` a `.env` y completar. **Sin defaults para secretos**: si falta una
variable, el arranque falla a propósito (*fail-fast*).

| Variable | Ejemplo | Rol |
|----------|---------|-----|
| `DB_URL` | `jdbc:postgresql://localhost:5433/tramita-db` | Conexión JDBC |
| `DB_USER` | `postgres` | Usuario de BD |
| `DB_PASSWORD` | `postgres` | Clave de BD |
| `SEED_COORD_EMAIL` | `coordinacion.cali@uniremington.edu.co` | Email de la cuenta real de la Coordinación |
| `SEED_COORD_PASSWORD` | *(frase de paso ≥ 15 caracteres)* | Clave inicial; la Coordinación la rota luego vía `/password` |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Allowlist del SPA (coma-separada, sin comodín) |

Los límites del canal público (`app.public-capture.*`) sí traen default en
`application.yml`: no son secretos, y su valor está justificado ahí mismo.

### 3. Levantar (perfil `dev`)

El perfil `dev` desactiva **solo** el flag `Secure` de la cookie (una cookie `Secure` no viaja
por `http://localhost`). En producción `Secure` queda siempre activo.

```bash
set -a; source .env; set +a
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

Al primer arranque, Flyway aplica las once migraciones y `CoordinationUserSeeder` provisiona
la cuenta (hasheando `SEED_COORD_PASSWORD` con BCrypt). Reinicios posteriores no la duplican
(idempotente).

> **Si el arranque falla en `ck_request_subject_grades_one_decimal`**, es el saneamiento de
> `V4.1.0`: hay calificaciones con más de un decimal en la base. El diagnóstico está en
> [`specs/006-verifiable-document-seal/quickstart.md`](specs/006-verifiable-document-seal/quickstart.md).

---

## Tests

- **Unitarios** (`*Test`, Surefire): servicios, política de contraseña, throttling, reglas de
  negocio, render del PDF y determinismo, generador de códigos.
- **Integración** (`*IT`, Failsafe + Testcontainers): cadena de filtros y controladores
  end-to-end contra un PostgreSQL real efímero, más los triggers de inmutabilidad y las
  restricciones de BD, que solo se pueden probar contra el motor real.
- **Cobertura**: reporte JaCoCo generado en la fase `verify`.

```bash
./mvnw test                          # solo unitarios
./mvnw clean verify                  # unitarios + integración + reporte JaCoCo
./mvnw -Dtest=AuthControllerIT test  # solo la integración de la cadena de filtros
```

Última medición sobre `789faea`: **145 unitarios + 94 de integración, `BUILD SUCCESS`**. La
cifra se re-mide, no se cita de memoria: es lo que imprime `./mvnw clean verify`.

> Nota: el build incremental de Maven puede reportar un `BUILD SUCCESS` engañoso tras editar un
> test ("Nothing to compile"). Para evidenciar el RED de TDD usar `./mvnw clean test-compile`.

---

## Integración continua

Cada push a `main` y cada pull request contra `main` dispara
[`.github/workflows/ci.yml`](.github/workflows/ci.yml): un runner limpio compila el proyecto y
corre la suite completa —unitarios y de integración— con `./mvnw clean verify`.

No necesita secretos ni un servicio de PostgreSQL en el runner: los tests de integración levantan
su propio contenedor vía `@ServiceConnection`, y cada uno declara `APP_CORS_ALLOWED_ORIGINS` en su
`@SpringBootTest`. El workflow tampoco filtra por `paths:` **a propósito**: si no corriera en
cambios de documentación, toda PR de solo-docs quedaría esperando un check que nunca llega.

---

## Cómo se contribuye

`main` está protegida por el ruleset `branch-protect`, con `bypass_actors` vacío: **las reglas
aplican también al administrador**.

| Regla | Efecto |
|-------|--------|
| `pull_request` | No se puede pushear directo a `main`. Todo cambio va por PR, documentación incluida |
| `required_status_checks` | El check `build` debe estar en verde para habilitar el merge |
| `required_review_thread_resolution` | No se mergea con comentarios de revisión sin resolver |
| `non_fast_forward` · `deletion` | No hay force-push ni borrado de `main` |
| Métodos de merge | `merge` y `rebase`. **Squash está deshabilitado**: aplastaría los cuerpos de commit, que son la evidencia de por qué se tomó cada decisión |

Commits en **Conventional Commits en español**, con la plantilla de [`.gitmessage`](.gitmessage)
—que cada quien activa en su copia con `git config commit.template .gitmessage`, porque esa
configuración vive en `.git/config` y no se versiona—. El cuerpo explica el **porqué**, un
commit tiene un solo propósito, y todo cambio de comportamiento lleva una línea `Verificado:`
con el comando ejecutado y su resultado.

El **estado del proyecto** se sigue en los [milestones](../../milestones). Para cerrar un issue,
incluir `Closes #N` en el cuerpo de la PR — **en texto plano, nunca entre backticks**: entre
backticks GitHub no lo enlaza y el issue sobrevive abierto sin avisar. El avance lo calcula
GitHub; no hay ningún documento de estado que mantener a mano.

> **Si una PR queda en «Expected — Waiting for status to be reported»**, el workflow no se disparó.
> Pasa cuando GitHub reapunta la base de una PR encadenada al mergear su base: el reapuntado no
> emite evento `pull_request`. Se destraba cerrando y reabriendo la PR (`gh pr close N && gh pr
> reopen N`), que emite `reopened`.

---

## Documentación

Cada feature deja sus artefactos en `specs/<feature>/`: `spec.md` (requisitos y criterios
medibles), `plan.md`, `research.md` (las decisiones `D1…Dn` con sus fuentes), `data-model.md`,
`contracts/openapi.yaml` y `quickstart.md`.

| Documento | Contenido |
|-----------|-----------|
| [`specs/002-workflow-engine/spec.md`](specs/002-workflow-engine/spec.md) | Requisitos del motor y criterios medibles — el núcleo de la tesis |
| [`specs/003-request-form-rules/research.md`](specs/003-request-form-rules/research.md) | Por qué las reglas de negocio son configuración y no código |
| [`specs/004-public-request-capture/spec.md`](specs/004-public-request-capture/spec.md) | El canal público: qué acepta, qué nunca devuelve y por qué |
| [`specs/005-formal-document/spec.md`](specs/005-formal-document/spec.md) | El DO-FR-100 como salida del sistema |
| [`specs/006-verifiable-document-seal/research.md`](specs/006-verifiable-document-seal/research.md) | Las decisiones D1–D11 del sello, incluida la reproducibilidad del PDF |
| [`specs/001-auth-login/integracion-frontend.md`](specs/001-auth-login/integracion-frontend.md) | Guía de integración para el SPA |
| [`.specify/memory/constitution.md`](.specify/memory/constitution.md) | La constitución del proyecto: **7 principios, v2.3.0** |
| [`docs/nuevo-proyecto/01-planteamiento/arbol-de-problemas.md`](docs/nuevo-proyecto/01-planteamiento/arbol-de-problemas.md) | Planteamiento (Marco Lógico), alcance y supuestos |

Flujo SDD con **Spec Kit v0.8.12**: `specify → plan → tasks → implement`. Los commits de fase
son **manuales**: los hooks de auto-commit están apagados uno por uno en
`.specify/extensions/git/git-config.yml`.

La versión vigente de la constitución se lee de una línea, sin creerle a este archivo:

```bash
grep -n '^\*\*Versión\*\*' .specify/memory/constitution.md
```

---

## Equipo

Backend: Julian Villanueva · Frontend: Juan Ramirez. Universidad Remington — Ingeniería de Sistemas (Distancia,
SNIES 53112). Documentación, commits y comentarios en español; identificadores de código en inglés.
