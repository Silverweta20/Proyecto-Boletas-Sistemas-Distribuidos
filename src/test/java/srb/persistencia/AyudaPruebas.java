package srb.persistencia;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Funciones de apoyo para las pruebas de integración: conexión a la base de datos y limpieza.
 * Las pruebas reservan siempre con clientes cuyo nombre empieza por "prueba-"; al terminar se
 * borran esas reservas y se devuelven los asientos ocupados a su valor original.
 */
public class AyudaPruebas {

    public static String url() {
        return System.getenv("DB_URL");
    }

    public static String usuario() {
        String valor = System.getenv("DB_USUARIO");
        if (valor == null || valor.isBlank()) {
            return "srb";
        }
        return valor;
    }

    public static String clave() {
        String valor = System.getenv("DB_CLAVE");
        if (valor == null || valor.isBlank()) {
            return "srb";
        }
        return valor;
    }

    // Abre una conexión nueva a la base de datos de pruebas.
    public static BaseDatos nuevaBaseDatos() throws SQLException {
        return new BaseDatos(url(), usuario(), clave());
    }

    // Abre una conexión JDBC directa, para revisar y limpiar la base sin pasar por BaseDatos.
    public static Connection nuevaConexion() throws SQLException {
        return DriverManager.getConnection(url(), usuario(), clave());
    }

    // Guarda cuántos asientos ocupados tiene cada ocurrencia indicada, para restaurarlos después.
    public static Map<Integer, Integer> guardarOcupados(int... ocurrencias) throws SQLException {
        Map<Integer, Integer> original = new HashMap<>();
        try (Connection conexion = nuevaConexion()) {
            for (int ocurrencia : ocurrencias) {
                original.put(ocurrencia, ocupados(conexion, ocurrencia));
            }
        }
        return original;
    }

    // Asientos ocupados que tiene una ocurrencia en este momento.
    public static int ocupados(Connection conexion, int ocurrencia) throws SQLException {
        try (PreparedStatement consulta = conexion.prepareStatement("SELECT ocupados FROM ocurrencias WHERE id = ?")) {
            consulta.setInt(1, ocurrencia);
            try (ResultSet fila = consulta.executeQuery()) {
                fila.next();
                return fila.getInt(1);
            }
        }
    }

    // Suma de asientos de las reservas hechas por las pruebas en una ocurrencia.
    public static int asientosReservadosPorPruebas(Connection conexion, int ocurrencia) throws SQLException {
        String sql = "SELECT COALESCE(SUM(asientos), 0) FROM reservas WHERE ocurrencia_id = ? AND cliente LIKE 'prueba-%'";
        try (PreparedStatement consulta = conexion.prepareStatement(sql)) {
            consulta.setInt(1, ocurrencia);
            try (ResultSet fila = consulta.executeQuery()) {
                fila.next();
                return fila.getInt(1);
            }
        }
    }

    // Borra las reservas de las pruebas y deja cada ocurrencia con los asientos ocupados que tenía antes.
    public static void restaurar(Map<Integer, Integer> original) throws SQLException {
        try (Connection conexion = nuevaConexion()) {
            try (PreparedStatement borrar = conexion.prepareStatement("DELETE FROM reservas WHERE cliente LIKE 'prueba-%'")) {
                borrar.executeUpdate();
            }
            for (Map.Entry<Integer, Integer> entrada : original.entrySet()) {
                try (PreparedStatement poner = conexion.prepareStatement("UPDATE ocurrencias SET ocupados = ? WHERE id = ?")) {
                    poner.setInt(1, entrada.getValue());
                    poner.setInt(2, entrada.getKey());
                    poner.executeUpdate();
                }
            }
        }
    }
}
