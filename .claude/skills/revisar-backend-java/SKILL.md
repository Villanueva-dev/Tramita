---
name: revisar-backend-java
description: Revisión de código del backend de Trámita (Java 21 / Spring Boot 4 / Spring Security 7). Aplica el checklist general de Java más las reglas duras del proyecto. Invocar cuando el usuario pida "revisá este código", "code review", "revisar el backend", antes de un commit de código o al terminar de implementar una feature.
---

# Revisión de código — backend de Trámita

Checklist de revisión para el backend. Se divide en dos partes: las **reglas duras del proyecto**, que son las que ninguna guía genérica de Java trae y donde están los errores que ya nos costaron caro, y el **checklist general de Java**, que cubre el resto.

Leer las reglas duras primero. Ahí está el 80 % del valor.

---

## Contexto del proyecto (no inventar sobre esto)

- **Java 21 · Spring Boot 4.0.7 · PostgreSQL · Maven.** No proponer migrar de versión.
- **Spring Security 7** con autenticación por **sesión + cookie `HttpOnly`** (patrón BFF). **No hay JWT y no debe haberlo.**
- **Flyway posee el schema; Hibernate solo valida** (`ddl-auto: validate`). Todo cambio de schema es una migración nueva.
- **Errores según RFC 7807** (`application/problem+json`).
- **Estructura por capas**: `controller/ · dto/ · model/ · repo/ · security/ · service/` (contratos) `· service/impl/` (implementaciones) `· util/ · shared/{config,exception,seed}`. Migración desde package-by-feature **completada el 2026-08-02**; la norma es la constitución v2.0.0 §II.
- **Las interfaces llevan prefijo `I`** (`IUserRepo`, `IAuthService`). Es convención deliberada del proyecto: **NO marcarla como defecto.**
- La constitución vive en `.specify/memory/constitution.md` y prevalece sobre cualquier práctica.

---

## Reglas duras de Trámita

Cada una salió de un error real. Violarlas es hallazgo **Crítico** o **Alto**, no una sugerencia de estilo.

### 1. Nunca `@Data` ni `@AllArgsConstructor` en una entidad JPA

```java
// ❌ CRÍTICO
@Entity
@Data
public class User {
    private String passwordHash;
}

// ✅
@Entity
@Getter
@Setter
@NoArgsConstructor
public class User { ... }
```

**Por qué.** `@Data` genera `toString()` con **todos** los campos, incluido `passwordHash`: cualquier log, mensaje de excepción o serialización accidental filtra el hash BCrypt. Y genera `equals()`/`hashCode()` sobre campos mutables, lo que rompe el contrato de identidad de JPA — si la entidad entra en un `HashSet` y Hibernate le muta un campo, el hashCode cambia y el objeto se pierde dentro de la colección.

`@AllArgsConstructor` permite además construir la entidad salteando invariantes.

Marcar también el `import lombok.*` con comodín: usar imports explícitos.

### 2. Los filtros del chain de Spring Security NO llevan estereotipo

```java
// ❌ CRÍTICO — se registra dos veces
@Component
public class LoginThrottlingFilter extends OncePerRequestFilter { ... }

// ✅ sin anotación; SecurityConfig lo construye con new
public class LoginThrottlingFilter extends OncePerRequestFilter { ... }
```

**Por qué.** `SecurityConfig` instancia estos filtros a mano y los inserta con `addFilterBefore`/`addFilterAt`. Si además llevan `@Component`, Spring Boot los **auto-registra en la cadena de filtros del servlet container**: se ejecutan dos veces por request. En `LoginThrottlingFilter` eso significa que cada intento fallido cuenta doble y el 429 salta a la mitad de los intentos configurados.

**No rompe la compilación y los tests unitarios no lo detectan.** Es la excepción a la regla general de "estereotipo según la función de la clase".

Aplica hoy a `LoginThrottlingFilter` y `CsrfCookieFilter`.

### 3. Nunca exponer una entity en la frontera de la API

Constitución §III. Todo request y response usa DTO. El motivo concreto: `User` contiene el hash BCrypt.

### 4. Errores en RFC 7807, y nunca devolver el mensaje interno

```java
// ❌ ALTO — fuga de información
return ResponseEntity.status(500).body(new ErrorTemplate(ex.getMessage()));

// ✅
ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
problem.setTitle("Error interno");
```

Devolver `ex.getMessage()` al cliente filtra errores de SQL, rutas del servidor y detalles de implementación. Loguear el detalle del lado servidor; al cliente, un mensaje genérico.

Verificar además que el **código de estado sea semánticamente correcto**. Un error de cálculo es 500, no 406 (406 es sobre content negotiation).

### 5. Nada de `throws Exception` en interfaces de servicio

```java
// ❌ ALTO
public interface ICRUD<T, ID> {
    T save(T entity) throws Exception;
}
```

**Por qué.** Obliga a todos los callers a capturar `Exception`, y —lo grave— **`@Transactional` no hace rollback ante checked exceptions por defecto**. Una operación que falla puede quedar confirmada en la base. Las excepciones de dominio se modelan como `RuntimeException`.

### 6. Nada de Reflection para resolver setters o nombres de métodos

```java
// ❌ ALTO — falla en runtime, no en compilación
String methodName = "setId" + entity.getClass().getSimpleName();
entity.getClass().getMethod(methodName, id.getClass()).invoke(entity, id);
```

