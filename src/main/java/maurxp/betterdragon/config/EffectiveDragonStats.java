package maurxp.betterdragon.config;

import java.util.Objects;
import java.util.Optional;

/**
 * Representa los atributos y estadísticas efectivas calculadas de un dragón para una sesión de batalla.
 * <p>
 * Principios:
 * <ul>
 *   <li><b>Inmutabilidad Estricta:</b> Una vez calculadas, no pueden ser mutadas.</li>
 *   <li><b>Aislamiento Físico:</b> No almacena entidades Bukkit vivas.</li>
 *   <li><b>Trazabilidad Determinista:</b> Registra la cantidad de jugadores considerada y el multiplicador aplicado.</li>
 * </ul>
 *
 * @param definitionId     identificador del perfil o definición del dragón
 * @param maxHealth        salud máxima efectiva tras escalado (> 0.0)
 * @param movementSpeed    velocidad de movimiento opcional
 * @param followRange      rango de seguimiento de la IA opcional
 * @param attackDamage     daño base opcional
 * @param playerCountUsed  número de jugadores computados para el escalado (>= 0)
 * @param healthMultiplier multiplicador efectivo resultante aplicado sobre la salud base (>= 1.0)
 * @author maurxp
 */
public record EffectiveDragonStats(
        String definitionId,
        double maxHealth,
        Optional<Double> movementSpeed,
        Optional<Double> followRange,
        Optional<Double> attackDamage,
        int playerCountUsed,
        double healthMultiplier
) {

    public EffectiveDragonStats {
        Objects.requireNonNull(definitionId, "definitionId no puede ser nulo");
        if (definitionId.isBlank()) {
            throw new IllegalArgumentException("definitionId no puede estar vacío");
        }
        if (!Double.isFinite(maxHealth) || maxHealth <= 0.0) {
            throw new IllegalArgumentException("maxHealth efectiva debe ser finita y > 0.0. Recibido: " + maxHealth);
        }
        Objects.requireNonNull(movementSpeed, "movementSpeed no puede ser nulo");
        Objects.requireNonNull(followRange, "followRange no puede ser nulo");
        Objects.requireNonNull(attackDamage, "attackDamage no puede ser nulo");

        if (playerCountUsed < 0) {
            throw new IllegalArgumentException("playerCountUsed debe ser >= 0. Recibido: " + playerCountUsed);
        }
        if (!Double.isFinite(healthMultiplier) || healthMultiplier < 1.0) {
            throw new IllegalArgumentException("healthMultiplier debe ser finito y >= 1.0. Recibido: " + healthMultiplier);
        }
    }

    /**
     * Constructor de conveniencia con playerCount y healthMultiplier antes de los atributos opcionales.
     */
    public EffectiveDragonStats(
            String definitionId,
            double maxHealth,
            int playerCountUsed,
            double healthMultiplier,
            Optional<Double> movementSpeed,
            Optional<Double> followRange,
            Optional<Double> attackDamage
    ) {
        this(definitionId, maxHealth, movementSpeed, followRange, attackDamage, playerCountUsed, healthMultiplier);
    }

    /**
     * Construye estadísticas efectivas directas a partir de la definición sin escalado adicional (1 jugador).
     */
    public static EffectiveDragonStats from(DragonDefinition definition) {
        return from(definition, 1);
    }

    /**
     * Construye estadísticas efectivas calculadas para una definición y cantidad de jugadores.
     */
    public static EffectiveDragonStats from(DragonDefinition definition, int playerCount) {
        return DragonScalingCalculator.calculate(definition, playerCount);
    }
}
