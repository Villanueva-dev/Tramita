package com.uniremington.api.tramita.shared.exception;

/**
 * Una guarda evaluó la solicitud y la regla no se cumple (FR-016): la transición
 * existe en la definición, pero hoy está condicionada y la condición no da.
 *
 * Hereda de {@link IllegalTransitionException} —y con ella el mapeo a 409— porque
 * el conflicto es con el estado actual del recurso, no con el formato del body
 * (RFC 9110 §15.5.10). Un 422 sería semánticamente falso: el body de la petición
 * de avance está bien; lo que no da es la solicitud contra la regla.
 *
 * Es un tipo propio y no el padre a secas para que los tests puedan afirmar que
 * bloqueó LA GUARDA. Con el padre, una regresión que dejara de resolver la guarda
 * y cayera en el "transición no definida" lanzaría la misma excepción y el test
 * seguiría verde con la guarda muerta.
 *
 * No lleva método propio en el handler a propósito: el título "Transición no
 * permitida" describe bien ambas causas y el detail ya nombra la regla. Si el
 * cliente algún día necesita distinguirlas, se agrega el @ExceptionHandler sin
 * tocar ni el motor ni las guardas.
 */
public class GuardRejectedException extends IllegalTransitionException {

    public GuardRejectedException(String message) {
        super(message);
    }
}
