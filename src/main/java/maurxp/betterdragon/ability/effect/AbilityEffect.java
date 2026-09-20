package maurxp.betterdragon.ability.effect;

import maurxp.betterdragon.ability.AbilityExecutionContext;

/**
 * Contrato funcional para la ejecución de efectos de habilidades del dragón.
 * <p>
 * Principios:
 * <ul>
 * <li><b>0% NMS:</b> Toda la interacción utiliza exclusivamente la API pública de Paper/Bukkit.</li>
 * <li><b>Aislamiento de Combate:</b> El daño producido contra jugadores jamás se registra en {@code CombatRuntime}.</li>
 * <li><b>No Mutación de PDC:</b> Los efectos jamás alteran el PDC de identidad ni las máquinas de estado.</li>
 * </ul>
 *
 * @author maurxp
 */
@FunctionalInterface
public interface AbilityEffect {

    /**
     * Ejecuta el efecto con el contexto inmutable provisto.
     *
     * @param context contexto de ejecución con origen y objetivos previamente resueltos
     */
    void execute(AbilityExecutionContext context);
}
