package maurxp.betterdragon.ability;

/**
 * Tipos de efectos ejecutables por las habilidades de BetterDragon.
 *
 * @author maurxp
 */
public enum AbilityEffectType {
    /**
     * Inflige daño directo a los objetivos resueltos sin alimentar CombatRuntime.
     */
    DAMAGE,

    /**
     * Aplica empuje físico (knockback) vectorial a los objetivos resueltos.
     */
    KNOCKBACK,

    /**
     * Genera partículas visuales en el origen resuelto vía Paper Particle API.
     */
    PARTICLE,

    /**
     * Reproduce un sonido en el origen resuelto vía Paper Sound API.
     */
    SOUND
}
