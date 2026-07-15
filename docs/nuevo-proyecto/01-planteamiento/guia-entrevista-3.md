# Guía de entrevista N.º 3 — Coordinación Académica de la Sede Cali

> **Fecha de elaboración**: 2026-05-19
> **Próxima entrevista**: por agendar
> **Entrevistador(es)**: equipo de trabajo de grado — Programa de Ingeniería de Sistemas, Universidad Remington (modalidad Distancia, SNIES 53112, Resolución 015939 del 1 de septiembre de 2023).
> **Entrevistada**: Coordinación Académica de la Sede Cali.
> **Propósito**: validar y profundizar la información recogida en las dos entrevistas previas (`material-coord/transcript-entrevista-coordi.md` y `material-coord/transcript-entrevista-coordi-2.md`) para cerrar los puntos que aún quedan ambiguos y permitir el avance hacia las fases de especificación (Spec Kit) y arquitectura del MVP.
> **Duración estimada**: 60 a 75 minutos.
> **Modalidad sugerida**: presencial o vía Microsoft Teams (grabada, con autorización previa).

---

## 0. Apertura y agradecimiento

Antes de iniciar el cuestionario, el entrevistador debe:

1. Agradecer formalmente la disposición y el tiempo otorgado por la coordinación, recordando que el ejercicio se enmarca en el desarrollo del trabajo de grado y que su colaboración es insumo primario del proyecto.
2. Recordar que el alcance del MVP se ha acotado a los **dos procesos** identificados como críticos en las entrevistas previas: *adición de créditos* y *novedad de notas*, ambos restringidos a la **Sede Cali**.
3. Solicitar autorización para grabar la sesión, de manera consistente con las entrevistas anteriores.
4. Indicar que la entrevista está organizada en bloques temáticos y que algunas preguntas pueden parecer detalladas: el propósito no es duplicar lo ya conversado, sino **cerrar ambigüedades específicas** detectadas al revisar las transcripciones.
5. Informar que algunas preguntas del **Bloque IV** (datos y plantillas reales) idealmente se atenderían **antes** de la entrevista mediante correo electrónico, para llegar a la reunión con material analizable.

---

## 1. Convenciones del documento

Cada pregunta se presenta con la siguiente estructura:

- **Pregunta** — enunciado tal como se debe formular durante la entrevista.
- **Referencia en entrevistas previas** — cita textual o paráfrasis del fragmento donde el tema apareció (entrevista 1 = `material-coord/transcript-entrevista-coordi.md`; entrevista 2 = `material-coord/transcript-entrevista-coordi-2.md`).
- **Motivo de la consulta** — explica qué quedó ambiguo, incompleto o no abordado.
- **Lo que se busca confirmar** — puntos concretos que la respuesta debe permitir cerrar.

Los bloques se clasifican según su criticidad para el avance del proyecto:

| Categoría | Significado | Bloques |
|-----------|-------------|---------|
| **BLOQUEANTE** | Sin esta información, no se puede cerrar la fase de especificación (Spec Kit `specify`) ni la constitución del proyecto con la rigurosidad exigida por un software universitario. | I, II, III, IV, V |
| **IMPORTANTE** | Necesaria para iniciar el primer sprint con criterios de aceptación realistas. Puede recolectarse en paralelo, pero no debe diferirse más allá del cierre del Sprint 1. | VI, VII, VIII |
| **ÚTIL** | Refina el producto y ayuda a tomar mejores decisiones en sprints posteriores. No bloquea el inicio. | IX |

---

## 2. Bloque I — Marco normativo institucional [BLOQUEANTE]

### Justificación del bloque

Tratándose de un software destinado a operar sobre procesos académicos de una **universidad privada**, todo trámite que el sistema gestione debe poder respaldarse en un documento normativo oficial: resolución, comunicado, manual de procesos o circular. Sin estas referencias, no es posible:

- Definir los campos obligatorios de cada trámite con base en la normativa vigente y no únicamente en la práctica observada.
- Demostrar ante el tutor, ante el tribunal evaluador y eventualmente ante la institución, que el MVP respeta el marco regulatorio interno.
- Cumplir con la regla #4 del estándar interno del equipo, que exige citar fuentes exactas para toda afirmación basada en normativa o documentación oficial.

### Pregunta 1 — Comunicado o resolución que creó el formato de adición de créditos

**Pregunta**: En la entrevista anterior usted mencionó que el formato de adición de créditos fue creado en 2025 mediante un comunicado oficial. ¿Sería posible conocer la referencia exacta de ese comunicado o resolución (número, fecha, dependencia que lo emite) y, de ser posible, obtener una copia?

**Referencia en entrevistas previas (entrevista 2):**

> "Lo crearon en el 2025. Y ahí nos dijeron, primero tienen que pasar por la facultad y luego registro y control. Entonces eso de la adición de créditos es un formato que lleva año y medio."

**Motivo de la consulta**: la fecha aproximada y la dependencia emisora no quedaron registradas. La trazabilidad normativa es indispensable para fundamentar las reglas de negocio del módulo de adición de créditos.

**Lo que se busca confirmar**:

- Número y fecha exacta del comunicado o acto administrativo.
- Dependencia emisora (Vicerrectoría Académica, Registro y Control Nacional, Consejo Académico, etc.).
- Si existe copia oficial que pueda compartirse para citarla en el documento de requisitos.

---

### Pregunta 2 — Normativa que regula la novedad de notas

**Pregunta**: A diferencia del formato de adición de créditos, el proceso de novedad de notas, según lo conversado, "siempre ha existido". ¿Sabe usted qué reglamento, resolución o manual interno regula formalmente este trámite? ¿Existe una versión escrita del procedimiento?

**Referencia en entrevistas previas (entrevista 2):**

> "El de novedad de notas siempre ha estado. […] no, yo ya llevo dos años y medio y ese [proceso no se ha mejorado]."

**Motivo de la consulta**: a diferencia de la adición de créditos, no se identificó ningún documento de referencia para novedad de notas. Sin un marco normativo, las reglas del trámite quedarían apoyadas solo en la práctica de la coordinación, lo que introduce riesgo de subjetividad.

**Lo que se busca confirmar**:

- Si existe reglamento estudiantil, manual académico o resolución que codifique el procedimiento.
- Vigencia y última fecha de actualización del documento, si existe.
- Posibilidad de obtener una copia.

---

### Pregunta 3 — Existencia de un manual de procesos académicos

**Pregunta**: La universidad menciona varias veces formatos versionados gestionados por "gestión de calidad". ¿Existe un manual o repositorio de procesos académicos institucional que documente, paso a paso, los flujos de adición de créditos y novedad de notas?

**Referencia en entrevistas previas (entrevista 2):**

