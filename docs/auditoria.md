# Convenia — Documento de Auditoría

**Proyecto:** Convenia — Gestor SaaS de Convenios de Práctica Profesional
**Institución:** Universidad Remington — Facultad de Ingeniería
**Marco normativo:** Resolución CF No. 002 de 2024
**Versión backend:** 0.10.0
**Fecha:** 2026-04-24

---

## 6. Desarrollo del Proyecto

El proyecto se desarrolló siguiendo un ciclo iterativo e incremental dividido en cuatro etapas, entregando 10 versiones funcionales (de `v0.1.0` a `v0.10.0`).

### 6.1 Análisis

El análisis partió del reglamento institucional — Resolución CF No. 002 de 2024 de la Facultad de Ingeniería — del cual se extrajeron los requisitos funcionales y las reglas de negocio.

**Actores identificados (6 roles):**
- `ADMIN` — super-administrador multi-tenant.
- `SECRETARY` — secretaría académica.
- `COORDINATOR` — coordinación de prácticas.
- `ACADEMIC_ADVISOR` — docente asesor.
- `COMPANY_TUTOR` — tutor co-formador en empresa.
- `STUDENT` — practicante.

**Reglas normativas relevadas:**
- Créditos académicos mínimos: 80 % (nivel Profesional) y 70 % (nivel Tecnológico).
- Mínimo 2 seminarios de práctica completados antes del envío de la solicitud.
- Duración: mínimo 4 meses (20 h/semana), máximo 12 meses (48 h/semana). Modalidad `APPRENTICESHIP` de duración exacta de 6 meses.
- Mínimo 3 visitas de seguimiento del asesor académico antes de abrir la evaluación.
- Nota final calculada como 50 % asesor académico + 50 % tutor de empresa, en escala 0.0–5.0.
- Documentos obligatorios por fase: CV, NIT, RUT, Cámara de Comercio, Contrato, Cédula, EPS, ARL y Plan de Trabajo.

**Máquina de estados derivada de la normativa:**
```
DRAFT → ADMIN_REVIEW → COORDINATION_REVIEW → PENDING_SIGNATURE → ACTIVE → EVALUATION → FINISHED
              ↓                    ↓
          (vuelve DRAFT)       REJECTED (terminal)
```

### 6.2 Diseño

**Arquitectura general:** aplicación web cliente-servidor SaaS multi-tenant. El aislamiento por institución se implementa mediante la columna `university_id` presente en todas las tablas operativas; el rol `ADMIN` opera cross-tenant (`university_id = NULL`).

**Stack tecnológico:**
- **Backend:** Spring Boot 4.0.5, Java 21 LTS, PostgreSQL, JWT (HMAC-SHA-256, expiración 24 h), MapStruct, Flyway, Lombok.
- **Frontend:** Angular 21 (componentes standalone, signals, `ChangeDetectionStrategy.OnPush`, reactive forms, Angular Material).
- **Almacenamiento de archivos:** Cloudflare R2 (S3-compatible), acceso vía AWS SDK for Java v2.
- **Firmas digitales:** Documenso v2 REST API (modo plantilla y modo carga directa con webhook de activación).
- **Generación de PDF:** Thymeleaf + OpenHTMLToPDF para el convenio y la constancia de culminación.

**Diseño de seguridad por capas:**
1. Autenticación JWT apátrida (stateless) con filtro `JwtAuthenticationFilter`.
2. Autorización basada en roles (RBAC) mediante `@PreAuthorize` y `@EnableMethodSecurity`.
3. Aislamiento por tenant vía helper `assertTenantAccess` y helpers especializados (`assertAgreementReadAccess`, `assertDocumentAccess`, `assertCertificateAccess`).
4. Invariantes de base de datos mediante restricciones `CHECK` (3 constraints de integridad semántica: no-admin-tiene-universidad, rechazado-tiene-razón, nota-final-requiere-fuentes, más el `chk_certificate_approval_pair` de aprobación de constancia).
5. Manejo uniforme de errores RFC 7807 (Problem Details) para todas las respuestas de error.

**Modelo de datos:** 10 migraciones Flyway versionadas, desde el esquema inicial (V1.0.0) hasta la normalización de documentos (V1.1.0) y el paso formal de aprobación de constancia (V1.1.1). Incluye 10 índices de optimización (parciales y compuestos para consultas de coordinador).

### 6.3 Implementación

La implementación se realizó en 10 iteraciones entregables:

