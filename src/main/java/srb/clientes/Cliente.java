package srb.clientes;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.zeromq.ZContext;
import srb.comun.Util;

import java.util.UUID;

/**
 * Cliente de línea de comandos: envía una sola solicitud al Gestor de Transacciones y muestra la respuesta.
 *
 * Uso:
 *   java -cp srb.jar srb.clientes.Cliente consulta 2026-10
 *   java -cp srb.jar srb.clientes.Cliente reserva ana 5 2      (cliente, ocurrencia, asientos)
 *
 * Los métodos consulta() y reserva() arman los mensajes; también los usan el generador de carga,
 * el ejecutor de casos y la página web.
 */
public class Cliente {

    // Tiempo máximo (en milisegundos) que un cliente espera la respuesta del Gestor de Transacciones.
    public static final int ESPERA_GESTOR_MS = 5000;

    // Arma el mensaje de consulta de los eventos de un mes.
    public static JsonObject consulta(String mes) {
        JsonObject mensaje = nuevoMensaje("consulta");
        mensaje.addProperty("mes", mes);
        return mensaje;
    }

    // Arma el mensaje de reserva de asientos para una ocurrencia.
    public static JsonObject reserva(String cliente, int ocurrencia, int asientos) {
        JsonObject mensaje = nuevoMensaje("reserva");
        mensaje.addProperty("cliente", cliente);
        mensaje.addProperty("ocurrencia", ocurrencia);
        mensaje.addProperty("asientos", asientos);
        return mensaje;
    }

    // Crea el mensaje base. Cada solicitud lleva un identificador único (8 letras o números)
    // para poder seguirla en las bitácoras de todos los servicios.
    private static JsonObject nuevoMensaje(String operacion) {
        JsonObject mensaje = new JsonObject();
        mensaje.addProperty("id", UUID.randomUUID().toString().substring(0, 8));
        mensaje.addProperty("operacion", operacion);
        return mensaje;
    }

    // Envía una solicitud escrita en la línea de comandos y muestra la respuesta.
    public static void main(String[] args) {
        String urlGestor = Util.variable("URL_GESTOR", "tcp://localhost:5555");
        JsonObject mensaje;
        if (args.length == 2 && args[0].equals("consulta")) {
            mensaje = consulta(args[1]);
        } else if (args.length == 4 && args[0].equals("reserva")) {
            mensaje = reserva(args[1], Integer.parseInt(args[2]), Integer.parseInt(args[3]));
        } else {
            System.out.println("Uso: Cliente consulta <AAAA-MM>  |  Cliente reserva <cliente> <ocurrencia> <asientos>");
            return;
        }
        try (ZContext contexto = new ZContext()) {
            System.out.println("Enviando: " + mensaje);
            JsonObject respuesta = Util.enviar(contexto, urlGestor, mensaje, ESPERA_GESTOR_MS);
            System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(respuesta));
        }
    }
}
