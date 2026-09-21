package maurxp.betterdragon.reward.model;

import maurxp.betterdragon.battle.model.BattleId;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Cuota inmutable de recompensa calculada para un participante de una batalla.
 *
 * @param battleId             identificador de la batalla
 * @param participantId        UUID inmutable del jugador receptor
 * @param participantName      nombre histórico o último conocido del participante
 * @param source               origen de la recompensa (PARTICIPATION o SLAYER)
 * @param rewardId             identificador de la definición de recompensa
 * @param item                 ítem y cantidad asignada
 * @param participationPercent porcentaje de participación en el daño total
 * @param allocatedAt          momento en el que se realizó la asignación
 * @author maurxp
 */
public record RewardAllocation(
        BattleId battleId,
        UUID participantId,
        String participantName,
        RewardSource source,
        String rewardId,
        RewardItem item,
        double participationPercent,
        Instant allocatedAt
) implements Serializable {

    public RewardAllocation {
        Objects.requireNonNull(battleId, "battleId no puede ser nulo");
        Objects.requireNonNull(participantId, "participantId no puede ser nulo");
        Objects.requireNonNull(participantName, "participantName no puede ser nulo");
        Objects.requireNonNull(source, "source no puede ser nulo");
        Objects.requireNonNull(rewardId, "rewardId no puede ser nulo");
        rewardId = rewardId.trim();
        if (rewardId.isEmpty()) {
            throw new IllegalArgumentException("rewardId no puede estar vacío");
        }
        Objects.requireNonNull(item, "item no puede ser nulo");
        Objects.requireNonNull(allocatedAt, "allocatedAt no puede ser nulo");
        if (Double.isNaN(participationPercent) || Double.isInfinite(participationPercent) || participationPercent < 0.0) {
            throw new IllegalArgumentException("participationPercent debe ser un número finito no negativo: " + participationPercent);
        }
    }

    /**
     * Construye la clave canónica de idempotencia para esta asignación.
     * <p>
     * Formato: {@code battleId:participantId:rewardId}
     *
     * @return clave de idempotencia única
     */
    public String idempotencyKey() {
        return battleId.asString() + ":" + participantId + ":" + rewardId;
    }
}