| Versión | Contenido principal |
|---------|---------------------|
| 0.1.0 | Base: JWT, máquina de estados DRAFT→ACTIVE, PDF, multi-tenancy, Flyway V1.0.0–V1.0.4 |
| 0.2.0 | Integración Documenso v2 (template + direct mode), webhook, APIs de soporte (dropdowns) |
| 0.3.0 | Cloudflare R2, registro de visitas, calificación, subida de documentos, creación de usuarios, Swagger Bearer Auth global, 37 tests unitarios |
| 0.4.0 | Revisión de máquina de estados: EVALUATION y FINISHED, rechazo administrativo no terminal, ventanas de carga por fase |
| 0.5.0 | Cobertura RFC 7807 completa, corrección de bugs críticos (validación 3 visitas, null-safety) |
| 0.6.0 | Endpoint de descarga de documentos, validación de rol al asignar asesor/tutor |
| 0.7.0 | Constancia de culminación (V1.0.8), `users.full_name NOT NULL` con backfill en 4 etapas (V1.0.7), hardening de credenciales |
| 0.8.0 | Normalización de documentos a tabla `agreement_documents` (V1.1.0), upsert atómico `ON CONFLICT`, autorización endurecida |
| 0.9.0 | Corrección de IDOR en listado/detalle de convenios, aprobación formal de constancia (V1.1.1), alta de `COORDINATOR` controlada por jerarquía |
| 0.10.0 | Límite multipart 5 MB con handler 413, endpoint `GET /universities`, downgrade a Java 21 LTS para compatibilidad con PaaS |

**Integraciones externas implementadas:**
- Cloudflare R2: `upload`, `download`, `delete` vía `S3Client` con `pathStyleAccessEnabled`.
- Documenso v2: creación de sobre, distribución, descarga del PDF firmado, verificación de webhook con `MessageDigest.isEqual` (comparación en tiempo constante).
- Generación de PDFs: convenio completo y constancia de culminación con cache determinista en R2 (`certificates/{universityId}/{agreementId}.pdf`).

**Endpoints principales del backend (REST):** 24 endpoints agrupados en autenticación (`/auth/...`), convenios (`/api/v1/agreements/...`), empresas, estudiantes, usuarios, universidades, visitas y webhooks.

### 6.4 Pruebas

**Pruebas automatizadas (unitarias):**
- Stack: JUnit 5 + Mockito + AssertJ.
- Cobertura medida con JaCoCo 0.8.13.
- **80 tests, 0 fallos** en la versión actual.
- Suites destacadas:
  - `AgreementServiceImplTest` — transiciones de estado, calificaciones, rechazo, aislamiento por tenant.
  - `UploadDocument`, `DownloadDocumentTests`, `ListDocumentsTests` — autorización por rol/asignación.
  - `ApproveCertificate`, `DownloadCertificate` — flujo de constancia con aprobación.
  - `ListAgreementsByRole`, `TenantIsolation` — regresiones de IDOR.
  - `UserServiceImplTest` — jerarquía de creación (ADMIN crea COORDINATOR, no al revés).

**Pruebas de revisión automatizada mediante agentes especializados:**
- `java-reviewer` — revisión de patrones Spring Boot, JPA, concurrencia.
- `security-reviewer` — detección de vulnerabilidades (OWASP Top 10, IDOR, inyección).
- `database-reviewer` — revisión de migraciones, índices y restricciones.

**Pruebas manuales:** validación end-to-end de cada estado del flujo desde el frontend Angular (login → creación → revisión → firma → visitas → evaluación → constancia).

---

## 7. Resultados del Proyecto

### 7.1 Resultados obtenidos

- **MVP funcional completo** del ciclo de convenio de práctica, desde la creación por el estudiante hasta la emisión de la constancia de culminación aprobada por coordinación.
- **Cumplimiento estricto** del reglamento: las reglas de la Resolución 002-2024 están codificadas en validaciones de servicio (créditos, seminarios, duración, visitas) y en restricciones de base de datos (`CHECK` constraints).
- **Sistema multi-tenant** operativo: la arquitectura soporta múltiples facultades o instituciones sin cambios de código.
- **Base de código estable:** 80 tests automatizados con 0 fallos, 9 migraciones Flyway aplicadas sin downtime ni pérdida de datos.
- **Auditabilidad:** cambios de estado persistidos en `agreement_status_history`; aprobación de constancia registra `approved_at` y `approved_by`.
- **Integración con servicios de terceros** en producción real: Cloudflare R2 (almacenamiento) y Documenso (firma electrónica).

