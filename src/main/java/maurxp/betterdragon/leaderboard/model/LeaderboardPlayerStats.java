package maurxp.betterdragon.leaderboard.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Representación inmutable de las estadísticas acumuladas históricas de un jugador en el leaderboard.
 * <p>
 * Principios:
 * <ul>
 *   <li><b>Identidad Inmutable:</b> La identidad permanente es exclusivamente el {@link UUID}.</li>
 *   <li><b>Sin Objetos Bukkit Vivos:</b> Thread-safe por diseño, seguro para transporte entre hilos.</li>
 *   <li><b>Cálculo Dinámico de Promedios:</b> {@code average_damage} no se almacena en disco; se calcula bajo demanda.</li>
 * </ul>
 *
 * @param playerUuid           UUID único e inmutable del jugador
 * @param lastKnownName        nombre más reciente capturado en una batalla
 * @param battlesParticipated  número total de batallas completadas en las que participó
 * @param totalDamage          suma total acumulada de daño infligido a dragones
 * @param highestDamage        máximo daño alcanzado en una sola batalla
 * @param slayerCount          número total de victorias donde fue TOP_DAMAGE (Slayer)
 * @param firstParticipationAt instante de la primera batalla registrada
 * @param lastParticipationAt  instante de la batalla más reciente registrada
 * @author maurxp
 */
public record LeaderboardPlayerStats(
        UUID playerUuid,
        String lastKnownName,
        int battlesParticipated,
        double totalDamage,
        double highestDamage,
        int slayerCount,
        Instant firstParticipationAt,
        Instant lastParticipationAt
) {

    public LeaderboardPlayerStats {
        Objects.requireNonNull(playerUuid, "playerUuid no puede ser nulo");
        Objects.requireNonNull(lastKnownName, "lastKnownName no puede ser nulo");
        Objects.requireNonNull(firstParticipationAt, "firstParticipationAt no puede ser nulo");
        Objects.requireNonNull(lastParticipationAt, "lastParticipationAt no puede ser nulo");

        if (battlesParticipated < 0) {
            throw new IllegalArgumentException("battlesParticipated no puede ser negativo: " + battlesParticipated);
        }
        if (Double.isNaN(totalDamage) || Double.isInfinite(totalDamage) || totalDamage < 0) {
            throw new IllegalArgumentException("totalDamage debe ser finito y no negativo: " + totalDamage);
        }
        if (Double.isNaN(highestDamage) || Double.isInfinite(highestDamage) || highestDamage < 0) {
            throw new IllegalArgumentException("highestDamage debe ser finito y no negativo: " + highestDamage);
        }
        if (slayerCount < 0) {
            throw new IllegalArgumentException("slayerCount no puede ser negativo: " + slayerCount);
        }
        if (slayerCount > battlesParticipated) {
            throw new IllegalArgumentException("slayerCount (" + slayerCount + ") no puede exceder battlesParticipated (" + battlesParticipated + ")");
        }
    }

    /**
     * Calcula dinámicamente el daño promedio por batalla.
     *
     * @return daño promedio infligido o 0.0 si no ha participado en batallas
     */
    public double getAverageDamage() {
        if (battlesParticipated <= 0) {
            return 0.0;
        }
        return totalDamage / (double) battlesParticipated;
    }
}
