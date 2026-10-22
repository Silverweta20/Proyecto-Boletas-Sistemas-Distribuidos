package srb.persistencia;

import java.util.ArrayList;
import java.util.List;

/**
 * Un evento (E1, E2, ...) con la lista de sus ocurrencias dentro del mes consultado.
 * Los nombres de los campos son los que se envían en el JSON (y los que lee la página web).
 */
public class Evento {

    public String id;
    public String nombre;
    public List<Ocurrencia> ocurrencias = new ArrayList<>();

    public Evento(String id, String nombre) {
        this.id = id;
        this.nombre = nombre;
    }
}
