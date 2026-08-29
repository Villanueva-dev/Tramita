# Code review con agente limpio

Procedimiento normalizado para revisar un slice de trabajo antes de continuar con el siguiente.
Escrito el 2026-08-28 a partir de tres reviews consecutivos sobre la Fase B del frontend.

## Por qué un agente sin contexto

Quien escribió el código no puede revisarlo: conoce la intención y la lee en lugar de leer el
diff. El revisor recibe **el target y las fuentes de autoridad, nada más** — ni el razonamiento de
la sesión, ni las decisiones tomadas, ni qué se consideró y se descartó.

Eso no es una precaución teórica. En los tres reviews de la Fase B, dos hallazgos no fueron
defectos del código sino **afirmaciones falsas del autor**: un mensaje de commit que declaraba
imposible una condición de carrera que sí era posible, y una cuenta de columnas equivocada. Un
revisor con contexto habría leído esas afirmaciones como premisas.

## Cuándo se lanza

Al cerrar **cada** slice, antes de empezar el siguiente. La razón es la profundidad de la cadena:
si cada slice asume el anterior aplicado, un defecto encontrado tarde obliga a rehacer todo lo que
se construyó encima.

**Excepción admisible**: el último slice de una cadena puede revisarse junto con el anterior, porque
ya no hay nada que herede el defecto. Fuera de ese caso, agrupar reviews traslada el costo a un
lugar donde es más caro.

## Cómo se lanza

| Parámetro | Valor | Por qué |
|---|---|---|
| Tipo de agente | `general-purpose` | Arranca sin el contexto de la sesión |
| Modelo | `opus` | El trabajo es analítico y adversarial |
| Ejecución | En segundo plano | Consume ~170k tokens; no bloquea |

**Nunca** un fork del agente actual: heredaría exactamente el contexto que lo invalida.

## Plantilla del prompt

### Bloque 1 — Rol

Revisor independiente, exhaustivo y adversarial. Decir explícitamente que **no tener contexto es
deliberado** y que debe formar su propio juicio.

### Bloque 2 — Target *(varía)*

Ruta absoluta del repositorio, rama, y el **rango exacto de commits**, con los comandos ya escritos:

```
git -C <repo> log --oneline <base>..<head>
git -C <repo> diff <base>..<head>
```

Cuando el rango tiene commits de naturaleza distinta —comportamiento nuevo frente a supresión—
conviene nombrarlos por separado: el borrado se audita con otras preguntas.

### Bloque 3 — Contexto mínimo *(varía)*

Dos o tres frases: qué es el proyecto y qué hace este slice. **Nada más.** Cualquier detalle
adicional es una premisa que el revisor va a heredar en lugar de verificar.

### Bloque 4 — Fuentes de autoridad *(fijo en estructura, varía en rutas)*

1. **El contrato** — `openapi.yaml`, autoridad final del cableado.
2. **El código que lo implementa** — *"por si el YAML y el código difieren"*. **No es opcional**:
   en la Fase B, el YAML no declaraba un 400 que el backend sí devuelve, y dejaba un 422 más ancho
   que el código. Un review que solo mire el YAML no encuentra eso.
3. Las **especificaciones** del slice.
4. La **lista de tareas** y sus criterios.
5. La **convención de commits** (`.gitmessage`), para auditar los mensajes.
6. **El checklist del stack** — qué mirar en este lenguaje y en este proyecto:

   | Repositorio | Checklist |
   |---|---|
   | `Tramita/` (backend) | `.claude/skills/revisar-backend-java/SKILL.md` |
   | `tramita-frontend/` | `.claude/skills/revisar-frontend-next/SKILL.md` |

   Los dos son **contenido**, no procedimiento: dicen qué mirar, no cómo lanzarlo. Sin ellos el
   agente tiene que inferir las convenciones del proyecto, y no las va a acertar — los tres
   primeros reviews de la Fase B se lanzaron sin el del frontend y ninguno señaló, por ejemplo,
   que `getAllByText` global es frágil por construcción en una página que crece.

### Bloque 5 — Qué evaluar *(el núcleo varía poco)*

Corrección · fidelidad al contrato **verificada contra el código, no solo contra el YAML** ·
interfaces de los hooks o módulos nuevos · **los tests, con mutantes** · cobertura de la spec ·
accesibilidad y UX · calidad · **los mensajes de commit y sus líneas `Verificado:`**.

Ángulos que se agregan según el slice:

- Commit con **supresión masiva** → *¿quedó algo huérfano, alguna referencia colgante, algún
  comportamiento perdido sin querer?*
- Slice con **hook nuevo** → *¿puede quedar en un estado inconsistente?*
- Slice con **formulario o diálogo** → foco, `aria-*`, estados vacíos distinguibles entre sí.

### Bloque 6 — Reglas de trabajo *(fijo)*

- **Verificá cada hallazgo antes de reportarlo.** Lo que no se puede demostrar, no se reporta.
- **No confundas "existe" con "falla".** Para cada defecto, el camino concreto: qué entrada, qué
  estado, qué resultado incorrecto. Sin ese camino, baja la severidad o se descarta.
- **Aplicá mutantes sobre una copia descartable.** Pedirlo explícitamente: es lo que encuentra los
  tests que no prueban nada, y eso no aparece leyendo.
- **No modifiques el repositorio real. No commitees. No hagas push.**
- Los servicios locales exigen sesión: **prohibido autenticarse o usar credenciales**.

### Bloque 7 — Formato del informe *(fijo)*

Por severidad (CRÍTICO / ALTO / MEDIO / BAJO), y para cada hallazgo: **qué** (una frase) ·
**dónde** (`archivo:línea`) · **camino al fallo** · **evidencia** (el comando corrido o la cita) ·
**sugerencia** (sin aplicarla).

Cerrar con un veredicto y con esta instrucción, que evita el informe inflado:

> *Si no encontrás defectos en alguna categoría, decilo explícitamente en vez de inflar el informe.
> Un informe de 3 hallazgos reales vale más que uno de 15 especulativos.*

### Bloque 8 — Trampas del entorno *(varía; ver la lista viva abajo)*

Solo las que le harían perder tiempo. **No son pistas sobre el código**: son sobre el entorno.

## Protocolo de recepción — la mitad que se olvida

Un informe no se aplica. Se confirma primero.

1. **Re-verificar cada hallazgo prioritario con comandos propios**, sin creerle al informe. En el
   review de la 3a, dos de tres hallazgos ALTO/MEDIO tenían **la severidad inflada en un eslabón**.
2. Confirmar no es preguntar *"¿existe?"* sino **"¿el camino al fallo es el que se describe?"**.
3. **Un mutante que sobrevive no siempre significa un test faltante.** A veces señala **código que
   no puede fallar** — una rama redundante que ninguna entrada puede ejercitar. La pregunta
   correcta es *"¿por qué esta línea no cambia nada?"*.
4. **Verificar que el experimento se aplicó.** Un reemplazo de texto que no coincide no muta nada y
   reporta "sobrevive" sobre código intacto. Todo script de mutación lleva `assert`.
5. **Para revertir un mutante, `cp` desde un backup — nunca `git checkout`.** Sobre trabajo no
   commiteado, `git checkout` no revierte el experimento: descarta el slice entero.
6. Decidir el alcance **con el usuario**: qué se aplica ahora, qué queda anotado, qué se difiere.
7. Los hallazgos no aplicados **se registran**, no se olvidan.

## Trampas del entorno — lista viva

Agregar cada vez que una cueste tiempo.

| Trampa | Síntoma | Qué decirle al agente |
|---|---|---|
| `rm -rf .next` con `pnpm dev` vivo | Todo responde **HTTP 500**, `ENOENT ... routes-manifest.json`. El proceso no muere | Que no lo corra en el repo real; si necesita `tsc` limpio, sobre su copia |
| Rutas con corchetes en zsh | `app/requests/[id]/...` se expande como glob; el filtro no matchea y la corrida mide otra cosa | Entre comillas, o correr la suite completa |
| `pnpm lint` | Declara `eslint .` sin ESLint instalado | No es criterio de verificación de nada |

## Lo que estos reviews encontraron y una lectura no habría encontrado

Justificación del método, para la defensa:

- **Tests que no probaban nada** — en la 3a, borrar la validación de cédula entera dejaba la suite
  en verde. Lo destapó un mutante, no una lectura.
- **Un texto roto en la pantalla principal** — el ternario `{canSearch ? '' : 'dos '}` renderizaba
  *"Escriba al menos caracteres"*. Vivía en el único archivo sin test de render.
- **Una afirmación falsa en un mensaje de commit**, presentada con documentación oficial de
  respaldo — la parte citada era cierta; la conclusión que se extendía de ella, no.
- **Una regla de negocio inventada** — un mínimo de 5 caracteres para un comentario, sin fuente en
  el contrato ni en ninguna spec.
