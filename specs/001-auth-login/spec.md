# Feature Specification: Autenticación de la Coordinación Académica (login)

**Feature Branch**: `001-auth-login`

**Created**: 2026-07-02

**Status**: Draft

**Input**: Autenticación de la Coordinación Académica para Trámita — login con email y contraseña, cambio de contraseña por la propia Coordinación, y validación de contraseña en tiempo real en el frontend. Único actor: la Coordinación. La cuenta se provisiona por fuera (semilla/migración).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Iniciar sesión (Priority: P1)

La Coordinación Académica accede al sistema ingresando su email y contraseña, para poder gestionar los trámites académicos.

**Why this priority**: es la puerta de entrada al sistema. Sin login no hay acceso; es el MVP mínimo demostrable ante la coordinación y el jurado.

**Independent Test**: con una cuenta semilla activa, verificar que credenciales correctas otorgan acceso y una sesión activa, y que las incorrectas lo niegan.

**Acceptance Scenarios**:

1. **Given** una cuenta activa de la Coordinación, **When** ingresa email y contraseña correctos, **Then** obtiene acceso y queda con una sesión activa.
2. **Given** credenciales incorrectas, **When** intenta ingresar, **Then** el sistema niega el acceso con un mensaje genérico que no revela si falló el email o la contraseña.
3. **Given** una cuenta marcada como inactiva, **When** ingresa credenciales correctas, **Then** el sistema niega el acceso.

---

### User Story 2 - Cambiar la contraseña (Priority: P2)

La Coordinación, ya autenticada, cambia su propia contraseña (por ejemplo, para rotar la contraseña inicial provista por semilla).

**Why this priority**: permite a la Coordinación tomar control real de su cuenta tras el primer acceso. Depende de la US1.

**Independent Test**: estando autenticada, cambiar la contraseña y verificar que el próximo inicio de sesión exige la nueva y rechaza la anterior.

**Acceptance Scenarios**:

1. **Given** una sesión activa, **When** provee la contraseña actual correcta y una nueva que cumple la política, **Then** la contraseña se actualiza y el próximo login usa la nueva.
2. **Given** una sesión activa, **When** provee una contraseña actual incorrecta, **Then** el sistema rechaza el cambio.
3. **Given** una sesión activa, **When** la nueva contraseña no cumple la política, **Then** el sistema rechaza el cambio e indica el motivo.

---

### User Story 3 - Validación de contraseña en tiempo real (Priority: P2)

Al escribir una nueva contraseña, la Coordinación ve de inmediato qué reglas de la política cumple y cuáles no, como guía de experiencia de usuario.

**Why this priority**: reduce errores y frustración en el cambio de contraseña. Es UX de apoyo — la validación autoritativa la hace el servidor.

**Independent Test**: en el formulario de cambio de contraseña, escribir y observar retroalimentación inmediata; confirmar que una contraseña "válida" en pantalla igual es re-validada por el servidor al enviarse.

**Acceptance Scenarios**:

1. **Given** el formulario de cambio de contraseña, **When** la Coordinación escribe caracteres, **Then** ve retroalimentación inmediata de las reglas cumplidas y las pendientes.
2. **Given** una contraseña que el frontend marca como válida, **When** se envía, **Then** el servidor la valida nuevamente (autoritativo) antes de aceptarla.

---

### User Story 4 - Cerrar sesión (Priority: P3)

La Coordinación cierra su sesión de forma segura al terminar de usar el sistema.

**Why this priority**: cierra el ciclo de una autenticación basada en sesión; es esencial pero de bajo esfuerzo.

**Independent Test**: con una sesión activa, cerrar sesión y verificar que se requiere autenticarse de nuevo para volver a acceder.

**Acceptance Scenarios**:

1. **Given** una sesión activa, **When** la Coordinación cierra sesión, **Then** la sesión se invalida y se requiere volver a autenticarse.

---

### Edge Cases

