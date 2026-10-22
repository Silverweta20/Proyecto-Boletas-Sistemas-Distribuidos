package srb.clientes;

import srb.comun.Util;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Generador de carga: simula varios clientes al mismo tiempo (un hilo por cliente, ver ClienteSimulado).
 * Cada cliente envía solicitudes al Gestor de Transacciones a un ritmo fijo y se mide el tiempo de
 * respuesta de cada una. Al terminar muestra y guarda en un CSV el promedio, la desviación estándar
 * y las transacciones por minuto de cada operación.
 *
 * Parámetros (variables de entorno):
 *   CLIENTES                 número de clientes (por defecto 3)
 *   CLIENTES_SOLO_CONSULTA   cuántos de ellos solo consultan (por defecto 2)
 *   SOLICITUDES_POR_MINUTO   solicitudes de cada cliente por minuto (mínimo 20)
 *   PORCENTAJE_CONSULTA      en los clientes mixtos, % de consultas; el resto son reservas (por defecto 50)
 *   DURACION_SEGUNDOS        duración de la prueba (por defecto 120)
 *   MES, OCURRENCIAS         mes consultado y número de ocurrencias que se pueden reservar
 *   URL_GESTOR, ARQUITECTURA, ARCHIVO_METRICAS
 */
public class GeneradorCarga {

    private static final String NOMBRE = "CARGA";

    // Lee la configuración, lanza los clientes simulados, espera a que terminen y guarda las métricas.
    public static void main(String[] args) throws Exception {
        int clientes = Integer.parseInt(Util.variable("CLIENTES", "3"));
        int soloConsulta = Integer.parseInt(Util.variable("CLIENTES_SOLO_CONSULTA", "2"));
        int porMinuto = Integer.parseInt(Util.variable("SOLICITUDES_POR_MINUTO", "20"));
        int porcentajeConsulta = Integer.parseInt(Util.variable("PORCENTAJE_CONSULTA", "50"));
        int duracion = Integer.parseInt(Util.variable("DURACION_SEGUNDOS", "120"));
        String mes = Util.variable("MES", "2026-10");
        int ocurrencias = Integer.parseInt(Util.variable("OCURRENCIAS", "70"));
        String urlGestor = Util.variable("URL_GESTOR", "tcp://localhost:5555");

        if (porMinuto < 20) {
            throw new IllegalArgumentException("El enunciado pide al menos 20 solicitudes por minuto");
        }
        Util.log(NOMBRE, clientes + " clientes (" + soloConsulta + " solo consultan), " + porMinuto
                + " solicitudes por minuto cada uno, " + porcentajeConsulta + "% consultas en los mixtos, "
                + duracion + " s");

        Resultados resultados = new Resultados();
        long inicio = System.currentTimeMillis();
        long horaFin = inicio + duracion * 1000L;
        List<Thread> hilos = new ArrayList<>();
        for (int i = 1; i <= clientes; i++) {
            boolean mixto = i > soloConsulta;
            ClienteSimulado simulado = new ClienteSimulado("cliente-" + i, mixto, urlGestor, mes, ocurrencias,
                    porMinuto, porcentajeConsulta, horaFin, resultados);
            Thread hilo = new Thread(simulado);
            hilos.add(hilo);
            hilo.start();
        }
        for (Thread hilo : hilos) {
            hilo.join();
        }
        double minutos = (System.currentTimeMillis() - inicio) / 60000.0;

        String arquitectura = Util.variable("ARQUITECTURA", "base");
        String filaConsultas = filaMetricas(arquitectura, clientes, "consulta",
                resultados.tiemposConsulta, resultados.consultasExitosas, minutos);
        String filaReservas = filaMetricas(arquitectura, clientes, "reserva",
                resultados.tiemposReserva, resultados.reservasExitosas, minutos);
        Util.log(NOMBRE, "Resultados (arquitectura,clientes,operacion,procesadas,exitosas,"
                + "promedio_ms,desviacion_ms,transacciones_por_minuto):");
        Util.log(NOMBRE, filaConsultas);
        Util.log(NOMBRE, filaReservas);
        guardar(Util.variable("ARCHIVO_METRICAS", "resultados/metricas.csv"), filaConsultas, filaReservas);
    }

    // Promedio de una lista de tiempos (0 si la lista está vacía).
    public static double promedio(List<Double> tiempos) {
        if (tiempos.isEmpty()) {
            return 0;
        }
        double suma = 0;
        for (double tiempo : tiempos) {
            suma = suma + tiempo;
        }
        return suma / tiempos.size();
    }

    // Desviación estándar muestral (divide entre n - 1); es 0 si hay menos de dos tiempos.
    public static double desviacionEstandar(List<Double> tiempos) {
        if (tiempos.size() < 2) {
            return 0;
        }
        double media = promedio(tiempos);
        double suma = 0;
        for (double tiempo : tiempos) {
            suma = suma + (tiempo - media) * (tiempo - media);
        }
        return Math.sqrt(suma / (tiempos.size() - 1));
    }

    // Transacciones por minuto: cuántas operaciones se procesaron dividido entre los minutos que duró la prueba.
    public static double transaccionesPorMinuto(int procesadas, double minutos) {
        if (minutos <= 0) {
            return 0;
        }
        return procesadas / minutos;
    }

    // Arma una línea del CSV con las métricas de una operación (con punto decimal, sin importar el idioma del equipo).
    public static String filaMetricas(String arquitectura, int clientes, String operacion,
                                      List<Double> tiempos, int exitosas, double minutos) {
        int procesadas = tiempos.size();
        double promedio = promedio(tiempos);
        double desviacion = desviacionEstandar(tiempos);
        double transacciones = transaccionesPorMinuto(procesadas, minutos);
        return arquitectura + "," + clientes + "," + operacion + "," + procesadas + "," + exitosas + ","
                + String.format(Locale.US, "%.2f,%.2f,%.2f", promedio, desviacion, transacciones);
    }

    // Agrega las filas al CSV (lo crea con encabezado si no existe).
    private static void guardar(String archivo, String... filas) throws IOException {
        Path ruta = Path.of(archivo);
        if (ruta.getParent() != null) {
            Files.createDirectories(ruta.getParent());
        }
        boolean nuevo = !Files.exists(ruta);
        try (PrintWriter salida = new PrintWriter(new FileWriter(archivo, true))) {
            if (nuevo) {
                salida.println("fecha,arquitectura,clientes,operacion,procesadas,exitosas,"
                        + "promedio_ms,desviacion_ms,transacciones_por_minuto");
            }
            for (String fila : filas) {
                salida.println(LocalDateTime.now().withNano(0) + "," + fila);
            }
        }
        Util.log(NOMBRE, "Métricas guardadas en " + archivo);
    }
}
