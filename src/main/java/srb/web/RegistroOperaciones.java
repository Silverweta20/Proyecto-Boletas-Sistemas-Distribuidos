package srb.web;

import java.util.ArrayList;
import java.util.List;

/**
 * Lista de las últimas operaciones hechas desde la página (las más nuevas primero, máximo 200).
 * Varias solicitudes HTTP pueden llegar al mismo tiempo, por eso los métodos son synchronized.
 */
public class RegistroOperaciones {

    private static final int MAXIMO = 200;

    private final List<Operacion> operaciones = new ArrayList<>();

    // Agrega una operación al comienzo de la lista y descarta la más vieja si ya hay demasiadas.
    public synchronized void agregarOperacion(Operacion operacion) {
        operaciones.add(0, operacion);
        if (operaciones.size() > MAXIMO) {
            operaciones.remove(operaciones.size() - 1);
        }
    }

    // Devuelve una copia de la lista para poder recorrerla sin que otro hilo la cambie.
    public synchronized List<Operacion> obtenerOperaciones() {
        return new ArrayList<>(operaciones);
    }
}
