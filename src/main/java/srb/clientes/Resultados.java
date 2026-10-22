package srb.clientes;

import java.util.ArrayList;
import java.util.List;

/**
 * Guarda los tiempos de respuesta que miden todos los clientes simulados.
 * Varios hilos escriben al mismo tiempo, por eso los métodos que modifican son synchronized
 * (solo un hilo a la vez puede entrar).
 */
public class Resultados {

    public final List<Double> tiemposConsulta = new ArrayList<>();
    public final List<Double> tiemposReserva = new ArrayList<>();
    public int consultasExitosas = 0;
    public int reservasExitosas = 0;

    // Anota el tiempo (en milisegundos) de una consulta y si salió bien.
    public synchronized void agregarConsulta(double milisegundos, boolean exitosa) {
        tiemposConsulta.add(milisegundos);
        if (exitosa) {
            consultasExitosas++;
        }
    }

    // Anota el tiempo (en milisegundos) de una reserva y si salió bien.
    public synchronized void agregarReserva(double milisegundos, boolean exitosa) {
        tiemposReserva.add(milisegundos);
        if (exitosa) {
            reservasExitosas++;
        }
    }
}
