package srb.comun;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Pruebas unitarias de las funciones comunes (no necesitan ningún servicio funcionando).
class UtilTest {

    private JsonObject mensaje(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    @Test
    void textoLeeElCampoYQuitaEspacios() {
        assertEquals("ana", Util.texto(mensaje("{\"cliente\":\"  ana  \"}"), "cliente"));
    }

    @Test
    void textoDevuelveVacioSiElCampoNoExiste() {
        assertEquals("", Util.texto(mensaje("{}"), "cliente"));
    }

    @Test
    void textoDevuelveVacioSiElCampoEsNulo() {
        assertEquals("", Util.texto(mensaje("{\"cliente\":null}"), "cliente"));
    }

    @Test
    void enteroLeeUnNumero() {
        assertEquals(5, Util.entero(mensaje("{\"asientos\":5}"), "asientos"));
    }

    @Test
    void enteroAceptaUnNumeroEscritoComoTexto() {
        assertEquals(7, Util.entero(mensaje("{\"asientos\":\"7\"}"), "asientos"));
    }

    @Test
    void enteroDevuelveMenosUnoSiElCampoNoExiste() {
        assertEquals(-1, Util.entero(mensaje("{}"), "asientos"));
    }

    @Test
    void enteroDevuelveMenosUnoSiNoEsNumero() {
        assertEquals(-1, Util.entero(mensaje("{\"asientos\":\"abc\"}"), "asientos"));
    }

    @Test
    void enteroDevuelveMenosUnoSiTieneDecimales() {
        assertEquals(-1, Util.entero(mensaje("{\"asientos\":2.5}"), "asientos"));
    }

    @Test
    void enteroDevuelveMenosUnoSiEsNulo() {
        assertEquals(-1, Util.entero(mensaje("{\"asientos\":null}"), "asientos"));
    }

    @Test
    void respuestaOkTieneOkYDatos() {
        JsonObject datos = new JsonObject();
        datos.addProperty("libres", 10);
        JsonObject respuesta = Util.respuestaOk(datos);
        assertTrue(respuesta.get("ok").getAsBoolean());
        assertEquals(10, respuesta.getAsJsonObject("datos").get("libres").getAsInt());
    }

    @Test
    void respuestaErrorTieneOkFalsoYMotivo() {
        JsonObject respuesta = Util.respuestaError("No hay asientos");
        assertFalse(respuesta.get("ok").getAsBoolean());
        assertEquals("No hay asientos", respuesta.get("error").getAsString());
    }

    @Test
    void variableDevuelveElValorPorDefectoSiNoExiste() {
        assertEquals("valor", Util.variable("VARIABLE_QUE_NO_EXISTE_SRB", "valor"));
    }
}
