# TRÁMITA
## Motor de workflow configurable para la gestión de solicitudes académicas en la Universidad Remington Sede Cali

**Proyecto de Grado — Ingeniería de Sistemas**  
**Universidad Remington — Sede Cali**  
**Modalidad Distancia — SNIES 53112**  

**Autores:** [Nombre del estudiante]  
**Tutor:** [Nombre del tutor]  
**Co-tutor:** [Si aplica]  
**Año de presentación:** [2026]  

---

# Dedicatoria

A mi familia, por su apoyo incondicional y por ser el motor de mi perseverancia durante este proceso académico. También dedico este trabajo a todas las personas que, de una u otra manera, contribuyeron a la construcción de este proyecto con esfuerzo, paciencia y compromiso.

---

# Agradecimientos

Quiero expresar mi sincero agradecimiento a la Universidad Remington, a la Coordinación Académica de la Sede Cali y a mi tutor por la orientación brindada durante el desarrollo de este proyecto. Asimismo, agradezco la disposición de las personas que aportaron información valiosa para comprender los procesos académicos que hoy se pretenden optimizar mediante Trámita.

---

# Tabla de Contenidos

1. Resumen
2. Palabras clave
3. Introducción
   3.1. Título 1. Contexto institucional
   3.2. Título 2. Problema de investigación
   3.3. Título 2.1. Justificación del proyecto
   3.4. Organización del documento
4. Planteamiento del problema y justificación
5. Objetivos
6. Marco teórico y conceptual
7. Metodología
8. Propuesta de solución
9. Arquitectura propuesta
10. Resultados esperados
11. Conclusiones
12. Referencias
13. Anexos

---

# Resumen

Este trabajo de grado presenta el diseño y la propuesta de implementación de Trámita, un motor de workflow configurable orientado a la gestión de solicitudes académicas en la Sede Cali de la Universidad Remington. La solución busca reemplazar los procesos manuales y dispersos que actualmente se utilizan para trámites como la adición de créditos y la novedad de notas, mediante un flujo estructurado, validado, auditable y orientado a la mejora de la experiencia operativa de la coordinación académica.

La propuesta incorpora mecanismos de captura de datos, validación de entradas, trazabilidad de cada transición, generación automática de documentos formales y visibilidad del estado de las solicitudes. Con ello, se espera reducir los tiempos de atención, disminuir el re-trabajo generado por errores de formato y fortalecer la transparencia del proceso frente a los estudiantes y las autoridades académicas.

---

# Palabras clave

Workflow configurable; gestión documental; trazabilidad; solicitudes académicas; automatización; ingeniería de software; procesos de negocio.

---

# Introducción

## Título 1. Contexto institucional

La Universidad Remington, en su Sede Cali, desarrolla procesos académicos de alta relevancia que requieren coordinación entre diferentes áreas, actores y canales de comunicación. Entre ellos, se encuentran trámites como la adición de créditos y la novedad de notas, los cuales tienen impacto directo sobre la matrícula, la gestión del registro académico y la atención oportuna a los estudiantes. Estos procesos, aunque son esenciales para la operación institucional, han sido gestionados en gran medida de manera manual, lo que genera ineficiencias, retrasos y poca visibilidad sobre el estado real de cada solicitud.

La gestión administrativa de estos procesos ha dependido históricamente de correos electrónicos, documentos Word, firmas escaneadas, archivos dispersos y la memoria de los funcionarios encargados. Esta forma de operación no solo incrementa el riesgo de pérdida de información, sino que también dificulta la trazabilidad, la evaluación del desempeño del proceso y la respuesta oportuna ante las inquietudes de los estudiantes.

## Título 2. Problema de investigación

El problema central que orienta este proyecto consiste en la ausencia de un mecanismo estructurado para gestionar solicitudes académicas complejas, con múltiples etapas de revisión y aprobación, dentro de un entorno institucional que aún dependa de herramientas poco integradas. En este contexto, los procesos de adición de créditos y novedad de notas presentan características similares en cuanto a su necesidad de control, validación y seguimiento, pero se manejan de manera independiente y con baja interoperabilidad entre los actores involucrados.

