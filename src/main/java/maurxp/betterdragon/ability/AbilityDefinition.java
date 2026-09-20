package maurxp.betterdragon.ability;

import java.util.Map;
import java.util.Objects;

/**
 * Definición inmutable y tipada de una habilidad de combate del dragón.
 * <p>
 * Principios:
 * <ul>
 * <li><b>Sin Scripting Arbitrario:</b> Define configuraciones declarativas estrictas.</li>
 * <li><b>Cooldowns Lógicos:</b> El tiempo de recarga se especifica en ticks lógicos del servidor (no timestamps de sistema).</li>
 * <li><b>Inmutabilidad:</b> Sus propiedades son inmutables y congeladas durante la creación del snapshot.</li>
 * </ul>
 *
 * @param id             identificador único de la habilidad (ej. "roar_knockback")
 * @param trigger        disparador de ejecución (ON_PHASE_ENTER, PERIODIC)
 * @param cooldownTicks  duración de recarga en ticks lógicos del servidor (>= 0)
 * @param targetSelector tipo de selector de objetivos
 * @param effectOrigin   punto de origen espacial del efecto
 * @param effectType     tipo de efecto a desplegar
 * @param properties     mapa inmutable de parámetros específicos del efecto (daño, velocidad, partículas, etc.)
 * @author maurxp
 */
public record AbilityDefinition(
        String id,
        AbilityTrigger trigger,
        long cooldownTicks,
        TargetSelectorType targetSelector,
        EffectOriginType effectOrigin,
        AbilityEffectType effectType,
        Map<String, Object> properties) {

    public AbilityDefinition {
        Objects.requireNonNull(id, "El id de la habilidad no puede ser nulo");
        if (id.isBlank()) {
            throw new IllegalArgumentException("El id de la habilidad no puede estar vacío");
        }
        Objects.requireNonNull(trigger, "El trigger no puede ser nulo");
        if (cooldownTicks < 0) {
            throw new IllegalArgumentException("El cooldown en ticks debe ser >= 0: " + cooldownTicks);
        }
        Objects.requireNonNull(targetSelector, "El targetSelector no puede ser nulo");
        Objects.requireNonNull(effectOrigin, "El effectOrigin no puede ser nulo");
        Objects.requireNonNull(effectType, "El effectType no puede ser nulo");
        properties = properties != null ? Map.copyOf(properties) : Map.of();
    }

    /**
     * Constructor de conveniencia con propiedades vacías.
     */
    public AbilityDefinition(
            String id,
            AbilityTrigger trigger,
            long cooldownTicks,
            TargetSelectorType targetSelector,
            EffectOriginType effectOrigin,
            AbilityEffectType effectType) {
        this(id, trigger, cooldownTicks, targetSelector, effectOrigin, effectType, Map.of());
    }

    /**
     * Obtiene una propiedad de tipo Double de forma segura.
     */
    public double getDoubleProperty(String key, double defaultValue) {
        Object val = properties.get(key);
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        return defaultValue;
    }

    /**
     * Obtiene una propiedad de tipo Integer de forma segura.
     */
    public int getIntProperty(String key, int defaultValue) {
        Object val = properties.get(key);
        if (val instanceof Number n) {
            return n.intValue();
        }
        return defaultValue;
    }

    /**
     * Obtiene una propiedad de tipo String de forma segura.
     */
    public String getStringProperty(String key, String defaultValue) {
        Object val = properties.get(key);
        return val != null ? val.toString() : defaultValue;
    }
}
