package maurxp.betterdragon.reward.model;

import maurxp.betterdragon.battle.model.BattleId;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Plan de asignación global e inmutable resultante del cálculo de recompensas de una victoria.
 *
 * @param battleId               identificador de la batalla
 * @param allocations            lista inmutable de cuotas individuales calculadas
 * @param eligibleParticipants   lista de participantes que superaron el umbral mínimo
 * @param ineligibleParticipants lista de participantes descartados por participación insuficiente
 * @param totalBattleDamage      daño acumulado total registrado en el combate
 * @param eligibleDamage         suma del daño acumulado por los participantes elegibles
 * @param createdAt              momento de creación del plan
 * @author maurxp
 */
public record RewardAllocationPlan(
        BattleId battleId,
        List<RewardAllocation> allocations,
        List<UUID> eligibleParticipants,
        List<UUID> ineligibleParticipants,
        double totalBattleDamage,
        double eligibleDamage,
        Instant createdAt
) implements Serializable {

    public RewardAllocationPlan {
        Objects.requireNonNull(battleId, "battleId no puede ser nulo");
        Objects.requireNonNull(allocations, "allocations no puede ser nulo");
        Objects.requireNonNull(eligibleParticipants, "eligibleParticipants no puede ser nulo");
        Objects.requireNonNull(ineligibleParticipants, "ineligibleParticipants no puede ser nulo");
        Objects.requireNonNull(createdAt, "createdAt no puede ser nulo");

        allocations = List.copyOf(allocations);
        eligibleParticipants = List.copyOf(eligibleParticipants);
        ineligibleParticipants = List.copyOf(ineligibleParticipants);

        if (Double.isNaN(totalBattleDamage) || Double.isInfinite(totalBattleDamage) || totalBattleDamage < 0.0) {
            throw new IllegalArgumentException("totalBattleDamage debe ser finito y no negativo: " + totalBattleDamage);
        }
        if (Double.isNaN(eligibleDamage) || Double.isInfinite(eligibleDamage) || eligibleDamage < 0.0) {
            throw new IllegalArgumentException("eligibleDamage debe ser finito y no negativo: " + eligibleDamage);
        }
    }

    /**
     * Constructor de conveniencia con listas de participantes vacías y daño en 0.
     */
    public RewardAllocationPlan(BattleId battleId, List<RewardAllocation> allocations, Instant createdAt) {
        this(battleId, allocations, List.of(), List.of(), 0.0, 0.0, createdAt);
    }

    /**
     * Crea un plan vacío determinista para batallas sin participantes o sin daño.
     *
     * @param battleId identificador de la batalla
     * @return plan vacío
     */
    public static RewardAllocationPlan empty(BattleId battleId) {
        return new RewardAllocationPlan(
                battleId,
                List.of(),
                List.of(),
                List.of(),
                0.0,
                0.0,
                Instant.now()
        );
    }

    public boolean isEmpty() {
        return allocations.isEmpty();
    }
}