La carencia de un sistema que centralice la información, valide datos en origen y registre las decisiones tomadas a lo largo del flujo compromete la eficiencia del proceso, incrementa la probabilidad de errores formales y dificulta la rendición de cuentas frente a los actores institucionales y los estudiantes.

## Título 2.1. Justificación del proyecto

La propuesta de Trámita se justifica por la necesidad de transformar un proceso administrativo tradicional en un flujo digital, auditable y adaptable a las necesidades de la institución. El desarrollo de esta solución responde no solo a una necesidad operativa, sino también a una oportunidad de innovación dentro del entorno académico universitario, al demostrar que procesos similares pueden ser administrados mediante una arquitectura común basada en workflow configurable.

El valor académico de esta propuesta radica en su capacidad para combinar principios de ingeniería de software, diseño de procesos y gestión documental, con el propósito de resolver un problema real de la institución. Asimismo, se reconoce que la implementación de un sistema de este tipo puede generar beneficios tanto para la coordinación académica como para los estudiantes, al ofrecer mayor claridad, rapidez y confiabilidad en la gestión del trámite.

## Organización del documento

Este documento se organiza en secciones que permiten comprender, en primer lugar, el problema que motiva el proyecto; luego, los objetivos y el marco de referencia; posteriormente, la metodología y la propuesta de solución; y, finalmente, las conclusiones y recomendaciones derivadas del análisis realizado. El propósito es ofrecer una visión integral del proyecto, desde su contexto institucional hasta su posible impacto en la gestión académica.

---

# Planteamiento del problema y justificación

## Problema central

La gestión de solicitudes académicas en la Sede Cali depende de procesos manuales y fragmentados que dificultan la trazabilidad, la validación de datos y la visibilidad del estado del trámite. En particular, los procesos de adición de créditos y novedad de notas implican múltiples aprobaciones, formatos repetitivos, comunicación dispersa y seguimiento humano. Como consecuencia, los trámites presentan tiempos de ciclo impredecibles, niveles altos de re-trabajo y poca claridad para los estudiantes sobre el estado de sus solicitudes.

## Causas del problema

Entre las principales causas se encuentran:

- la ausencia de un repositorio central del estado de cada solicitud;
- la utilización de formatos manuales sin validación de datos;
- la dispersión de la comunicación entre correo, WhatsApp, OneDrive y otras herramientas;
- la falta de trazabilidad de las decisiones adoptadas en cada etapa del proceso;
- la dependencia excesiva de la memoria y la experiencia de la coordinación académica.

## Justificación del proyecto

La implementación de Trámita se justifica porque existe una necesidad real de modernizar el manejo de estos procesos sin perder la lógica institucional existente. El sistema puede contribuir a reducir errores derivados del llenado manual de formatos, centralizar el seguimiento de cada solicitud, registrar de forma estructurada los cambios de estado y generar documentos formales listos para ser asentados en sistemas institucionales.

Además, la propuesta es pertinente desde el punto de vista académico, ya que demuestra que procesos aparentemente distintos pueden ser modelados y administrados con un mismo motor de workflow configurable, lo cual representa un valor técnico, operativo y de investigación.

---

# Objetivos

## Objetivo general

Diseñar e implementar un sistema de gestión de solicitudes académicas basado en un motor de workflow configurable que permita organizar, validar y auditar los trámites de adición de créditos y novedad de notas en la Sede Cali de la Universidad Remington.

## Objetivos específicos

1. Analizar los procesos actuales de adición de créditos y novedad de notas para identificar necesidades, reglas de negocio y limitaciones.
2. Desarrollar un motor de workflow configurable que modele las transiciones de estado, roles y reglas de paso de ambos trámites sobre una misma infraestructura, con profundidad de automatización parametrizable por trámite.
3. Construir un módulo de formularios validados por backend que reemplace el diligenciamiento manual en Word y detecte errores de captura, con las reglas de negocio de cada trámite.
4. Generar automáticamente el PDF formal del trámite a partir de los datos validados, con un sello electrónico verificable (hash + sello de tiempo) y el registro auditable de cada aprobación.
5. Implementar la capa operativa de la coordinación: bandeja de trabajo con pendientes por SLA, timeline de auditoría inmutable y notificación de finalización al estudiante.

