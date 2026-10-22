package srb.persistencia;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Prueba de concurrencia: varios hilos (cada uno con su propia conexión) reservan en la misma
// ocurrencia al mismo tiempo y nunca se debe pasar de la capacidad. Usa la ocurrencia 70.
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class ConcurrenciaIntegracionTest {

    private static final int OCURRENCIA = 70;
    private static final int HILOS = 8;
    private static final int INTENTOS_POR_HILO = 20;
    private static final int ASIENTOS_POR_RESERVA = 3;

    private Map<Integer, Integer> original;

    @BeforeEach
    void preparar() throws Exception {
        original = AyudaPruebas.guardarOcupados(OCURRENCIA);
    }

    @AfterEach
    void limpiar() throws Exception {
        AyudaPruebas.restaurar(original);
    }

    // Un hilo que intenta reservar muchas veces y cuenta cuántas confirmó.
    private static class Reservador implements Runnable {
        int confirmadas = 0;
        Exception error = null;
        private final String cliente;

        Reservador(String cliente) {
            this.cliente = cliente;
        }

        @Override
        public void run() {
            try {
                BaseDatos baseDatos = AyudaPruebas.nuevaBaseDatos();
                for (int i = 0; i < INTENTOS_POR_HILO; i++) {
                    if (baseDatos.reservar(cliente, OCURRENCIA, ASIENTOS_POR_RESERVA) > 0) {
                        confirmadas++;
                    }
                }
                baseDatos.cerrar();
            } catch (Exception e) {
                error = e;
            }
        }
    }

    @Test
    void variosHilosNuncaSuperanLaCapacidad() throws Exception {
        int ocupadosAntes = original.get(OCURRENCIA);
        List<Reservador> reservadores = new ArrayList<>();
        List<Thread> hilos = new ArrayList<>();
        for (int i = 1; i <= HILOS; i++) {
            Reservador reservador = new Reservador("prueba-hilo-" + i);
            reservadores.add(reservador);
            Thread hilo = new Thread(reservador);
            hilos.add(hilo);
            hilo.start();
        }
        for (Thread hilo : hilos) {
            hilo.join();
        }

        int confirmadas = 0;
        for (Reservador reservador : reservadores) {
            assertEquals(null, reservador.error);
            confirmadas = confirmadas + reservador.confirmadas;
        }

        try (Connection conexion = AyudaPruebas.nuevaConexion()) {
            int ocupadosDespues = AyudaPruebas.ocupados(conexion, OCURRENCIA);
            assertTrue(ocupadosDespues <= 100, "No se puede pasar de 100 asientos ocupados");
            assertEquals(ocupadosAntes + confirmadas * ASIENTOS_POR_RESERVA, ocupadosDespues);
            assertEquals(confirmadas * ASIENTOS_POR_RESERVA, AyudaPruebas.asientosReservadosPorPruebas(conexion, OCURRENCIA));
        }
    }
}
