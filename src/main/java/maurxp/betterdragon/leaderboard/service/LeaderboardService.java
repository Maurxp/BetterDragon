package maurxp.betterdragon.leaderboard.service;

import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.battle.model.BattleResult;
import maurxp.betterdragon.combat.CombatSnapshot;
import maurxp.betterdragon.combat.ParticipantSnapshot;
import maurxp.betterdragon.leaderboard.model.LeaderboardBattleRecord;
import maurxp.betterdragon.leaderboard.model.LeaderboardParticipantRecord;
import maurxp.betterdragon.leaderboard.model.LeaderboardPlayerStats;
import maurxp.betterdragon.leaderboard.storage.LeaderboardStorage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Servicio de aplicación para la ingesta y consulta de datos del Leaderboard de BetterDragon.
 * <p>
 * Responsabilidades:
 * <ul>
 *   <li><b>Transformación Desacoplada:</b> Convierte {@link BattleResult} inmutable en registros del modelo de leaderboard
 *       sin interactuar con el hilo principal ni con objetos Bukkit vivos.</li>
 *   <li><b>Respeto Estricto de Slayer:</b> Consume directamente el Slayer determinado por {@code BattleResult} (TOP_DAMAGE)
 *       sin alterar ni recalcular la asignación.</li>
 *   <li><b>Consulta Unificada:</b> Expone métodos tipados para consultar estadísticas acumuladas y rankings globales.</li>
 * </ul>
 *
 * @author maurxp
 */
public class LeaderboardService implements AutoCloseable {

    private final LeaderboardStorage storage;
    private final Logger logger;

    public LeaderboardService(LeaderboardStorage storage, Logger logger) {
        this.storage = Objects.requireNonNull(storage, "storage no puede ser nulo");
        this.logger = Objects.requireNonNull(logger, "logger no puede ser nulo");
    }

    /**
     * Procesa y registra el resultado de una victoria en el leaderboard persistente de forma asíncrona.
     *
     * @param result    resultado inmutable de la batalla completada
     * @param worldName nombre del mundo donde ocurrió la victoria
     * @return CompletableFuture con {@code true} si la batalla fue registrada; {@code false} si ya existía (idempotente)
     */
    public CompletableFuture<Boolean> recordVictory(BattleResult result, String worldName) {
        Objects.requireNonNull(result, "result no puede ser nulo");
        Objects.requireNonNull(worldName, "worldName no puede ser nulo");

        if (!result.isVictory()) {
            logger.warning("[BetterDragon] Se intentó registrar en leaderboard una batalla no victoriosa: " + result.battleId());
            return CompletableFuture.completedFuture(false);
        }

        BattleId battleId = result.battleId();
        Instant completedAt = result.endTime();
        UUID slayerUuid = result.slayerUniqueId();

        LeaderboardBattleRecord battleRecord = new LeaderboardBattleRecord(
                battleId,
                completedAt,
                worldName,
                slayerUuid
        );

        List<LeaderboardParticipantRecord> participantRecords = new ArrayList<>();
        if (result.combatSnapshot() != null) {
            CombatSnapshot combatSnapshot = result.combatSnapshot();
            for (ParticipantSnapshot participant : combatSnapshot.participants()) {
                boolean wasSlayer = slayerUuid != null && participant.playerId().equals(slayerUuid);
                participantRecords.add(new LeaderboardParticipantRecord(
                        battleId,
                        participant.playerId(),
                        participant.historicalName(),
                        participant.lastKnownName(),
                        participant.totalDamage(),
                        participant.firstHitSequence(),
                        wasSlayer,
                        completedAt
                ));
            }
        }

        return storage.recordBattle(battleRecord, participantRecords);
    }

    /**
     * Obtiene las estadísticas de un jugador por UUID.
     */
    public CompletableFuture<Optional<LeaderboardPlayerStats>> getPlayerStats(UUID playerUuid) {
        return storage.getPlayerStats(playerUuid);
    }

    /**
     * Obtiene el ranking por daño total.
     */
    public CompletableFuture<List<LeaderboardPlayerStats>> getTopDamage(int limit) {
        return storage.getTopDamage(limit);
    }

    /**
     * Obtiene el ranking por cantidad de victorias como Slayer (TOP_DAMAGE).
     */
    public CompletableFuture<List<LeaderboardPlayerStats>> getTopSlayers(int limit) {
        return storage.getTopSlayers(limit);
    }

    /**
     * Obtiene el ranking por cantidad de batallas participadas.
     */
    public CompletableFuture<List<LeaderboardPlayerStats>> getTopParticipations(int limit) {
        return storage.getTopParticipations(limit);
    }

    /**
     * Obtiene el registro de una batalla específica.
     */
    public CompletableFuture<Optional<LeaderboardBattleRecord>> getBattle(BattleId battleId) {
        return storage.getBattle(battleId);
    }

    /**
     * Obtiene la lista de participantes de una batalla específica.
     */
    public CompletableFuture<List<LeaderboardParticipantRecord>> getBattleParticipants(BattleId battleId) {
        return storage.getBattleParticipants(battleId);
    }

    /**
     * Obtiene el historial de participaciones de un jugador.
     */
    public CompletableFuture<List<LeaderboardParticipantRecord>> getPlayerParticipations(UUID playerUuid) {
        return storage.getPlayerParticipations(playerUuid);
    }

    /**
     * Obtiene la cantidad total de batallas registradas.
     */
    public CompletableFuture<Integer> getBattleCount() {
        return storage.getBattleCount();
    }

    /**
     * Obtiene la cantidad total de jugadores registrados.
     */
    public CompletableFuture<Integer> getPlayerCount() {
        return storage.getPlayerCount();
    }

    public LeaderboardStorage getStorage() {
        return storage;
    }

    @Override
    public void close() {
        storage.close();
    }
}
