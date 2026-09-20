package maurxp.betterdragon.ability;

import maurxp.betterdragon.arena.ArenaBounds;
import maurxp.betterdragon.arena.ArenaRuleSet;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Proveedor de ubicaciones espaciales contextuales para una batalla (podium, arena).
 * <p>
 * Principios:
 * <ul>
 * <li><b>Separación Semántica Estricta:</b> Desacopla la ubicación física del podium/portal
 * de salida del End (nivel de pedestal) del centro espacial de la arena de combate.</li>
 * <li><b>Contexto Espacial Real (Fase 3.6):</b> Provee acceso a los límites reales ({@link ArenaBounds})
 * y al conjunto de reglas ({@link ArenaRuleSet}) de la arena asociada a la batalla.</li>
 * </ul>
 *
 * @author maurxp
 */
public interface BattleSpatialContext {

    /**
     * Obtiene el centro lógico del podium/portal del End para el mundo indicado.
     *
     * @param world mundo del contexto de batalla
     * @return ubicación del centro del podium
     */
    Location getPodiumCenter(World world);

    /**
     * Obtiene el centro lógico de la arena de combate para el mundo indicado.
     *
     * @param world mundo del contexto de batalla
     * @return ubicación del centro de la arena
     */
    Location getArenaCenter(World world);

    /**
     * Retorna los límites geométricos de la arena, o null si no están definidos.
     *
     * @return límites de la arena o null
     */
    default ArenaBounds getBounds() {
        return null;
    }

    /**
     * Retorna el conjunto de reglas de la arena.
     *
     * @return reglas de la arena
     */
    default ArenaRuleSet getRules() {
        return ArenaRuleSet.defaults();
    }

    /**
     * Comprueba si una ubicación se encuentra dentro de los límites de la arena.
     *
     * @param location ubicación evaluada
     * @return true si los límites existen y contienen la ubicación
     */
    default boolean isInArena(Location location) {
        ArenaBounds bounds = getBounds();
        return bounds != null && bounds.contains(location);
    }
}
