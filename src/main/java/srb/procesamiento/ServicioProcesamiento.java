package srb.procesamiento;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import srb.comun.Util;

/**
 * Servicio de Procesamiento de Reservas, Modificaciones y Consultas (capa de negocio).
 * Atiende las solicitudes síncronas del Gestor de Transacciones (petición y respuesta, puerto 5556)
 * y le pide al Servicio de Persistencia que lea y escriba los datos.
 *
 * Primera entrega: consulta y reserva. La modificación se hará en la segunda entrega
 * (ver ProcesadorModificacion).
 */
public class ServicioProcesamiento {

    private static final String NOMBRE = "PROCESAMIENTO";
    private static final int ESPERA_PERSISTENCIA_MS = 3000;

    private static ZContext contexto;
    private static String urlPersistencia;

    // Arranca el servicio y atiende las solicitudes del gestor una por una, para siempre.
    public static void main(String[] args) {
        String puerto = Util.variable("PUERTO", "5556");
        urlPersistencia = Util.variable("URL_PERSISTENCIA", "tcp://localhost:5557");

        contexto = new ZContext();
        ZMQ.Socket servidor = contexto.createSocket(SocketType.REP);
        servidor.bind("tcp://*:" + puerto);
        Util.log(NOMBRE, "Escuchando en el puerto " + puerto + "; persistencia en " + urlPersistencia);

        while (true) {
            JsonObject solicitud = JsonParser.parseString(servidor.recvStr()).getAsJsonObject();
            JsonObject respuesta = atender(solicitud);
            servidor.send(respuesta.toString());
        }
    }

    // Decide qué hacer según la operación pedida.
    private static JsonObject atender(JsonObject solicitud) {
        String operacion = Util.texto(solicitud, "operacion");
        if (operacion.equals("consulta")) {
            return consultar(solicitud);
        }
        if (operacion.equals("reserva")) {
            return reservar(solicitud);
        }
        return Util.respuestaError("Operación no soportada por el servicio de procesamiento");
    }

    // Consulta: recibe la solicitud, la envía a persistencia y devuelve el resultado al gestor.
    private static JsonObject consultar(JsonObject solicitud) {
        String mes = Util.texto(solicitud, "mes");
        JsonObject mensaje = new JsonObject();
        mensaje.addProperty("operacion", "consulta");
        mensaje.addProperty("mes", mes);
        JsonObject respuesta = Util.enviar(contexto, urlPersistencia, mensaje, ESPERA_PERSISTENCIA_MS);
        Util.log(NOMBRE, "id=" + Util.texto(solicitud, "id") + " consulta mes=" + mes + " -> " + resultado(respuesta, "OK"));
        return respuesta;
    }

    // Reserva: 1) pregunta a persistencia cuántos asientos libres hay en ese momento,
    // 2) si alcanzan, pide confirmar la reserva en la base de datos, 3) devuelve éxito o rechazo al gestor.
    private static JsonObject reservar(JsonObject solicitud) {
        String id = Util.texto(solicitud, "id");
        int ocurrencia = Util.entero(solicitud, "ocurrencia");
        int asientos = Util.entero(solicitud, "asientos");

        // Paso 1: disponibilidad en tiempo real
        JsonObject pregunta = new JsonObject();
        pregunta.addProperty("operacion", "disponibilidad");
        pregunta.addProperty("ocurrencia", ocurrencia);
        JsonObject disponibilidad = Util.enviar(contexto, urlPersistencia, pregunta, ESPERA_PERSISTENCIA_MS);
        if (!disponibilidad.get("ok").getAsBoolean()) {
            Util.log(NOMBRE, "id=" + id + " reserva RECHAZADA: " + disponibilidad.get("error").getAsString());
            return disponibilidad;
        }
        int libres = disponibilidad.getAsJsonObject("datos").get("libres").getAsInt();
        if (libres < asientos) {
            Util.log(NOMBRE, "id=" + id + " reserva RECHAZADA: pidió " + asientos + " y quedan " + libres);
            return Util.respuestaError("No hay asientos suficientes: quedan " + libres);
        }

        // Paso 2: confirmar la reserva (persistencia vuelve a comprobar el cupo dentro de la transacción)
        JsonObject orden = new JsonObject();
        orden.addProperty("operacion", "reservar");
        orden.addProperty("cliente", Util.texto(solicitud, "cliente"));
        orden.addProperty("ocurrencia", ocurrencia);
        orden.addProperty("asientos", asientos);
        JsonObject respuesta = Util.enviar(contexto, urlPersistencia, orden, ESPERA_PERSISTENCIA_MS);
        Util.log(NOMBRE, "id=" + id + " reserva ocurrencia=" + ocurrencia + " asientos=" + asientos
                + " -> " + resultado(respuesta, "CONFIRMADA"));
        return respuesta;
    }

    // Texto corto para la bitácora: el texto indicado si salió bien, o "RECHAZADA: motivo" si no.
    private static String resultado(JsonObject respuesta, String textoSiSalioBien) {
        if (respuesta.get("ok").getAsBoolean()) {
            return textoSiSalioBien;
        }
        return "RECHAZADA: " + respuesta.get("error").getAsString();
    }
}
