package com.uniremington.api.tramita.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Arma el POST de transición con {@code fromStateCode} igual al estado vigente, que
 * el endpoint exige como premisa (H-10): vuelve a pedir el detalle y envía el estado
 * que ACABA de leer. Los IT que solo quieren avanzar una solicitud usan esto y no se
 * enteran del campo.
 *
 * ⚠️ Es una comodidad de los tests, NO el patrón del cliente real. Un cliente que
 * vuelva a pedir el detalle justo antes de enviar siempre coincide con el vigente y
 * anula la protección: el front debe enviar el estado que mostraba la pantalla en la
 * que la persona decidió (contrato de la 002).
 *
 * El estado se lee al EJECUTAR la petición (post-procesador), no al construir el
 * builder: el llamador agrega {@code .session(session)} después de recibirlo, y
 * sin la sesión el GET del detalle no autentica. Así los helpers de cada IT
 * conservan su firma. Los IT que necesitan un {@code fromStateCode} distinto del
 * vigente —el caso de la pestaña vieja— construyen su body a mano.
 */
final class AdvanceRequestSupport {

    /** Solo se envía si el detalle no se pudo leer (sin sesión, id inexistente): el error esperado es anterior a esta premisa. */
    private static final String UNREADABLE_STATE = "ESTADO_NO_LEIDO";

    private AdvanceRequestSupport() {
    }

    static MockHttpServletRequestBuilder advanceFromCurrentState(
            MockMvc mockMvc, String id, String targetStateCode, String note) {
        return post("/api/requests/" + id + "/transitions")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .with(request -> {
                    String from = currentStateCode(mockMvc, id, request);
                    String body = note == null
                            ? "{\"fromStateCode\":\"%s\",\"targetStateCode\":\"%s\"}"
                                    .formatted(from, targetStateCode)
                            : "{\"fromStateCode\":\"%s\",\"targetStateCode\":\"%s\",\"note\":\"%s\"}"
                                    .formatted(from, targetStateCode, note);
                    ((MockHttpServletRequest) request)
                            .setContent(body.getBytes(StandardCharsets.UTF_8));
                    return request;
                });
    }

    private static String currentStateCode(MockMvc mockMvc, String id, MockHttpServletRequest request) {
        MockHttpSession session = (MockHttpSession) request.getSession(false);
        if (session == null) {
            return UNREADABLE_STATE;
        }
        try {
            MockHttpServletResponse detail = mockMvc.perform(
                            get("/api/requests/" + id).session(session))
                    .andReturn().getResponse();
            return detail.getStatus() == 200
                    ? JsonPath.read(detail.getContentAsString(), "$.currentState.code")
                    : UNREADABLE_STATE;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo leer el detalle de " + id, e);
        }
    }
}
