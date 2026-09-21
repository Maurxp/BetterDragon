package maurxp.betterdragon.application.leaderboard;

import maurxp.betterdragon.leaderboard.model.LeaderboardPlayerStats;
import maurxp.betterdragon.leaderboard.service.LeaderboardService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Servicio de aplicación para la consulta y presentación de estadísticas del Leaderboard.
 * <p>
 * Sirve como punto de acceso único tanto para comandos CLI como para futuras GUIs.
 * No bloquea el hilo principal y delega la persistencia relacional en {@link LeaderboardService}.
 *
 * @author maurxp
 */
public class LeaderboardApplicationService {

    private final LeaderboardService leaderboardService;

    public LeaderboardApplicationService(LeaderboardService leaderboardService) {
        this.leaderboardService = Objects.requireNonNull(leaderboardService, "leaderboardService no puede ser nulo");
    }

    /**
     * Consulta el ranking global por daño total acumulado.
     *
     * @param limit cantidad máxima de registros a retornar
     * @return futuro con la lista ordenada de estadísticas
     */
    public CompletableFuture<List<LeaderboardPlayerStats>> getTopDamage(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return leaderboardService.getTopDamage(safeLimit);
    }

    /**
     * Consulta el ranking global por cantidad de victorias como Slayer.
     *
     * @param limit cantidad máxima de registros a retornar
     * @return futuro con la lista ordenada de estadísticas
     */
    public CompletableFuture<List<LeaderboardPlayerStats>> getTopSlayers(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return leaderboardService.getTopSlayers(safeLimit);
    }

    /**
     * Consulta el ranking global por cantidad de batallas participadas.
     *
     * @param limit cantidad máxima de registros a retornar
     * @return futuro con la lista ordenada de estadísticas
     */
    public CompletableFuture<List<LeaderboardPlayerStats>> getTopParticipations(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return leaderboardService.getTopParticipations(safeLimit);
    }

    /**
     * Consulta las estadísticas acumuladas de un jugador por su identificador UUID.
     *
     * @param playerUuid UUID del jugador
     * @return futuro con las estadísticas si existen
     */
    public CompletableFuture<Optional<LeaderboardPlayerStats>> getPlayerStats(UUID playerUuid) {
        if (playerUuid == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return leaderboardService.getPlayerStats(playerUuid);
    }

    /**
     * Resuelve un jugador por nombre (online u offline) o UUID en formato String y consulta sus estadísticas.
     *
     * @param query nombre del jugador o representación de UUID
     * @return futuro con las estadísticas del jugador si fue encontrado
     */
    public CompletableFuture<Optional<LeaderboardPlayerStats>> getPlayerStatsByQuery(String query) {
        if (query == null || query.isBlank()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        String trimmed = query.trim();

        // 1. Intentar parsear como UUID directo
        try {
            UUID uuid = UUID.fromString(trimmed);
            return getPlayerStats(uuid);
        } catch (IllegalArgumentException ignored) {
        }

        // 2. Intentar resolver por jugador online
        Player onlinePlayer = Bukkit.getPlayerExact(trimmed);
        if (onlinePlayer != null) {
            return getPlayerStats(onlinePlayer.getUniqueId());
        }

        // 3. Intentar resolver por jugador conocido offline (sin dependencias externas)
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayerIfCached(trimmed);
        if (offlinePlayer != null) {
            return getPlayerStats(offlinePlayer.getUniqueId());
        }

        // Si no está en caché o no existe, retornar vacío
        return CompletableFuture.completedFuture(Optional.empty());
    }

    /**
     * Conteo total de batallas históricas registradas.
     */
    public CompletableFuture<Integer> getTotalBattles() {
        return leaderboardService.getBattleCount();
    }

    /**
     * Conteo total de jugadores únicos registrados.
     */
    public CompletableFuture<Integer> getTotalPlayers() {
        return leaderboardService.getPlayerCount();
    }
}
