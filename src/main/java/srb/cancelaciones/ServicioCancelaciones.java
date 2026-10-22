package srb.cancelaciones;

/**
 * SEGUNDA ENTREGA - Servicio de Cancelaciones y Notificaciones (capa de negocio).
 *
 * Recibirá por PULL las cancelaciones que el Gestor de Transacciones acepta de inmediato,
 * pedirá al Servicio de Persistencia que libere los asientos y, cuando la base responda,
 * enviará por PUSH una notificación directamente al cliente que hizo la solicitud
 * (el cliente manda en la cancelación la dirección donde quiere recibirla).
 */
public class ServicioCancelaciones {
}
