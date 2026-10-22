package srb.persistencia;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Pruebas de integración de la base de datos. Solo corren si existe la variable de entorno DB_URL
// (PostgreSQL levantado). Usan la ocurrencia 69 y al final dejan la base como estaba.
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class BaseDatosIntegracionTest {

    private static final int OCURRENCIA = 69;

    private BaseDatos baseDatos;
    private Map<Integer, Integer> original;

    @BeforeEach
    void preparar() throws Exception {
        original = AyudaPruebas.guardarOcupados(OCURRENCIA);
        baseDatos = AyudaPruebas.nuevaBaseDatos();
    }

    @AfterEach
    void limpiar() throws Exception {
        baseDatos.cerrar();
        AyudaPruebas.restaurar(original);
    }

    @Test
    void laConsultaDeOctubreTrae50EventosY70Ocurrencias() throws Exception {
        List<Evento> eventos = baseDatos.consultarMes("2026-10");
        int ocurrencias = 0;
        for (Evento evento : eventos) {
            ocurrencias = ocurrencias + evento.ocurrencias.size();
        }
        assertEquals(50, eventos.size());
        assertEquals(70, ocurrencias);
        assertEquals("E1", eventos.get(0).id);
    }

    @Test
    void laConsultaDeUnMesSinEventosVieneVacia() throws Exception {
        assertTrue(baseDatos.consultarMes("2030-01").isEmpty());
    }

    @Test
    void reservarDescuentaLosAsientosLibres() throws Exception {
        int antes = baseDatos.asientosLibres(OCURRENCIA);
        int reserva = baseDatos.reservar("prueba-ana", OCURRENCIA, 2);
        assertTrue(reserva > 0);
        assertEquals(antes - 2, baseDatos.asientosLibres(OCURRENCIA));
    }

    @Test
    void reservarMasDeLoDisponibleDevuelveMenosUnoYNoCambiaNada() throws Exception {
        int antes = baseDatos.asientosLibres(OCURRENCIA);
        int reserva = baseDatos.reservar("prueba-luis", OCURRENCIA, antes + 1);
        assertEquals(-1, reserva);
        assertEquals(antes, baseDatos.asientosLibres(OCURRENCIA));
        try (Connection conexion = AyudaPruebas.nuevaConexion()) {
            assertEquals(0, AyudaPruebas.asientosReservadosPorPruebas(conexion, OCURRENCIA));
        }
    }

    @Test
    void sePuedeReservarExactamenteLosAsientosQueQuedan() throws Exception {
        int antes = baseDatos.asientosLibres(OCURRENCIA);
        assertTrue(baseDatos.reservar("prueba-maria", OCURRENCIA, antes) > 0);
        assertEquals(0, baseDatos.asientosLibres(OCURRENCIA));
        assertEquals(-1, baseDatos.reservar("prueba-maria", OCURRENCIA, 1));
    }

    @Test
    void laOcurrenciaInexistenteDaMenosUno() throws Exception {
        assertEquals(-1, baseDatos.asientosLibres(99999));
        assertEquals(-1, baseDatos.reservar("prueba-ana", 99999, 1));
    }

    @Test
    void elServicioDePersistenciaRespondeDisponibilidadYReserva() {
        JsonObject pregunta = new JsonObject();
        pregunta.addProperty("operacion", "disponibilidad");
        pregunta.addProperty("ocurrencia", OCURRENCIA);
        JsonObject respuesta = ServicioPersistencia.atender(baseDatos, pregunta);
        assertTrue(respuesta.get("ok").getAsBoolean());
        int libres = respuesta.getAsJsonObject("datos").get("libres").getAsInt();

        JsonObject orden = new JsonObject();
        orden.addProperty("operacion", "reservar");
        orden.addProperty("cliente", "prueba-ana");
        orden.addProperty("ocurrencia", OCURRENCIA);
        orden.addProperty("asientos", 3);
        JsonObject confirmacion = ServicioPersistencia.atender(baseDatos, orden);
        assertTrue(confirmacion.get("ok").getAsBoolean());
        assertEquals(libres - 3, confirmacion.getAsJsonObject("datos").get("libres").getAsInt());
    }

    @Test
    void elServicioDePersistenciaRechazaUnaOcurrenciaInexistente() {
        JsonObject pregunta = new JsonObject();
        pregunta.addProperty("operacion", "disponibilidad");
        pregunta.addProperty("ocurrencia", 99999);
        JsonObject respuesta = ServicioPersistencia.atender(baseDatos, pregunta);
        assertFalse(respuesta.get("ok").getAsBoolean());
        assertEquals("La ocurrencia no existe", respuesta.get("error").getAsString());
    }
}
