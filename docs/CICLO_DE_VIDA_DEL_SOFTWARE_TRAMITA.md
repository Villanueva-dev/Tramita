# Ciclo de Vida del Software — Trámita

## 1. Introducción

El desarrollo de Trámita puede entenderse como un proceso estructurado de ingeniería de software que permite pasar desde la identificación de un problema organizacional hasta la propuesta de una solución tecnológica viable. En este contexto, el ciclo de vida del software comprende las etapas de planificación, análisis, diseño, implementación, pruebas, despliegue y mantenimiento, con el fin de asegurar que el producto final responda a las necesidades del usuario y del entorno institucional.

Para este proyecto, el ciclo de vida adquiere especial relevancia porque el sistema no solo debe funcionar técnicamente, sino también ajustarse a un contexto académico y administrativo concreto. Por ello, la documentación y la organización de cada fase son fundamentales para garantizar un desarrollo coherente, escalable y alineado con la propuesta de trabajo de grado.

---

## 2. Fase de Planificación

La fase de planificación permite definir el alcance del proyecto, los objetivos generales y específicos, los recursos necesarios y las condiciones de desarrollo. En el caso de Trámita, esta fase se materializa en la identificación del problema central, la delimitación del MVP, el análisis de los procesos académicos implicados y la definición de los actores principales.

### Actividades principales

- Definición del problema y de la propuesta de solución.
- Identificación de los trámites académicos a cubrir.
- Delimitación del alcance inicial del sistema.
- Establecimiento de objetivos, metas y criterios de éxito.
- Determinación de roles, responsabilidades y restricciones del proyecto.

### Entregables esperados

- Documento de planteamiento del problema.
- Alcance del MVP.
- Definición inicial de requisitos.
- Plan general del proyecto.

---

## 3. Fase de Análisis de Requerimientos

En esta etapa se estudian en profundidad los procesos actuales de gestión de solicitudes académicas. El objetivo es comprender cómo se desarrollan los trámites de adición de créditos y novedad de notas, cuáles son sus reglas de negocio, qué actores intervienen y qué necesidades deben satisfacerse con la solución propuesta.

### Actividades principales

- Revisión documental de los procesos académicos.
- Análisis de entrevistas y observaciones del contexto institucional.
- Identificación de requerimientos funcionales y no funcionales.
- Definición de reglas de negocio del sistema.
- Construcción de historias de usuario y criterios de aceptación.

### Requerimientos identificados

#### Requerimientos funcionales

- Captura de solicitudes de adición de créditos.
- Captura de solicitudes de novedad de notas.
- Validación de datos en origen.
- Registro de estados y transiciones del trámite.
- Generación de un documento formal en PDF.
- Consulta del historial y del estado de cada solicitud.

#### Requerimientos no funcionales

- Facilidad de uso para un usuario no técnico.
- Trazabilidad de las operaciones realizadas.
- Seguridad en el manejo de datos académicos.
- Mantenibilidad del sistema.
- Escalabilidad para futuros procesos.

---

## 4. Fase de Diseño del Sistema

Una vez definidos los requerimientos, se procede al diseño del sistema. En esta fase se establecen la arquitectura, los módulos principales, la estructura de datos y la lógica del workflow que permitirá gestionar los trámites académicos.

### Aspectos del diseño

- Diseño de la arquitectura del sistema.
- Definición de módulos: captura, workflow, auditoría, generación de documentos y visualización.
- Diseño del modelo de datos.
- Definición de estados del trámite.
- Diseño de la interfaz de usuario para la coordinación académica.

### Propuesta de diseño para Trámita

El sistema se puede organizar en los siguientes componentes:

1. Módulo de interfaz de usuario.
2. Módulo de negocio o lógica de workflow.
3. Módulo de persistencia de datos.
4. Módulo de auditoría y trazabilidad.
5. Módulo de generación de documentos PDF.

Esta estructura permite separar responsabilidades, facilitar el mantenimiento y preparar el sistema para futuras mejoras o integraciones.

---

## 5. Fase de Implementación

