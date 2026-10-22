package srb.web;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

/**
 * Ruta GET /api/operaciones: operaciones hechas desde la página y su resultado.
 */
public class ManejadorOperaciones implements HttpHandler {

    private final RegistroOperaciones registro;

    public ManejadorOperaciones(RegistroOperaciones registro) {
        this.registro = registro;
    }

    // Devuelve el registro de operaciones en formato JSON.
    @Override
    public void handle(HttpExchange intercambio) throws IOException {
        RespuestaHttp.responderJson(intercambio, new Gson().toJson(registro.obtenerOperaciones()));
    }
}
