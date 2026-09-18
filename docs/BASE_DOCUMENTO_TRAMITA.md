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

### Modelado y rediseño de procesos

El rediseño de procesos estudia cómo transformar un proceso existente para mejorar su desempeño. Milani y Lashkevich (2025), en una revisión sistemática de la literatura, ordenan las oportunidades de mejora y las opciones de rediseño disponibles, y muestran que la elección de una opción depende del tipo de deficiencia que se busca corregir y no de una preferencia técnica. Este trabajo adopta esa distinción: el diagnóstico del proceso vigente precede a la decisión de rediseño.

En el ámbito de los servicios públicos, Mukherjee et al. (2021) documentan la construcción de un modelo de proceso estándar a partir de un rediseño estructurado, y evidencian que procesos administrativos ejecutados de forma heterogénea pueden converger en un modelo común sin perder la especificidad de cada caso. Esa convergencia es el supuesto que habilita tratar dos trámites distintos sobre una misma infraestructura.

La calidad del dato capturado constituye una dimensión del rediseño y no un asunto posterior a él. Miller et al. (2024) proponen un marco que ordena las dimensiones de calidad de datos y sitúan la exactitud y la completitud entre las propiedades que quedan determinadas en el momento de la captura. La evidencia empírica sobre el efecto de validar en origen proviene del ámbito clínico: Reeves et al. (2020) compararon formularios de consentimiento quirúrgico en papel y en formato electrónico con validaciones, y hallaron una reducción significativa de la tasa de errores de captura. Aunque el dominio de ese estudio difiere del que aquí se aborda, el mecanismo que explica el hallazgo —la imposibilidad de registrar un valor inválido cuando el formulario lo impide— no depende del dominio de aplicación.

### Motores de workflow y modelos de proceso configurables

Un modelo de proceso configurable es un modelo que integra las variantes de una familia de procesos en una representación única, de la cual se derivan por configuración los procesos individuales. Rosemann y van der Aalst (2007) formularon originalmente esta noción mediante un lenguaje de modelado de referencia configurable, y Gottschalk et al. (2007) establecieron su fundamento al distinguir entre los puntos de variación de un modelo y las decisiones que los resuelven. Ambas fuentes ingresan por la excepción temporal declarada en la Metodología, en su primer supuesto.

Sobre esa base se desarrollaron enfoques que difieren en la forma de representar la variabilidad. Hallerbach et al. (2010) propusieron Provop, que deriva variantes aplicando operaciones de ajuste sobre un modelo base, también admitido por la excepción; Calegari et al. (2020), dentro de la ventana temporal general, recurren a un lenguaje de variabilidad común para dirigir mediante modelos la generación de familias de procesos. La Rosa et al. (2017), en una revisión del campo igualmente admitida por la excepción, clasifican estas aproximaciones y señalan que la diferencia central reside en si la variabilidad se expresa restringiendo un modelo que las contiene a todas o extendiendo un modelo mínimo.

La pertinencia de este enfoque para el ámbito universitario está documentada. Subić y Dimitrijević (2015) modelaron el proceso de matrícula de instituciones de educación superior como un proceso configurable y mostraron que las diferencias entre programas académicos podían expresarse como configuraciones de un modelo común, en lugar de como procesos independientes; esta fuente ingresa por el segundo supuesto de la excepción, referido a la aplicación del concepto a procesos administrativos de educación superior. Ese hallazgo constituye el antecedente directo de la propuesta de este trabajo.

Finalmente, van der Aalst (2023) advierte que los modelos de proceso adquieren valor operativo cuando se contrastan con el comportamiento efectivamente registrado durante la ejecución, lo que vincula la configuración del modelo con la evidencia que el propio sistema produce.

### Trazabilidad y auditoría del proceso

La trazabilidad de un proceso descansa en el registro de los eventos que lo componen. Andrews y Wynn (2026) analizan los patrones de imperfección que afectan a esos registros y sostienen que un registro incompleto o inconsistente compromete todo análisis posterior, por lo que su calidad debe garantizarse en el momento de producirlo y no reconstruirse después. Este trabajo traslada ese principio al diseño: los eventos del trámite se registran como consecuencia de la transición de estado y no como una acción separada que pueda omitirse.