Cualquier rename rompe esto sin que el compilador avise. Si hace falta una abstracción genérica, resolverla con una interfaz (`Identifiable<ID>`), que es segura en tiempo de compilación.

### 7. Campos de instancia en `camelCase`

```java
// ❌ parece una llamada estática
private final IUserRepo IUserRepo;

// ✅
private final IUserRepo userRepo;
```

Suele aparecer cuando un rename del IDE arrastra el nombre del campo junto con el del tipo. Revisar siempre los puntos de inyección después de renombrar un tipo.

### 8. El schema es de Flyway

Ninguna entidad nueva ni cambio de columna sin su migración. `ddl-auto` sigue en `validate`. Si Hibernate y Flyway divergen, la app no arranca — y eso es deseado.

### 9. Tests: sensibles, pocos y honestos

Constitución §V: se testea el comportamiento **crítico, no obvio o de alto costo de regresión**. No se testea lo trivial por dogma ni se persigue cobertura nominal.

**Un test jamás se amaña para que pase.** Un test que afirma una garantía falsa es peor que no tener test, porque documenta una seguridad que no existe.

En Spring Boot 4 usar **`@MockitoBean`** (`org.springframework.test.context.bean.override.mockito`), **no `@MockBean`**, que fue reemplazado.

Para evidenciar el RED de un ciclo TDD, correr siempre `./mvnw clean test-compile`: el incremental de Maven ya dio un `BUILD SUCCESS` falso en este repo.

---

## Checklist general de Java

Recorrer estas categorías después de las reglas duras.

### Seguridad de nulos
- Cadenas de llamadas sin verificación intermedia.
- `Optional.get()` sin comprobar presencia.
- Devolver `null` donde corresponde `Optional` o una colección vacía.
- `Objects.requireNonNull()` en parámetros de constructores públicos.

### Manejo de excepciones
- Bloques `catch` vacíos.
- Capturar `Exception` o `Throwable` de forma amplia.
- Perder la excepción original: encadenar siempre con `cause`.
- Usar excepciones para control de flujo.
- Loguear con contexto **y** stack trace.

### Colecciones y streams
- Modificar una colección mientras se itera (usar `removeIf`).
- Asumir que `Collectors.toList()` devuelve una lista mutable.
- Usar streams para efectos de lado donde un bucle es más claro.
- `List.of()` / `Map.of()` para inmutables; `List.copyOf()` para copias defensivas.

### Concurrencia
- Estado mutable compartido sin sincronizar (`HashMap` → `ConcurrentHashMap`).
- Patrones check-then-act sin atomicidad (`computeIfAbsent`).
- Relevante en `LoginAttemptService`, que mantiene estado en memoria.

### Idiomas de Java
- `equals` sin `hashCode`, o `hashCode` sobre campos mutables.
- `toString` que incluya datos sensibles (ver regla dura 1).
- Constructores con más de 3-4 parámetros: considerar builder.
- Pattern matching de `instanceof` (Java 16+) donde aplique.

### Gestión de recursos
- `try-with-resources` en todo `Closeable`/`AutoCloseable`.
- Lecturas sin tope de tamaño en endpoints públicos — hallazgo real del proyecto (`readAllBytes()` en el login, M-1).

### Diseño de API
- Parámetros booleanos: preferir enums.
- Validación con Jakarta Validation en los DTOs de entrada, no en el controller a mano.
- Nombres de endpoint RESTful y coherentes con `contracts/openapi.yaml`.

### Rendimiento
- Concatenación de strings en bucles.
- Compilación de regex dentro de bucles.
- Consultas N+1.

---

## Formato de salida

```markdown
## Revisión: [archivo o feature]

### Críticos
- [defecto] — `archivo:línea` — por qué falla y qué pasa si llega a producción — corrección

### Altos
- ...

### Medios
- ...

### Menores
- ...

### Bien resuelto
- [lo que está correcto y conviene conservar]
```

**Reglas de reporte:**
- Toda observación lleva `archivo:línea`. Sin referencia no es un hallazgo, es una opinión.
- Ordenar por severidad, no por orden de aparición en el archivo.
- Agrupar hallazgos repetidos en vez de listarlos uno por uno.
- Si no hay hallazgos, decirlo claramente e indicar el **riesgo residual** — qué no se pudo verificar.
- Señalar los aciertos: sirve para saber qué patrón replicar.

## Severidad

| Nivel | Criterio |
|---|---|
| **Crítico** | Vulnerabilidad, fuga de datos, pérdida de información o caída en producción |
| **Alto** | Bug probable, rompe un contrato de la API o una garantía documentada |
| **Medio** | Deuda de mantenibilidad, buena práctica ausente |
| **Menor** | Estilo, optimización marginal |

Una violación de las **reglas duras** es Crítica o Alta por definición. No degradarlas a "sugerencia".

## Antes de cerrar la revisión

- ¿Se corrió `./mvnw clean test-compile`? Con `clean`, siempre.
- ¿Los tests siguen pasando, o al menos se dice explícitamente que no se corrieron y por qué?
- ¿Algún hallazgo toca `SecurityConfig` o el filter chain? Ese código solo se valida arrancando la app: los tests unitarios no ven el cableado.