- Email inexistente o contraseña incorrecta → mensaje genérico, sin revelar cuál campo falló (evita enumeración de usuarios).
- Cuenta inactiva con credenciales correctas → acceso denegado.
- Cambio de contraseña con la contraseña actual incorrecta → rechazo.
- Nueva contraseña que no cumple la política → rechazo con el motivo.
- Nueva contraseña idéntica a la actual → se rechaza; se exige un secreto distinto. No se guarda historial de contraseñas.
- Sesión expirada por inactividad a mitad de uso → se solicita volver a autenticarse.
- Intentos de login fallidos repetidos (posible fuerza bruta) → ver FR-010.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST permitir a la Coordinación autenticarse con email y contraseña.
- **FR-002**: El sistema MUST rechazar credenciales inválidas con un mensaje genérico que no revele si el error estuvo en el email o en la contraseña.
- **FR-003**: El sistema MUST validar las credenciales de forma autoritativa en el servidor; la validación del cliente es únicamente orientativa (UX).
- **FR-004**: El sistema MUST permitir a la Coordinación autenticada cambiar su propia contraseña.
- **FR-005**: El cambio de contraseña MUST exigir la contraseña actual correcta antes de aceptar la nueva.
- **FR-006**: El sistema MUST aplicar en el servidor una política de contraseñas con longitud mínima de 15 caracteres (la contraseña es el único factor de autenticación; NIST SP 800-63B-4), longitud máxima de 72, sin reglas de composición obligatorias (mayúsculas/números/símbolos) y sin rotación periódica forzada. Las reglas verificables en el cliente (longitud y coincidencia con la confirmación) MUST reflejarse en la retroalimentación en tiempo real del frontend; el servidor MUST re-validar de forma autoritativa (ver FR-003).
- **FR-007**: El sistema MUST establecer una sesión tras un login exitoso y MUST permitir cerrarla (logout).
- **FR-008**: La credencial de sesión MUST ser inaccesible para los scripts del navegador y MUST transmitirse solo por conexión segura.
- **FR-009**: El sistema MUST expirar las sesiones tras un período de inactividad (ver Supuestos: 30 minutos por defecto).
- **FR-010**: El sistema MUST mitigar los intentos automatizados de fuerza bruta limitando temporalmente los intentos fallidos repetidos (throttling que se auto-repara tras una ventana de tiempo), SIN bloquear la cuenta de forma permanente.
- **FR-011**: Solo las cuentas marcadas como activas MUST poder autenticarse.

### Key Entities *(include if feature involves data)*

- **Usuario (cuenta de la Coordinación)**: identidad que se autentica en el sistema. Atributos conceptuales: email (único, identificador de login), contraseña (almacenada de forma irreversible, nunca en texto plano), estado activo/inactivo, y fechas de creación y última actualización. En este alcance NO tiene rol ni sede (un único actor, una única sede).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: La Coordinación inicia sesión con credenciales correctas al primer intento en menos de 30 segundos.
- **SC-002**: El 100% de los intentos con credenciales inválidas se rechazan sin revelar cuál campo falló.
- **SC-003**: Tras un cambio de contraseña exitoso, el siguiente inicio de sesión exige la nueva contraseña y la anterior deja de funcionar de inmediato.
- **SC-004**: La retroalimentación de la política de contraseña se percibe como instantánea mientras la Coordinación escribe.
- **SC-005**: El 100% de las contraseñas aceptadas fueron validadas por el servidor, incluso cuando el frontend ya las había marcado como válidas.

## Assumptions

- La cuenta de la Coordinación se provisiona por fuera de la aplicación (semilla/migración): no hay auto-registro ni alta de usuarios desde la app.
- La recuperación de contraseña olvidada queda FUERA del alcance del MVP: no hay flujo de "olvidé mi contraseña". Si la Coordinación pierde el acceso, se re-provisiona su contraseña por script/migración. Una vista de administración para gestionar cuentas se documenta como enhancement de una versión posterior.
- La verificación contra listas de contraseñas comunes o filtradas (blocklist) queda fuera del MVP; se documenta como enhancement de v2. La política de contraseñas se concentra en un único punto para que incorporarla luego sea una extensión localizada.
- Alcance de un único actor (la Coordinación) y una única sede; sin roles ni gestión de sedes.
- El frontend y el backend viven en orígenes distintos; el intercambio de la credencial de sesión se realiza con soporte de credenciales de origen cruzado.
- Timeout de sesión por inactividad: 30 minutos (valor por defecto razonable; ajustable en el plan).
- Class y QF son sistemas externos (cajas negras) y no participan de la autenticación.