> "Eso lo hace, sí, eso lo hace gestión de calidad, solo Remington es muy organizada en eso, todo tiene formato y tiene codificación."

**Motivo de la consulta**: la existencia de un sistema de gestión de calidad sugiere que debe haber procedimientos documentados con codificación interna. Acceder a ellos permitiría modelar el workflow con base en el flujo oficial y no en una reconstrucción a partir de entrevistas.

**Lo que se busca confirmar**:

- Si existe un manual o sistema documental de procesos consultable.
- Cómo se accede (portal interno, contacto en gestión de calidad).
- Si los procesos de interés tienen ficha de proceso registrada y disponible.

---

### Pregunta 4 — Acuerdos de nivel de servicio (SLA) formales

**Pregunta**: En las entrevistas anteriores se mencionaron rangos de tiempo amplios: de 3 a 5 días en el mejor caso y hasta 2 meses en el peor para ambos trámites. ¿Existe un acuerdo formal de tiempo máximo de respuesta para cada trámite, o esos rangos corresponden a la práctica observada?

**Referencia en entrevistas previas (entrevista 1, sobre adición de créditos):**

> "Se puede ir tres días, cinco días, pero si el formato quedó con errores lo regresan […] aproximadamente dos semanas […] máximo […] por ahí tres semanas […] a veces con estudiantes me he demorado y medio dos meses."

**Referencia en entrevistas previas (entrevista 2, sobre novedad de notas):**

> "El mejor tiempo es una semana […] mes y medio, dos meses […] es relativo."

**Motivo de la consulta**: la existencia o ausencia de un SLA institucional afecta directamente las **métricas de éxito** del MVP (sección 9 del árbol de problemas). Si existe un máximo formal, se convierte en línea base contra la cual comparar.

**Lo que se busca confirmar**:

- Si existe un tiempo máximo institucional definido para cada trámite.
- Origen del SLA (reglamento, política interna, calendario académico).
- Si hay consecuencias formales por incumplimiento del plazo.

---

## 3. Bloque II — Reglas de validación y variantes del trámite [BLOQUEANTE]

### Justificación del bloque

El motor de workflow del MVP debe **validar los datos de entrada** (causa raíz C2 del árbol de problemas) antes de iniciar el flujo de aprobaciones. Para hacerlo correctamente, se requiere conocer las reglas de negocio que rigen los trámites: topes, restricciones, materias elegibles, ventanas temporales, y cuántas **variantes** existen de cada trámite. Sin esto, no es posible definir los **criterios de aceptación** de las historias de usuario del Sprint 1 (SP1, SP2, SP6 del árbol de problemas).

### Pregunta 5 — Reglas de adición de créditos: tope y materias elegibles

**Pregunta**: Para una solicitud de adición de créditos, ¿cuál es el número máximo de créditos adicionales que un estudiante puede solicitar? ¿Existen restricciones sobre el tipo de materias que pueden adicionarse (línea de énfasis, electivas, materias perdidas previamente)? ¿Hay asignaturas que **no** pueden adicionarse bajo este mecanismo?

**Referencia en entrevistas previas (entrevista 1):**

> "Si noveno semestre, los créditos son 15, solo puedes matricular 15 créditos. […] el class con que se pase uno solo no deja matricular esa materia. […] Hay que llenar un formato de adición de créditos para que nos autoricen eso en el clase."

> "Por eso no me deja matricular línea de énfasis, por ejemplo."

**Motivo de la consulta**: las entrevistas describen el mecanismo pero no fijan límites cuantitativos ni restricciones por tipo de materia. Estas reglas determinan validaciones automáticas que el sistema debe ejecutar antes de iniciar el workflow.

**Lo que se busca confirmar**:

- Tope máximo de créditos adicionales por solicitud y por semestre.
- Categorías de asignatura habilitadas (énfasis, electivas, núcleo, electivas libres, etc.).
- Restricciones por número de semestre del estudiante.
- Casos en los que la solicitud no procede formalmente y debe rechazarse de entrada.

---

### Pregunta 6 — Reglas de novedad de notas: límite temporal y tipos de novedad

**Pregunta**: Para una novedad de notas, ¿existe un plazo máximo entre el cierre del semestre y el momento en que el estudiante o el docente pueden solicitarla? ¿Procede únicamente por nota faltante o también por nota mal calculada, corrección por reclamación u otros motivos?

**Referencia en entrevistas previas (entrevista 1):**

> "Después de que se acaba el semestre, los profesores tienen de 3 a 4 días para subir notas o arreglar errores. […] Pero cuando el sistema se cierra, se cierra a nivel nacional y eso no se vuelve a aperturar."

**Referencia en entrevistas previas (entrevista 2):**

> "Empieza el proceso con el estudiante me notifica y empieza mi proceso […] viene el proceso más tedioso, que es hacer la novedad de la nota para que la suban al sistema."

**Motivo de la consulta**: la entrevista describe el origen típico de la novedad (nota olvidada o pago tardío), pero no clarifica si hay un plazo máximo para solicitarla o si existen otras causales válidas. Ambas variables son críticas para validar la elegibilidad de una solicitud en la captura inicial.

**Lo que se busca confirmar**:

- Plazo máximo institucional para solicitar una novedad después del cierre de notas.
- Causales válidas reconocidas (nota faltante, nota incorrecta, reclamación tardía, ajuste por homologación, otros).
- Diferencias procedimentales según la causal, si las hay.

---

### Pregunta 7 — Variantes internas de cada trámite

**Pregunta**: ¿Existen variantes o subtipos de cada trámite que se procesen de forma distinta? Por ejemplo, ¿una novedad de notas por error de digitación del docente sigue exactamente el mismo flujo que una novedad por nota nunca cargada? ¿Una adición de créditos para una materia de énfasis sigue el mismo flujo que una para una electiva?

**Referencia en entrevistas previas (entrevista 1):**

> "Hay facultades que contestan más rápido que otras. […] el proceso para todas las dependencias es igual, la firma es diferente."

**Motivo de la consulta**: el principio rector del MVP es que el motor de workflow sea **configurable por dato y no por código**. Para validar que esa abstracción es viable, es indispensable identificar **todas las variantes** que actualmente se procesan, aunque hoy se traten manualmente como casos individuales.

**Lo que se busca confirmar**:

- Si existen subtipos formales o informales de cada trámite.
- Diferencias en el conjunto de roles que aprueban, los campos requeridos, o las validaciones, entre subtipos.
- Si las diferencias se reducen a parámetros (firmas, anexos) o son flujos verdaderamente distintos.

---

### Pregunta 8 — Estándar institucional para el formato de firma

