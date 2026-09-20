package maurxp.betterdragon.combat;

import maurxp.betterdragon.battle.model.BattleId;

import java.util.List;
import java.util.Objects;

/**
 * Instantánea inmutable del estado completo de combate de una sesión de batalla.
 * <p>
 * Diseñado como frontera limpia entre el {@link CombatRuntime} mutable en memoria
 * y los consumidores externos (persistencia, cálculo de recompensas, leaderboard).
 *
 * @param battleId     identificador de la batalla asociada
 * @param participants lista inmutable de instantáneas de cada participante
 * @param hitSequence  contador monotónico final de impactos registrados
 * @param totalDamage  suma total de daño acumulado por todos los participantes
 * @author maurxp
 */
public record CombatSnapshot(
        BattleId battleId,
        List<ParticipantSnapshot> participants,
        long hitSequence,
        double totalDamage
) {
    public CombatSnapshot {
        Objects.requireNonNull(battleId, "battleId no puede ser nulo");
        Objects.requireNonNull(participants, "participants no puede ser nulo");
        participants = List.copyOf(participants);
        if (hitSequence < 0) {
            throw new IllegalArgumentException("hitSequence no puede ser negativo: " + hitSequence);
        }
        if (Double.isNaN(totalDamage) || Double.isInfinite(totalDamage) || totalDamage < 0) {
            throw new IllegalArgumentException("totalDamage debe ser un número finito no negativo. Valor: " + totalDamage);
        }
    }
}
