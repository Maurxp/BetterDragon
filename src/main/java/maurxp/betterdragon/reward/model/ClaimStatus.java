package maurxp.betterdragon.reward.model;

/**
 * Estados del ciclo de vida de un reclamo de recompensa en el buzón.
 *
 * @author maurxp
 */
public enum ClaimStatus {
    /**
     * Recompensa asignada que no pudo entregarse de inmediato (jugador offline o inventario lleno).
     */
    PENDING,

    /**
     * Recompensa entregada de manera íntegra y confirmada en el inventario del jugador.
     */
    CLAIMED,

    /**
     * Fallo transitorio durante la entrega que permite un reintento seguro sin duplicar ítems.
     */
    FAILED_RETRYABLE
}