**Pregunta**: En la entrevista anterior se mencionó que la firma puede ser **escaneada** (firma a mano sobre papel, escaneada y montada en el documento) o **digital** (dibujada con el mouse o trazada en pantalla). ¿La institución tiene una preferencia o estándar oficial entre estas dos modalidades, o ambas son indistintamente válidas? ¿Existe alguna restricción técnica documentada (por ejemplo, derivada del incidente reciente en el que una firma sobre un documento escaneado no se visualizó correctamente al subirse al sistema QF)?

**Referencia en entrevistas previas (entrevista 1):**

> "La firma tiene que ser análoga digital. O sea, no puede escribir en el computador Juan, no, tienes que hacer tu firma Juan, la escaneas, le tomas la foto y la montas en el documento. Pero es válido, por ejemplo, una firma digital que él, o sea, digamos por el mouse dibuje su firma […] Sí, sí, sí, si es válido."

**Referencia en entrevistas previas (entrevista 2):**

> "Cada cosa que ambas formas de firmar son válidas, tanto imprimir como firmar a mano y subir, como digital. No hay un estándar. No. No es estricto por ese lado."

> "Ella imprimió el archivo, lo escaneó. […] firmó a mano […] lo escaneó y lo mató. Yo lo mandé a la facultad, pero la facultad puso la firma sobre ese escáner de fotos. […] Cuando se subió al QF, pues yo no me percaté […] la firma no se quedó viendo en QF y me lo regresaron."

**Motivo de la consulta**: la respuesta inicial fue "ambas son válidas, no hay estándar", pero la propia coordinación reportó un incidente reciente donde la combinación de una firma sobre un documento escaneado generó un problema técnico (firma invisible al subirse a QF). Esto sugiere que, aunque formalmente ambas modalidades sean válidas, **en la práctica una de ellas produce menos errores**. La decisión es relevante porque el módulo de generación de PDF del MVP (SP3 del árbol de problemas) y el módulo de firma (SP4) deben optar por un mecanismo concreto.

**Lo que se busca confirmar**:

- Si formalmente existe un lineamiento de la universidad o si la libertad es total.
- Si en la práctica la coordinación prefiere o recomienda una modalidad por confiabilidad.
- Si el sistema QF impone restricciones técnicas concretas sobre el formato de firma aceptado.
- Si la institución estaría abierta a un sello electrónico verificable (hash + sello de tiempo + identidad del firmante) generado por el sistema, como alternativa a la firma gráfica.

---

## 4. Bloque III — Autenticación e identidad institucional [BLOQUEANTE]

### Justificación del bloque

La definición del mecanismo de autenticación es estructural: define si el MVP puede integrarse al ecosistema de identidades de la universidad (Microsoft 365, Active Directory institucional) o si debe gestionar sus propios usuarios y credenciales. La decisión afecta el modelo de datos, el módulo de seguridad, las políticas de acceso y, eventualmente, la aceptación del piloto por parte del área de tecnología institucional. Sin esta información, cualquier diseño del módulo de autenticación corre el riesgo de quedar invalidado al primer contacto con tecnología institucional.

### Pregunta 9 — Mecanismo de autenticación en los sistemas institucionales

**Pregunta**: ¿Cómo se autentican hoy los funcionarios y estudiantes en los sistemas internos (Class, QF, OneDrive, Teams)? ¿Utilizan un único usuario y contraseña (inicio de sesión único / SSO con Microsoft 365), o cada sistema mantiene credenciales independientes?

**Referencia en entrevistas previas (entrevista 2):**

> "Hay un correo específico para eso que llaman novedades de registro."

> "Las personas que tenemos clases tenemos un usuario. […] todos tenemos un usuario que son con las iniciales de nuestro nombre."

> "OneDrive de todo lo que es Microsoft."

**Motivo de la consulta**: las menciones a Microsoft Teams y OneDrive sugieren que la universidad cuenta con un tenant institucional de Microsoft 365, lo cual habilitaría inicio de sesión único basado en Azure Active Directory (Entra ID). Confirmarlo es relevante porque cambiaría radicalmente el módulo de autenticación del MVP.

**Lo que se busca confirmar**:

- Si los usuarios institucionales tienen una cuenta única tipo `usuario@uniremington.edu.co` que da acceso a múltiples sistemas.
- Si la coordinación conoce o puede consultar al área de tecnología sobre la existencia de SSO institucional.
- Si Class mantiene un sistema de usuarios independiente.

---

### Pregunta 10 — Disponibilidad de directorio institucional consultable

**Pregunta**: ¿Existe un directorio institucional (servicio interno de la universidad) al que un sistema externo podría consultar para validar identidades, por ejemplo para verificar que un estudiante o un docente pertenece efectivamente a la institución?

**Referencia en entrevistas previas**: no abordado en las entrevistas anteriores.

**Motivo de la consulta**: incluso si el MVP no se integra al SSO institucional en su primera versión, conocer si existe un servicio de validación de identidades evita decisiones que después haya que revertir. Si no existe, el sistema debe asumir un esquema de gestión propia de credenciales.

**Lo que se busca confirmar**:

- Si la institución expone un servicio de directorio o de validación de identidades.
- A quién se puede consultar para confirmarlo (área de tecnología, mesa de ayuda interna).

---

### Pregunta 11 — Correo institucional como identificador mínimo

**Pregunta**: ¿Todos los estudiantes activos cuentan con correo institucional? ¿Cuál es el dominio? ¿La coordinación cuenta también con cuenta de correo institucional propia (distinta a la cuenta funcional `Cali Autorizado` mencionada en las entrevistas anteriores)?

**Referencia en entrevistas previas (entrevista 1):**

> "El estudiante le digo que me lo mande por correo electrónico […] al correo de Cali Autorizado y Remington."

**Motivo de la consulta**: si bien se conoce que existe una cuenta funcional para la sede, no quedó claro si cada estudiante y cada funcionario tienen identidad de correo institucional individual. El correo institucional es el identificador mínimo aprovechable como sustituto de un esquema de SSO completo.

**Lo que se busca confirmar**:

- Si todo estudiante activo tiene una cuenta de correo institucional propia.
- Si esa cuenta sirve como identificador inequívoco dentro de los sistemas internos.
- Cuál es el formato (por ejemplo, `nombre.apellido@miremington.edu.co`).

---

## 5. Bloque IV — Datos reales y plantillas oficiales [BLOQUEANTE — solicitar previa]

### Justificación del bloque

Sin acceso a los **artefactos reales** que circulan hoy en los procesos, cualquier diseño del MVP queda apoyado en una reconstrucción narrativa del flujo. Tener los formatos Word vigentes, los hilos de correo completos y los códigos institucionales reales permite:

- Modelar los campos exactos de cada trámite con su tipo de dato, longitud y formato.
- Replicar el documento PDF de salida con fidelidad institucional.
- Validar que el motor de workflow modela correctamente la cadena de aprobaciones tal como ocurre en la realidad, no como se interpreta de la entrevista.

