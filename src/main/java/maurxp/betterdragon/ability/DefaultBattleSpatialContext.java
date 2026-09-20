package maurxp.betterdragon.ability;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

/**
 * Implementación estándar y canónica de {@link BattleSpatialContext}.
 * <p>
 * Principios:
 * <ul>
 * <li><b>Separación Canónica:</b> El podium se sitúa a nivel del suelo/portal del End (Y=65.0),
 * mientras que el centro de la arena de combate se sitúa a altitud de vuelo/combate (Y=100.0).</li>
 * <li><b>Cero Coordenadas Confundidas:</b> Evita la equivalencia forzada entre podium y arena.</li>
 * <li><b>Soporte de Personalización:</b> Permite inyectar ubicaciones específicas para pruebas
 * o configuraciones particulares.</li>
 * </ul>
 *
 * @author maurxp
 */
public class DefaultBattleSpatialContext implements BattleSpatialContext {

    public static final double DEFAULT_PODIUM_X = 0.0;
    public static final double DEFAULT_PODIUM_Y = 65.0;
    public static final double DEFAULT_PODIUM_Z = 0.0;

    public static final double DEFAULT_ARENA_X = 0.0;
    public static final double DEFAULT_ARENA_Y = 100.0;
    public static final double DEFAULT_ARENA_Z = 0.0;

    private final Location customPodiumCenter;
    private final Location customArenaCenter;

    public DefaultBattleSpatialContext() {
        this(null, null);
    }

    public DefaultBattleSpatialContext(Location customPodiumCenter, Location customArenaCenter) {
        this.customPodiumCenter = customPodiumCenter != null ? customPodiumCenter.clone() : null;
        this.customArenaCenter = customArenaCenter != null ? customArenaCenter.clone() : null;
    }

    @Override
    public Location getPodiumCenter(World world) {
        Objects.requireNonNull(world, "World no puede ser nulo");
        if (customPodiumCenter != null && customPodiumCenter.getWorld() != null
                && customPodiumCenter.getWorld().equals(world)) {
            return customPodiumCenter.clone();
        }
        return new Location(world, DEFAULT_PODIUM_X, DEFAULT_PODIUM_Y, DEFAULT_PODIUM_Z);
    }

    @Override
    public Location getArenaCenter(World world) {
        Objects.requireNonNull(world, "World no puede ser nulo");
        if (customArenaCenter != null && customArenaCenter.getWorld() != null
                && customArenaCenter.getWorld().equals(world)) {
            return customArenaCenter.clone();
        }
        return new Location(world, DEFAULT_ARENA_X, DEFAULT_ARENA_Y, DEFAULT_ARENA_Z);
    }
}
