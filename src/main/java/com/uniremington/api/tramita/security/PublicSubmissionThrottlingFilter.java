package com.uniremington.api.tramita.security;

import com.uniremington.api.tramita.service.impl.PublicSubmissionCounter;
import com.uniremington.api.tramita.shared.config.PublicCaptureProperties;
import com.uniremington.api.tramita.shared.exception.ProblemJsonWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Protege el canal público de captura (004, US3, FR-016 a FR-019): corta con 413 los
 * envíos desmesurados y con 429 los que superan el límite por origen.
 *
 * LO QUE PROTEGE ES LA DISPONIBILIDAD DEL ENLACE, no un secreto (research.md D3-bis).
 * El canal no guarda nada que valga la pena robar, pero si deja de responder, el
 * trámite no arranca. Por eso los números son holgados: un script de abuso hace más de
 * 100 peticiones por segundo y se corta igual con 20 que con 5, de modo que bajar el
 * umbral no agrega protección y sí agrega probabilidad de bloquear a un estudiante.
 *
 * LOS ENVÍOS SE CUENTAN ANTES DE PROCESARLOS, así que un envío incompleto —que
 * terminará en 422— consume cupo igual que uno válido. Es deliberado: el recurso que
 * este límite protege es el procesamiento del envío, y quien manda basura lo consume
 * exactamente igual. Contar solo los válidos dejaría el límite sin efecto justo contra
 * el tráfico que motiva tenerlo.
 *
 * NO LLEVA ESTEREOTIPO A PROPÓSITO, igual que LoginThrottlingFilter. SecurityConfig lo
 * construye con new y lo inserta en el chain. Anotarlo con @Component haría que Spring
 * Boot lo auto-registre además en la cadena del servlet container: correría dos veces
 * por petición y cada envío contaría doble, disparando el 429 a la mitad del umbral
 * configurado. No rompe la compilación y los tests unitarios no lo detectan.
 */
public class PublicSubmissionThrottlingFilter extends OncePerRequestFilter {

    private static final Logger log =
            LoggerFactory.getLogger(PublicSubmissionThrottlingFilter.class);

    // Mismo matcher context-path-aware con que SecurityConfig abre la ruta: getRequestURI()
    // incluye el context-path y un despliegue con contexto saltearía el filtro (JD3-007)
    private static final RequestMatcher PUBLIC_CAPTURE_MATCHER = PathPatternRequestMatcher
            .withDefaults().matcher(HttpMethod.POST, "/api/public/requests/*");

    private final PublicSubmissionCounter counter;
    private final PublicCaptureProperties properties;
    private final ProblemJsonWriter problemJsonWriter;

    public PublicSubmissionThrottlingFilter(PublicSubmissionCounter counter,
            PublicCaptureProperties properties, ProblemJsonWriter problemJsonWriter) {
        this.counter = counter;
        this.properties = properties;
        this.problemJsonWriter = problemJsonWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !PUBLIC_CAPTURE_MATCHER.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        // EL CUPO SE JUZGA PRIMERO, Y EL 413 LO CONSUME. Revierte la decisión anterior, que
        // cortaba por tamaño antes de contar «porque el envío ni siquiera llegó a
        // materializarse». Lo que la hacía insostenible no es cuánto se lee —con
        // Content-Length declarado no se lee nada— sino que un envío consume capacidad del
        // canal se lea o no: conexión, filtro y ciclo de petición. No cobrarlo dejaba una
        // ruta que esquiva la ÚNICA defensa de tasa de un canal sin sesión, y un mismo
        // origen podía repetir envíos desmesurados sin límite alguno (research.md D7-bis).
        //
        // Y el orden importa tanto como el cobro: consultado DESPUÉS de leer, el contador
        // frenaría los envíos válidos pero no las relecturas —cada intento desmesurado
        // volvería a pedirle al servidor que mida—. Consultarlo antes corta sin abrir
        // siquiera el stream. Un test lo fija: quien agotó su cupo recibe 429, no 413.
        String origin = request.getRemoteAddr();
        if (counter.isAtLimit(origin)) {
            long retryAfter = counter.retryAfterSeconds(origin);
            // ⚠️ ESTE LOG ES EL ÚNICO DIAGNÓSTICO del modo de fallo que D3-bis documenta:
            // si en producción aparece siempre el mismo origen, o uno del rango privado
            // (10.x, 172.16-31.x, 192.168.x), se está contando contra la IP del proxy y no
            // la del estudiante — revisar server.forward-headers-strategy.
            log.warn("Envío público bloqueado por límite de tasa. Origen contado: {}. "
                    + "Reintento en {} s. Si este origen se repite siempre o es una IP "
                    + "privada, revisar server.forward-headers-strategy.", origin, retryAfter);

            ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.TOO_MANY_REQUESTS);
            problem.setTitle("Demasiados envíos desde este origen");
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            problemJsonWriter.write(response, problem);
            return;
        }

        byte[] body = readBodyWithinLimit(request);
        if (body == null) {
            counter.record(origin);
            // Sin este WARN, un origen que satura el canal con envíos desmesurados no deja
            // rastro: el 413 no se registra en ningún lado y el 429 que vendrá después
            // parecería un estudiante bloqueado sin causa.
            //
            // ⚠️ No afirma que el cuerpo se haya leído: con Content-Length declarado se corta
            // sin abrir el stream, y solo sin él se lee hasta el tope para poder medirlo.
            log.warn("Envío público rechazado por tamaño. Origen contado: {}. El corte "
                    + "consume cupo: el envío ocupó el canal aunque no se procesara.", origin);

            ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONTENT_TOO_LARGE);
            problem.setTitle("El envío excede el tamaño admitido");
            problemJsonWriter.write(response, problem);
            return;
        }
        counter.record(origin);

        filterChain.doFilter(new CachedBodyRequest(request, body), response);
    }

    /**
     * Lee el cuerpo acotado al tope configurado, o devuelve {@code null} si lo excede.
     *
     * El Content-Length se consulta primero para cortar sin leer un solo byte, pero NO
     * alcanza como única defensa: es declarativo y con Transfer-Encoding chunked ni
     * siquiera existe. Por eso la lectura pide un byte de más — así distingue «justo en
     * el tope» de «lo excede» sin confiar en lo que el cliente declara.
     */
    private byte[] readBodyWithinLimit(HttpServletRequest request) throws IOException {
        long maxBytes = properties.maxBodySize().toBytes();
        if (request.getContentLengthLong() > maxBytes) {
            return null;
        }
        // toIntExact y no un cast: si alguien elevara el tope por encima de un int, esto
        // falla ruidosamente en vez de desbordar a negativo y hacer estallar readNBytes en
        // CADA petición. Hoy no puede pasar —PublicCaptureProperties topa en 1 MB—, y esta
        // línea es lo que permite que ese techo sea una decisión de producto y no una muleta.
        byte[] body = request.getInputStream().readNBytes(Math.toIntExact(maxBytes + 1));
        return body.length > maxBytes ? null : body;
    }
}
