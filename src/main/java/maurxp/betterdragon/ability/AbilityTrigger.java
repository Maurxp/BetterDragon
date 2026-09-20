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
    PERIODIC
}