El registro de eventos permite además verificar el cumplimiento de las reglas del proceso. González et al. (2022) combinan minería de procesos con un modelo de controles genéricos de cumplimiento para evaluar si un proceso colaborativo satisface los requisitos que le fueron impuestos, con lo cual la traza de ejecución se convierte en evidencia auditable.

El documento formal que cierra un trámite materializa esa evidencia. Vargas y Piedra (2023) describen un esquema de emisión y verificación de micro-credenciales universitarias en el que la institución emite un documento cuya autenticidad puede comprobarse con independencia de quien lo presenta. En el plano del mecanismo, Sharif et al. (2021) documentan la protección de la integridad de archivos PDF mediante firma digital y funciones de resumen criptográfico, procedimiento que permite detectar cualquier alteración posterior a la emisión.

### Transformación digital de la gestión administrativa universitaria

La digitalización de los procesos administrativos de las instituciones de educación superior ha sido abordada desde distintos ángulos. Almarayeh y Frehat (2026) examinan el papel de la automatización robótica de procesos en la mejora de los procesos administrativos universitarios y señalan que las ganancias se concentran en tareas repetitivas de alto volumen. Sorour et al. (2020) proponen marcos comparativos para el monitoreo del aseguramiento de la calidad en estas instituciones mediante inteligencia de negocios, y evidencian que la información necesaria para la gestión suele existir dispersa en los sistemas antes de ser aprovechada. Tsakalidis (2022), por su parte, desarrolla un marco de evaluación sistemática de iniciativas de rediseño que introduce la noción de plasticidad del modelo como medida de su capacidad de ser rediseñado antes de la implementación.

## Marco normativo y de buenas prácticas

El proyecto incorpora referentes normativos de documentación y desarrollo de software, citados en su edición vigente al momento de esta redacción:

- **ISO/IEC/IEEE 29148:2018** para la ingeniería de requisitos y la estructura de la especificación de requisitos de software (ISO/IEC/IEEE, 2018). Además de fijar el contenido del documento, la norma define el enunciado de un requisito como la composición de una condición, un sujeto, una acción, un objeto y una restricción de la acción, lo que permite redactar requisitos verificables por construcción.
- **ISO/IEC/IEEE 12207:2017** para los procesos del ciclo de vida del software (ISO/IEC/IEEE, 2017).
- Principios de simplicidad, modularidad y trazabilidad aplicados al desarrollo ágil.

La selección de estas ediciones responde a una verificación del estado de vigencia de cada norma en el catálogo de su organismo emisor. La norma IEEE 830-1998, de uso extendido para la especificación de requisitos, figura en el catálogo del IEEE Standards Association con estado *superseded*: fue reemplazada por ISO/IEC/IEEE 29148:2011, cuya edición vigente es la de 2018. Por la misma razón se excluye IEEE 1016-2009, cuyo estado en ese catálogo es *inactive-reserved*.

---

# Metodología

Este trabajo combina una fase de investigación, orientada a comprender el proceso administrativo
tal como ocurre hoy, y una fase de construcción, orientada a producir un artefacto de software que
lo soporte. Ambas se describen a continuación con las metodologías y normas que las rigen.

## Enfoque y alcance de la investigación

La investigación es de **enfoque cualitativo** y **carácter aplicado**: no busca generalizar a una
población, sino caracterizar en profundidad dos procesos administrativos concretos —adición de
créditos y novedad de notas— en una unidad institucional determinada, la Sede Cali de la
Universidad Remington, para derivar de esa caracterización los requisitos de un sistema.

El **alcance es descriptivo y proyectivo**. Descriptivo, porque documenta el flujo real de los dos
trámites, sus actores, sus reglas y sus puntos de fricción. Proyectivo, porque a partir de esa
descripción propone y construye una solución técnica. No es un estudio explicativo ni
correlacional: no se contrastan hipótesis sobre relaciones entre variables.

La **unidad de análisis** es el proceso administrativo, no la persona. El estudiante no es sujeto de
investigación ni usuario del sistema propuesto; su relación con el trámite se observa de forma
mediada, a través de la coordinación.

## Técnicas e instrumentos de recolección

### Entrevista semiestructurada a informante clave

La técnica principal de levantamiento fue la **entrevista semiestructurada**. La selección de la
informante no fue aleatoria sino **intencional por criterio de posición**: la Coordinación
Académica de la Sede Cali es el único actor que interviene en la totalidad de los dos trámites, de
extremo a extremo, y por tanto el único con visión completa del proceso. En un levantamiento cuyo
objeto es el proceso y no la percepción individual, el criterio pertinente es la posición del
informante respecto del flujo, no el tamaño de la muestra.

