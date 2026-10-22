package srb.clientes;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Pruebas unitarias del cálculo de métricas del generador de carga.
class GeneradorCargaTest {

    private List<Double> tiempos(double... valores) {
        List<Double> lista = new ArrayList<>();
        for (double valor : valores) {
            lista.add(valor);
        }
        return lista;
    }

    @Test
    void promedioDeVariosTiempos() {
        assertEquals(20.0, GeneradorCarga.promedio(tiempos(10, 20, 30)), 0.0001);
    }

    @Test
    void promedioDeUnaListaVaciaEsCero() {
        assertEquals(0.0, GeneradorCarga.promedio(tiempos()), 0.0001);
    }

    @Test
    void desviacionEstandarMuestral() {
        // Tiempos 2, 4, 4, 4, 5, 5, 7, 9: promedio 5, suma de cuadrados 32, 32 / 7 = 4.571, raíz = 2.138
        double desviacion = GeneradorCarga.desviacionEstandar(tiempos(2, 4, 4, 4, 5, 5, 7, 9));
        assertEquals(2.138, desviacion, 0.001);
    }

    @Test
    void desviacionConUnSoloTiempoEsCero() {
        assertEquals(0.0, GeneradorCarga.desviacionEstandar(tiempos(15)), 0.0001);
    }

    @Test
    void desviacionDeTiemposIgualesEsCero() {
        assertEquals(0.0, GeneradorCarga.desviacionEstandar(tiempos(8, 8, 8)), 0.0001);
    }

    @Test
    void transaccionesPorMinuto() {
        assertEquals(60.0, GeneradorCarga.transaccionesPorMinuto(120, 2.0), 0.0001);
    }

    @Test
    void transaccionesPorMinutoSinDuracionEsCero() {
        assertEquals(0.0, GeneradorCarga.transaccionesPorMinuto(10, 0), 0.0001);
    }

    @Test
    void filaDeMetricasTieneTodasLasColumnas() {
        String fila = GeneradorCarga.filaMetricas("base", 3, "consulta", tiempos(10, 20, 30), 3, 0.5);
        assertEquals("base,3,consulta,3,3,20.00,10.00,6.00", fila);
    }
}