---

# Marco teórico y conceptual

## Marco contextual

La Universidad Remington opera procesos académicos complejos en los que la coordinación académica asume un rol central. En este entorno, la gestión administrativa se ha apoyado históricamente en herramientas genéricas como correo electrónico, documentos Word, OneDrive y QF. Aunque estas herramientas son funcionales para la comunicación, no ofrecen una solución integral para el control del ciclo de vida de un trámite.

## Marco teórico

El proyecto se sustenta en conceptos de gestión de procesos de negocio, workflow, trazabilidad digital, validación de datos y documentación formal. La propuesta se alinea con enfoques de ingeniería de software orientados a la automatización de flujos de trabajo, la mejora de la calidad de los datos y la auditoría de procesos.

Entre los referentes conceptuales relevantes se encuentran:

- ingeniería de software orientada a requisitos y documentación;
- diseño de sistemas basados en flujo de trabajo y estados;
- principios de usabilidad para usuarios no técnicos;
- buenas prácticas de calidad, trazabilidad y pruebas de software;
- generación automatizada de documentos formales.

## Marco normativo y de buenas prácticas

El proyecto incorpora referentes de documentación y desarrollo de software, entre ellos:

- IEEE 830 para especificación de requisitos;
- ISO/IEC 12207 para procesos del ciclo de vida del software;
- IEEE 1016 para diseño de software;
- principios de simplicidad, modularidad y trazabilidad aplicados al desarrollo ágil.

---

# Metodología

La metodología propuesta combina análisis documental, entrevistas, diseño de requisitos, desarrollo iterativo y validación funcional. La intención es construir una solución viable en un horizonte de tiempo acotado, priorizando el valor del sistema para el usuario final y la posibilidad de realizar ajustes en función del feedback recibido.

## Fases de desarrollo

1. **Recolección de información**  
   Se revisó la documentación existente del proyecto, la plantilla de trabajo de grado, los documentos de planteamiento del problema y la información generada a partir de entrevistas y análisis de contexto.

2. **Análisis de requisitos**  
   Se definieron los procesos críticos, los actores involucrados, las reglas de negocio y los requerimientos funcionales y no funcionales.

3. **Diseño del sistema**  
   Se propuso una arquitectura basada en módulos de captura, workflow, auditoría, generación de documentos y visualización de solicitudes.

4. **Desarrollo incremental**  
   Se organizó el desarrollo en sprints o iteraciones cortas, priorizando primero la columna vertebral del sistema y luego la salida formal del trámite.

5. **Validación y ajuste**  
   El sistema se valida continuamente con base en criterios de aceptación y retroalimentación del usuario final, en este caso la coordinadora académica.

## Enfoque de trabajo

El proyecto sigue un enfoque de desarrollo ágil, con foco en la entrega de una demostración funcional y en la mejora continua del producto. La estrategia prioriza la viabilidad del MVP, la claridad del flujo y la capacidad de adaptación a nuevas reglas del proceso institucional.

## Criterios de selección de fuentes bibliográficas

> **PENDIENTE DE REVISIÓN CON LA TUTORA.** Esta subsección declara un criterio metodológico que aún no ha sido validado por la dirección del trabajo. Se somete a revisión antes de darlo por cerrado.

La revisión de literatura de este trabajo aplica dos criterios temporales distintos, según la función que cumple cada fuente dentro del marco de referencia.

**Criterio general: ventana 2020–2026.** Las fuentes que sustentan el estado del arte y los antecedentes se restringen a publicaciones aparecidas entre 2020 y 2026. La razón es que estas fuentes deben responder a la pregunta de qué se ha implementado recientemente en materia de digitalización de trámites académicos, y una respuesta construida sobre trabajos de hace quince años no describiría el estado actual de la práctica. Todas las fuentes se referencian bajo normas APA en su séptima edición.

