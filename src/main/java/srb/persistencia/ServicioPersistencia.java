package srb.persistencia;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import srb.comun.Util;

import java.sql.SQLException;

/**
 * Servicio de Persistencia (capa de almacenamiento).
 * Es el único proceso que habla con la base de datos. Atiende al servicio de procesamiento por ZeroMQ
 * (petición y respuesta) en el puerto 5557.
 *
 * Operaciones que entiende:
 *   consulta       {"operacion":"consulta","mes":"2026-10"}
 *   disponibilidad {"operacion":"disponibilidad","ocurrencia":5}
 *   reservar       {"operacion":"reservar","cliente":"ana","ocurrencia":5,"asientos":2}
 */
public class ServicioPersistencia {

    private static final String NOMBRE = "PERSISTENCIA";

    // Arranca el servicio: se conecta a la base de datos y atiende solicitudes para siempre.
    public static void main(String[] args) throws Exception {
        String puerto = Util.variable("PUERTO", "5557");
        BaseDatos baseDatos = conectar();

        try (ZContext contexto = new ZContext()) {
            ZMQ.Socket servidor = contexto.createSocket(SocketType.REP);
            servidor.bind("tcp://*:" + puerto);
            Util.log(NOMBRE, "Escuchando en el puerto " + puerto);

            while (true) {
                JsonObject solicitud = JsonParser.parseString(servidor.recvStr()).getAsJsonObject();
                JsonObject respuesta = atender(baseDatos, solicitud);
                servidor.send(respuesta.toString());
                Util.log(NOMBRE, Util.texto(solicitud, "operacion") + " " + solicitud + " -> " + resumen(respuesta));
            }
        }
    }

    // Ejecuta la operación pedida y arma la respuesta; si la base de datos falla, responde con un error.
    static JsonObject atender(BaseDatos baseDatos, JsonObject solicitud) {
        String operacion = Util.texto(solicitud, "operacion");
        try {
            if (operacion.equals("consulta")) {
                return Util.respuestaOk(baseDatos.consultarMes(Util.texto(solicitud, "mes")));
            }
            if (operacion.equals("disponibilidad")) {
                return disponibilidad(baseDatos, solicitud);
            }
            if (operacion.equals("reservar")) {
                return reservar(baseDatos, solicitud);
            }
            return Util.respuestaError("Operación desconocida");
        } catch (SQLException e) {
            Util.log(NOMBRE, "Error de base de datos: " + e.getMessage());
            return Util.respuestaError("Error de base de datos");
        }
    }

    // Responde cuántos asientos libres tiene una ocurrencia.
    private static JsonObject disponibilidad(BaseDatos baseDatos, JsonObject solicitud) throws SQLException {
        int libres = baseDatos.asientosLibres(Util.entero(solicitud, "ocurrencia"));
        if (libres < 0) {
            return Util.respuestaError("La ocurrencia no existe");
        }
        JsonObject datos = new JsonObject();
        datos.addProperty("libres", libres);
        return Util.respuestaOk(datos);
    }

    // Guarda la reserva en la base de datos y responde con su número.
    private static JsonObject reservar(BaseDatos baseDatos, JsonObject solicitud) throws SQLException {
        int ocurrencia = Util.entero(solicitud, "ocurrencia");
        int asientos = Util.entero(solicitud, "asientos");
        int reserva = baseDatos.reservar(Util.texto(solicitud, "cliente"), ocurrencia, asientos);
        if (reserva < 0) {
            return Util.respuestaError("No hay asientos suficientes");
        }
        JsonObject datos = new JsonObject();
        datos.addProperty("reserva", reserva);
        datos.addProperty("ocurrencia", ocurrencia);
        datos.addProperty("asientos", asientos);
        datos.addProperty("libres", baseDatos.asientosLibres(ocurrencia));
        return Util.respuestaOk(datos);
    }

    // Se conecta a PostgreSQL; reintenta cada 2 segundos mientras la base de datos termina de arrancar.
    private static BaseDatos conectar() throws InterruptedException {
        String url = Util.variable("DB_URL", "jdbc:postgresql://localhost:5432/srb");
        String usuario = Util.variable("DB_USUARIO", "srb");
        String clave = Util.variable("DB_CLAVE", "srb");
        while (true) {
            try {
                BaseDatos baseDatos = new BaseDatos(url, usuario, clave);
                Util.log(NOMBRE, "Conectado a " + url);
                return baseDatos;
            } catch (SQLException e) {
                Util.log(NOMBRE, "Esperando a la base de datos: " + e.getMessage());
                Thread.sleep(2000);
            }
        }
    }

    // Texto corto para la bitácora (la consulta devuelve mucho texto).
    private static String resumen(JsonObject respuesta) {
        if (!respuesta.get("ok").getAsBoolean()) {
            return "RECHAZO: " + respuesta.get("error").getAsString();
        }
        String datos = respuesta.get("datos").toString();
        if (datos.length() > 80) {
            datos = datos.substring(0, 80) + "...";
        }
        return "OK " + datos;
    }
}
