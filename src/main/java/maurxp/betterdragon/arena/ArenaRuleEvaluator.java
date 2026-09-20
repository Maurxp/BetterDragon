package maurxp.betterdragon.arena;

import org.bukkit.Location;

import java.util.Objects;

/**
 * Evaluador de reglas geométricas y ambientales para una arena de BetterDragon.
 * <p>
 * Principios:
 * <ul>
 *   <li><b>Responsabilidad Cohesiva:</b> Evalúa las condiciones y reglas de arena sin ejecutar acciones destructivas
 *       en el mundo ni teleportaciones automáticas prematuras.</li>
 *   <li><b>Separación de Detección y Consecuencia:</b> Responde si una acción o posición cumple o viola las reglas
 *       configuradas de la arena.</li>
 *   <li><b>Cero Escaneo Global:</b> Solo evalúa ubicaciones puntuales en O(1), sin recorrer bloques ni entidades del servidor.</li>
 * </ul>
 *
 * @author maurxp
 */
public class ArenaRuleEvaluator {

    private final ArenaDefinition arena;

    public ArenaRuleEvaluator(ArenaDefinition arena) {
        this.arena = Objects.requireNonNull(arena, "La definición de arena no puede ser nula");
    }

    /**
     * Retorna la definición de arena asociada.
     *
     * @return definición de arena
     */
    public ArenaDefinition getArena() {
        return arena;
    }

    /**
     * Comprueba si la ubicación indicada pertenece al mundo y límites de la arena.
     *
     * @param location ubicación a comprobar
     * @return true si el mundo coincide y las coordenadas están dentro de los límites
     */
    public boolean isLocationInArena(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        if (!arena.worldName().equalsIgnoreCase(location.getWorld().getName())) {
            return false;
        }
        return arena.bounds().contains(location);
    }

    /**
     * Comprueba si una posición fuera de los límites viola la regla de perímetro activo.
     *
     * @param location ubicación evaluada
     * @return true si los límites están activos y la ubicación está fuera de la arena
     */
    public boolean isBoundaryViolated(Location location) {
        if (!arena.rules().isBoundaryEnabled()) {
            return false;
        }
        return !isLocationInArena(location);
    }

    /**
     * Comprueba si está permitida la presencia o colocación de agua en la ubicación indicada.
     * Si la ubicación no pertenece a la arena, la regla de BetterDragon no aplica (retorna true).
     *
     * @param location ubicación evaluada
     * @return true si el agua está permitida; false si está denegada por la regla de la arena
     */
    public boolean isWaterAllowed(Location location) {
        if (!isLocationInArena(location)) {
            return true;
        }
        return arena.rules().isWaterAllowed();
    }

    /**
     * Comprueba si la denegación de agua está activa en la ubicación indicada.
     *
     * @param location ubicación evaluada
     * @return true si la ubicación está dentro de la arena y la denegación de agua está activa
     */
    public boolean isWaterDenialActiveAt(Location location) {
        return isLocationInArena(location) && arena.rules().isWaterDenialEnabled();
    }

    /**
     * Comprueba si la regla de anti-túnel está activa para la arena.
     *
     * @return true si anti-túnel está habilitado
     */
    public boolean isAntiTunnelActive() {
        return arena.rules().isAntiTunnelEnabled();
    }

    /**
     * Evalúa si una coordenada dentro de la arena constituye una condición de túnel/trinchera no permitida.
     *
     * @param location ubicación evaluada
     * @param isEnclosed condición geométrica de encierro (ej. túnel bajo tierra)
     * @return true si anti-túnel está activo, la ubicación está en la arena y se detecta encierro
     */
    public boolean isAntiTunnelViolation(Location location, boolean isEnclosed) {
        if (!arena.rules().isAntiTunnelEnabled()) {
            return false;
        }
        return isLocationInArena(location) && isEnclosed;
    }
}
