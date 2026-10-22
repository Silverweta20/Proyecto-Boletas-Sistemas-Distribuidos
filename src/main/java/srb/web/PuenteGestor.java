package srb.web;

import com.google.gson.JsonObject;
import org.zeromq.ZContext;
import srb.clientes.Cliente;
import srb.comun.Util;

import java.time.LocalTime;

/**
 * Envía las solicitudes de la página al Gestor de Transacciones y anota cada una en el registro.
 */
public class PuenteGestor {

    private static final String NOMBRE = "WEB";

    private final ZContext contexto = new ZContext();
    private final String urlGestor;
    private final RegistroOperaciones registro;

    public PuenteGestor(String urlGestor, RegistroOperaciones registro) {
        this.urlGestor = urlGestor;
        this.registro = registro;
    }

    // Envía el mensaje al gestor, anota la operación en el registro y devuelve la respuesta.
    public JsonObject enviar(JsonObject mensaje, String cliente) {
        long inicio = System.nanoTime();
        JsonObject respuesta = Util.enviar(contexto, urlGestor, mensaje, Cliente.ESPERA_GESTOR_MS);
        long milisegundos = (System.nanoTime() - inicio) / 1000000;

        Operacion operacion = armarOperacion(mensaje, respuesta, cliente);
        operacion.ms = milisegundos;
        registro.agregarOperacion(operacion);
        Util.log(NOMBRE, operacion.operacion + " " + operacion.detalle + " -> " + operacion.resultado);
        return respuesta;
    }

    // Llena los datos de la operación a partir del mensaje enviado y la respuesta recibida.
    private Operacion armarOperacion(JsonObject mensaje, JsonObject respuesta, String cliente) {
        Operacion operacion = new Operacion();
        operacion.hora = LocalTime.now().withNano(0).toString();
        operacion.id = Util.texto(mensaje, "id");
        operacion.operacion = Util.texto(mensaje, "operacion");
        operacion.cliente = cliente;
        operacion.ok = respuesta.get("ok").getAsBoolean();

        if (mensaje.has("mes")) {
            operacion.detalle = "mes " + Util.texto(mensaje, "mes");
        } else {
            operacion.detalle = "ocurrencia " + mensaje.get("ocurrencia") + ", " + mensaje.get("asientos") + " asiento(s)";
        }

        if (!operacion.ok) {
            operacion.resultado = respuesta.get("error").getAsString();
        } else if (respuesta.get("datos").isJsonArray()) {
            operacion.resultado = respuesta.getAsJsonArray("datos").size() + " eventos";
        } else {
            // Reserva confirmada: se guardan sus datos para la sección "Mis reservas"
            operacion.reserva = respuesta.getAsJsonObject("datos");
            operacion.resultado = "Reserva #" + operacion.reserva.get("reserva") + " confirmada";
        }
        return operacion;
    }
}
