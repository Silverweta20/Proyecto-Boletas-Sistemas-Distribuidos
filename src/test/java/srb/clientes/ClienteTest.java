package srb.clientes;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

// Pruebas unitarias de los mensajes que arma el cliente.
class ClienteTest {

    @Test
    void laConsultaTieneOperacionMesEIdentificador() {
        JsonObject mensaje = Cliente.consulta("2026-10");
        assertEquals("consulta", mensaje.get("operacion").getAsString());
        assertEquals("2026-10", mensaje.get("mes").getAsString());
        assertFalse(mensaje.get("id").getAsString().isEmpty());
    }

    @Test
    void laReservaTieneClienteOcurrenciaYAsientos() {
        JsonObject mensaje = Cliente.reserva("ana", 5, 2);
        assertEquals("reserva", mensaje.get("operacion").getAsString());
        assertEquals("ana", mensaje.get("cliente").getAsString());
        assertEquals(5, mensaje.get("ocurrencia").getAsInt());
        assertEquals(2, mensaje.get("asientos").getAsInt());
        assertFalse(mensaje.get("id").getAsString().isEmpty());
    }

    @Test
    void cadaMensajeTieneUnIdentificadorDistinto() {
        String primero = Cliente.consulta("2026-10").get("id").getAsString();
        String segundo = Cliente.consulta("2026-10").get("id").getAsString();
        assertNotEquals(primero, segundo);
    }
}
