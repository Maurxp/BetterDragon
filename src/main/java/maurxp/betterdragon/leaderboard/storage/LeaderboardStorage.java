package maurxp.betterdragon.leaderboard.storage;

import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.leaderboard.model.LeaderboardBattleRecord;
import maurxp.betterdragon.leaderboard.model.LeaderboardParticipantRecord;
import maurxp.betterdragon.leaderboard.model.LeaderboardPlayerStats;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Contrato de persistencia y consulta para el subsistema de leaderboard de BetterDragon.
 * <p>
 * Principios:
 * <ul>
 *   <li><b>Asincronía Completa:</b> Todas las operaciones retornan {@link CompletableFuture}
 *       para garantizar que ninguna llamada JDBC bloquee el hilo principal de Bukkit.</li>
 *   <li><b>Identidad Inmutable:</b> Las consultas por jugador se fundamentan exclusivamente en {@link UUID}.</li>
 *   <li><b>Determinismo en Rankings:</b> Todas las consultas de ordenación aplican {@code player_uuid ASC}
 *       como criterio estricto de desempate.</li>
 * </ul>
 *
 * @author maurxp
 */
public interface LeaderboardStorage extends AutoCloseable {

    /**
     * Registra atómicamente una batalla, las participaciones asociadas y actualiza las estadísticas
     * acumuladas de los jugadores en una única transacción de base de datos.
     * <p>
     * Es estrictamente idempotente: si el {@code battle_id} ya fue registrado previamente,
     * la operación se descarta sin duplicar registros ni alterar estadísticas acumuladas.
     *
     * @param battle       registro de la batalla completada
     * @param participants lista de participaciones de jugadores en la batalla
     * @return {@code true} si la batalla fue registrada exitosamente; {@code false} si ya existía previamente (idempotente)
     */
    CompletableFuture<Boolean> recordBattle(LeaderboardBattleRecord battle, List<LeaderboardParticipantRecord> participants);

    /**
     * Obtiene las estadísticas acumuladas de un jugador por su UUID.
     *
     * @param playerUuid UUID del jugador
     * @return {@link Optional} con las estadísticas si el jugador tiene historial registrado, o vacío si no existe
     */
    CompletableFuture<Optional<LeaderboardPlayerStats>> getPlayerStats(UUID playerUuid);

    /**
     * Obtiene el ranking de jugadores ordenados por daño total infligido en orden descendente.
     *
     * @param limit cantidad máxima de resultados a retornar (debe ser > 0)
     * @return lista inmutable de estadísticas ordenadas por {@code total_damage DESC, player_uuid ASC}
     */
    CompletableFuture<List<LeaderboardPlayerStats>> getTopDamage(int limit);

    /**
     * Obtiene el ranking de jugadores ordenados por cantidad de victorias como Slayer (TOP_DAMAGE) en orden descendente.
     *
     * @param limit cantidad máxima de resultados a retornar (debe ser > 0)
     * @return lista inmutable de estadísticas ordenadas por {@code slayer_count DESC, player_uuid ASC}
     */
    CompletableFuture<List<LeaderboardPlayerStats>> getTopSlayers(int limit);

    /**
     * Obtiene el ranking de jugadores ordenados por cantidad de batallas participadas en orden descendente.
     *
     * @param limit cantidad máxima de resultados a retornar (debe ser > 0)
     * @return lista inmutable de estadísticas ordenadas por {@code battles_participated DESC, player_uuid ASC}
     */
    CompletableFuture<List<LeaderboardPlayerStats>> getTopParticipations(int limit);

    /**
     * Obtiene el registro histórico de una batalla por su ID.
     *
     * @param battleId ID de la batalla
     * @return {@link Optional} con el registro de la batalla o vacío si no existe
     */
    CompletableFuture<Optional<LeaderboardBattleRecord>> getBattle(BattleId battleId);

    /**
     * Obtiene los registros de participación individuales de una batalla específica.
     *
     * @param battleId ID de la batalla
     * @return lista inmutable de participaciones registradas para esa batalla
     */
    CompletableFuture<List<LeaderboardParticipantRecord>> getBattleParticipants(BattleId battleId);

    /**
     * Obtiene el historial de participaciones de un jugador específico en todas sus batallas.
     *
     * @param playerUuid UUID del jugador
     * @return lista inmutable de participaciones del jugador ordenadas por fecha ascendente
     */
    CompletableFuture<List<LeaderboardParticipantRecord>> getPlayerParticipations(UUID playerUuid);

    /**
     * Obtiene la cantidad total de batallas registradas en el historial.
     *
     * @return conteo de batallas
     */
    CompletableFuture<Integer> getBattleCount();

    /**
     * Obtiene la cantidad total de jugadores únicos registrados con estadísticas.
     *
     * @return conteo de jugadores
     */
    CompletableFuture<Integer> getPlayerCount();

    /**
     * Elimina todos los registros de tablas del leaderboard (utilizado principalmente en suites de test).
     *
     * @return futuro completado al terminar
     */
    CompletableFuture<Void> clear();

    @Override
    void close();
}