La implementación corresponde a la construcción del sistema a partir del diseño previamente definido. En esta etapa se desarrollan los componentes principales del software y se integran los módulos que permiten cubrir el flujo completo del trámite.

### Actividades principales

- Desarrollo del backend del sistema.
- Implementación de la lógica del workflow.
- Desarrollo de formularios de captura.
- Implementación de la capa de persistencia.
- Integración del módulo de generación de documentos.

### Enfoque de implementación

Para Trámita, la implementación debe priorizar:

- claridad del flujo del trámite;
- simplicidad de uso para la coordinadora;
- robustez en la validación de datos;
- correcto registro de eventos del proceso;
- generación automática del documento formal al cierre del trámite.

---

## 6. Fase de Pruebas

La fase de pruebas tiene como propósito verificar que el sistema cumple con los requisitos definidos y que su comportamiento es correcto en los escenarios esperados. En el contexto de Trámita, las pruebas deben centrarse en aspectos clave como la validación de formularios, las transiciones de estado, la generación de documentos y la trazabilidad de cada acción.

### Tipos de pruebas sugeridos

- Pruebas unitarias de reglas de negocio.
- Pruebas de integración de módulos.
- Pruebas de flujo de workflow.
- Pruebas de validación de formularios.
- Pruebas de generación de PDF.
- Pruebas funcionales orientadas a la experiencia de uso.

### Objetivo de las pruebas

Garantizar que el sistema sea confiable, que los estados del trámite sean consistentes y que la información registrada sea precisa y útil para la coordinación y para la auditoría del proceso.

---

## 7. Fase de Implementación o Despliegue

En esta fase se prepara el sistema para su uso real o para su demostración funcional. Para este proyecto, la implementación puede realizarse inicialmente de forma local o en un entorno de prueba, con el fin de validar el funcionamiento del sistema antes de una posible adopción institucional.

### Actividades principales

- Preparación del entorno de despliegue.
- Configuración de base de datos y dependencias.
- Publicación de la aplicación para demostración.
- Validación del flujo completo con datos de prueba.

### Consideraciones importantes

El despliegue del proyecto debe ser sencillo y viable dentro del alcance del MVP, de manera que la solución pueda ser evaluada sin requerir una infraestructura compleja o costosa.

---

## 8. Fase de Mantenimiento

El mantenimiento es la fase final del ciclo de vida del software y permite asegurar la continuidad del sistema una vez implementado. En el caso de Trámita, el mantenimiento deberá centrarse en la corrección de errores, la adaptación del sistema a nuevas reglas institucionales y la mejora de la experiencia del usuario.

### Tipos de mantenimiento esperados

- Mantenimiento correctivo: corrección de fallas detectadas.
- Mantenimiento adaptativo: ajuste del sistema a cambios en procesos o normas.
- Mantenimiento perfectivo: mejoras de rendimiento y usabilidad.
- Mantenimiento preventivo: prevención de problemas futuros mediante ajustes y documentación.

### Valor del mantenimiento

Esta fase es importante porque el flujo académico y las necesidades institucionales pueden cambiar con el tiempo. Un sistema como Trámita debe poder evolucionar sin perder la trazabilidad, la consistencia y la utilidad que lo justificaron desde el inicio.

---

## 9. Conclusión del ciclo de vida del software

El ciclo de vida del software en Trámita permite entender el proyecto como un proceso integral que va desde la identificación del problema hasta la continuidad del sistema en producción o en demostración. Cada fase aporta valor al desarrollo del producto y contribuye a que la solución sea más sólida, más alineada con las necesidades de la institución y más pertinente desde el punto de vista académico.

En este sentido, el diseño del sistema no debe limitarse a la programación de funcionalidades, sino que debe contemplar también la documentación, la validación de requisitos, la organización del flujo y la calidad del producto final.

---

## 10. Referencias sugeridas

- IEEE. (1998). Recommended Practice for Software Requirements Specifications (IEEE 830).
- ISO/IEC. (2017). Systems and software engineering — Software life cycle processes (ISO/IEC 12207).
- Sommerville, I. (2016). Software Engineering (10th ed.). Pearson.
- Documentación interna del proyecto Tramita.
