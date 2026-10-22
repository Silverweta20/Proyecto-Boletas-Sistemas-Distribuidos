package srb.web;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import srb.clientes.Cliente;

import java.io.IOException;

/**
 * Ruta GET /api/eventos?mes=AAAA-MM: consulta de los eventos del mes.
 */
public class ManejadorEventos implements HttpHandler {

    private final PuenteGestor puente;

    public ManejadorEventos(PuenteGestor puente) {
        this.puente = puente;
    }

    // Arma el mensaje de consulta, lo envía al gestor y devuelve la respuesta al navegador.
    @Override
    public void handle(HttpExchange intercambio) throws IOException {
        String mes = RespuestaHttp.parametro(intercambio, "mes");
        JsonObject respuesta = puente.enviar(Cliente.consulta(mes), "—");
        RespuestaHttp.responderJson(intercambio, respuesta.toString());
    }
}