### 7.2 Procesos automatizados

El sistema automatiza tareas que antes eran manuales y sujetas a error humano:

| Proceso automatizado | Qué reemplaza |
|----------------------|---------------|
| **Generación del PDF del convenio** con datos y firmas pre-configuradas | Redacción manual en Word por parte de la coordinación |
| **Envío automático a firma electrónica** (Documenso v2) | Impresión, firma física y escaneo |
| **Activación del convenio por webhook** al completarse las 3 firmas | Revisión manual del estado de las firmas |
| **Cálculo automático de la nota final** (50 % asesor + 50 % tutor) | Cálculo manual en hoja de cálculo |
| **Transición automática** `EVALUATION → FINISHED` al recibir ambas calificaciones | Cierre manual por parte del coordinador |
| **Generación y cacheo de la constancia de culminación** | Emisión manual de certificados |
| **Validación automática de requisitos normativos** (créditos, seminarios, duración, visitas) | Verificación manual documento por documento |
| **Almacenamiento versionado de documentos** con re-upload atómico y borrado del archivo anterior | Gestión manual de carpetas institucionales |

### 7.3 Mejoras logradas

**Respecto al proceso manual previo:**
- **Trazabilidad completa:** cada cambio de estado, aprobación y calificación queda registrado con usuario y fecha.
- **Reducción de errores:** las validaciones normativas se ejecutan en todos los casos, no dependen del criterio humano de turno.
- **Aislamiento entre roles:** un asesor solo ve sus convenios asignados; un estudiante solo los propios. Se corrigió durante el desarrollo una vulnerabilidad tipo IDOR (`v0.9.0`) que permitía a asesores del mismo tenant ver convenios ajenos.
- **Seguridad por capas:** autenticación JWT, autorización por rol, aislamiento por tenant, restricciones a nivel de base de datos e invariantes CHECK. Defensa en profundidad en lugar de confiar en una sola capa.
- **Integridad garantizada a nivel de esquema:** imposible tener un convenio rechazado sin razón, o una constancia aprobada sin aprobador — la base de datos misma lo rechaza.

**Respecto al proceso con herramientas no integradas** (ej. correo + Word + Drive):
- **Punto único de verdad:** todos los actores ven el mismo estado del convenio en tiempo real.
- **Documentación centralizada** en Cloudflare R2 con control de acceso por rol.
- **Firma electrónica legalmente válida** (Documenso) en lugar de firmas escaneadas.

---

## 8. Manual de Usuario

Manual breve por rol. Cada flujo está pensado para completarse en pocos pasos; el sistema valida automáticamente cada transición.

### 8.1 Acceso inicial

1. Ingresar a la URL del sistema.
2. En la pantalla de **Login**, introducir correo y contraseña.
3. Para usuarios nuevos (estudiante, asesor, tutor de empresa), usar **Registrarse** y completar el formulario según el rol.

> La contraseña debe tener mínimo 8 caracteres. Los campos marcados con `*` son obligatorios.

### 8.2 Rol Estudiante (`STUDENT`)

1. **Crear convenio** desde *Convenios → Nuevo*. Seleccionar empresa, asesor, tutor, modalidad y fechas. El sistema valida créditos mínimos, duración y seminarios.
2. **Subir hoja de vida (CV)** en la fase `DRAFT`.
3. **Enviar a revisión** con el botón *Enviar a revisión*. El convenio pasa a `ADMIN_REVIEW`.
4. Si la secretaría devuelve el convenio a borrador, revisar el motivo, corregir y reenviar.
5. Cuando el convenio llegue a `PENDING_SIGNATURE`, **subir los documentos de afiliación** (Contrato, Cédula, EPS, ARL, Plan de Trabajo) y **firmar** el documento en el enlace que llega al correo (Documenso).
6. En fase `FINISHED`, una vez aprobada la constancia por coordinación, **descargar la constancia de culminación** desde el detalle del convenio.

### 8.3 Rol Tutor de Empresa (`COMPANY_TUTOR`)

1. En la fase `DRAFT` del convenio, **subir los documentos de la empresa**: NIT, RUT y Cámara de Comercio.
2. Cuando llegue el correo de Documenso, **firmar el convenio**.
3. En la fase `EVALUATION`, **registrar la calificación final** del practicante (escala 0.0–5.0) desde el detalle del convenio.