Se realizaron **dos sesiones**, con diseño deliberadamente distinto:

| | **Sesión 1** — 12 de mayo | **Sesión 2** — 4 de junio |
|---|---|---|
| Conducción | Exploratoria, sin guion previo | Estructurada sobre guion escrito |
| Diseño | Pregunta de apertura amplia y profundización emergente | 35 preguntas en 9 bloques temáticos |
| Origen del guion | — | Vacíos detectados al analizar la Sesión 1 |
| Modalidad | Presencial, grabada con autorización expresa | Presencial, grabada con autorización expresa |

La secuencia no es casual: la primera sesión sirvió para **descubrir** la estructura del proceso, y
la segunda para **cerrar** los vacíos que ese descubrimiento dejó abiertos. El guion de la segunda
es, en sí mismo, un producto del análisis de la primera.

Ambas sesiones fueron grabadas previa autorización expresa de la entrevistada, solicitada al inicio
de cada encuentro, y transcritas mediante un servicio automático de voz a texto.

### Tratamiento de datos personales

El material derivado de las entrevistas está **anonimizado por rol**: cada persona mencionada se
sustituye por su función institucional, conforme a la Ley 1581 de 2012 (Régimen General de
Protección de Datos Personales). Se trata de datos de terceros que no otorgaron consentimiento para
figurar nominalmente en un documento académico. Las transcripciones originales, que sí conservan
los nombres, permanecen bajo custodia del equipo y fuera del repositorio del proyecto.

### Documento de evidencia

El registro completo del levantamiento —ficha técnica de cada sesión, transcripción de los bloques
temáticos con sus citas textuales, inventario de fuentes primarias, instrumento íntegro de la
Sesión 2, trazabilidad entre cada decisión de diseño y la cita que la sustenta, y limitaciones del
levantamiento— se consigna en un **documento de evidencia independiente**, referenciado en el
Anexo A de este trabajo.

Este apartado presenta únicamente la síntesis metodológica y los hallazgos que sustentan
directamente el planteamiento del problema y la definición de requisitos. La remisión al documento
dedicado responde a una decisión editorial: incorporar aquí el corpus completo desplazaría el
argumento del trabajo sin aportar a su comprensión, y el material conserva mayor utilidad como
instrumento consultable que como cuerpo del texto.

### Limitaciones declaradas del instrumento

El documento de evidencia declara las limitaciones del levantamiento, de las cuales interesa
destacar aquí las que condicionan la lectura de las citas:

- El servicio de transcripción empleado **no realiza diarización de hablantes**: el texto resultante
  es un flujo continuo sin marca de quién habla en cada turno. La atribución de las preguntas al
  entrevistador se reconstruyó por análisis del contenido y del giro dialógico.
- La transcripción presenta **errores de reconocimiento acústico** recurrentes sobre nombres
  propios, siglas y terminología institucional. Las citas se reproducen sin corrección editorial,
  marcando con `[sic]` los pasajes donde el error es evidente.
- La duración registrada de la Sesión 2 es una **estimación del equipo**, no un dato del
  instrumento.

Estas limitaciones no invalidan el levantamiento —la cobertura temática está completa— pero
determinan que toda afirmación atribuida a la Coordinación se contraste contra el giro dialógico
completo y no contra una línea aislada.

## Análisis del problema: Marco Lógico

El análisis del problema se estructuró con la **Metodología del Marco Lógico** (Ortegón, Pacheco y
Prieto, 2005). El procedimiento consistió en construir un árbol de problemas que identifica un
problema central, sus causas raíz y sus efectos, y en derivar de cada causa un sub-problema y su
objetivo específico correspondiente.

La descomposición se realizó bajo criterio **MECE** —mutuamente excluyente, colectivamente
exhaustivo—, de modo que cada causa se traduzca en un único sub-problema y que el conjunto cubra el
problema central sin solapamientos. Los verbos de los objetivos específicos se seleccionaron sobre
la **taxonomía de Bloom revisada** (Anderson y Krathwohl, 2001), priorizando los niveles de
*aplicar*, *analizar* y *crear*.

