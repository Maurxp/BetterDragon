package maurxp.betterdragon.ability;

/**
 * Estrategias de selección de objetivos para habilidades de combate.
 *
 * @author maurxp
 */
public enum TargetSelectorType {
    /**
     * Todos los jugadores válidos presentes dentro del área de la arena de combate.
     */
    ALL_IN_ARENA,

    /**
     * Un único jugador válido seleccionado aleatoriamente con desempate y RNG determinista.
     */
    RANDOM_PLAYER,

    /**
     * Un subconjunto de hasta N jugadores válidos sin duplicados.
     */
    RANDOM_SUBSET,

    /**
     * El jugador válido más cercano a la ubicación de origen resuelta.
     */
    NEAREST_PLAYER,

    /**
     * El jugador registrado con mayor daño acumulado (TOP_DAMAGE) según CombatRuntime.
     */
    DAMAGER,

    /**
     * El jugador que originó el trigger (si existe en el contexto).
     */
    TRIGGERING_PLAYER
}
