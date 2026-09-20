package maurxp.betterdragon.platform.bossbar;

import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

import java.util.logging.Logger;

/**
 * Listener de eventos de mundo para garantizar la neutralización de la BossBar vanilla
 * en mundos del End cargados de forma diferida o dinámica durante la ejecución del servidor.
 *
 * @author maurxp
 */
public class BossBarWorldListener implements Listener {

    private final VanillaBossBarController controller;
    private final Logger logger;
    private final boolean suppressEnabled;

    public BossBarWorldListener(VanillaBossBarController controller, Logger logger, boolean suppressEnabled) {
        this.controller = controller;
        this.logger = logger;
        this.suppressEnabled = suppressEnabled;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldLoad(WorldLoadEvent event) {
        if (!suppressEnabled) {
            return;
        }

        World world = event.getWorld();
        if (world.getEnvironment() == World.Environment.THE_END) {
            logger.info("[BetterDragon] Mundo del End detectado tras WorldLoadEvent: " + world.getName() + ". Aplicando supresión vanilla...");
            boolean result = controller.suppressVanillaBossBar(world);
            if (result) {
                logger.fine("[BetterDragon] Supresión de BossBar vanilla aplicada exitosamente a " + world.getName());
            } else {
                logger.warning("[BetterDragon] No se pudo suprimir la BossBar vanilla en " + world.getName());
            }
        }
    }
}
