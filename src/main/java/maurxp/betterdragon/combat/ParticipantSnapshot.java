package maurxp.betterdragon.combat;

import java.util.Objects;
import java.util.UUID;

/**
 * Representación inmutable del estado de participación de un jugador en una batalla.
 * <p>
 * Principios:
 * <ul>
 *   <li><b>Inmutabilidad Completa:</b> Seguro para consumo externo, cálculo de recompensas,
 *       leaderboards o serialización sin riesgo de mutación concurrente.</li>
 *   <li><b>Sin Referencias Bukkit Vivas:</b> No contiene {@code Player}, {@code Entity} ni
 *       {@code Location}; la identidad se define exclusivamente por {@link UUID}.</li>
 *   <li><b>Preservación Histórica:</b> {@code historicalName} contiene el nombre original al entrar
 *       en combate, mientras que {@code lastKnownName} refleja el nombre más reciente conocido.</li>
 * </ul>
 *
 * @param playerId         UUID inmutable del jugador
 * @param historicalName   nombre del jugador en su primer impacto válido registrado
 * @param lastKnownName    nombre del jugador en su interacción de combate más reciente
 * @param totalDamage      daño total acumulado contra el BetterDragon
 * @param firstHitSequence secuencia monotónica del primer impacto registrado
 * @param lastHitSequence  secuencia monotónica del último impacto registrado
 * @param lastActivityTick tick del servidor en el momento del último impacto
 * @author maurxp
 */
public record ParticipantSnapshot(
        UUID playerId,
        String historicalName,
        String lastKnownName,
        double totalDamage,
        long firstHitSequence,
        long lastHitSequence,
        long lastActivityTick
) {
    public ParticipantSnapshot {
        Objects.requireNonNull(playerId, "playerId no puede ser nulo");
        Objects.requireNonNull(historicalName, "historicalName no puede ser nulo");
        Objects.requireNonNull(lastKnownName, "lastKnownName no puede ser nulo");
        if (Double.isNaN(totalDamage) || Double.isInfinite(totalDamage) || totalDamage < 0) {
            throw new IllegalArgumentException("totalDamage debe ser un número finito no negativo. Valor: " + totalDamage);
        }
        if (firstHitSequence < 0 || lastHitSequence < 0) {
            throw new IllegalArgumentException("Las secuencias deben ser no negativas. first: " + firstHitSequence + ", last: " + lastHitSequence);
        }
        if (firstHitSequence > lastHitSequence) {
            throw new IllegalArgumentException("firstHitSequence (" + firstHitSequence + ") no puede ser mayor que lastHitSequence (" + lastHitSequence + ")");
        }
    }
}