**Recomendación operativa**: solicitar el material de este bloque **por correo electrónico antes** de la entrevista. Permitirá analizar los archivos previamente y formular preguntas más precisas en la reunión.

### Pregunta 12 — Hilo de correo real de una adición de créditos

**Pregunta**: Tal como usted ofreció amablemente en la entrevista anterior, ¿podría compartirnos un hilo de correo completo de un proceso de adición de créditos resuelto recientemente? Podría ser el hilo de la solicitud del propio entrevistador, para evitar consideraciones de privacidad de terceros.

**Referencia en entrevistas previas (entrevista 2):**

> "Yo no te copio cuando lo mando a Medellín, pero ejemplo, te puedo buscar el hilo de correos de tu proceso y reenviártelo. […] Ah bueno, sirve. Claro. Si es así, de crédito, sí, de notas. El de créditos."

**Motivo de la consulta**: el hilo permite ver los **tiempos reales** entre cada paso, los **textos exactos** de los correos institucionales, las **firmas reales** que circulan en cada etapa, y las **dependencias copiadas** que las entrevistas no detallaron al 100%.

**Lo que se busca confirmar**:

- Encadenamiento real de correos.
- Identificación de los actores intermedios.
- Plantillas de correo de cada paso (asunto, cuerpo, anexos).

---

### Pregunta 13 — Hilo de correo real de una novedad de notas

**Pregunta**: Si fuera posible y sin comprometer datos personales de terceros, ¿podría compartirnos también un hilo de correo de un proceso de novedad de notas resuelto? Comprendemos que este trámite involucra más datos sensibles; cualquier hilo en el que pueda anonimizar nombres y datos del estudiante también sería de gran utilidad.

**Referencia en entrevistas previas (entrevista 2):**

> "Ella manda el correo porque ya salió de facultad, mandó novedad de nota con los debidos soportes […] y nos copia a todos. Ahí ya me copian a mí. El correo va a la facultad, hay que esperar que la facultad revise, apruebe y firme el decano."

**Motivo de la consulta**: la novedad de notas es el trámite más complejo (mes y medio o más en el peor caso, según entrevista 2) y el que más actores involucra. El hilo real permite verificar la cadena de aprobaciones, los tiempos reales por etapa y los mensajes de error o devolución típicos.

**Lo que se busca confirmar**:

- Cadena real de remitentes, copia y reenvíos.
- Frecuencia y forma de las devoluciones por error.
- Plantillas de correo de cada paso.

---

### Pregunta 14 — Formatos Word vigentes de ambos trámites

**Pregunta**: ¿Podría compartirnos el formato Word vigente de **adición de créditos** y el de **novedad de notas**, con su codificación oficial y su versión actual?

**Referencia en entrevistas previas (entrevista 2):**

> "Eso viene con unos códigos, formato, ta, edición 2021. El de la edición de créditos, formato es 000012025."

> "La casilla de los formatos tienen la fecha de la versión."

**Motivo de la consulta**: los formatos Word son los **artefactos canónicos** del trámite. Los campos del formato son los campos que el módulo de formularios validados (SP2 del árbol de problemas) debe replicar y validar, y son los campos que el módulo de generación de PDF (SP3) debe poder reproducir con fidelidad institucional.

**Lo que se busca confirmar**:

- Estructura exacta de cada formato.
- Tipo de dato y restricciones por campo (longitud, formato de fecha, lista cerrada de valores).
- Versión vigente y su codificación oficial.
- Si los formatos se actualizan periódicamente y cómo se comunica el cambio.

---

### Pregunta 15 — Códigos institucionales y estructura del pénsum

**Pregunta**: Para el desarrollo del MVP necesitamos modelar las asignaturas y los programas. ¿Podría compartirnos al menos un pénsum vigente del programa de Ingeniería de Sistemas con los códigos exactos de las asignaturas y la distribución de créditos por semestre? Si existe un documento institucional con la nomenclatura oficial de programas y facultades de la sede, también sería de gran ayuda.

**Referencia en entrevistas previas (entrevista 1):**

> "Las materias tienen que crear con el Class con el código que es. […] yo puse cálculo diferencial, pero no sé por qué no puse el código de sistema y no que puse el código de industrial."

**Motivo de la consulta**: el sistema debe identificar inequívocamente la asignatura sobre la cual se solicita el trámite. Los códigos institucionales son la fuente de verdad: el modelo de datos del MVP debe replicarlos en lugar de inventar identificadores propios. Tener al menos el pénsum de Ingeniería de Sistemas como caso de referencia es suficiente para arrancar.

**Lo que se busca confirmar**:

- Códigos de asignatura del programa de Ingeniería de Sistemas (al menos uno).
- Distribución de créditos por semestre.
- Convenciones institucionales para identificar programas y facultades.

---

## 6. Bloque V — Clarificación de roles y firmas [BLOQUEANTE]

### Justificación del bloque

El motor de workflow debe modelar con precisión los **roles aprobadores** de cada trámite. Las entrevistas describen los pasos pero dejan algunos roles con definición ambigua. Sin esta clarificación, no se puede modelar la máquina de estados ni asignar correctamente los permisos en el sistema.

### Pregunta 16 — Significado y rol de la "Dirección de CD"

**Pregunta**: En la descripción del flujo de novedad de notas usted mencionó a la "Dirección de CD" como una instancia de firma intermedia, gestionada por su auxiliar Jennifer. ¿Podría aclarar qué significa la sigla CD (Centro de Distancia, Centro Directivo u otra), cuál es exactamente el alcance de su firma en este trámite (revisión administrativa, revisión académica, validación de procedimiento) y por qué interviene en novedad de notas pero **no** en adición de créditos?

**Referencia en entrevistas previas (entrevista 2):**

> "Le escribo por el interno al Teams a ella. Jennifer ya está lista la carpeta de la novedad de la nota […] adelante para que la revises y la mandes por favor."

> "Va a una firma de dirección de CD. Son cuatro casillas de firmas. […] Va la firma del docente, va la firma de la dirección de CD, también le tengo que escribir a ella que ponga la firma."

> "Ella le digo a Jennifer […] ella descarga la carpeta y ahí sí ya redacta el correo electrónico."

**Motivo de la consulta**: la sigla y la naturaleza exacta de esta dependencia no quedaron explicadas. Modelar el rol requiere conocer su jerarquía, su responsabilidad y el porqué de su asimetría entre los dos trámites (interviene solo en uno).

**Lo que se busca confirmar**:

- Significado exacto de la sigla CD.
- Función formal de la dependencia en la estructura de la sede.
- Alcance preciso de su firma (qué revisa, qué autoriza).
- Razón por la cual interviene en novedad de notas y no en adición de créditos.

---

### Pregunta 17 — Naturaleza de la firma del decano

