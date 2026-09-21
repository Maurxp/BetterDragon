package maurxp.betterdragon.leaderboard.model;

import maurxp.betterdragon.battle.model.BattleId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Representación inmutable del registro histórico de una batalla completada en el leaderboard.
 *
 * @param battleId    identificador único de la batalla
 * @param completedAt instante de conclusión de la batalla (epoch ms)
 * @param worldName   nombre del mundo donde se libró el combate
 * @param slayerUuid  UUID del jugador proclamado Slayer (TOP_DAMAGE), o null si no hubo participantes
 * @author maurxp
 */
public record LeaderboardBattleRecord(
        BattleId battleId,
        Instant completedAt,
        String worldName,
        UUID slayerUuid
) {

    public LeaderboardBattleRecord {
        Objects.requireNonNull(battleId, "battleId no puede ser nulo");
        Objects.requireNonNull(completedAt, "completedAt no puede ser nulo");
        Objects.requireNonNull(worldName, "worldName no puede ser nulo");
    }

    public Optional<UUID> getSlayerUuid() {
        return Optional.ofNullable(slayerUuid);
    }
}
