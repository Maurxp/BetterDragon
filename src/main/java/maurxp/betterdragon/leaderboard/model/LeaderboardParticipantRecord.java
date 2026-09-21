package maurxp.betterdragon.leaderboard.model;

import maurxp.betterdragon.battle.model.BattleId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Representación inmutable de la participación de un jugador individual en una batalla para el leaderboard.
 * <p>
 * Principios:
 * <ul>
 *   <li><b>Inmutabilidad Completa:</b> Seguro para uso multihilo sin referencias Bukkit.</li>
 *   <li><b>Separación de Nombres:</b> Preserva {@code historicalName} para el registro estático de esta batalla
 *       y {@code lastKnownName} para actualizar el acumulado en {@code bd_leaderboard_players}.</li>
 * </ul>
 *
 * @param battleId          identificador de la batalla
 * @param playerUuid        UUID único e inmutable del jugador
 * @param historicalName    nombre del jugador capturado durante esta batalla específica
 * @param lastKnownName     nombre más reciente conocido para actualizar estadísticas del jugador
 * @param damage            daño total infligido en esta batalla
 * @param firstHitSequence  secuencia de impacto para desempate
 * @param wasSlayer         indica si el jugador fue galardonado como Slayer (TOP_DAMAGE)
 * @param participatedAt    instante en que se completó/registró la participación
 * @author maurxp
 */
public record LeaderboardParticipantRecord(
        BattleId battleId,
        UUID playerUuid,
        String historicalName,
        String lastKnownName,
        double damage,
        long firstHitSequence,
        boolean wasSlayer,
        Instant participatedAt
) {

    public LeaderboardParticipantRecord {
        Objects.requireNonNull(battleId, "battleId no puede ser nulo");
        Objects.requireNonNull(playerUuid, "playerUuid no puede ser nulo");
        Objects.requireNonNull(historicalName, "historicalName no puede ser nulo");
        Objects.requireNonNull(lastKnownName, "lastKnownName no puede ser nulo");
        Objects.requireNonNull(participatedAt, "participatedAt no puede ser nulo");

        if (Double.isNaN(damage) || Double.isInfinite(damage) || damage < 0) {
            throw new IllegalArgumentException("damage debe ser finito y no negativo: " + damage);
        }
        if (firstHitSequence < 0) {
            throw new IllegalArgumentException("firstHitSequence no puede ser negativo: " + firstHitSequence);
        }
    }

    /**
     * Constructor de conveniencia cuando se lee desde la tabla de participación histórica (donde sólo se persiste historicalName).
     */
    public static LeaderboardParticipantRecord ofHistorical(
            BattleId battleId,
            UUID playerUuid,
            String historicalName,
            double damage,
            long firstHitSequence,
            boolean wasSlayer,
            Instant participatedAt
    ) {
        return new LeaderboardParticipantRecord(
                battleId,
                playerUuid,
                historicalName,
                historicalName,
                damage,
                firstHitSequence,
                wasSlayer,
                participatedAt
        );
    }
}