**Pregunta**: En la entrevista anterior usted comentó que recibe el formato firmado por el decano, pero que no le consta si la firma es realmente del decano o si su asistente Rodrigo la aplica en su nombre. Para fines del MVP es importante saber si el rol que el sistema debe modelar es **"Decano"** (toma de decisión personal) o **"Asistente del Decano por delegación"** (operación administrativa rutinaria). ¿Sería posible consultarlo directamente con el asistente o con la decanatura para confirmar la naturaleza formal del acto de firma?

**Referencia en entrevistas previas (entrevista 1):**

> "Yo creo que usan la firma. Supongo que usan la firma de él. Pero en algunos casos, él me reenvía al correo, te enviaba el decano. No sé si el decano solo revisa o si hace la firma, no me consta."

> "No sé, hasta ahí no sé, solo sé que Rodrigo me reenvía el correo del decano con el formato firmado."

**Motivo de la consulta**: la diferencia entre los dos escenarios es significativa. Si el decano es quien decide y firma, el sistema debe asignar el paso al **decano** y notificarlo a él. Si en la práctica el asistente firma por delegación, el paso se asigna al **asistente** y el decano puede o no recibir notificación informativa. Esto impacta el diseño de bandejas por rol (SP5 del árbol de problemas) y la auditoría de aprobaciones (SP4).

**Lo que se busca confirmar**:

- Si existe un acto formal de delegación firmado.
- Si el decano efectivamente revisa cada trámite o solo lo hace de manera excepcional.
- Quién es el firmante de derecho y quién de hecho.

---

## 7. Bloque VI — Casos borde y excepciones [IMPORTANTE]

### Justificación del bloque

Las entrevistas describen el **camino feliz** del trámite. Para definir criterios de aceptación de las historias de usuario del Sprint 1, es necesario conocer los **escenarios de excepción** que el MVP debe poder modelar (aunque sea con flujo manual de escape en su primera versión).

### Pregunta 18 — Docente no disponible para firmar

**Pregunta**: En el flujo de novedad de notas se requiere la firma del docente que dictó la asignatura. ¿Qué procedimiento se sigue cuando ese docente **ya no trabaja** en la universidad o no responde a la solicitud después de un tiempo razonable?

**Referencia en entrevistas previas**: no abordado.

**Motivo de la consulta**: este escenario es plausible (rotación docente, semestres antiguos) y bloquea por completo el trámite si no existe un procedimiento alternativo formal.

**Lo que se busca confirmar**:

- Si existe un mecanismo formal de sustitución de firma docente.
- Quién asume la responsabilidad si el docente original no está disponible.
- Si esto ocurre con frecuencia y cómo se gestiona hoy.

---

### Pregunta 19 — Solicitudes rechazadas

**Pregunta**: ¿Existen solicitudes que se rechacen definitivamente (no por error formal corregible, sino por incumplimiento de la regla)? ¿Cuáles son los motivos típicos de rechazo y aproximadamente con qué frecuencia ocurren al semestre?

**Referencia en entrevistas previas**: no abordado explícitamente.

**Motivo de la consulta**: la máquina de estados del workflow debe contemplar el estado terminal "rechazada" con su correspondiente motivo. Conocer los motivos típicos permite ofrecer al estudiante una explicación clara, y permite definir si el rechazo admite apelación o no.

**Lo que se busca confirmar**:

- Existencia y motivos típicos de rechazo definitivo.
- Frecuencia aproximada por semestre.
- Si existe mecanismo de apelación o reconsideración.

---

### Pregunta 20 — Trámites urgentes

**Pregunta**: ¿Existen situaciones en las que un estudiante necesite que su trámite se resuelva con urgencia (por ejemplo, riesgo de no graduarse en la fecha prevista, vencimiento de un plazo institucional)? ¿Hay un procedimiento de priorización o son tramitados todos en el mismo orden?

**Referencia en entrevistas previas**: no abordado.

**Motivo de la consulta**: si existen casos urgentes, el sistema podría modelar una **bandera de prioridad** o un **flujo expedito**. Si no existen, la decisión es no contemplar la funcionalidad en el MVP y mantener un único flujo estándar.

**Lo que se busca confirmar**:

- Si existen casos formalmente urgentes y cuáles son.
- Si hay un procedimiento de priorización vigente.
- Si conviene modelar la urgencia en el MVP o postergarla.

---

## 8. Bloque VII — Vista del estudiante (rol de consulta) [IMPORTANTE]

### Justificación del bloque

El MVP define dos roles: la **Coordinadora** (rol con acción) y el **Estudiante** (rol de consulta de su propia solicitud). El comportamiento de la vista del estudiante depende de cómo hoy se identifica un trámite y de qué información el estudiante esperaría encontrar.

### Pregunta 21 — Identificación actual de una solicitud por parte del estudiante

**Pregunta**: Hoy, cuando un estudiante quiere saber el estado de su trámite, ¿cómo identifica la solicitud? ¿Por su número de cédula, por un consecutivo, por el asunto del correo, o simplemente preguntándole a usted directamente?

**Referencia en entrevistas previas (entrevista 1):**

> "Como a los 15 días se acuerda de coordinadora, ¿cómo va a mi materia?"

**Motivo de la consulta**: el sistema debe ofrecer un identificador estable que el estudiante reconozca o pueda obtener fácilmente. Si hoy no existe ninguno (el estudiante simplemente pregunta), el sistema podrá introducir uno propio (por ejemplo, número correlativo) sin chocar con expectativas previas.

**Lo que se busca confirmar**:

- Si existe un identificador en uso hoy.
- Cómo prefieren los estudiantes referirse a sus trámites.
- Si tendría sentido introducir un consecutivo legible (por ejemplo, AC-2026-001 para adición de créditos).

---

### Pregunta 22 — Información esperada por el estudiante

**Pregunta**: Si un estudiante pudiera ver el estado de su trámite en un sistema web, ¿qué información considera usted que le sería más útil? Por ejemplo: en qué etapa está, quién tiene actualmente la responsabilidad de actuar, cuándo se espera la próxima respuesta, historial completo de movimientos.

**Referencia en entrevistas previas**: no abordado de forma directa.

**Motivo de la consulta**: la vista del estudiante debe ser **informativa pero acotada**. Conocer la expectativa permite priorizar qué mostrar en el MVP y qué dejar para versiones posteriores.

**Lo que se busca confirmar**:

- Información considerada esencial.
- Información considerada sensible que **no** debería visibilizarse al estudiante (nombres de funcionarios, comentarios internos, etc.).
- Granularidad útil del historial (cada cambio de estado, solo hitos mayores).

---

### Pregunta 23 — Notificaciones automáticas al estudiante

