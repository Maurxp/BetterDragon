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
    SOUND,

    /**
     * Genera proyectiles explosivos telegrafiados (TNTPrimed) con daño a bloques suprimido (CAND-03).
     */
    CARPET_BOMB,

    /**
     * Emite un anillo concéntrico expansivo de partículas, empuje y daño radial calibrado (CAND-04).
     */
    SHOCKWAVE,

    /**
     * Invoca esbirros menores marcados inequívocamente con PDC y sujetos a limpieza determinista (CAND-06).
     */
    SUMMON
}
