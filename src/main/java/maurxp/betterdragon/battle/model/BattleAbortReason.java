package maurxp.betterdragon.battle.model;

/**
 * Razones tipadas y estructuradas de aborto o cancelación anticipada de una batalla.
 *
 * @author maurxp
 */
public enum BattleAbortReason {

    /**
     * La entidad del dragón desapareció del mundo cargado sin disparar el evento de muerte.
     */
    ENTITY_MISSING,

    /**
     * El intento de spawn falló o la entidad no superó la verificación de identidad PDC.
     */
    SPAWN_FAILED,

    /**
     * El mundo especificado no es válido o no corresponde a una dimensión THE_END.
     */
    INVALID_WORLD,

    /**
     * Ya existe una batalla activa incompatible para el mismo mundo.
     */
    DUPLICATE_BATTLE,

    /**
     * La configuración requerida para la batalla es inválida o inexistente.
     */
    INVALID_CONFIGURATION,

    /**
     * Cancelación manual explícita por comando o intervención administrativa.
     */
    MANUAL_ABORT,

    /**
     * Error interno no recuperable durante la ejecución o preparación de la batalla.
     */
    INTERNAL_ERROR
}
