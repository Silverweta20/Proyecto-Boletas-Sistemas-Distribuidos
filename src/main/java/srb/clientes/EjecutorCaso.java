package srb.clientes;

import com.google.gson.JsonObject;
import org.zeromq.ZContext;
import srb.comun.Util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Ejecuta un caso de prueba (una lista fija de operaciones) desde un archivo CSV.
 * Las operaciones se envían en orden, una por una, para que el estado final de la base de datos sea siempre el mismo.
 *
 * Formato del archivo (la primera línea es el encabezado):
 *   cliente,operacion,mes,ocurrencia,asientos
 *   ana,consulta,2026-10,,
 *   ana,reserva,,5,2
 *
 * Uso: java -cp srb.jar srb.clientes.EjecutorCaso casos/ejemplo.csv
 */
public class EjecutorCaso {

    // Lee el archivo y envía cada línea al Gestor de Transacciones.
    public static void main(String[] args) throws Exception {
        String archivo = "casos/ejemplo.csv";
        if (args.length > 0) {
            archivo = args[0];
        }
        String urlGestor = Util.variable("URL_GESTOR", "tcp://localhost:5555");
        List<String> lineas = Files.readAllLines(Path.of(archivo));

        try (ZContext contexto = new ZContext()) {
            // La línea 0 es el encabezado, por eso se empieza en 1
            for (int i = 1; i < lineas.size(); i++) {
                String linea = lineas.get(i).trim();
                if (linea.isEmpty() || linea.startsWith("#")) {
                    continue;
                }
                String[] campos = separarCampos(linea);
                JsonObject mensaje = construirMensaje(campos);
                JsonObject respuesta = Util.enviar(contexto, urlGestor, mensaje, Cliente.ESPERA_GESTOR_MS);
                Util.log("CASO", "línea " + (i + 1) + ": " + campos[0] + " " + campos[1] + " -> "
                        + describirRespuesta(campos[1], respuesta));
            }
        }
    }

    // Separa una línea del CSV en sus 5 campos (cliente, operacion, mes, ocurrencia, asientos),
    // sin espacios sobrantes. Si faltan campos al final, quedan como texto vacío.
    static String[] separarCampos(String linea) {
        String[] partes = (linea + ",,,,").split(",", -1);
        String[] campos = new String[5];
        for (int i = 0; i < 5; i++) {
            campos[i] = partes[i].trim();
        }
        return campos;
    }

    // Arma el mensaje que se envía según la operación de la línea.
    // La modificación y la cancelación se envían igual: el gestor responde que son de la segunda entrega.
    static JsonObject construirMensaje(String[] campos) {
        String cliente = campos[0];
        String operacion = campos[1];
        if (operacion.equals("consulta")) {
            return Cliente.consulta(campos[2]);
        }
        if (operacion.equals("reserva")) {
            return Cliente.reserva(cliente, Integer.parseInt(campos[3]), Integer.parseInt(campos[4]));
        }
        JsonObject mensaje = new JsonObject();
        mensaje.addProperty("operacion", operacion);
        mensaje.addProperty("cliente", cliente);
        return mensaje;
    }

    // Texto corto con el resultado de una operación, para mostrar en pantalla.
    private static String describirRespuesta(String operacion, JsonObject respuesta) {
        if (!respuesta.get("ok").getAsBoolean()) {
            return "RECHAZO: " + respuesta.get("error").getAsString();
        }
        if (operacion.equals("consulta")) {
            return "OK (" + respuesta.getAsJsonArray("datos").size() + " eventos)";
        }
        return "OK " + respuesta.get("datos");
    }
}
