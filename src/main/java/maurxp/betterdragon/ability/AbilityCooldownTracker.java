package maurxp.betterdragon.ability;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Rastreador de recargas (cooldowns) para las habilidades de una sesión de batalla.
 * <p>
 * Principios:
 * <ul>
 * <li><b>Ticks Lógicos:</b> Opera sobre los ticks del servidor en lugar de tiempo de sistema (evita problemas de lag/desincronización).</li>
 * <li><b>Aislamiento por Sesión:</b> Una instancia dedicada por cada sesión de batalla.</li>
 * <li><b>Reset en Transición de Fase:</b> Al cambiar de fase, los cooldowns de la fase anterior se descartan.</li>
 * </ul>
 *
 * @author maurxp
 */
public class AbilityCooldownTracker {

    private final Map<String, Long> nextAvailableTicks = new HashMap<>();

    /**
     * Verifica si una habilidad se encuentra disponible para ejecutarse en el tick dado.
     *
     * @param abilityId   identificador de la habilidad
     * @param currentTick tick lógico actual
     * @return true si la habilidad no tiene recarga activa o su cooldown ya expiró
     */
    public boolean isReady(String abilityId, long currentTick) {
        Objects.requireNonNull(abilityId, "abilityId no puede ser nulo");
        Long nextTick = nextAvailableTicks.get(abilityId);
        return nextTick == null || currentTick >= nextTick;
    }

    /**
     * Establece el tick en que la habilidad volverá a estar disponible.
     *
     * @param abilityId     identificador de la habilidad
     * @param currentTick   tick lógico actual
     * @param cooldownTicks cantidad de ticks de recarga
     */
    public void setCooldown(String abilityId, long currentTick, long cooldownTicks) {
        Objects.requireNonNull(abilityId, "abilityId no puede ser nulo");
        if (cooldownTicks <= 0) {
            nextAvailableTicks.remove(abilityId);
        } else {
            nextAvailableTicks.put(abilityId, currentTick + cooldownTicks);
        }
    }

    /**
     * Obtiene el próximo tick disponible para una habilidad.
     */
    public long getNextAvailableTick(String abilityId) {
        return nextAvailableTicks.getOrDefault(abilityId, 0L);
    }

    /**
     * Limpia todas las recargas registradas (utilizado al transicionar de fase).
     */
    public void reset() {
        nextAvailableTicks.clear();
    }
}