### 8.4 Rol Secretaría Académica (`SECRETARY`)

1. En la bandeja *Convenios*, filtrar por estado `ADMIN_REVIEW`.
2. **Revisar documentos** del estudiante y de la empresa desde el detalle del convenio.
3. Elegir una acción:
   - **Aprobar** → el convenio pasa a `COORDINATION_REVIEW`.
   - **Rechazar** → el convenio vuelve a `DRAFT` con la razón visible para el estudiante.

### 8.5 Rol Coordinador (`COORDINATOR`)

1. Revisar los convenios en `COORDINATION_REVIEW`.
2. **Avalar** el convenio → se genera el PDF y se envía a firmas vía Documenso. El convenio pasa a `PENDING_SIGNATURE`.
3. Cuando un convenio llega a `ACTIVE`, hacer seguimiento a las visitas del asesor.
4. En `ACTIVE` con 3 o más visitas registradas, **iniciar la evaluación**. El convenio pasa a `EVALUATION`.
5. Cuando el convenio llegue a `FINISHED`, **aprobar la constancia** de culminación para que el estudiante pueda descargarla.
6. Desde *Usuarios*, **crear cuentas** para asesores, tutores y secretarías de su universidad.

### 8.6 Rol Asesor Académico (`ACADEMIC_ADVISOR`)

1. En el detalle del convenio `ACTIVE`, usar *Gestionar visitas* para **registrar las visitas** (mínimo 3). Cada visita requiere fecha, tipo (presencial o virtual) y observaciones (10–2000 caracteres).
2. En la fase `EVALUATION`, **registrar la calificación** del estudiante (escala 0.0–5.0).

### 8.7 Rol Administrador (`ADMIN`)

1. Acceso cross-tenant: ve y puede operar sobre convenios de todas las universidades.
2. Desde *Usuarios*, **crear cuentas** seleccionando la universidad desde el dropdown (incluido el rol `COORDINATOR`, que solo el ADMIN puede dar de alta).
3. Respaldo operativo ante incidencias — puede ejecutar las mismas acciones que cualquier rol cuando sea necesario para desbloquear un convenio.

### 8.8 Recomendaciones operativas

- **No compartir credenciales.** Cada usuario debe tener su propia cuenta.
- **Tamaño máximo de archivo:** 5 MB por documento. Si un PDF excede el límite, comprimirlo o reducir su resolución antes de subir.
- **Estados del convenio:** el sistema **no permite saltarse etapas**. Cada transición requiere que se cumplan sus pre-condiciones (documentos, visitas, calificaciones).
- **Ante un error con mensaje claro**, leer el motivo antes de reintentar — el sistema indica qué falta o qué es inválido.
- **Si aparece un error 403 (Prohibido)**, el rol actual no tiene permiso para esa acción. Contactar al coordinador.

---

## 10. Impacto del Proyecto en la Empresa

El sistema aporta valor a la Universidad Remington en cuatro dimensiones concretas:

**Operativo.** El ciclo completo del convenio — creación, revisión, firma, seguimiento, evaluación y constancia — se ejecuta dentro de una sola plataforma. Procesos que antes involucraban correos, Word, Drive y firmas físicas escaneadas ahora son transacciones auditadas con estados persistidos. La generación automática del PDF, el envío a firma vía Documenso y la activación por webhook eliminan intervención manual en ≥ 5 pasos del flujo anterior.

**Cumplimiento normativo.** La Resolución CF No. 002 de 2024 está codificada en el sistema en dos capas: validaciones de servicio (créditos mínimos, seminarios, duración, 3 visitas, calificación 50/50) y restricciones de base de datos (`CHECK` constraints que impiden estados inválidos a nivel de esquema). Esto facilita auditorías internas y reduce el riesgo de no conformidades.

**Gobernanza y trazabilidad.** Cada transición de estado, aprobación, calificación y descarga de documentos queda registrada con usuario y fecha. La tabla `agreement_status_history` permite reconstruir el ciclo completo de cualquier convenio. La aprobación formal de la constancia por parte de coordinación (v0.9.0) deja un rastro auditable con `approved_at` y `approved_by`.

**Estratégico.** La arquitectura multi-tenant (`university_id` en todas las tablas) permite que el mismo despliegue atienda varias facultades de Remington sin cambios de código, y es extensible a otras universidades como producto SaaS. El `ADMIN` cross-tenant facilita la operación central mientras cada facultad conserva su aislamiento.

