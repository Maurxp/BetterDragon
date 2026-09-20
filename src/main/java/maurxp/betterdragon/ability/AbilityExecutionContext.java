package maurxp.betterdragon.ability;

import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.phase.PhaseDefinition;
import org.bukkit.Location;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Contexto de ejecución inmutable suministrado a un efecto de habilidad en el momento de su invocación.
 * <p>
 * Principios:
 * <ul>
 * <li><b>Inmutabilidad:</b> Los objetivos resueltos y la ubicación de origen ya se encuentran calculados.</li>
 * <li><b>Sin Servicios Globales:</b> No contiene referencias a repositorios SQL, gestores de recompensas ni singletons.</li>
 * <li><b>Vida Efímera:</b> Diseñado para ser consumido durante el tick de ejecución y descartado.</li>
 * </ul>
 *
 * @param battleId         identificador de la batalla
 * @param worldName        nombre del mundo de la batalla
 * @param dragon           entidad física viva del dragón
 * @param trigger          disparador que originó la ejecución
 * @param triggeringPlayer jugador causante (si aplica)
 * @param resolvedTargets  lista inmutable de jugadores objetivos
 * @param resolvedOrigin   ubicación resuelta del efecto
 * @param executionTick    tick lógico del servidor en que se ejecuta
 * @param ability          definición de la habilidad en ejecución
 * @param phase            definición de la fase de combate activa
 * @author maurxp
 */
public record AbilityExecutionContext(
        BattleId battleId,
        String worldName,
        EnderDragon dragon,
        AbilityTrigger trigger,
        Optional<Player> triggeringPlayer,
        List<Player> resolvedTargets,
        Location resolvedOrigin,
        long executionTick,
        AbilityDefinition ability,
        PhaseDefinition phase) {

    public AbilityExecutionContext {
        Objects.requireNonNull(battleId, "battleId no puede ser nulo");
        Objects.requireNonNull(worldName, "worldName no puede ser nulo");
        Objects.requireNonNull(dragon, "dragon no puede ser nulo");
        Objects.requireNonNull(trigger, "trigger no puede ser nulo");
        Objects.requireNonNull(triggeringPlayer, "triggeringPlayer no puede ser nulo (usar Optional.empty())");
        Objects.requireNonNull(resolvedTargets, "resolvedTargets no puede ser nulo");
        Objects.requireNonNull(resolvedOrigin, "resolvedOrigin no puede ser nulo");
        Objects.requireNonNull(ability, "ability no puede ser nula");
        Objects.requireNonNull(phase, "phase no puede ser nula");
        resolvedTargets = List.copyOf(resolvedTargets);
    }
}