**Excepción acotada: eje de modelos de proceso configurables.** Se admiten fuentes anteriores a 2020 en dos supuestos, ambos circunscritos al eje de motores de workflow y modelos de proceso configurables: cuando la fuente constituya la formulación original del concepto, o cuando documente una aplicación de ese concepto a procesos administrativos de instituciones de educación superior.

Esta excepción se justifica por cuatro razones. Primera: el concepto de proceso configurable es el núcleo de la pregunta de investigación de este trabajo, y su formulación canónica es anterior a la ventana general; citarlo únicamente a través de revisiones posteriores equivaldría a sustentar el concepto central sobre fuentes secundarias. Segunda: en la teoría del diseño de procesos, la obra fundacional no queda obsoleta por el paso del tiempo del modo en que sí lo queda un reporte de implementación tecnológica, porque describe una construcción conceptual y no un estado de la técnica. Tercera: la búsqueda documental evidenció que la restricción temporal estricta excluía por año —no por pertinencia— trabajos cuyo objeto coincide con el de esta investigación. Cuarta, y en relación con el segundo supuesto: la aplicación específica del modelo configurable a trámites de educación superior está escasamente documentada, y el registro más pertinente localizado es anterior a la ventana; excluirlo obligaría a presentar como no documentado algo que sí lo está, lo cual constituye un defecto de mayor gravedad que citar una fuente fuera de la ventana declarándolo.

**Delimitación de la excepción.** La apertura temporal se aplica exclusivamente al eje de motores de workflow y modelos de proceso configurables. Los demás ejes del marco de referencia —modelado de procesos, validación de datos en origen, documentos electrónicos verificables, trazabilidad y auditoría, y transformación digital en educación superior— se mantienen dentro de la ventana 2020–2026. Cada fuente admitida por la excepción se identifica como tal en el momento de citarla.

**Procedimiento de verificación de fuentes.** Toda referencia empleada en este trabajo se comprueba individualmente contra la fuente primaria antes de incorporarse: se contrasta título completo, lista completa de autores, año, medio de publicación y paginación, consultando el registro del identificador digital de objeto (DOI) en Crossref o, cuando no existe DOI, el documento mismo. Este procedimiento se adoptó tras constatar que las herramientas de búsqueda asistida utilizadas en la fase exploratoria produjeron fichas bibliográficas defectuosas —títulos truncados, autores omitidos, paginación inexistente y, en un caso, la combinación de dos trabajos distintos en una sola referencia—, sin que dichos defectos fueran detectables sin acudir a la fuente.

**Fuentes admitidas por la excepción.** Ingresan por el primer supuesto Rosemann y van der Aalst (2007), Gottschalk et al. (2007), Hallerbach et al. (2010) y La Rosa et al. (2017); por el segundo, Subić y Dimitrijević (2015). Sus fichas completas se relacionan en el apartado de Referencias.

---

# Propuesta de solución

## Visión general

Trámita es una solución orientada a transformar los procesos académicos manuales en flujos estructurados, auditable y configurables. Su objetivo principal es orquestar el ciclo de vida de solicitudes académicas desde la captura inicial hasta el cierre formal del trámite.

## Módulos principales

### 1. Módulo de captura de solicitudes
El sistema permite registrar solicitudes de adición de créditos y novedad de notas mediante formularios con validaciones de negocio. Esto evita que los datos se ingresen de forma inconsistente o incompleta.

### 2. Motor de workflow
El sistema implementa una máquina de estados que permite mover la solicitud entre diferentes estados del trámite, como borrador, enviado, aprobado, finalizado o devuelto para corrección.

### 3. Auditoría inmutable
Cada transición del trámite queda registrada con fecha, actor y comentario. Esta trazabilidad permite reconstruir el historial completo de una solicitud.

### 4. Generación automática de documentos
Al completar el proceso, el sistema genera un PDF formal que puede ser utilizado como evidencia o soporte documental del trámite.

