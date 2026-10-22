package srb.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;

/**
 * Ruta "/": entrega index.html, app.js y estilos.css desde la carpeta web de los recursos.
 */
public class ManejadorArchivos implements HttpHandler {

    // Busca el archivo pedido y lo devuelve; si no existe responde 404.
    @Override
    public void handle(HttpExchange intercambio) throws IOException {
        String ruta = intercambio.getRequestURI().getPath();
        if (ruta.equals("/")) {
            ruta = "/index.html";
        }
        InputStream contenido = null;
        if (!ruta.contains("..")) {
            contenido = ManejadorArchivos.class.getResourceAsStream("/web" + ruta);
        }
        if (contenido == null) {
            intercambio.sendResponseHeaders(404, -1);
            intercambio.close();
            return;
        }
        RespuestaHttp.responder(intercambio, tipoDeContenido(ruta), contenido.readAllBytes());
    }

    // Decide el tipo de contenido según la terminación del archivo.
    private String tipoDeContenido(String ruta) {
        if (ruta.endsWith(".js")) {
            return "text/javascript";
        }
        if (ruta.endsWith(".css")) {
            return "text/css";
        }
        return "text/html";
    }
}
