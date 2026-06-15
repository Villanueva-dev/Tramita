# TRÁMITA
## Motor de Workflow Configurable para Gestión de Solicitudes Académicas

**Proyecto de Grado — Ingeniería de Sistemas**  
**Universidad Remington — Sede Cali**  
**Modalidad Distancia — SNIES 53112**

---

# 📄 DOCUMENTACIÓN DEL PROYECTO

**Versión:** 1.0.0  
**Fecha:** 15 de junio de 2026  
**Estado:** Línea base — Aprobado para Sprint 1  
**Autores:** Equipo de Proyecto de Grado — Programa de Ingeniería de Sistemas

---

## TABLA DE CONTENIDOS

1. [Resumen Ejecutivo](#resumen-ejecutivo)
2. [Introducción](#introducción)
3. [Definición del Problema](#definición-del-problema)
4. [Solución Propuesta](#solución-propuesta)
5. [Requerimientos Funcionales](#requerimientos-funcionales)
6. [Requerimientos No Funcionales](#requerimientos-no-funcionales)
7. [Arquitectura Técnica](#arquitectura-técnica)
8. [Plan de Implementación](#plan-de-implementación)
9. [Gestión de Riesgos](#gestión-de-riesgos)
10. [Criterios de Éxito](#criterios-de-éxito)
11. [Referencias](#referencias)
12. [Anexos](#anexos)

---

# RESUMEN EJECUTIVO

## Problema

La **Coordinación Académica de la Sede Cali de la Universidad Remington** gestiona dos procesos críticos — **adición de créditos** y **novedad de notas** — mediante un flujo manual basado en correos electrónicos, formatos Word sin validación, firmas escaneadas y memoria humana, sin un sistema que orqueste el ciclo de vida de cada solicitud ni que registre trazabilidad.

**Impacto observado:**
- Tiempos de ciclo impredecibles (1 semana a 2 meses).
- Re-trabajo por errores de formato detectados tardíamente.
- Trámites que se pierden en bandejas de correo saturadas.
- Opacidad total para el estudiante (no sabe en qué estado está su solicitud).
- Sobrecarga administrativa sobre la coordinadora (punto único de orquestación humano).

## Solución

**Trámita** es un **motor de workflow configurable** que:

1. **Captura validada en origen** — Formularios con validación backend que reemplazan los Word manuales.
2. **Modela ambos trámites con la misma maquinaria** — Un único motor de workflow parametrizado por configuración (no código) maneja adición de créditos y novedad de notas.
3. **Genera PDF formal automático** — Al aprobarse, el sistema genera el documento institucional listo para asentar en QF (invariante de cierre).
4. **Registra trazabilidad inmutable** — Tabla append-only con restricciones SQL que imposibilita modificar el histórico.
5. **Proporciona visibilidad mediada** — La coordinadora consulta el sistema en < 1 minuto y responde al estudiante con certeza sobre el estado.

## Alcance MVP

- **2 trámites:** adición de créditos + novedad de notas.
- **1 sede:** Cali.
- **1 usuaria actora:** Coordinadora Académica (no técnica).
- **1 beneficiario notificado:** Estudiante (recibe aviso de cierre; sin login ni auditoría del sistema).
- **Plazo:** ~2.5 meses (mayo–agosto 2026).
- **Equipo:** 2 personas.
- **Entregable:** Demo presentable + defensa académica.

## Métricas de Éxito

| Variable | Línea Base | Meta MVP | Medición |
|----------|-----------|----------|----------|
| **Re-trabajo (devoluciones/solicitud)** | ~2-3 | < 1 | Conteo en demo |
| **Opacidad (tiempo buscar estado)** | ~5-10 min (búsqueda en correos) | < 1 min (timeline en UI) | Demo coordinadora |
| **Tiempo ciclo coordinadora** | 1-2 semanas (captura + validación manual) | 5-10 min (formulario validado) | Observación |

---

# INTRODUCCIÓN

## Contexto Institucional

La Universidad Remington es una institución de educación superior privada que opera en modalidad presencial y distancia. La **Sede Cali** es responsable de la coordinación académica de estudiantes en formación técnica y profesional, adhiriéndose a los estándares normativos nacionales (SNIES, MEN) y a políticas institucionales propias.

Dos procesos académicos críticos requieren gestión continua:
- **Adición de Créditos:** Estudiantes homologantes o con restricciones de matrícula solicitan excepciones para matricular asignaturas que superan el tope automático de CLASS (sistema académico institucional).
- **Novedad de Notas:** Después del cierre nacional del período académico, docentes o estudiantes solicitan registro de notas faltantes o erróneamente no cargadas en CLASS.

Ambos procesos han permanecido **altamente manualizados** durante años, con infraestructura limitada a herramientas genéricas (correo, Word, OneDrive, QF — gestor documental institucional).

## Motivación Académica

Este proyecto responde a la **pregunta de investigación acotada:**

> **¿Cómo puede un sistema de gestión de solicitudes académicas con un motor de workflow configurable reducir los tiempos de ciclo, el re-trabajo y la opacidad de la tramitación de adición de créditos y novedad de notas en la Sede Cali de la Universidad Remington?**

El aporte académico reside en demostrar que **dos procesos operativamente distintos pueden ser modelados con una única maquinaria parametrizada**, sin duplicación de código, validando así el principio de genericidad en diseño de workflows.

---

# DEFINICIÓN DEL PROBLEMA

## Síntesis del Árbol de Problemas

### Problema Central

*La gestión de solicitudes académicas con flujo multi-aprobación depende de correos electrónicos, formatos Word sin validación y memoria humana, sin un workflow estructurado que orqueste el ciclo de vida, valide datos de entrada y registre trazabilidad.*

### Efectos Observados (E1-E5)

| ID | Efecto | Manifestación | Atribuibilidad a Trámita |
|----|--------|---------------|------------------------|
| **E1** | Estudiantes no quedan matriculados/sin nota a tiempo | Mencionado en entrevistas | Parcial (depende CLASS y Registro) |
| **E2** | Re-trabajo por errores formales | Formato vuelve atrás varias veces | ✅ **100% atribuible** |
| **E3** | Sobrecarga administrativa | Coordinadora orquesta manualmente | ✅ Reducible |
| **E4** | Trámites perdidos en bandejas | "Se pueden traspapelar" | ✅ **100% evitable** |
| **E5** | Tiempos impredecibles (1 semana - 2 meses) | Estimación coordinadora | Parcial (40% Trámita, 60% externos) |

### Causas Raíz (C1-C7)

| Causa | Solución Trámita | Implementabilidad |
|-------|------------------|------------------|
| C1: No hay repositorio central del estado | Base de datos + bandeja en UI | ✅ Sprint 1 |
| C2: Formatos manuales sin validación | Formularios con validación backend | ✅ Sprint 1 |
| C3: Firmas escaneadas sin trazabilidad digital | Sello electrónico verificable + tabla de auditoría | ✅ Sprint 2 |
| C4: No hay bandeja por rol | Bandeja de coordinadora | ✅ Sprint 3 |
| C5: Aprobaciones por correo sin trazabilidad | Tabla `solicitud_event` append-only | ✅ Sprint 1 |
| C6: Anexos manejados manualmente | Almacenamiento centralizado | ✅ Sprint 1-2 |
| C7: Comunicación dispersa (6 canales) | Cockpit centralizado + timeline | ✅ Sprint 1 |

---

# SOLUCIÓN PROPUESTA

## Visión General

**Trámita** es un **motor de workflow configurable** que:

1. **Modela estados y transiciones** de solicitudes académicas.
2. **Parametriza por configuración** (no código) los diferenciadores entre trámites.
3. **Valida en origen** mediante formularios con reglas de negocio.
4. **Genera artefactos formales** (PDFs) automáticamente.
5. **Registra auditoría inmutable** de cada decisión.
6. **Proporciona visibilidad operativa** a la coordinadora en tiempo real.

## Características Clave

### 1. Motor Configurable (Principio III)

Ambos trámites comparten el **mismo esqueleto de máquina de estados:**

```
BORRADOR → ENVIADO → APROBADO_POR_COORD → FINALIZADO
```

Lo que cambia entre trámites es **puramente configuración:**

| Aspecto | Adición de Créditos | Novedad de Notas |
|--------|-------------------|-----------------|
| **Formulario** | Estudiante + asignatura + motivo | Estudiante + asignatura + nota + justificación |
| **Validaciones** | Tope de créditos (21), ventana temporal (2-3 meses) | Sin tope, sin plazo |
| **Aprobadores** | Coordinadora → Facultad → Registro | Coordinadora → Registro (coordinadora marca transiciones) |
| **Profundidad de automatización** | Alta (transiciones automáticas en segundo plano) | Media (transiciones marcadas manualmente) |
| **PDF** | Formato adición de créditos (DFR100 v0.1) | Formato novedad de notas |

**Código compartido:** Motor base + validador + generador PDF + auditador.  
**Código específico:** Formatos de entrada/salida, plantillas PDF, reglas negocio por tipo.

### 2. Validación en Origen (Sprint 1 - SP2)

Los formularios **validan en backend antes de persistir:**

**Adición de créditos:**
- Código de asignatura existe en CLASS.
- Cantidad total ≤ 21 créditos.
- Dentro de ventana temporal (primeros 2-3 meses).
- Asignatura está disponible para matricular en CLASS.

**Novedad de notas:**
- Estudiante existe.
- Docente existe.
- Asignatura existe.
- (Validación de asistencia: manual por coordinadora, no automatizable).

**Rechazo:** Si validación falla, retorna error 400 con detalles específicos. Usuario corrige y reenvía.

### 3. PDF Formal Automático (Sprint 2 - SP3)

**Invariante (Principio III):** Toda solicitud que alcanza estado `FINALIZADO` tiene un PDF formal generado.

El PDF:
- Contiene todos los datos del trámite.
- Incluye campos para firmas (solicitante + aprobadores).
- Está codificado según estándar institucional (DFR100 vX.Y).
- Es listo para asentar en QF sin modificaciones.

**Motor de generación:** Thymeleaf + OpenHTMLToPDF (como en Convenia).

### 4. Auditoría Inmutable (Sprint 1 - SP6, Sprint 2 - SP4)

Tabla `solicitud_event` (append-only):

```sql
CREATE TABLE solicitud_event (
  id BIGSERIAL PRIMARY KEY,
  solicitud_id BIGINT NOT NULL REFERENCES solicitud,
  event_type VARCHAR(50) NOT NULL, -- CREATED, APPROVED, REJECTED, PDF_GENERATED, etc.
  actor_id BIGINT, -- Usuario que ejecutó la transición
  occurred_at TIMESTAMP DEFAULT NOW(),
  comment TEXT,
  payload_json JSONB -- Datos del evento
);

-- Protección a nivel SQL
ALTER TABLE solicitud_event DISABLE TRIGGER ALL;
REVOKE UPDATE, DELETE ON solicitud_event FROM app_user;
```

Cada transición del motor escribe una fila. El timeline es **inmutable e impopular.**

### 5. Visibilidad Mediada (Sprint 1 - SP6)

**Timeline de auditoría para la coordinadora:**

UI muestra:
- Nombre estudiante.
- Tipo de solicitud.
- Estado actual.
- Histórico completo de eventos (fechas, actores, acciones).
- Comentarios de aprobadores.

**Consulta del estudiante:** Via coordinadora → ella busca por nombre + cédula en el timeline → responde al estudiante.

**Notificación final:** Solo al completarse (estado FINALIZADO), correo automático al estudiante: "Tu solicitud ha sido completada. El PDF está disponible para asentar en CLASS."

---

# REQUERIMIENTOS FUNCIONALES

## RF1: Captura de Solicitud

### RF1.1 Formulario de Adición de Créditos

**Actor:** Coordinadora (captura en nombre del estudiante).

**Campos:**
- Código de estudiante (cédula).
- Nombre estudiante.
- Correo institucional.
- Código asignatura (a adicionar).
- Nombre asignatura (auto-relleno desde CLASS).
- Créditos asignatura.
- Motivo solicitud (texto libre).
- Firma solicitante (escaneada o digital).

**Validaciones:**
- Campo requerido si está marcado.
- Código de asignatura existe en CLASS.
- Suma de créditos ≤ 21.
- Dentro de ventana de 2-3 meses desde inicio semestre.
- Firma presente (no mecánica).

**Resultado:**
- Solicitud creada en estado `BORRADOR`.
- Evento creado en `solicitud_event`.
- Coordinadora puede editar o enviar.

---

### RF1.2 Formulario de Novedad de Notas

**Actor:** Coordinadora (captura en nombre de estudiante o docente).

**Campos:**
- Código de estudiante / docente (quién origina).
- Nombre estudiante.
- Correo institucional.
- Código asignatura.
- Nombre asignatura (auto-relleno).
- Nota original (estado en CLASS).
- Nota corregida (a registrar).
- Justificación (dropdown + texto):
  - "Pendiente: El docente no digitó las notas en tiempos estipulados."
  - "Materia no matriculada: La materia no fue matriculada en tiempos establecidos."
  - (Otros motivos)
- Firma solicitante.

**Validaciones:**
- Campos requeridos.
- Asignatura existe.
- Nota en escala válida (0.0-5.0).
- Firma presente.

**Resultado:**
- Solicitud creada en estado `BORRADOR`.
- Evento creado.

---

## RF2: Validación y Aprobación

### RF2.1 Aprobación por Coordinadora

**Actor:** Coordinadora.

**Acción:** Revisa solicitud en detalle.
- **Opción A:** Aprobar → solicitud pasa a `ENVIADO`.
- **Opción B:** Devolver para correcciones → solicitud vuelve a `BORRADOR` con motivo.
- **Opción C:** Rechazar definitivamente (solo adición si excede plazo) → `RECHAZADO_FINAL`.

**Validación manual (no automatizable):**
- Coordinadora verifica asistencia en archivo (firma estudiante en estado).
- Coordinadora verifica registro en planilla docente.

**Evento generado:** `APPROVED_BY_COORD` / `RETURNED` / `REJECTED`.

### RF2.2 Transición Automática a Aprobado Final

**Trigger:** Coordinadora marca "Enviar a Registro Medellín".
- **Adición de créditos:** Transición `ENVIADO` → `APROBADO_POR_COORD` (automática si validaciones OK).
- **Novedad de notas:** Transición `ENVIADO` → `APROBADO_POR_COORD` (coordinadora marca "Respuesta recibida de Registro").

**Evento:** `AUTO_APPROVED` / `APPROVED_BY_EXTERNAL`.

---

## RF3: Generación de PDF

### RF3.1 Generación Automática al Aprobar

**Trigger:** Transición a `APROBADO_POR_COORD`.

**Proceso:**
1. Sistema recupera plantilla de PDF (Thymeleaf).
2. Rellena con datos de solicitud.
3. Añade sello electrónico (hash SHA-256 + timestamp + actor).
4. Genera PDF.
5. Almacena en directorio controlado (local en MVP).
6. Registra ruta en `solicitud.pdf_path`.

**Plantilla:** Provista por coordinadora (Anexo B de entrevista 3).

**Sello verificable:** Campo de sello contiene: `SHA256(contenido_pdf) | timestamp | actor | firma_base64`.

**Evento:** `PDF_GENERATED`.

---

## RF4: Finalización y Notificación

### RF4.1 Marcar como Finalizado

**Actor:** Coordinadora.

**Acción:** Coordi verifica que PDF fue asentado en QF (fuera del sistema).
- Botón "Marcar como Finalizado".
- Solicitud transiciona a `FINALIZADO`.

**Evento:** `FINALIZED`.

### RF4.2 Notificación al Estudiante

**Trigger:** Transición a `FINALIZADO`.

**Notificación:**
- Correo institucional automático: "Tu solicitud [ID] ha sido completada."
- Opción de enviar mensaje por chat (template) desde cockpit.

**No incluir:** Detalles del proceso, críticas, solicitud de acciones.

**Evento:** `NOTIFICATION_SENT`.

---

## RF5: Consulta de Estado por Coordinadora

### RF5.1 Bandeja de Solicitudes

**Vista:** Todas las solicitudes, filtradas por estado y SLA.

**Columnas:**
- ID solicitud.
- Estudiante.
- Tipo de solicitud.
- Estado actual.
- Fecha de creación.
- Días en estado actual (SLA rojo si > X días).

**Acciones:**
- Clic → ir a detalle.
- Filtrar por estado, tipo, fecha.

### RF5.2 Detalle de Solicitud

**Vista:** Timeline de auditoría completo.

**Información mostrada:**
- Datos del solicitante.
- Datos de la solicitud (asignatura, monto, motivo).
- Transiciones de estado con fecha/hora.
- Actor de cada transición.
- Comentarios.
- PDF (si existe) → descargar.

**Acciones disponibles** (según estado):
- Aprobar / Devolver / Rechazar (en BORRADOR).
- Marcar como Enviado (en ENVIADO).
- Marcar como Finalizado (en APROBADO).

---

## RF6: Búsqueda Rápida

**Entrada:** Nombre + Cédula (búsqueda por historia de solicitudes).

**Resultado:** Solicitud(es) coincidente(s) con timeline completo.

**Uso:** Coordinadora responde estudiante en < 1 minuto.

---

# REQUERIMIENTOS NO FUNCIONALES

## RNF1: Rendimiento

- **Tiempo de respuesta API:** < 500 ms para endpoints de lectura.
- **Generación de PDF:** < 5 segundos.
- **Búsqueda:** < 1 segundo para 500 solicitudes.

## RNF2: Disponibilidad

- **Uptime:** 99% durante horarios operacionales (8 AM - 6 PM lunes-viernes).
- **Despliegue:** Local (docker-compose), no producción institucional en MVP.

## RNF3: Seguridad

- **Autenticación:** Spring Security + sesión por cookie HTTP.
- **Autorización:** RBAC (Role-Based Access Control) — solo coordinadora.
- **Cifrado en tránsito:** HTTPS (sí, incluso en local con certificados autofirmados).
- **Protección de datos:** PDFs almacenados en directorio controlado, sin copia en BD.
- **Auditoría:** Toda transición registrada de forma inmutable.

## RNF4: Usabilidad

- **Interfaz:** SPA React con diseño intuitivo (no requiere capacitación extensa).
- **Accesibilidad:** WCAG 2.1 AA mínimo.
- **Lenguaje:** Español.

## RNF5: Mantenibilidad

- **Código:** Clean Code, SOLID, comentarios en español.
- **Testing:** Tests unitarios en capas críticas (motor, validaciones, auditoría).
- **Documentación:** README, Swagger/OpenAPI, guía de configuración.

## RNF6: Escalabilidad (Futuro, no MVP)

- **Multi-tenancy:** Diseño preparado (columna `sede_id` en tablas), no implementado en MVP.
- **Performance:** Índices en BD para búsquedas comunes.

---

# ARQUITECTURA TÉCNICA

## Stack Mandatorio (Principio II)

**Backend:**
- Java 21 LTS.
- Spring Boot 4.x.
- PostgreSQL (BD relacional).
- Flyway (migraciones BD).
- OpenAPI/Springdoc (documentación API).

**Frontend:**
- React 18+ (SPA).
- TypeScript.
- Material-UI o similar (componentes).

**Despliegue:**
- Docker + docker-compose (local).

**Integración con externos:**
- CLASS: Caja negra (consultas de existencia de asignaturas, validación de prerrequisitos). API expuesta por Remington (asumimos disponible).
- QF: Caja negra (coordinadora asiente manualmente el PDF).

## Capas de Arquitectura

```
┌─────────────────────────────────────────┐
│         Frontend (React SPA)            │
├─────────────────────────────────────────┤
│   REST API (Spring Boot Controllers)    │
├─────────────────────────────────────────┤
│  Service Layer (Lógica de Negocio)      │
│  - Workflow Engine                      │
│  - Validador de Solicitudes             │
│  - Generador de PDF                     │
│  - Auditor (Evento)                     │
├─────────────────────────────────────────┤
│  Data Access Layer (JPA Repositories)   │
├─────────────────────────────────────────┤
│      PostgreSQL Database                │
└─────────────────────────────────────────┘
```

## Entidades Principales

### Solicitud
```
- id: Long
- tipo_solicitud: ENUM (ADICION_CREDITOS, NOVEDAD_NOTAS)
- estado: ENUM (BORRADOR, ENVIADO, APROBADO_COORD, FINALIZADO, RECHAZADO)
- estudiante_id: String (cédula)
- estudiante_nombre: String
- estudiante_correo: String
- asignatura_codigo: String
- asignatura_nombre: String
- asignatura_creditos: Integer
- motivo: Text
- firma_escaneada: BLOB (opcional)
- pdf_path: String
- created_at: Timestamp
- updated_at: Timestamp
```

### SolicitudEvent
```
- id: Long
- solicitud_id: Long (FK)
- event_type: VARCHAR (CREATED, APPROVED, REJECTED, PDF_GENERATED, etc.)
- actor_id: String (usuario)
- occurred_at: Timestamp
- comment: Text
- payload_json: JSONB
```

### Configuration
```
- id: Long
- solicitud_tipo: VARCHAR
- tope_creditos: Integer
- ventana_dias_inicio: Integer
- ventana_dias_fin: Integer
- requiere_plazo: Boolean
- plantilla_pdf_path: String
```

## Flujo de Solicitud (Máquina de Estados)

```
┌────────┐
│ START  │
└───┬────┘
    │ Usuario crea solicitud
    ▼
┌────────────┐
│ BORRADOR   │ ◄──┐ Coordinadora devuelve
└────┬───────┘    │ para correcciones
     │            │
     │ Coordinadora aprueba
     ├──────────────┘
     │
     ▼
┌────────────┐
│ ENVIADO    │ ◄──┐ Solo si adición
└────┬───────┘    │ y excede plazo
     │            │
     │ Genera PDF │ Coordinadora rechaza
     ├──────────────┘
     │
     ▼
┌──────────────────────┐
│ APROBADO_POR_COORD   │
└────┬─────────────────┘
     │
     │ Coordinadora marca finalizado
     ▼
┌────────────┐
│ FINALIZADO │
└────────────┘
     │
     └──► Notificación al estudiante
     
┌────────────┐
│ RECHAZADO  │ (terminal)
└────────────┘
```

## Validaciones de Negocio

**En formulario (backend):**
- Campos requeridos completos.
- Código de asignatura existe en CLASS.
- Créditos totales ≤ tope máximo (parámetro configurable).
- Fecha dentro de ventana (parámetro configurable).
- Firma no es mecánica (detectar si es solo nombre escrito).

**Manual (coordinadora):**
- Asistencia del estudiante (verificar en archivo).
- Registro en planilla docente.

---

# PLAN DE IMPLEMENTACIÓN

## Distribución por Sprint

### Sprint 1: Columna Vertebral (Semana 1-3)

**Objetivo:** Columna vertebral del sistema. Captura validada + motor básico + auditoría.

**Cubre:** SP1 (motor) + SP2 (formularios) + SP6 (timeline).

**Entregables:**

1. **Backend (Java 21 + Spring Boot 4):**
   - BD schema con Flyway (solicitud, solicitud_event, configuration, user).
   - Entidades JPA + Repositories.
   - Servicio de workflow (transiciones, invariantes).
   - Validador de solicitudes.
   - Servicio de auditoría (escribir eventos).
   - Controladores REST (POST /api/solicitudes, GET /api/solicitudes/{id}, etc.).
   - Autenticación básica (Spring Security + sesión).

2. **Frontend (React):**
   - Página de captura (adición de créditos).
   - Bandeja de solicitudes (tabla filtrada).
   - Detalle de solicitud (timeline de auditoría).
   - Búsqueda por nombre + cédula.

3. **Documentación:**
   - Swagger (OpenAPI) de API.
   - README de setup local.

**Definition of Done:**
- Tests unitarios del motor (transiciones, validaciones).
- Coordinadora prueba captura + bandeja + timeline.
- Coordinadora aprueba.

**Demo:**
- Capturar solicitud de prueba.
- Ver bandeja.
- Ver timeline completo.
- Responder pregunta: "¿Te sirve este modelo de captura?"

---

### Sprint 2: Salida Formal (Semana 4-5)

**Objetivo:** Generación de PDF + firma/sello + finales.

**Cubre:** SP3 (PDF) + SP4 (sello + auditoría aprobaciones).

**Entregables:**

1. **Backend:**
   - Generador de PDF con Thymeleaf + OpenHTMLToPDF.
   - Plantilla PDF (provided by coordinadora, Anexo B E3).
   - Sello electrónico: SHA-256 + timestamp + actor.
   - Endpoint `/api/solicitudes/{id}/pdf` (descargar).
   - Transición automática a APROBADO_POR_COORD.

2. **Frontend:**
   - Botón "Descargar PDF" en detalle.
   - Visualización del sello en PDF.

3. **Testing:**
   - Tests del generador de PDF.
   - Verificación de sello.

**Definition of Done:**
- PDF generado se puede abrir y contiene todos los datos.
- Sello es verificable.
- Coordinadora aprueba "¿Este PDF es asentable en QF?"

**Demo:**
- Aprobar una solicitud.
- Descargar PDF.
- Verificar contenido + sello.

---

### Sprint 3: Validación del Motor + Novedad de Notas (Semana 6-7)

**Objetivo:** Bandeja por rol + segundo trámite + validación de genericidad.

**Cubre:** SP5 (bandeja) + soporte completo a novedad de notas.

**Entregables:**

1. **Backend:**
   - Soporte completo de novedad de notas (formulario, validaciones, PDF distinct).
   - Bandeja refactorizada (filtros, SLA).
   - Notificación automática al estudiante (correo).
   - Configuración de templates por solicitud_tipo.

2. **Frontend:**
   - Formulario de novedad de notas.
   - Bandeja mejorada (columna SLA, filtros).
   - Opción de notificación manual (chat template).

3. **Testing:**
   - Ambos trámites end-to-end con el mismo motor.
   - Demo comparativa: mismo código, distinta configuración.

**Definition of Done:**
- Ambos trámites funcionan.
- Cambiar tipo de solicitud en formulario → cambia plantilla PDF automáticamente.
- Coordinadora valida: "¿Esto es un motor o dos sistemas?"

**Demo final (coordinadora + tutor):**
- Capturar adición de créditos.
- Capturar novedad de notas.
- Aprobar ambas.
- Descargar PDFs distintos.
- Mostrar timeline para ambas.
- Responder pregunta: "¿Es el mismo motor parametrizado o código duplicado?"

---

## Hitos Críticos

| Hito | Plazo | Responsable | Deliverable |
|------|-------|------------|-------------|
| Sprint 1 aprobado por coordinadora | Fin semana 3 | Equipo + Coord | Sistema operativo para adición |
| Plantilla PDF oficial entregada | Antes semana 4 | Coordinadora | Archivo PDF template (Anexo B) |
| Sprint 2 aprobado | Fin semana 5 | Equipo + Coord | PDF generado verificable |
| Tutor asignado | [BLOQUEANTE] | Universidad | Validación académica |
| Sprint 3 (demo final) | Fin semana 7-8 | Equipo | Sistema completo + defensa |

---

# GESTIÓN DE RIESGOS

## Matriz de Riesgos

| ID | Riesgo | Probabilidad | Impacto | Mitigation | Responsable |
|----|--------|--------------|---------|-----------|-------------|
| **R1** | Tutor no asignado / pide pivot arquitectónico tardío | Alta | Alto | Validar scope en próxima reunión académica. Documentar decisiones arquitectónicas hoy. | Equipo + Coordinadora académica |
| **R2** | Plantilla PDF no llega a tiempo (Sprint 2) | Media | Alto | Usar template placeholder en Sprint 1. Iterar cuando llegue oficial. | Equipo + Coordinadora |
| **R3** | Plazo se reduce por causas externas | Media | Alto | Priorizar Sprint 1 (columna vertebral). Sprint 2-3 son iteraciones. | Equipo + Coordinadora |
| **R4** | Dependencia con CLASS (consultas API) | Baja | Medio | CLASS es caja negra. Mockear en desarrollo. Validar integración tarde. | Equipo + TI Remington |
| **R5** | Firma digital no viable (costo/complejidad) | Baja | Medio | Fallback: sello electrónico + hash verificable (ya diseñado). | Equipo |
| **R6** | Estudiante intenta acceso a portal (fuera de alcance) | Media | Bajo | Educación clara de coordinadora. Sistema no tiene login estudiantil. | Coordinadora |

---

# CRITERIOS DE ÉXITO

## Técnicos

✅ **Sistema operativo end-to-end:**
- Captura validada de ambos trámites.
- Motor de workflow ejecutando transiciones correctamente.
- Auditoría inmutable registrando cada evento.
- PDF generado y verificable.

✅ **Tests:**
- Cobertura mínima 70% en capas críticas (motor, validador, auditor).
- 0 bugs bloqueantes encontrados en demo.

✅ **Documentación:**
- API completa en Swagger.
- README de setup.
- Guía de uso para coordinadora.

## Académicos

✅ **Pregunta de investigación respondida:**
- Demostrar que **dos procesos operativamente distintos se modelan con la misma maquinaria parametrizada.**
- Comparar líneas de código: motor genérico << suma de dos sistemas independientes.

✅ **Defensa clara:**
- Explicar cómo cada SP del árbol se tradujo a feature.
- Cómo se redujeron E2 (re-trabajo) y E4 (pérdida de trámites).
- Cómo se midió opacidad (timeline < 1 min).

## Funcionales

✅ **Coordinadora aprueba:**
- "Este sistema me sirve."
- "Recortó el trabajo manual en un 70%."
- "Los estudiantes ya no me preguntarán en qué va su trámite."

✅ **Completitud del MVP:**
- Ambos trámites operativos.
- PDF asentable en QF.
- Auditoría para defensa ante reclamos.

---

# REFERENCIAS

## Normas Internacionales

- **ISO/IEC 12207:2017** — Systems and software engineering — Software life cycle processes.
- **ISO/IEC 15288:2015** — Systems and software engineering — System life cycle processes.
- **IEEE 830:1998** — Recommended Practice for Software Requirements Specifications.
- **IEEE 1016:2009** — Standard for Information and Software Design.
- **RFC 7807:2016** — Problem Details for HTTP APIs.

## Literatura Académica

- **Sommerville, I.** (2016). *Software Engineering* (10th ed.). Pearson.
- **Evans, E.** (2003). *Domain-Driven Design*. Addison-Wesley.
- **Gamma, E., et al.** (1994). *Design Patterns*. Addison-Wesley.

## Documentos del Proyecto

- [arbol-de-problemas.md](docs/nuevo-proyecto/01-planteamiento/arbol-de-problemas.md) — Descomposición del problema (Marco Lógico).
- [draft-principios.md](docs/nuevo-proyecto/02-constitucion/draft-principios.md) — 6 principios de diseño + stack.
- [prd.md](docs/nuevo-proyecto/03-prd/prd.md) — Personas, journeys, roadmap.
- [entrevista3-limpia.md](docs/nuevo-proyecto/03-prd/entrevista3-limpia.md) — Requerimientos validados con coordinadora.
- [auditoria.md](docs/auditoria.md) — Referencia de Convenia (stack similar, lecciones).

---

# ANEXOS

## Anexo A: Glosario

| Término | Definición |
|---------|-----------|
| **CLASS** | Sistema académico institucional de Remington. Caja negra para Trámita. |
| **QF** | Gestor documental de firmas de Remington. Almacena PDFs asentados. |
| **Adición de Créditos** | Proceso de excepción para matricular más créditos del tope permitido. |
| **Novedad de Notas** | Proceso de registro de calificación faltante o no cargada. |
| **Coordinadora** | Usuaria primaria: gestiona solicitudes administrativas. |
| **Motor Configurable** | Máquina de estados parametrizada por datos, no código. |
| **Auditoría Inmutable** | Tabla append-only de eventos (no UPDATE/DELETE). |
| **Visibilidad Mediada** | Coordinadora consulta sistema, responde al estudiante (sin portal). |
| **SLA** | Acuerdo de nivel de servicio (días máximos en un estado). |
| **MVP** | Mínimo Viable Product (2.5 meses, demo presentable). |

## Anexo B: Plantilla de Entrevista de Validación (Semanal)

```
PREGUNTA SEMANAL (Coordinadora):

□ ¿El sistema me está ahorrando tiempo?
□ ¿Encontré algo que no me esperaba?
□ ¿Hay algo que cambiaría?
□ ¿Recomendarías usar esto en otros procesos?

Comentarios:
_________________________________________
```

## Anexo C: Checklist de Deployment Local

```
□ Java 21 LTS instalado.
□ PostgreSQL 14+ corriendo.
□ Docker Desktop instalado.
□ Git clone del repo.
□ `docker-compose up` (levanta BD + backend + frontend).
□ Backend en http://localhost:8080/api/...
□ Frontend en http://localhost:3000
□ Swagger en http://localhost:8080/swagger-ui.html
□ Login: cédula / clave de prueba.
```

---

**Documento preparado para exportación a Word/PDF.**  
**Última actualización:** 15 de junio de 2026.  
**Versión:** 1.0.0 — Línea base aprobada.