### 5. Vista operativa para la coordinación
La coordinación cuenta con una vista centralizada donde puede revisar solicitudes pendientes, filtrar por estado, consultar historial y tomar decisiones con mayor rapidez.

## Valor de la propuesta

La propuesta aporta valor porque:

- transforma un proceso desordenado en uno controlado;
- reduce la dependencia de la memoria humana;
- mejora la calidad de los datos y la documentación;
- permite escalar a más trámites en el futuro;
- ofrece una base tecnológica sólida para la gestión académica institucional.

---

# Arquitectura propuesta

La arquitectura del sistema se organiza en módulos funcionales que permiten separar claramente las responsabilidades del producto. En primer lugar, se encuentra la interfaz de usuario, encargada de facilitar la interacción con la coordinadora y de presentar la información de forma comprensible. Luego, se encuentra la capa de negocio, responsable de aplicar las reglas de validación, manejar los estados del workflow y controlar las transiciones del trámite. Finalmente, se incorpora una capa de persistencia y auditoría, encargada de almacenar los datos, los eventos del proceso y los documentos generados.

Esta estructura permite que el sistema sea escalable, mantenible y adaptable a nuevos trámites o reglas de negocio sin necesidad de reescribir la lógica base. Además, facilita la incorporación de futuras mejoras, como la integración con otros sistemas institucionales o la extensión del producto a otros procesos académicos.

---

# Resultados esperados

Se espera que la implementación de Trámita genere los siguientes resultados:

- reducción del tiempo de atención de las solicitudes;
- menor re-trabajo debido a errores formales;
- mejor trazabilidad de los trámites;
- mayor control y visibilidad para la coordinación;
- mejora en la comunicación con los estudiantes;
- consolidación de una base de software que pueda extenderse a otros procesos académicos.

Asimismo, el proyecto busca demostrar que dos trámites de naturaleza distinta pueden ser gestionados con la misma lógica de workflow configurable, lo que convierte el sistema en una propuesta de valor tanto operativa como académica.

---

# Conclusiones

El proyecto Trámita representa una propuesta relevante para la modernización de procesos académicos en la Universidad Remington. La solución aborda problemas concretos de la gestión manual actual, como la dispersión de información, la falta de trazabilidad, los errores de formato y la sobrecarga administrativa. A partir de este análisis, se concluye que la automatización y la estructuración del flujo del trámite no solo mejoran la eficiencia operativa, sino que también aportan mayor seguridad, calidad y control institucional.

La propuesta no solo busca automatizar una tarea, sino reorganizar el flujo completo del trámite de manera más clara, más auditable y más útil para los usuarios. En este sentido, Trámita constituye una herramienta estratégica para fortalecer la operatividad académica y mejorar la experiencia de gestión de solicitudes en la Sede Cali.

Además, el proyecto aporta un valor académico importante al demostrar cómo un motor de workflow configurable puede adaptarse a procesos distintos con una base común de software.

---

# Referencias

## Referencias internas

- Documentación del proyecto Tramita.
- Archivo de planteamiento del problema y árbol de causas.
- Borrador de principios y constitución del proyecto.
- Guía de entrevista N.º 3.
- Plantilla de presentación de trabajo de grado proporcionada por la universidad.

## Referencias técnicas y académicas

Las fichas de este apartado fueron verificadas individualmente contra la fuente primaria conforme al procedimiento declarado en la Metodología. Se indica con la marca *(excepción)* toda fuente admitida por la excepción temporal del eje de modelos de proceso configurables.

### Modelos de proceso configurables y motores de workflow