El resultado son siete sub-problemas (SP1 a SP7), cada uno con su objetivo específico medible, que
constituyen el insumo directo del backlog de desarrollo.

## Metodología de desarrollo: Scrum

La construcción se organiza con **Scrum**, en tres sprints. El ordenamiento no responde a
conveniencia sino a **dependencia técnica**: cada sprint presupone lo que entregó el anterior.

| Sprint | Sub-problemas | Criterio de agrupación |
|---|---|---|
| **1** | SP1, SP2, SP6 | La columna vertebral. Sin motor de workflow, datos validados ni auditoría, los demás módulos no tienen dónde apoyarse |
| **2** | SP3, SP4 | Lo que permite que el trámite *salga* del sistema con valor formal: documento generado y traza de aprobaciones |
| **3** | SP5, SP7 | La experiencia operativa diaria. Llega último porque presupone que el flujo y el documento ya funcionan |

El proceso de desarrollo se inscribe en el marco de **ISO/IEC/IEEE 12207:2017**, que define los
procesos del ciclo de vida del software y bajo el cual se ubican las actividades de especificación,
diseño, construcción y verificación descritas en este apartado.

## Normas de especificación y diseño

**Requisitos.** La especificación de requisitos se estructura según **ISO/IEC/IEEE 29148:2018**,
cláusula 9.6. Esta norma sustituye a IEEE 830-1998, retirada del catálogo del IEEE Standards
Association, que figuraba en versiones previas de este marco.

**Arquitectura.** La arquitectura se documenta mediante el **modelo C4** para la descripción
estructural en niveles de abstracción sucesivos —contexto, contenedores, componentes y código— y el
**modelo de vistas 4+1** para la separación entre vistas lógica, de proceso, de desarrollo, física y
de escenarios. Se descartó IEEE 1016-2009, cuyo estado en el catálogo del IEEE es
*inactive-reserved*.

## Validación

La validación prevista opera en dos niveles:

- **Verificación técnica**, durante el desarrollo: pruebas automatizadas sobre el comportamiento
  especificado y revisión de código contra los criterios de aceptación de cada sub-problema.
- **Validación funcional con el usuario final**, sobre la demostración operativa del sistema, con la
  Coordinación Académica en el rol de validadora.

La validación funcional **está prevista y no se ha realizado al momento de redactar este apartado**.
Sus resultados se consignarán en el capítulo correspondiente.

## Criterios de selección de fuentes bibliográficas

> **PENDIENTE DE REVISIÓN CON LA TUTORA.** Esta subsección declara un criterio metodológico que aún no ha sido validado por la dirección del trabajo. Se somete a revisión antes de darlo por cerrado.

La revisión de literatura de este trabajo aplica dos criterios temporales distintos, según la función que cumple cada fuente dentro del marco de referencia.

**Criterio general: ventana 2020–2026.** Las fuentes que sustentan el estado del arte y los antecedentes se restringen a publicaciones aparecidas entre 2020 y 2026. La razón es que estas fuentes deben responder a la pregunta de qué se ha implementado recientemente en materia de digitalización de trámites académicos, y una respuesta construida sobre trabajos de hace quince años no describiría el estado actual de la práctica. Todas las fuentes se referencian bajo normas APA en su séptima edición.

**Excepción acotada: eje de modelos de proceso configurables.** Se admiten fuentes anteriores a 2020 en dos supuestos, ambos circunscritos al eje de motores de workflow y modelos de proceso configurables: cuando la fuente constituya la formulación original del concepto, o cuando documente una aplicación de ese concepto a procesos administrativos de instituciones de educación superior.

Esta excepción se justifica por cuatro razones. Primera: el concepto de proceso configurable es el núcleo de la pregunta de investigación de este trabajo, y su formulación canónica es anterior a la ventana general; citarlo únicamente a través de revisiones posteriores equivaldría a sustentar el concepto central sobre fuentes secundarias. Segunda: en la teoría del diseño de procesos, la obra fundacional no queda obsoleta por el paso del tiempo del modo en que sí lo queda un reporte de implementación tecnológica, porque describe una construcción conceptual y no un estado de la técnica. Tercera: la búsqueda documental evidenció que la restricción temporal estricta excluía por año —no por pertinencia— trabajos cuyo objeto coincide con el de esta investigación. Cuarta, y en relación con el segundo supuesto: la aplicación específica del modelo configurable a trámites de educación superior está escasamente documentada, y el registro más pertinente localizado es anterior a la ventana; excluirlo obligaría a presentar como no documentado algo que sí lo está, lo cual constituye un defecto de mayor gravedad que citar una fuente fuera de la ventana declarándolo.

