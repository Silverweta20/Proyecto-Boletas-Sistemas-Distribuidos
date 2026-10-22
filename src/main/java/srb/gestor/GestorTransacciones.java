package srb.gestor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import srb.comun.Util;

/**
 * Gestor de Transacciones (capa de negocio).
 * Es la puerta de entrada del sistema: recibe las solicitudes de los clientes (petición y respuesta,
 * puerto 5555), las valida y las envía al servicio de procesamiento.
 *
 * Canal síncrono (implementado): consulta y reserva van al Servicio de Procesamiento de Reservas,
 * Modificaciones y Consultas y se espera su respuesta.
 * Canal asíncrono (segunda entrega): la cancelación irá al Servicio de Cancelaciones
 * con el patrón de envío y recepción (Push/Pull).
 *
 * También mide el desempeño: tiempo de cada operación y transacciones por minuto.
 */
public class GestorTransacciones {

    private static final String NOMBRE = "GESTOR DE TRANSACCIONES";
    private static final int ESPERA_PROCESAMIENTO_MS = 4000;

    // Contadores del minuto actual
    private static long inicioMinuto = System.currentTimeMillis();
    private static int consultasMinuto = 0;
    private static int reservasMinuto = 0;

    // Arranca el gestor y atiende solicitudes una por una, para siempre.
    public static void main(String[] args) {
        String puerto = Util.variable("PUERTO", "5555");
        String urlProcesamiento = Util.variable("URL_PROCESAMIENTO", "tcp://localhost:5556");

        try (ZContext contexto = new ZContext()) {
            ZMQ.Socket servidor = contexto.createSocket(SocketType.REP);
            servidor.bind("tcp://*:" + puerto);
            Util.log(NOMBRE, "Escuchando en el puerto " + puerto + "; servicio de procesamiento en " + urlProcesamiento);

            while (true) {
                String texto = servidor.recvStr();
                long inicio = System.nanoTime();
                JsonObject solicitud = leerSolicitud(texto);

                JsonObject respuesta;
                String error = validar(solicitud);
                if (error != null) {
                    respuesta = Util.respuestaError(error);
                } else {
                    // Canal síncrono: el gestor espera la respuesta del servicio de procesamiento
                    respuesta = Util.enviar(contexto, urlProcesamiento, solicitud, ESPERA_PROCESAMIENTO_MS);
                }
                servidor.send(respuesta.toString());

                double milisegundos = (System.nanoTime() - inicio) / 1000000.0;
                registrar(solicitud, respuesta, milisegundos);
            }
        }
    }

    // Convierte el texto recibido en un objeto JSON; si no se puede, devuelve un mensaje vacío (que luego se rechaza).
    static JsonObject leerSolicitud(String texto) {
        try {
            return JsonParser.parseString(texto).getAsJsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    // Revisa que la solicitud esté completa. Devuelve el motivo del rechazo, o null si es válida.
    static String validar(JsonObject solicitud) {
        String operacion = Util.texto(solicitud, "operacion");
        if (operacion.equals("consulta")) {
            return validarConsulta(solicitud);
        }
        if (operacion.equals("reserva")) {
            return validarReserva(solicitud);
        }
        if (operacion.equals("modificacion") || operacion.equals("cancelacion")) {
            return "La operación '" + operacion + "' estará disponible en la segunda entrega";
        }
        if (operacion.isEmpty()) {
            return "Mensaje inválido: falta la operación";
        }
        return "Operación desconocida: " + operacion;
    }

    // La consulta necesita un mes con formato AAAA-MM.
    private static String validarConsulta(JsonObject solicitud) {
        String mes = Util.texto(solicitud, "mes");
        if (!mes.matches("\\d{4}-(0[1-9]|1[0-2])")) {
            return "El mes debe tener el formato AAAA-MM";
        }
        return null;
    }

    // La reserva necesita cliente, una ocurrencia y un número de asientos positivos.
    private static String validarReserva(JsonObject solicitud) {
        if (Util.texto(solicitud, "cliente").isEmpty()) {
            return "Falta el cliente";
        }
        if (Util.entero(solicitud, "ocurrencia") < 1) {
            return "La ocurrencia debe ser un número positivo";
        }
        if (Util.entero(solicitud, "asientos") < 1) {
            return "Los asientos deben ser un número positivo";
        }
        return null;
    }

    // Escribe en la bitácora lo que pasó con la solicitud y lleva la cuenta de transacciones por minuto.
    private static void registrar(JsonObject solicitud, JsonObject respuesta, double milisegundos) {
        String operacion = Util.texto(solicitud, "operacion");
        boolean ok = respuesta.get("ok").getAsBoolean();
        String linea = "id=" + Util.texto(solicitud, "id") + " cliente=" + Util.texto(solicitud, "cliente")
                + " operacion=" + operacion + " ok=" + ok + " tiempo=" + Math.round(milisegundos * 10) / 10.0 + " ms";
        if (!ok) {
            linea = linea + " motivo=" + respuesta.get("error").getAsString();
        }
        Util.log(NOMBRE, linea);

        if (operacion.equals("consulta")) {
            consultasMinuto++;
        }
        if (operacion.equals("reserva")) {
            reservasMinuto++;
        }
        if (System.currentTimeMillis() - inicioMinuto >= 60000) {
            Util.log(NOMBRE, "Transacciones por minuto (último minuto): consultas=" + consultasMinuto
                    + " reservas=" + reservasMinuto);
            inicioMinuto = System.currentTimeMillis();
            consultasMinuto = 0;
            reservasMinuto = 0;
        }
    }
}