- Calegari, D., Delgado, A., & Peña, L. (2020). Model-driven support for business process families with the Common Variability Language (CVL). *CLEI Electronic Journal, 23*(1). https://doi.org/10.19153/cleiej.23.1.3
- Gottschalk, F., van der Aalst, W. M. P., & Jansen-Vullers, M. H. (2007). Configurable process models — A foundational approach. En *Reference modeling* (pp. 59–77). Physica-Verlag. https://doi.org/10.1007/978-3-7908-1966-3_3 *(excepción)*
- Hallerbach, A., Bauer, T., & Reichert, M. (2010). Capturing variability in business process models: The Provop approach. *Journal of Software Maintenance and Evolution: Research and Practice, 22*(6-7), 519–546. https://doi.org/10.1002/smr.491 *(excepción)*
- La Rosa, M., van der Aalst, W. M. P., Dumas, M., & Milani, F. P. (2017). Business process variability modeling: A survey. *ACM Computing Surveys, 50*(1), 1–45. https://doi.org/10.1145/3041957 *(excepción)*
- Rosemann, M., & van der Aalst, W. M. P. (2007). A configurable reference modelling language. *Information Systems, 32*(1), 1–23. https://doi.org/10.1016/j.is.2005.05.003 *(excepción)*
- Subić, N., & Dimitrijević, M. (2015). Modeling flexible configurable processes applied to the enrollment process in higher education institutions. *Online Journal of Applied Knowledge Management, 3*(2), 181–190. http://www.iiakm.org/ojakm/articles/2015/volume3_2/OJAKM_Volume3_2pp181-190.pdf *(excepción)*
- van der Aalst, W. M. P. (2023). Toward more realistic simulation models using object-centric process mining. En *ECMS 2023 Proceedings*. https://doi.org/10.7148/2023-0005

### Digitalización de procesos administrativos en educación superior

- Almarayeh, T., & Frehat, R. (2026). The role of robotic process automation in improving administrative processes in higher education. *EDPACS*, 1–9. https://doi.org/10.1080/07366981.2026.2635153
- Sorour, A., Atkins, A. S., Stanier, C. F., & Alharbi, F. D. (2020). Comparative frameworks for monitoring quality assurance in higher education institutions using business intelligence. En *2020 International Conference on Computing and Information Technology (ICCIT-1441)* (pp. 1–5). IEEE. https://doi.org/10.1109/ICCIT-144147971.2020.9213808
- Tsakalidis, G. (2022). *A framework for systematic evaluation of Business Process Redesign (BPR) initiatives using the notion of model plasticity* [Tesis doctoral, University of Macedonia].

### Normas y referencias de ingeniería de software

- IEEE. (1998). Recommended Practice for Software Requirements Specifications (IEEE 830).
- ISO/IEC. (2017). Systems and software engineering — Software life cycle processes (ISO/IEC 12207).
- IEEE. (2009). Software Design Description (IEEE 1016).
- Sommerville, I. (2016). Software Engineering (10th ed.). Pearson.
- Weske, M. (2012). Business Process Management: Concepts, Languages, Architectures. Springer.

> **Notas de trabajo (no forman parte del documento final).**
> 1. **Tsakalidis (2022)**: título y filiación confirmados por fuente secundaria independiente, pero el repositorio institucional (`dspace.lib.uom.gr`) rechazó la conexión al intentar abrir el registro. Falta confirmar la URL directa y el departamento antes de dar la ficha por cerrada.
> 2. **Hallerbach et al.**: el año se consigna como 2010 siguiendo el volumen impreso 22(6-7); el registro de Crossref fecha el trabajo en 2009 por publicación anticipada en línea. Confirmar en el sitio del editor. Existe además un segundo identificador para el mismo artículo (`10.1002/spip.446`) que corresponde a un registro incompleto y no debe usarse.
> 3. **Sommerville (2016) y Weske (2012)** quedan fuera de la ventana 2020–2026 y no pertenecen al eje amparado por la excepción: bajo el criterio declarado en la Metodología, hoy son inconsistentes. Decidir si se sustituyen por fuentes dentro de ventana o si se amplía el criterio para las obras de referencia general de la disciplina.

---

# Anexos

## Anexo A. Documentos de apoyo del proyecto

- Documento de planteamiento del problema.
- Documento de principios y constitución.
- Documento de requisitos funcionales y no funcionales.
- Plantilla universitaria extraída del PDF.

## Anexo B. Material inicial para la implementación

- Formatos de adición de créditos y novedad de notas.
- Reglas de negocio identificadas en entrevistas.
- Esquema inicial de entidades para la base de datos.
- Lista de módulos del MVP.
