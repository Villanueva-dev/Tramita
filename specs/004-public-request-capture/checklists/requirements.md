# Specification Quality Checklist: Captura pública del formato DO-FR-100

**Purpose**: Validar la completitud y calidad de la especificación antes de planificar
**Created**: 2026-09-15
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] Sin detalles de implementación (lenguajes, frameworks, APIs)
- [x] Centrada en el valor para el usuario y la necesidad del negocio
- [x] Escrita para interlocutores no técnicos
- [x] Todas las secciones obligatorias completas

## Requirement Completeness

- [x] No quedan marcadores [NEEDS CLARIFICATION] — **resueltos los 2, ver Notas**
- [x] Los requisitos son verificables y no ambiguos
- [x] Los criterios de éxito son medibles
- [x] Los criterios de éxito son agnósticos de la tecnología
- [x] Todos los escenarios de aceptación están definidos
- [x] Los casos borde están identificados
- [x] El alcance está acotado explícitamente
- [x] Dependencias y supuestos identificados

## Feature Readiness

- [x] Todos los requisitos funcionales tienen criterios de aceptación claros
- [x] Los escenarios de usuario cubren los flujos principales
- [x] La feature cumple los resultados medibles definidos en Success Criteria
- [x] Ningún detalle de implementación se filtró en la especificación

## Notas

**Los dos [NEEDS CLARIFICATION] se resolvieron el 2026-09-15** y pasaron a la sección
«Riesgos aceptados» del spec, con su justificación y con lo que se descartó:

1. **Envíos duplicados** → se registran como solicitudes distintas. El sistema no adivina
   la intención de quien envía; un duplicado visible es preferible a un reenvío legítimo
   silenciosamente descartado.
2. **Suplantación en un canal anónimo** → riesgo aceptado y declarado. La mitigación es el
   filtro humano que ya existe: toda solicitud pasa por la revisión de la Coordinación
   antes de seguir a la facultad.

⚠️ El segundo es un **juicio del equipo sobre un riesgo institucional, no validado con la
institución**. Está marcado como tal en el spec, con la ruta de mitigación si la
Coordinación o la tutora lo objetan.

**Verificaciones que se hicieron sobre el texto, no solo sobre la estructura:**

- Se revisó que ningún FR nombre una tecnología, un endpoint, una tabla o un formato de
  serialización. Los requisitos hablan de «canal», «vista» y «origen», no de rutas ni
  esquemas.
- Los Success Criteria se revisaron uno a uno contra el criterio de agnosticismo: SC-002 y
  SC-006 estuvieron cerca de nombrar mecanismos y se reescribieron en términos de lo que
  observa una persona.
- FR-021 y el supuesto sobre validez legal existen porque el riesgo ya estaba declarado en
  el material de la feature y no debía perderse al pasar a especificación.
