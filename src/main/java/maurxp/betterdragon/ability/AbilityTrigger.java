package maurxp.betterdragon.ability;

/**
 * Disparadores de ejecución para habilidades de BetterDragon.
 *
 * @author maurxp
 */
public enum AbilityTrigger {
    /**
     * Se dispara exactamente una vez al entrar en una fase de combate.
     */
    ON_PHASE_ENTER,

    /**
     * Se evalúa periódicamente en los ticks del servidor sujeto a cooldowns lógicos.
     */
    PERIODIC,

    /**
     * Se dispara al transicionar a una fase de vuelo nativa de Paper (EnderDragonChangePhaseEvent),
     * filtrada por la fase configurada en la habilidad (ej. CIRCLING, LAND_ON_PORTAL).
     */
    ON_FLIGHT_PHASE,

    /**
     * Se dispara de forma reactiva al recibir daño el dragón, con evaluación de cooldown
     * individual por atacante y probabilidad configurable.
     */
    ON_DAMAGE
}
