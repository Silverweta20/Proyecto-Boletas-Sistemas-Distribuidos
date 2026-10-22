package srb.clientes;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

// Pruebas unitarias de la lectura de una línea del archivo CSV de casos.
class EjecutorCasoTest {

    @Test
    void lineaDeReservaCompleta() {
        String[] campos = EjecutorCaso.separarCampos("ana,reserva,,5,2");
        assertArrayEquals(new String[] {"ana", "reserva", "", "5", "2"}, campos);
    }

    @Test
    void lineaDeConsultaConCamposVaciosAlFinal() {
        String[] campos = EjecutorCaso.separarCampos("ana,consulta,2026-10,,");
        assertArrayEquals(new String[] {"ana", "consulta", "2026-10", "", ""}, campos);
    }

    @Test
    void lineaCortaSeCompletaConVacios() {
        String[] campos = EjecutorCaso.separarCampos("ana,cancelacion");
        assertArrayEquals(new String[] {"ana", "cancelacion", "", "", ""}, campos);
    }

    @Test
    void seQuitanLosEspaciosSobrantes() {
        String[] campos = EjecutorCaso.separarCampos(" luis , reserva , , 1 , 98 ");
        assertArrayEquals(new String[] {"luis", "reserva", "", "1", "98"}, campos);
    }

    @Test
    void laLineaDeConsultaGeneraUnMensajeDeConsulta() {
        JsonObject mensaje = EjecutorCaso.construirMensaje(EjecutorCaso.separarCampos("ana,consulta,2026-10,,"));
        assertEquals("consulta", mensaje.get("operacion").getAsString());
        assertEquals("2026-10", mensaje.get("mes").getAsString());
    }

    @Test
    void laLineaDeReservaGeneraUnMensajeDeReserva() {
        JsonObject mensaje = EjecutorCaso.construirMensaje(EjecutorCaso.separarCampos("luis,reserva,,51,3"));
        assertEquals("reserva", mensaje.get("operacion").getAsString());
        assertEquals("luis", mensaje.get("cliente").getAsString());
        assertEquals(51, mensaje.get("ocurrencia").getAsInt());
        assertEquals(3, mensaje.get("asientos").getAsInt());
    }

    @Test
    void laCancelacionSeEnviaSoloConOperacionYCliente() {
        JsonObject mensaje = EjecutorCaso.construirMensaje(EjecutorCaso.separarCampos("ana,cancelacion,,,"));
        assertEquals("cancelacion", mensaje.get("operacion").getAsString());
        assertEquals("ana", mensaje.get("cliente").getAsString());
    }
}
