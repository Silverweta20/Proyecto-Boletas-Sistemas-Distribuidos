package srb.clientes;

import com.google.gson.JsonObject;
import org.zeromq.ZContext;
import srb.comun.Util;

import java.util.Random;

/**
 * Un cliente simulado del generador de carga. Se ejecuta en su propio hilo (por eso implementa Runnable).
 * Envía una solicitud cada (60 / solicitudesPorMinuto) segundos hasta que se acaba el tiempo
 * y anota cuánto se demora cada respuesta.
 */
public class ClienteSimulado implements Runnable {

    private final String nombre;
    private final boolean mixto;            // true: consulta y reserva; false: solo consulta
    private final String urlGestor;
    private final String mes;
    private final int ocurrencias;          // cuántas ocurrencias se pueden reservar (1 a ocurrencias)
    private final int solicitudesPorMinuto;
    private final int porcentajeConsulta;
    private final long horaFin;             // momento (en milisegundos) en que debe terminar
    private final Resultados resultados;

    public ClienteSimulado(String nombre, boolean mixto, String urlGestor, String mes, int ocurrencias,
                           int solicitudesPorMinuto, int porcentajeConsulta, long horaFin, Resultados resultados) {
        this.nombre = nombre;
        this.mixto = mixto;
        this.urlGestor = urlGestor;
        this.mes = mes;
        this.ocurrencias = ocurrencias;
        this.solicitudesPorMinuto = solicitudesPorMinuto;
        this.porcentajeConsulta = porcentajeConsulta;
        this.horaFin = horaFin;
        this.resultados = resultados;
    }

    // Repite: elegir una operación, enviarla, medir el tiempo y esperar hasta el próximo envío.
    @Override
    public void run() {
        Random azar = new Random();
        long intervaloMs = 60000L / solicitudesPorMinuto;
        long siguienteEnvio = System.currentTimeMillis();
        try (ZContext contexto = new ZContext()) {
            while (System.currentTimeMillis() < horaFin) {
                boolean esConsulta = elegirConsulta(azar);
                JsonObject mensaje;
                if (esConsulta) {
                    mensaje = Cliente.consulta(mes);
                } else {
                    mensaje = Cliente.reserva(nombre, 1 + azar.nextInt(ocurrencias), 1 + azar.nextInt(3));
                }
                enviarYMedir(contexto, mensaje, esConsulta);

                // Espera hasta el próximo envío para mantener el ritmo pedido
                siguienteEnvio = siguienteEnvio + intervaloMs;
                long espera = siguienteEnvio - System.currentTimeMillis();
                if (espera > 0) {
                    Thread.sleep(espera);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Los clientes que solo consultan siempre consultan; los mixtos consultan según el porcentaje indicado.
    private boolean elegirConsulta(Random azar) {
        if (!mixto) {
            return true;
        }
        return azar.nextInt(100) < porcentajeConsulta;
    }

    // Envía el mensaje al gestor, mide cuánto tarda la respuesta y la anota en los resultados.
    private void enviarYMedir(ZContext contexto, JsonObject mensaje, boolean esConsulta) {
        long envio = System.nanoTime();
        JsonObject respuesta = Util.enviar(contexto, urlGestor, mensaje, Cliente.ESPERA_GESTOR_MS);
        double milisegundos = (System.nanoTime() - envio) / 1000000.0;
        boolean ok = respuesta.get("ok").getAsBoolean();

        if (esConsulta) {
            resultados.agregarConsulta(milisegundos, ok);
        } else {
            resultados.agregarReserva(milisegundos, ok);
        }

        String detalle;
        if (esConsulta) {
            detalle = "mes=" + mes;
        } else {
            detalle = "ocurrencia=" + mensaje.get("ocurrencia") + " asientos=" + mensaje.get("asientos");
        }
        String resultado = "OK";
        if (!ok) {
            resultado = "RECHAZO: " + respuesta.get("error").getAsString();
        }
        Util.log(nombre, Util.texto(mensaje, "operacion") + " " + detalle + " -> " + resultado
                + " (" + Math.round(milisegundos * 10) / 10.0 + " ms)");
    }
}