**Pregunta**: ¿Le parecería útil que el sistema enviara automáticamente una notificación por correo electrónico al estudiante cada vez que su trámite cambia de estado? ¿Esto reemplazaría o complementaría la comunicación actual por WhatsApp?

**Referencia en entrevistas previas (entrevista 2):**

> "El WhatsApp el WhatsApp de formación con el estudiante."

**Motivo de la consulta**: la notificación automática es una respuesta directa al efecto E4 del árbol de problemas (trámites perdidos o estancados sin alerta automática). Confirmar la utilidad y el canal preferido permite priorizar el SP7 del árbol de problemas.

**Lo que se busca confirmar**:

- Si el correo electrónico es canal aceptado y suficiente.
- Si el WhatsApp debería seguir siendo el canal principal de comunicación con el estudiante.
- Si conviene contemplar notificaciones por otros canales más adelante.

---

## 9. Bloque VIII — Volumetría operacional [IMPORTANTE]

### Justificación del bloque

Dimensionar el volumen de transacciones permite tomar decisiones razonables sobre infraestructura, tamaños de tabla, índices, paginación y políticas de retención.

### Pregunta 24 — Volumen de adiciones de créditos por semestre

**Pregunta**: Aproximadamente, ¿cuántas solicitudes de adición de créditos se procesan por semestre en la Sede Cali? Su comentario inicial fue que afecta al 70 % u 80 % de los estudiantes; sería de utilidad un número aproximado.

**Referencia en entrevistas previas (entrevista 1):**

> "Entonces nos pasa con el 80 %, 70 % de los estudiantes."

**Lo que se busca confirmar**:

- Número aproximado de solicitudes por semestre.
- Distribución típica a lo largo del semestre (concentradas al inicio, dispersas, etc.).

---

### Pregunta 25 — Volumen de novedades de notas por semestre

**Pregunta**: ¿Cuántas novedades de notas se procesan típicamente por semestre en la Sede Cali, después del trabajo de mejora que usted realizó (visitas a los salones para que los estudiantes revisaran sus notas)?

**Referencia en entrevistas previas (entrevista 2):**

> "Entonces ya se ha ido reduciendo ese tipo de procesos."

**Lo que se busca confirmar**:

- Número aproximado de novedades por semestre actualmente.
- Comparación cualitativa con la situación previa a las visitas a salones.

---

### Pregunta 26 — Población de la sede

**Pregunta**: ¿Aproximadamente cuántos estudiantes activos tiene la Sede Cali en un semestre típico? ¿Cuántos docentes están vinculados, de manera que puedan intervenir como firmantes en una novedad de notas?

**Referencia en entrevistas previas**: no abordado con cifras.

**Lo que se busca confirmar**:

- Tamaño aproximado de la población estudiantil.
- Tamaño aproximado del cuerpo docente.

---

## 10. Bloque IX — Aspectos complementarios [ÚTIL]

### Justificación del bloque

Las preguntas de este bloque no bloquean el inicio del MVP, pero su respuesta refina decisiones que se tomarán en los Sprints 2 y 3.

### Pregunta 27 — Ventanas del calendario académico

**Pregunta**: ¿En qué momentos del calendario académico están **habilitados** o **deshabilitados** los trámites de adición de créditos y de novedad de notas? ¿Existen ventanas cerradas en las que el trámite no procede formalmente?

**Referencia en entrevistas previas (entrevista 1):**

> "Después de que se acaba el semestre, los profesores tienen de 3 a 4 días para subir notas o arreglar errores."

**Lo que se busca confirmar**:

- Ventanas oficiales por tipo de trámite.
- Si el sistema debería bloquear automáticamente solicitudes fuera de ventana.

---

### Pregunta 28 — Fechas límite por semestre

**Pregunta**: ¿Existen fechas límite específicas dentro del semestre para presentar cada trámite? ¿Es el mismo calendario para todas las facultades o varía?

**Referencia en entrevistas previas**: no abordado en detalle.

**Lo que se busca confirmar**:

- Fechas o reglas relativas (por ejemplo, "hasta la semana 4 del semestre").
- Variaciones por facultad o programa.

---

### Pregunta 29 — Validez legal de las firmas escaneadas y digitales

**Pregunta**: ¿La universidad reconoce formalmente la firma escaneada o trazada digitalmente con valor probatorio equivalente al de la firma manuscrita? ¿Existe alguna política institucional que lo regule expresamente?

**Referencia en entrevistas previas (entrevista 2):**

> "No es estricto por ese lado. […] Lo importante es que vean que sí lo hiciste a mano."

**Motivo de la consulta**: complementa la Pregunta 8 con la dimensión legal. Si la firma escaneada no tiene reconocimiento formal, conviene desde el MVP introducir un mecanismo verificable (sello electrónico con hash y sello de tiempo) que ofrezca garantías superiores.

**Lo que se busca confirmar**:

- Existencia de política institucional sobre firmas electrónicas.
- Riesgo de que un trámite firmado únicamente con firma escaneada sea cuestionado legalmente.

---

### Pregunta 30 — Protocolo formal de recordatorio entre dependencias

**Pregunta**: Cuando un trámite se "traspapela" en alguna dependencia y se demora más de lo razonable, ¿existe un protocolo formal de escalamiento o de recordatorio, o es siempre informal vía chat de Teams?

**Referencia en entrevistas previas (entrevista 2):**

> "Tengo que escribirle al chat del Teams, ay mira de canatura, recuerde que tenemos este formato pendiente."

**Lo que se busca confirmar**:

- Si existe procedimiento formal de escalamiento.
- Si el sistema debería ofrecer recordatorios automáticos a los aprobadores morosos.

---

### Pregunta 31 — Canal preferido para el feedback del piloto

**Pregunta**: Para las sesiones de validación del MVP a lo largo del proyecto, ¿qué canal prefiere usted para enviar y recibir comentarios sobre el sistema: correo electrónico, Microsoft Teams o reuniones presenciales?

**Referencia en entrevistas previas**: no abordado.

**Lo que se busca confirmar**:

- Canal principal de comunicación durante el piloto.
- Disponibilidad y latencia esperada de respuesta.

---

### Pregunta 32 — Disponibilidad para sesiones de demostración

**Pregunta**: Si organizamos sesiones de demostración del avance del MVP cada dos semanas, con duración aproximada de 30 a 60 minutos, ¿cuál sería su día y franja horaria de mayor disponibilidad?

**Referencia en entrevistas previas**: no abordado.

**Lo que se busca confirmar**:

- Día y franja horaria viable.
- Cadencia aceptable de revisión (cada dos semanas, mensual, otra).

---

### Pregunta 33 — Estudiante voluntario para probar la vista de consulta

**Pregunta**: Para validar la vista del estudiante en el MVP, ¿podría usted facilitar el contacto de uno o dos estudiantes voluntarios que estén dispuestos a probar la funcionalidad de consulta de sus solicitudes y dar retroalimentación?

