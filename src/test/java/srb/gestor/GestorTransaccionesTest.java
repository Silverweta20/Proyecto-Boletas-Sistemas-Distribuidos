package srb.gestor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Pruebas unitarias de la validación del Gestor de Transacciones (sin infraestructura).
class GestorTransaccionesTest {

    private JsonObject mensaje(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    @Test
    void consultaValidaSeAcepta() {
        assertNull(GestorTransacciones.validar(mensaje("{\"operacion\":\"consulta\",\"mes\":\"2026-10\"}")));
    }

    @Test
    void consultaConMesMalEscritoSeRechaza() {
        String error = GestorTransacciones.validar(mensaje("{\"operacion\":\"consulta\",\"mes\":\"octubre\"}"));
        assertEquals("El mes debe tener el formato AAAA-MM", error);
    }

    @Test
    void consultaConMesFueraDeRangoSeRechaza() {
        assertNotNull(GestorTransacciones.validar(mensaje("{\"operacion\":\"consulta\",\"mes\":\"2026-13\"}")));
    }

    @Test
    void consultaSinMesSeRechaza() {
        assertNotNull(GestorTransacciones.validar(mensaje("{\"operacion\":\"consulta\"}")));
    }

    @Test
    void reservaValidaSeAcepta() {
        String json = "{\"operacion\":\"reserva\",\"cliente\":\"ana\",\"ocurrencia\":5,\"asientos\":2}";
        assertNull(GestorTransacciones.validar(mensaje(json)));
    }

    @Test
    void reservaSinClienteSeRechaza() {
        String json = "{\"operacion\":\"reserva\",\"ocurrencia\":5,\"asientos\":2}";
        assertEquals("Falta el cliente", GestorTransacciones.validar(mensaje(json)));
    }

    @Test
    void reservaConOcurrenciaCeroSeRechaza() {
        String json = "{\"operacion\":\"reserva\",\"cliente\":\"ana\",\"ocurrencia\":0,\"asientos\":2}";
        assertEquals("La ocurrencia debe ser un número positivo", GestorTransacciones.validar(mensaje(json)));
    }

    @Test
    void reservaConOcurrenciaNegativaSeRechaza() {
        String json = "{\"operacion\":\"reserva\",\"cliente\":\"ana\",\"ocurrencia\":-3,\"asientos\":2}";
        assertNotNull(GestorTransacciones.validar(mensaje(json)));
    }

    @Test
    void reservaConAsientosCeroSeRechaza() {
        String json = "{\"operacion\":\"reserva\",\"cliente\":\"ana\",\"ocurrencia\":5,\"asientos\":0}";
        assertEquals("Los asientos deben ser un número positivo", GestorTransacciones.validar(mensaje(json)));
    }

    @Test
    void reservaConAsientosNegativosSeRechaza() {
        String json = "{\"operacion\":\"reserva\",\"cliente\":\"ana\",\"ocurrencia\":5,\"asientos\":-1}";
        assertNotNull(GestorTransacciones.validar(mensaje(json)));
    }

    @Test
    void reservaConAsientosNoNumericosSeRechaza() {
        String json = "{\"operacion\":\"reserva\",\"cliente\":\"ana\",\"ocurrencia\":5,\"asientos\":\"muchos\"}";
        assertEquals("Los asientos deben ser un número positivo", GestorTransacciones.validar(mensaje(json)));
    }

    @Test
    void modificacionEsDeLaSegundaEntrega() {
        String error = GestorTransacciones.validar(mensaje("{\"operacion\":\"modificacion\"}"));
        assertTrue(error.contains("segunda entrega"));
    }

    @Test
    void cancelacionEsDeLaSegundaEntrega() {
        String error = GestorTransacciones.validar(mensaje("{\"operacion\":\"cancelacion\"}"));
        assertTrue(error.contains("segunda entrega"));
    }

    @Test
    void operacionDesconocidaSeRechaza() {
        String error = GestorTransacciones.validar(mensaje("{\"operacion\":\"borrar\"}"));
        assertEquals("Operación desconocida: borrar", error);
    }

    @Test
    void mensajeVacioSeRechaza() {
        String error = GestorTransacciones.validar(new JsonObject());
        assertEquals("Mensaje inválido: falta la operación", error);
    }

    @Test
    void textoQueNoEsJsonSeConvierteEnMensajeVacio() {
        JsonObject solicitud = GestorTransacciones.leerSolicitud("esto no es json");
        assertEquals(0, solicitud.size());
        assertNotNull(GestorTransacciones.validar(solicitud));
    }
}
