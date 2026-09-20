package maurxp.betterdragon.platform.bossbar;

import org.bukkit.World;

import java.util.logging.Logger;

/**
 * Implementación no operativa (Fallback / Null Object Pattern) de {@link VanillaBossBarController}.
 * Se activa cuando el entorno no es compatible con Paper 26.1.2-74 o cuando los internals NMS
 * no se encuentran disponibles.
 * <p>
 * Permite que el servidor y el plugin continúen funcionando sin colapsar, registrando advertencias claras.
 *
 * @author maurxp
 */
public class NoneVanillaBossBarController implements VanillaBossBarController {

    private final Logger logger;

    public NoneVanillaBossBarController(Logger logger) {
        this.logger = logger != null ? logger : Logger.getLogger("BetterDragon");
        this.logger.warning("[BetterDragon] NoneVanillaBossBarController activo: La supresión de BossBar vanilla está desactivada (modo fallback).");
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public boolean suppressVanillaBossBar(World world) {
        if (world != null && world.getEnvironment() == World.Environment.THE_END) {
            logger.fine("[BetterDragon] [Fallback] suppressVanillaBossBar invocado pero no soportado en este entorno para el mundo: " + world.getName());
        }
        return false;
    }

    @Override
    public boolean isSuppressed(World world) {
        return false;
    }
}