**Referencia en entrevistas previas**: no abordado.

**Lo que se busca confirmar**:

- Disponibilidad de estudiantes piloto.
- Modalidad de contacto.

---

### Pregunta 34 — Infraestructura institucional para hosting

**Pregunta**: ¿Sabe usted si la Universidad Remington ofrece infraestructura (servidores, máquinas virtuales, espacio en nube institucional) para alojar proyectos académicos de estudiantes? En caso negativo, ¿conoce el contacto del área de tecnología que pueda absolver esta consulta?

**Referencia en entrevistas previas**: no abordado.

**Motivo de la consulta**: la decisión de despliegue del MVP (local con `docker-compose`, contenedor en servidor institucional o servicio en la nube pública) depende de esta información.

**Lo que se busca confirmar**:

- Existencia o no de infraestructura institucional para estudiantes.
- Persona o área de contacto para profundizar.

---

### Pregunta 35 — Restricciones por protección de datos personales

**Pregunta**: Dado que el sistema gestionará datos académicos de estudiantes (datos de naturaleza semi-privada según la Ley 1581 de 2012 de Colombia), ¿la universidad tiene políticas específicas sobre dónde pueden alojarse estos datos? Por ejemplo, ¿hay restricciones para alojarlos en servicios de nube fuera del país?

**Referencia en entrevistas previas**: no abordado.

**Motivo de la consulta**: si existen restricciones por habeas data o por política interna, podrían impedir el uso de servicios de nube extranjeros y obligar a alojamiento local. Es preferible conocer la restricción antes de comprometerse con una opción de despliegue.

**Lo que se busca confirmar**:

- Existencia de política institucional sobre alojamiento de datos académicos.
- Si existen acuerdos preexistentes con proveedores de nube.

---

## 11. Cierre de la entrevista

Al concluir el cuestionario, el entrevistador debe:

1. Reiterar el agradecimiento por el tiempo y la disposición de la coordinación.
2. Confirmar los **adjuntos solicitados** mediante el Anexo B de este documento, indicando cuáles se enviarán por correo posterior a la entrevista y a qué dirección.
3. Acordar la **fecha tentativa de la próxima interacción** (sea por correo, por Teams o sesión presencial) y la fase del proyecto en que dicha interacción tendrá sentido.
4. Ofrecer a la coordinación una **síntesis escrita** de las respuestas registradas durante la entrevista, para su revisión y eventual corrección antes de que sean incorporadas como insumo del trabajo de grado.

---

## Anexo A — Resumen de fuentes citadas

| Referencia | Documento | Ubicación en el repositorio |
|------------|-----------|-----------------------------|
| Entrevista 1 | Transcripción de la primera entrevista a la Coordinación Académica de la Sede Cali | `../../../material-coord/transcript-entrevista-coordi.md` |
| Entrevista 2 | Transcripción de la segunda entrevista (continuación) | `../../../material-coord/transcript-entrevista-coordi-2.md` |
| Árbol de problemas | Marco Lógico — CEPAL/ILPES (Ortegón, Pacheco y Prieto, 2005) | `./arbol-de-problemas.md` |
| Ley 1581 de 2012 | Régimen general de protección de datos personales en Colombia | Norma externa |

---

## Anexo B — Material a solicitar a la coordinación (con prioridad y confidencialidad)

> **Naturaleza del anexo**: este listado describe los **artefactos** que conviene pedir a la coordinación **antes** de la entrevista para llegar informados. Es **complementario** a las 35 preguntas del cuerpo de esta guía: si los artefactos llegan, varias preguntas se contestan solas y la reunión rinde más.

> **Política operativa**: el material que la coordinación envíe por correo se guarda **fuera del repositorio git** (carpeta local `material-coord/` agregada al `.gitignore`). El repositorio versionado solo conserva **referencias y citas mínimas**, nunca copias literales del material que pueda contener datos sensibles.

> **Versión del anexo**: refinada el 2026-06-01 (de 9 ítems planos a 13 ítems en tres niveles de prioridad, con nota de confidencialidad por ítem).

### CRÍTICO — sin esto no se inicia el Sprint 1 con realismo

Estos cinco ítems definen los campos del formulario, las reglas del workflow y los entregables del MVP. Sin ellos, cualquier diseño se hace sobre supuestos y se rehace tras la entrevista.

| # | Material a solicitar | Por qué lo necesitamos | Formato sugerido | Nota de confidencialidad | Cf. pregunta |
|---|----------------------|------------------------|------------------|--------------------------|--------------|
| 1 | Formato Word vigente de **adición de créditos** | Define los campos exactos del formulario validado del Sprint 1. Sin la plantilla, los campos quedan reconstruidos a partir de las entrevistas y se rehacen tras la reunión. | `.docx` en blanco, sin datos de estudiantes | Bajo riesgo — es la plantilla vacía, no datos reales | Pregunta 14 |
| 2 | Formato Word vigente de **novedad de notas** | Idem para el segundo trámite del MVP. | `.docx` en blanco | Bajo riesgo | Pregunta 14 |
| 3 | Pénsum del programa de **Ingeniería de Sistemas** con códigos de asignatura | Sin códigos reales, la validación de asignaturas del formulario de adición es teórica. El pénsum de Ingeniería de Sistemas alcanza como caso de referencia para arrancar. | PDF público del programa o tabla institucional de códigos | Probablemente pública (figura en sitio institucional y registro SNIES). Si existe versión interna más rica, también sirve. | Pregunta 15 |
| 4 | Plantilla institucional del **PDF formal** que se asienta en QF | Define lo que Trámita debe generar al cierre del trámite (SP3 del árbol). Si Trámita genera un PDF que no encaja con la plantilla esperada por QF, el ciclo no cierra. | PDF de ejemplo o `.docx` de la plantilla institucional | Bajo riesgo si es la plantilla vacía. Pedir explícitamente sin firmas reales ni datos de personas. | Preguntas 8 y 14 |
| 5 | **Normativa institucional aplicable** a los dos trámites | Las reglas de negocio del motor de workflow deben derivarse de la normativa, no de la práctica observada. Sin esta referencia, las reglas del MVP quedan sobre supuestos y son indefendibles ante el tribunal evaluador. | Nombre, número y fecha de la resolución, acuerdo o comunicado + enlace si la norma es pública | Probablemente pública (resoluciones de Consejo Académico, comunicados oficiales). Confirmar carácter público antes de citarla literalmente en el documento de requisitos. | Preguntas 1, 2 y 3 |

### IMPORTANTE — afina el diseño del MVP

Estos cinco ítems no bloquean el inicio del Sprint 1, pero **sin ellos los criterios de aceptación quedan genéricos**. Conviene pedirlos junto con los CRÍTICO; si tardan, no detienen el arranque.

