package maurxp.betterdragon.ability;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Proveedor de ubicaciones espaciales contextuales para una batalla (podium, arena).
 * <p>
 * Principios:
 * <ul>
 * <li><b>Separación Semántica Estricta:</b> Desacopla la ubicación física del podium/portal
 * de salida del End (nivel de pedestal) del centro espacial de la arena de combate.</li>
 * <li><b>Extensibilidad para Fase 3.6:</b> Permite que la fase posterior de Arena inyecte
 * coordenadas dinámicas o configuradas sin alterar la lógica de resolución ni introducir
 * dependencias prematuras.</li>
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
}
