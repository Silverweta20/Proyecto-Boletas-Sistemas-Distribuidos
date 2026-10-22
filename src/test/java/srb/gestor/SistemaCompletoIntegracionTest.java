package srb.gestor;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.zeromq.ZContext;
import srb.clientes.Cliente;
import srb.comun.Util;
import srb.persistencia.AyudaPruebas;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Prueba de punta a punta: envía solicitudes por ZeroMQ al Gestor de Transacciones y pasa por todo el
// sistema (gestor, procesamiento, persistencia y base de datos). Solo corre si existen URL_GESTOR y DB_URL.
// Usa la ocurrencia 68 y al final deja la base como estaba.
@EnabledIfEnvironmentVariable(named = "URL_GESTOR", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class SistemaCompletoIntegracionTest {

    private static final int OCURRENCIA = 68;

    private Map<Integer, Integer> original;

    @BeforeEach
    void preparar() throws Exception {
        original = AyudaPruebas.guardarOcupados(OCURRENCIA);
    }

    @AfterEach
    void limpiar() throws Exception {
        AyudaPruebas.restaurar(original);
    }

    // Envía un mensaje al gestor y devuelve su respuesta.
    private JsonObject enviar(JsonObject mensaje) {
        try (ZContext contexto = new ZContext()) {
            return Util.enviar(contexto, System.getenv("URL_GESTOR"), mensaje, Cliente.ESPERA_GESTOR_MS);
        }
    }

    @Test
    void laConsultaDevuelve50Eventos() {
        JsonObject respuesta = enviar(Cliente.consulta("2026-10"));
        assertTrue(respuesta.get("ok").getAsBoolean());
        assertEquals(50, respuesta.getAsJsonArray("datos").size());
    }

    @Test
    void unaReservaSeConfirmaYDescuentaAsientos() {
        JsonObject respuesta = enviar(Cliente.reserva("prueba-punta-a-punta", OCURRENCIA, 2));
        assertTrue(respuesta.get("ok").getAsBoolean());
        int libresAntes = 100 - original.get(OCURRENCIA);
        assertEquals(libresAntes - 2, respuesta.getAsJsonObject("datos").get("libres").getAsInt());
    }

    @Test
    void unaReservaDeMasAsientosDeLosQueHaySeRechaza() {
        JsonObject respuesta = enviar(Cliente.reserva("prueba-punta-a-punta", OCURRENCIA, 101));
        assertFalse(respuesta.get("ok").getAsBoolean());
        assertTrue(respuesta.get("error").getAsString().contains("No hay asientos suficientes"));
    }

    @Test
    void laCancelacionSeRechazaPorSerDeLaSegundaEntrega() {
        JsonObject mensaje = new JsonObject();
        mensaje.addProperty("operacion", "cancelacion");
        JsonObject respuesta = enviar(mensaje);
        assertFalse(respuesta.get("ok").getAsBoolean());
        assertTrue(respuesta.get("error").getAsString().contains("segunda entrega"));
    }
}
