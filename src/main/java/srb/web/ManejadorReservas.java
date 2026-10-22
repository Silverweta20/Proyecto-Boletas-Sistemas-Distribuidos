package srb.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import srb.clientes.Cliente;
import srb.comun.Util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Ruta POST /api/reservas: reserva {"cliente":"ana","ocurrencia":5,"asientos":2}.
 */
public class ManejadorReservas implements HttpHandler {

    private final PuenteGestor puente;

    public ManejadorReservas(PuenteGestor puente) {
        this.puente = puente;
    }

    // Lee el JSON que manda el navegador, arma el mensaje de reserva y lo envía al gestor.
    @Override
    public void handle(HttpExchange intercambio) throws IOException {
        if (!intercambio.getRequestMethod().equals("POST")) {
            RespuestaHttp.responderJson(intercambio, Util.respuestaError("Solo se acepta POST").toString());
            return;
        }
        JsonObject datos;
        try {
            String cuerpo = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            datos = JsonParser.parseString(cuerpo).getAsJsonObject();
        } catch (Exception e) {
            RespuestaHttp.responderJson(intercambio, Util.respuestaError("Datos inválidos").toString());
            return;
        }
        String cliente = Util.texto(datos, "cliente");
        int ocurrencia = Util.entero(datos, "ocurrencia");
        int asientos = Util.entero(datos, "asientos");
        JsonObject respuesta = puente.enviar(Cliente.reserva(cliente, ocurrencia, asientos), cliente);
        RespuestaHttp.responderJson(intercambio, respuesta.toString());
    }
}