| # | Material a solicitar | Por qué lo necesitamos | Formato sugerido | Nota de confidencialidad | Cf. pregunta |
|---|----------------------|------------------------|------------------|--------------------------|--------------|
| 6 | Un hilo de correo real de **adición de créditos**, anonimizado | Modela el journey real: quién participa en cada paso, qué se escribe, cuánto se demora cada etapa, qué se devuelve. Es lo que más enriquece el diseño tras la teoría. | Captura, copia-pega o reenvío del hilo con datos del estudiante tachados (`███` o `[Estudiante]`). Los códigos de asignatura pueden permanecer. | **Pedir explícitamente anonimizado**. En la entrevista 2 la coordinación ofreció reenviar el hilo del propio entrevistador, lo que evita exponer datos de terceros. | Pregunta 12 |
| 7 | Un hilo de correo real de **novedad de notas**, anonimizado | Es el trámite más complejo y el que más actores involucra (incluye Registro Medellín). El hilo permite verificar la cadena real de aprobaciones, los tiempos por etapa y los mensajes típicos de devolución. | Idem ítem 6 | Idem; pedir además que se anonimicen códigos de asignatura si tienen carga sensible. | Pregunta 13 |
| 8 | 2 a 3 ejemplos de **devoluciones** (casos donde un trámite volvió atrás para corrección) | Modela el error mode del workflow: las causas típicas de devolución se traducen en los códigos y mensajes de error que el formulario y el motor deben mostrar al usuario. | Descripción breve de cada caso, sin nombres ni números de documento. | Anonimizar. La coordinación puede describir verbalmente en lugar de adjuntar correos. | Complementa Pregunta 19 (que aborda rechazos definitivos, no devoluciones para corrección) |
| 9 | **Calendario académico institucional** del semestre vigente | Las ventanas de matrícula y de cierre de notas definen los picos de demanda de cada trámite. Permite priorizar funcionalidades por sprint según la urgencia operativa real. | PDF público del calendario semestral | Probablemente público | Preguntas 27 y 28 |
| 10 | **Volumen aproximado** de trámites por semestre, separando adición de créditos y novedad de notas | Dimensiona el sistema. Una bandeja de 5 trámites por semana se diseña distinto que una de 200. Cambia decisiones de UI, paginación e índices de base de datos. | Estimación libre — número o rango aproximado, sea en el correo o en la propia entrevista | No es dato sensible | Preguntas 24 y 25 |

### ÚTIL — enriquece el contexto, no bloquea el inicio

Estos tres ítems no afectan el diseño técnico del MVP pero refuerzan la defensa académica y el realismo operativo del documento de requisitos. Conviene pedirlos **solo si no se percibe que generan fricción** con la coordinación.

| # | Material a solicitar | Por qué lo necesitamos | Formato sugerido | Nota de confidencialidad | Cf. pregunta |
|---|----------------------|------------------------|------------------|--------------------------|--------------|
| 11 | Captura de pantalla de la vista de **QF** donde la coordinación asienta un trámite | Aunque Trámita no se integra técnicamente con QF (Principio VI de la constitución), saber cómo se ve la pantalla destino ayuda a generar un PDF que encaje correctamente al asentarse, y a entender por qué firmas escaneadas a veces no se visualizan (incidente reportado en entrevista 2). | Captura de un caso de prueba o caso anonimizado | **Solo si la coordinación lo considera no confidencial**. Si tiene dudas, omitir el ítem y conversar en la entrevista cómo es la pantalla. | Pregunta 8 (relacionada) |
| 12 | **Organigrama** de la cadena de aprobación de los dos trámites | Confirma los actores formales involucrados (Coordinación, Decanatura, Dirección de CD, Registro Medellín). Útil para la defensa ante el tribunal evaluador y para el diagrama de actores del documento de requisitos. | Diagrama oficial o lista textual reconstruida por la coordinación | Probablemente público — estructura institucional | Preguntas 16 y 17 |
| 13 | **Tiempos observados de respuesta** de Registro Medellín, si los tiene medidos | Línea base para la métrica "tiempo de ciclo" del árbol §9. Si la coordinación no los mide formalmente, no insistir — una estimación libre alcanza. | Estimación en la entrevista o tabla informal en el correo | No es dato sensible | Pregunta 4 (relacionada) |

### Reglas de confidencialidad para el pedido

Estas reglas se aplican al redactar el correo formal y al recibir el material:

- **Cero datos personales identificables (PII)**. No solicitar copias de Class, listas de estudiantes matriculados, números de cédula, ni listas con nombres de docentes activos.
- **Casos reales siempre anonimizados**. Cualquier ejemplo (correos, devoluciones, capturas de pantalla) se solicita con datos del estudiante tachados explícitamente. **Sugerir el tachado nosotros en el correo**, para que la coordinación no tenga que decidirlo.
- **Normativa como referencia, no como copia literal**. Cualquier documento normativo se cita por número, fecha y dependencia emisora en el documento de requisitos; no se reproduce literal en el repositorio salvo que la norma sea públicamente accesible.
- **Material dudoso → se conversa en la entrevista, no se pide por correo**. Si la coordinación tiene dudas sobre si algo es compartible, **mejor no se solicita y se discute en la reunión**. El costo de pedir lo que no debió compartirse es alto (riesgo de habeas data, fricción institucional).
- **Material recibido se almacena fuera del repositorio**. Todo lo que llegue por correo va a `material-coord/` (carpeta local, agregada al `.gitignore`). El repositorio versionado solo conserva **referencias** al material (nombre del archivo, fecha de recepción) y **citas mínimas** indispensables para fundamentar decisiones.

### Información de contacto a recolectar durante la entrevista

No son artefactos a pedir por correo, pero conviene asegurarse de obtenerlos durante la reunión:

- Contacto del área de tecnología institucional, para profundizar preguntas 9, 10 y 34 (autenticación, directorio institucional, infraestructura).
- Contacto de gestión de calidad, para profundizar la Pregunta 3 (manual de procesos académicos).
- Confirmación del canal preferido para feedback durante el piloto (Pregunta 31).

### Sugerencia operativa para redactar el correo

- Presentar la lista **agrupada por prioridad** (CRÍTICO / IMPORTANTE / ÚTIL), no como una lista plana — facilita que la coordinación entienda qué urge y qué puede esperar.
- En cada ítem, indicar el **formato sugerido** y la **nota de anonimización** — para que la coordinación no tenga que decidir esos detalles.
- Ofrecer explícitamente la opción de **omitir** cualquier ítem que la coordinación considere sensible; la entrevista cubre los temas como alternativa.
- Cerrar el correo agradeciendo el tiempo invertido y proponiendo una fecha tentativa para la entrevista, condicionada a la llegada del material crítico.
