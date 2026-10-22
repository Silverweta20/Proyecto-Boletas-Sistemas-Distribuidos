package srb.web;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import srb.comun.Util;

import java.io.IOException;

/**
 * Ruta GET /api/config: mes inicial que muestra la página.
 */
public class ManejadorConfig implements HttpHandler {

    // Devuelve {"mes":"2026-10"} (o el mes indicado en la variable MES).
    @Override
    public void handle(HttpExchange intercambio) throws IOException {
        JsonObject config = new JsonObject();
        config.addProperty("mes", Util.variable("MES", "2026-10"));
        RespuestaHttp.responderJson(intercambio, config.toString());
    }
}