**Delimitación de la excepción.** La apertura temporal se aplica exclusivamente al eje de motores de workflow y modelos de proceso configurables. Los demás ejes del marco de referencia —modelado de procesos, validación de datos en origen, documentos electrónicos verificables, trazabilidad y auditoría, y transformación digital en educación superior— se mantienen dentro de la ventana 2020–2026. Cada fuente admitida por la excepción se identifica como tal en el momento de citarla.

**Tratamiento de las normas técnicas.** Las normas emitidas por organismos de normalización no se someten a la ventana temporal. La razón es que una norma no cumple en este trabajo la función de describir el estado del arte, sino la de fijar un marco prescriptivo de referencia, y su vigencia no la determina el año de publicación sino el estado que le asigna el catálogo de su organismo emisor. En consecuencia, cada norma se cita en la edición vigente al momento de esta redacción y ese estado se comprueba en el catálogo oficial correspondiente. Cuando la comprobación revela que una norma fue reemplazada o retirada, se sustituye por la edición vigente y se deja constancia del reemplazo.

**Procedimiento de verificación de fuentes.** Toda referencia empleada en este trabajo se comprueba individualmente contra la fuente primaria antes de incorporarse: se contrasta título completo, lista completa de autores, año, medio de publicación y paginación, consultando el registro del identificador digital de objeto (DOI) en Crossref o, cuando no existe DOI, el documento mismo. En el caso de las normas técnicas, la comprobación se realiza además contra el catálogo del organismo emisor, que es la única fuente que declara el estado de vigencia; la aplicación de este procedimiento permitió detectar que dos de las normas inicialmente previstas para este trabajo se encontraban reemplazada e inactiva, respectivamente. Este procedimiento se adoptó tras constatar que las herramientas de búsqueda asistida utilizadas en la fase exploratoria produjeron fichas bibliográficas defectuosas —títulos truncados, autores omitidos, paginación inexistente y, en un caso, la combinación de dos trabajos distintos en una sola referencia—, sin que dichos defectos fueran detectables sin acudir a la fuente.

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

### Modelado y rediseño de procesos

- Milani, F., & Lashkevich, K. (2025). Business process improvement opportunities and redesign options: A systematic literature review. *Business Process Management Journal*. Publicación anticipada en línea. https://doi.org/10.1108/BPMJ-02-2025-0232
- Miller, R., Whelan, H., Chrubasik, M., Whittaker, D., Duncan, P., & Gregório, J. (2024). A framework for current and new data quality dimensions: An overview. *Data, 9*(12), Artículo 151. https://doi.org/10.3390/data9120151
- Mukherjee, K. K., Reka, L., Mullahi, R., Jani, K., & Taraj, J. (2021). Public services: A standard process model following a structured process redesign. *Business Process Management Journal, 27*(3), 796–835. https://doi.org/10.1108/BPMJ-03-2020-0107
- Reeves, J. J., Mekeel, K. L., Waterman, R. S., Rhodes, L. R., Clay, B. J., Clary, B. M., & Longhurst, C. A. (2020). Association of electronic surgical consent forms with entry error rates. *JAMA Surgery, 155*(8), 777. https://doi.org/10.1001/jamasurg.2020.1014

### Trazabilidad, auditoría y documentos verificables

- Andrews, R., & Wynn, M. T. (2026). Event log imperfection patterns for process mining: Towards a systematic approach to cleaning event logs. *Information Systems, 137*, Artículo 102645. https://doi.org/10.1016/j.is.2025.102645
- González, L., Delgado, A., Canaparo, J., & Gambetta, F. (2022). Evaluation of compliance requirements for collaborative business process with process mining and a model of generic compliance controls. *CLEI Electronic Journal, 25*(2). https://doi.org/10.19153/cleiej.25.2.7
- Sharif, A., Ginting, D. S., & Dias, A. D. (2021). Securing the integrity of PDF files using RSA digital signature and SHA-3 hash function. En *2021 International Conference on Data Science, Artificial Intelligence, and Business Analytics (DATABIA)* (pp. 154–159). IEEE. https://doi.org/10.1109/DATABIA53375.2021.9650121
- Vargas, F., & Piedra, N. (2023). Decentralized issuance and verification of university micro-credentials. En *2023 12th International Conference on Software Process Improvement (CIMPS)* (pp. 90–99). IEEE. https://doi.org/10.1109/CIMPS61323.2023.10528844

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
- Tsakalidis, G. (2022). *A framework for systematic evaluation of Business Process Redesign (BPR) initiatives using the notion of model plasticity* [Tesis doctoral, University of Macedonia, Department of Applied Informatics]. National Archive of PhD Theses. https://doi.org/10.12681/eadd/52832

