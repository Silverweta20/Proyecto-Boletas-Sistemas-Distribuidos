package srb.persistencia;

/**
 * Una realización de un evento en una fecha, con sus asientos.
 * Los nombres de los campos son los que se envían en el JSON (y los que lee la página web).
 */
public class Ocurrencia {

    public int id;
    public String fecha;
    public int capacidad;
    public int ocupados;
    public int libres;

    public Ocurrencia(int id, String fecha, int capacidad, int ocupados) {
        this.id = id;
        this.fecha = fecha;
        this.capacidad = capacidad;
        this.ocupados = ocupados;
        this.libres = capacidad - ocupados;
    }
}