---

## 11. Recomendaciones

### 11.1 Antes del despliegue a producción real (bloqueantes)

1. **Rotar credenciales expuestas en el histórico de git**: R2 access/secret keys, Documenso API token y JWT secret (ya identificadas y rastreadas).
2. **Separar seeds de prueba de migraciones de esquema** usando `spring.flyway.locations` por perfil (`dev` vs `prod`). Las V1.0.1, V1.0.2, V1.0.4 y parte de V1.0.7 contienen usuarios con contraseña de test que no deben crearse en producción.
3. **Reducir el nivel de logging** en el perfil `prod`: `org.hibernate.SQL: INFO` y `org.hibernate.orm.jdbc.bind: WARN`. Actualmente están en `DEBUG` y `TRACE`, lo que filtra parámetros SQL (incluidos hashes y correos) en los logs.
4. **Deshabilitar Swagger UI y `/v3/api-docs`** en `prod` (`springdoc.swagger-ui.enabled=false`, `springdoc.api-docs.enabled=false`).
5. **Configurar `APP_ALLOWED_ORIGINS`** con el dominio real del frontend (Vercel u otro), no `localhost`.
6. **Configurar el webhook secret de Documenso** (`DOCUMENSO_WEBHOOK_SECRET`) — obligatorio en producción para validar los callbacks de firma.

### 11.2 Mejoras técnicas de calidad (deuda técnica documentada)

| # | Ítem | Impacto |
|---|------|---------|
| 1 | Migrar `LocalDateTime` → `Instant` y usar `TIMESTAMPTZ` en las 10 tablas | Elimina ambigüedad de zona horaria en sistemas distribuidos |
| 2 | `@Lock(LockModeType.PESSIMISTIC_WRITE)` en el cache de constancia | Cierra la ventana TOCTOU de generación duplicada |
| 3 | Validación defensiva en `JwtAuthenticationFilter` para tokens no-ADMIN con `universityId == null` | Cierra un posible bypass de tenant isolation |
| 4 | Extraer magic numbers (3 visitas, 4/12 meses, 20/48 h/sem, 80/70 % créditos) a `PracticeConstants` citando la Resolución | Facilita mantenimiento si la normativa cambia |
| 5 | Política de lifecycle en R2 o job de limpieza para objetos huérfanos | Previene crecimiento indefinido del bucket |

### 11.3 Ampliaciones funcionales sugeridas

- **Notificaciones por correo electrónico** en cada transición de estado (al estudiante, asesor, tutor y coordinador según corresponda).
- **Dashboard analítico para coordinación**: convenios por estado, tiempo promedio en cada fase, convenios por empresa, por programa académico, tasa de rechazo.
- **Integración con el sistema académico de Remington (SIS)** para evitar duplicar estudiantes y validar créditos reales contra el histórico académico.
- **Aplicación móvil para asesores** — registro de visitas desde campo con geolocalización y foto opcional.
- **Reportes periódicos automáticos** (CSV/PDF) para la facultad, exportables por rango de fechas.
- **Evaluación 360°** — agregar evaluación del estudiante hacia la empresa como insumo para la acreditación de prácticas.
- **Onboarding multi-institucional**: la arquitectura multi-tenant ya lo soporta; falta el flujo de alta de nuevas universidades con su propia normativa.
- **Accesibilidad WCAG 2.1 AA** en el frontend Angular como requisito para instituciones públicas.
- **Timeline visual** del historial de estados en el detalle del convenio (consume `agreement_status_history`, pendiente en frontend).

---

## 12. Conclusiones

El proyecto entregó un MVP funcional que cubre el ciclo completo del convenio de práctica profesional bajo la Resolución CF No. 002 de 2024, con 80 pruebas automatizadas y 10 iteraciones incrementales. El enfoque iterativo permitió detectar y corregir temprano vulnerabilidades reales — un IDOR entre asesores del mismo tenant en `v0.9.0` — antes de exponer el sistema a usuarios.

Los aprendizajes principales fueron tres: la seguridad debe aplicarse **en capas** (autenticación, autorización, aislamiento por tenant, invariantes de base de datos) y no confiar en una sola; las reglas normativas ganan robustez cuando se codifican también a nivel de esquema mediante `CHECK` constraints; y la revisión continua con agentes especializados acelera la detección de problemas que pasarían desapercibidos en revisión manual. Con las acciones de hardening listadas en §11.1 completadas, el sistema queda apto para producción.
