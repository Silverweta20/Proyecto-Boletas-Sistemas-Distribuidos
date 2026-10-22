package srb.web;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Funciones para contestar solicitudes HTTP y leer parámetros de la dirección.
 */
public class RespuestaHttp {

    // Contesta con un texto JSON.
    public static void responderJson(HttpExchange intercambio, String json) throws IOException {
        responder(intercambio, "application/json", json.getBytes(StandardCharsets.UTF_8));
    }

    // Contesta con cualquier tipo de contenido (página, estilos, script...).
    public static void responder(HttpExchange intercambio, String tipo, byte[] cuerpo) throws IOException {
        intercambio.getResponseHeaders().set("Content-Type", tipo + "; charset=utf-8");
        intercambio.getResponseHeaders().set("Cache-Control", "no-store");
        intercambio.sendResponseHeaders(200, cuerpo.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(cuerpo);
        }
    }

    // Lee un parámetro de la dirección, por ejemplo "mes" en /api/eventos?mes=2026-10. Devuelve "" si no viene.
    public static String parametro(HttpExchange intercambio, String nombre) {
        String consulta = intercambio.getRequestURI().getRawQuery();
        if (consulta == null) {
            return "";
        }
        for (String par : consulta.split("&")) {
            String[] partes = par.split("=", 2);
            if (partes[0].equals(nombre) && partes.length == 2) {
                return URLDecoder.decode(partes[1], StandardCharsets.UTF_8);
            }
        }
        return "";
    }
}
