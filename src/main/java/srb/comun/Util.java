package srb.comun;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Funciones que usan todos los servicios: leer la configuración, escribir en la bitácora,
 * armar respuestas y enviar un mensaje por ZeroMQ con el patrón de petición y respuesta
 * (Request/Reply: quien pregunta espera a que le contesten).
 *
 * Todos los mensajes son JSON. Una respuesta siempre tiene la forma
 * {"ok": true, "datos": ...} o {"ok": false, "error": "motivo"}.
 */
public class Util {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    // Lee una variable de entorno; si no existe (o está vacía) devuelve el valor por defecto.
    public static String variable(String nombre, String porDefecto) {
        String valor = System.getenv(nombre);
        if (valor == null || valor.isBlank()) {
            return porDefecto;
        }
        return valor;
    }

    // Escribe una línea en la bitácora con la hora y el nombre del servicio, para poder seguir lo que pasa.
    public static void log(String servicio, String texto) {
        System.out.println(LocalTime.now().format(HORA) + " [" + servicio + "] " + texto);
    }

    // Arma la respuesta de una operación exitosa con los datos que se quieren devolver.
    public static JsonObject respuestaOk(Object datos) {
        JsonObject respuesta = new JsonObject();
        respuesta.addProperty("ok", true);
        respuesta.add("datos", new Gson().toJsonTree(datos));
        return respuesta;
    }

    // Arma la respuesta de una operación rechazada o fallida, con el motivo.
    public static JsonObject respuestaError(String motivo) {
        JsonObject respuesta = new JsonObject();
        respuesta.addProperty("ok", false);
        respuesta.addProperty("error", motivo);
        return respuesta;
    }

    // Envía un mensaje a otro servicio y espera su respuesta. Si no contesta a tiempo devuelve un error.
    // Se crea un socket nuevo en cada envío para que un mensaje perdido no bloquee los siguientes.
    public static JsonObject enviar(ZContext contexto, String url, JsonObject mensaje, int esperaMs) {
        ZMQ.Socket socket = contexto.createSocket(SocketType.REQ);
        try {
            socket.setLinger(0);
            socket.setReceiveTimeOut(esperaMs);
            socket.connect(url);
            socket.send(mensaje.toString());
            String respuesta = socket.recvStr();
            if (respuesta == null) {
                return respuestaError("Sin respuesta de " + url);
            }
            return JsonParser.parseString(respuesta).getAsJsonObject();
        } finally {
            contexto.destroySocket(socket);
        }
    }

    // Lee un campo de texto del mensaje; devuelve "" si el campo no viene.
    public static String texto(JsonObject mensaje, String campo) {
        if (!mensaje.has(campo)) {
            return "";
        }
        try {
            return mensaje.get(campo).getAsString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    // Lee un campo entero del mensaje; devuelve -1 si no viene o si no es un número entero.
    public static int entero(JsonObject mensaje, String campo) {
        if (!mensaje.has(campo)) {
            return -1;
        }
        try {
            double valor = mensaje.get(campo).getAsDouble();
            if (valor != Math.floor(valor)) {
                return -1;
            }
            return (int) valor;
        } catch (Exception e) {
            return -1;
        }
    }
}