### Metodología de investigación y de proyecto

- Anderson, L. W., y Krathwohl, D. R. (Eds.). (2001). *A taxonomy for learning, teaching, and assessing: A revision of Bloom's taxonomy of educational objectives*. Longman. *(excepción metodológica — ver nota 3)*
- Congreso de la República de Colombia. (2012, 17 de octubre). *Ley 1581 de 2012. Por la cual se dictan disposiciones generales para la protección de datos personales*. Diario Oficial No. 48.587.
- Ortegón, E., Pacheco, J. F., y Prieto, A. (2005). *Metodología del marco lógico para la planificación, el seguimiento y la evaluación de proyectos y programas* (Serie Manuales N.º 42). CEPAL/ILPES. *(excepción metodológica — ver nota 3)*

### Modelos de descripción arquitectónica

- Brown, S. (s.f.). *The C4 model for visualising software architecture*. https://c4model.com
- Kruchten, P. (1995). Architectural blueprints — The «4+1» view model of software architecture. *IEEE Software*, *12*(6), 42–50. https://doi.org/10.1109/52.469759 *(excepción metodológica — ver nota 3)*

### Normas de ingeniería de software

- ISO/IEC/IEEE. (2017). *Systems and software engineering — Software life cycle processes* (ISO/IEC/IEEE 12207:2017). IEEE. https://doi.org/10.1109/IEEESTD.2017.8100771
- ISO/IEC/IEEE. (2018). *Systems and software engineering — Life cycle processes — Requirements engineering* (ISO/IEC/IEEE 29148:2018). IEEE. https://doi.org/10.1109/IEEESTD.2018.8559686

> **Notas de trabajo (no forman parte del documento final).**
> 1. ✅ **Sustento del diseño arquitectónico — RESUELTO.** C4 y el modelo de vistas 4+1 ya figuran en la Metodología, en «Normas de especificación y diseño», con la constancia de que IEEE 1016-2009 se descartó por estar *inactive-reserved*. Sus referencias se incorporaron a la bibliografía.
> 2. **Especificación de requisitos**: la migración a ISO/IEC/IEEE 29148:2018 implica que la especificación de requisitos se estructure según la cláusula 9.6 de esa norma y no según la plantilla de IEEE 830. El entregable de requisitos aún no está redactado, de modo que el cambio no obliga a rehacer trabajo existente.
> 3. ⚠️ **CONFLICTO ABIERTO — la Metodología cita tres fuentes que su propio criterio de selección excluiría.** Ortegón, Pacheco y Prieto (2005), Anderson y Krathwohl (2001) y Kruchten (1995) son anteriores a la ventana 2020–2026, y **no las cubre la excepción declarada**, que está circunscrita al eje de motores de workflow y modelos de proceso configurables. No son normas técnicas —esas sí están exentas—, sino literatura metodológica.
>
> El argumento que ya sostiene la excepción vigente aplica de forma idéntica a estas tres: *«la obra fundacional no queda obsoleta por el paso del tiempo del modo en que sí lo queda un reporte de implementación tecnológica, porque describe una construcción conceptual y no un estado de la técnica»*. Marco Lógico, la taxonomía de Bloom revisada y el modelo 4+1 son, las tres, formulaciones canónicas de una construcción conceptual, y ninguna tiene sustituto posterior que las derogue.
>
> **Propuesta**: ampliar la excepción con un tercer supuesto —*fuentes que constituyan la formulación canónica de una metodología o de un modelo de descripción empleado por este trabajo*— en lugar de citarlas sin declarar. **Requiere aval de la tutora**, igual que el criterio general del que depende. Mientras no se resuelva, quedan marcadas *(excepción metodológica)* en la bibliografía.

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
