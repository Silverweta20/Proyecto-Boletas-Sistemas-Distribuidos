package srb.procesamiento;

/**
 * SEGUNDA ENTREGA - Escalabilidad: reparto de carga entre varias réplicas del servicio de procesamiento.
 *
 * Usará el patrón ROUTER/DEALER de ZeroMQ (reparte los mensajes entre varios trabajadores) para que
 * el Gestor de Transacciones envíe las solicitudes a varias copias del servicio de procesamiento
 * y así atender más clientes al mismo tiempo.
 */
public class BalanceadorProcesamiento {
}
