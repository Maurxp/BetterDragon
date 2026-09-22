package maurxp.betterdragon.config;

import java.util.Objects;

/**
 * Calculador puro y determinista de estadísticas efectivas y escalado por jugadores.
 * <p>
 * Regla de Determinismo:
 * Para la misma {@link DragonDefinition} y el mismo número de jugadores, la salida es idéntica
 * sin depender de reloj, TPS, estado global ni aleatoriedad.
 *
 * @author maurxp
 */
public final class DragonScalingCalculator {

    private DragonScalingCalculator() {
        // Pure utility class
    }

    /**
     * Calcula las estadísticas efectivas del dragón para la cantidad de jugadores dada.
     *
     * @param definition  definición de configuración del dragón
     * @param playerCount cantidad observada de jugadores presentes en la arena (o 0/1 si no hay presentes)
     * @return estadísticas efectivas inmutables resultantes
     */
    public static EffectiveDragonStats calculate(DragonDefinition definition, int playerCount) {
        Objects.requireNonNull(definition, "La definición del dragón no puede ser nula");

        DragonAttributes attrs = definition.attributes();
        DragonScalingDefinition scaling = definition.scaling();

        double multiplier = 1.0;
        int safePlayerCount = Math.max(0, playerCount);

        if (scaling.enabled() && scaling.mode() == ScalingMode.LINEAR) {
            int n = Math.max(1, safePlayerCount);
            double rawMultiplier = 1.0 + (n - 1) * scaling.healthPerPlayer();
            multiplier = Math.min(scaling.maxHealthMultiplier(), Math.max(1.0, rawMultiplier));
        }

        double effectiveHealth = attrs.maxHealth() * multiplier;

        return new EffectiveDragonStats(
                definition.id(),
                effectiveHealth,
                attrs.movementSpeed(),
                attrs.followRange(),
                attrs.attackDamage(),
                safePlayerCount,
                multiplier
        );
    }
}
