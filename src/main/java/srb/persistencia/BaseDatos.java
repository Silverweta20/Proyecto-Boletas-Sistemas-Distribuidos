package srb.persistencia;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Operaciones sobre la base de datos PostgreSQL (tablas eventos, ocurrencias y reservas).
 * Solo la usa el Servicio de Persistencia.
 */
public class BaseDatos {

    private final Connection conexion;

    // Abre la conexión con la base de datos.
    public BaseDatos(String url, String usuario, String clave) throws SQLException {
        conexion = DriverManager.getConnection(url, usuario, clave);
    }

    // Cierra la conexión (se usa al terminar las pruebas).
    public void cerrar() throws SQLException {
        conexion.close();
    }

    // Devuelve los eventos de un mes (formato AAAA-MM) con sus ocurrencias y asientos.
    public List<Evento> consultarMes(String mes) throws SQLException {
        String sql = "SELECT e.id, e.nombre, o.id AS ocurrencia, o.fecha, o.capacidad, o.ocupados "
                + "FROM eventos e JOIN ocurrencias o ON o.evento_id = e.id "
                + "WHERE to_char(o.fecha, 'YYYY-MM') = ? "
                + "ORDER BY substring(e.id from 2)::int, o.fecha";
        List<Evento> eventos = new ArrayList<>();
        try (PreparedStatement consulta = conexion.prepareStatement(sql)) {
            consulta.setString(1, mes);
            try (ResultSet filas = consulta.executeQuery()) {
                while (filas.next()) {
                    String idEvento = filas.getString("id");
                    // Las filas vienen ordenadas por evento: si cambió el evento, se crea uno nuevo
                    if (eventos.isEmpty() || !eventos.get(eventos.size() - 1).id.equals(idEvento)) {
                        eventos.add(new Evento(idEvento, filas.getString("nombre")));
                    }
                    Evento actual = eventos.get(eventos.size() - 1);
                    actual.ocurrencias.add(new Ocurrencia(filas.getInt("ocurrencia"), filas.getString("fecha"),
                            filas.getInt("capacidad"), filas.getInt("ocupados")));
                }
            }
        }
        return eventos;
    }

    // Asientos libres de una ocurrencia, o -1 si la ocurrencia no existe.
    public int asientosLibres(int ocurrencia) throws SQLException {
        String sql = "SELECT capacidad - ocupados FROM ocurrencias WHERE id = ?";
        try (PreparedStatement consulta = conexion.prepareStatement(sql)) {
            consulta.setInt(1, ocurrencia);
            try (ResultSet fila = consulta.executeQuery()) {
                if (fila.next()) {
                    return fila.getInt(1);
                }
                return -1;
            }
        }
    }

    // Registra una reserva en una sola transacción: suma los asientos a la ocurrencia solo si caben
    // y guarda la reserva. Devuelve el número de la reserva, o -1 si ya no hay asientos suficientes
    // (en ese caso no cambia nada en la base).
    public int reservar(String cliente, int ocurrencia, int asientos) throws SQLException {
        conexion.setAutoCommit(false);
        try {
            if (!ocuparAsientos(ocurrencia, asientos)) {
                conexion.rollback();
                return -1;
            }
            int numeroReserva = guardarReserva(cliente, ocurrencia, asientos);
            conexion.commit();
            return numeroReserva;
        } catch (SQLException e) {
            conexion.rollback();
            throw e;
        } finally {
            conexion.setAutoCommit(true);
        }
    }

    // Suma los asientos a la ocurrencia solo si todavía caben (la condición va en el mismo UPDATE,
    // así dos reservas al mismo tiempo nunca pasan de la capacidad). Devuelve false si no caben.
    private boolean ocuparAsientos(int ocurrencia, int asientos) throws SQLException {
        String sql = "UPDATE ocurrencias SET ocupados = ocupados + ? WHERE id = ? AND ocupados + ? <= capacidad";
        try (PreparedStatement ocupar = conexion.prepareStatement(sql)) {
            ocupar.setInt(1, asientos);
            ocupar.setInt(2, ocurrencia);
            ocupar.setInt(3, asientos);
            return ocupar.executeUpdate() > 0;
        }
    }

    // Inserta la reserva y devuelve el número que le asignó la base de datos.
    private int guardarReserva(String cliente, int ocurrencia, int asientos) throws SQLException {
        String sql = "INSERT INTO reservas (cliente, ocurrencia_id, asientos) VALUES (?, ?, ?) RETURNING id";
        try (PreparedStatement guardar = conexion.prepareStatement(sql)) {
            guardar.setString(1, cliente);
            guardar.setInt(2, ocurrencia);
            guardar.setInt(3, asientos);
            try (ResultSet fila = guardar.executeQuery()) {
                fila.next();
                return fila.getInt(1);
            }
        }
    }
}
