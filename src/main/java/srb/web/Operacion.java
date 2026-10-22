package srb.web;

import com.google.gson.JsonObject;

/**
 * Una operación hecha desde la página web, tal como se muestra en el registro de operaciones.
 * Los nombres de los campos son los que lee la página (Gson los convierte a JSON).
 */
public class Operacion {

    public String hora;
    public String id;
    public String operacion;
    public String cliente;
    public String detalle;
    public boolean ok;
    public String resultado;
    public JsonObject reserva;   // datos de la reserva confirmada; null si no fue una reserva exitosa
    public long ms;
}
