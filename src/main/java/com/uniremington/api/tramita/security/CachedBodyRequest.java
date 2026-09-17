package com.uniremington.api.tramita.security;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Re-sirve en cada getInputStream()/getReader() un body ya leído y acotado por un filtro.
 *
 * Existe porque un filtro que mide el tamaño del cuerpo lo consume, y downstream alguien
 * lo necesita íntegro: el converter del login y el binding del canal público. Se descartó
 * ContentCachingRequestWrapper a propósito — su cache guarda solo lo ya consumido y no
 * garantiza la relectura downstream (JD2-002).
 *
 * No lee del stream original: quien lee es quien aplica el tope. Era una clase anidada de
 * LoginThrottlingFilter hasta que la 004 sumó un segundo filtro con la misma necesidad;
 * extraerla no cambia comportamiento.
 */
final class CachedBodyRequest extends HttpServletRequestWrapper {

    private final byte[] body;

    CachedBodyRequest(HttpServletRequest request, byte[] body) {
        super(request);
        this.body = body;
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream buffer = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override
            public int read() {
                return buffer.read();
            }

            @Override
            public boolean isFinished() {
                return buffer.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                throw new UnsupportedOperationException("Lectura asíncrona no soportada");
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }
}
