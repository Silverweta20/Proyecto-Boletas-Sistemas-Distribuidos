package srb.web;

import com.sun.net.httpserver.HttpServer;
import srb.comun.Util;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * Página web del SRB (capa de consumidores).
 * Es un cliente más del sistema: el navegador le habla por HTTP y este servidor reenvía cada
 * solicitud al Gestor de Transacciones por ZeroMQ, igual que cualquier otro cliente.
 *
 * Rutas (cada una tiene su propia clase):
 *   GET  /                         página (archivos de src/main/resources/web)         ManejadorArchivos
 *   GET  /api/eventos?mes=AAAA-MM  consulta de eventos del mes                         ManejadorEventos
 *   POST /api/reservas             reserva {"cliente":"ana","ocurrencia":5,"asientos":2}   ManejadorReservas
 *   GET  /api/operaciones          operaciones hechas desde la página y su resultado   ManejadorOperaciones
 *   GET  /api/config               mes inicial que muestra la página                   ManejadorConfig
 */
public class ServidorWeb {

    private static final String NOMBRE = "WEB";

    // Arranca el servidor HTTP y registra las rutas.
    public static void main(String[] args) throws IOException {
        int puerto = Integer.parseInt(Util.variable("PUERTO", "8080"));
        String urlGestor = Util.variable("URL_GESTOR", "tcp://localhost:5555");

        RegistroOperaciones registro = new RegistroOperaciones();
        PuenteGestor puente = new PuenteGestor(urlGestor, registro);

        HttpServer servidor = HttpServer.create(new InetSocketAddress(puerto), 0);
        servidor.createContext("/", new ManejadorArchivos());
        servidor.createContext("/api/eventos", new ManejadorEventos(puente));
        servidor.createContext("/api/reservas", new ManejadorReservas(puente));
        servidor.createContext("/api/operaciones", new ManejadorOperaciones(registro));
        servidor.createContext("/api/config", new ManejadorConfig());
        servidor.start();
        Util.log(NOMBRE, "Página web en el puerto " + puerto + " ; Gestor de Transacciones en " + urlGestor);
    }
}
