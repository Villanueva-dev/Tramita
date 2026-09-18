---
name: review-agente-limpio
description: "Lanza un agente SIN CONTEXTO de la sesión para revisar un slice ya cerrado (un rango de commits) antes de continuar con el siguiente de la cadena, y define qué hacer con el informe que devuelve. Invocar cuando el usuario pida \"lanzá el agente limpio\", \"review del slice\", \"revisar la fase antes de seguir\", o al terminar de implementar y commitear una fase completa. NO usar para revisión inline de código que se está escribiendo — para eso está revisar-backend-java."
compatibility: "Requiere que Claude Code corra desde la raíz de Tramita/ para que la ruta del reglamento resuelva. Aplica a los dos repositorios del proyecto de grado: Tramita/ (backend) y ../tramita-frontend/."
---

# Review con agente limpio

## Procedimiento

**Leé el reglamento completo antes de lanzar nada**:

```
docs/workflow/code-review-agente-limpio.md
```

Ahí está el procedimiento entero: por qué el agente va sin contexto, cuándo se lanza, los ocho
bloques de la plantilla del prompt (cuáles son fijos y cuáles varían), el protocolo de recepción
del informe y la lista viva de trampas del entorno.

Este archivo no lo duplica a propósito: **una sola fuente, para que no se desincronicen**.

## Lo que hay que reunir antes de invocarlo

El reglamento marca qué bloques varían. En la práctica, antes de lanzar hacen falta cuatro datos:

1. **El rango de commits** — `<base>..<head>`, verificado con `git log --oneline`, no de memoria.
2. **Dos o tres frases de contexto** — qué es el proyecto y qué hace este slice. Nada más: todo lo
   demás es una premisa que el revisor heredaría en lugar de verificar.
3. **Las rutas de las fuentes de autoridad** — contrato, código que lo implementa, specs del slice,
   lista de tareas, `.gitmessage`.
4. **Las trampas del entorno vigentes** — si hay un `pnpm dev` corriendo, si el slice toca rutas con
   corchetes, etc.

## Lo innegociable

- **Nunca un fork del agente actual**: heredaría exactamente el contexto que invalida el review.
- **Pedir mutantes explícitamente.** Es lo que encuentra los tests que no prueban nada, y eso no
  aparece leyendo el diff.
- **Incluir el código que implementa el contrato entre las fuentes**, no solo el contrato. En la
  Fase B el YAML no declaraba un `400` que el backend sí devuelve.
- **El informe no se aplica: se confirma primero**, hallazgo por hallazgo, con comandos propios.
  En el review de la 3a, dos de tres hallazgos prioritarios tenían la severidad inflada.
