package maurxp.betterdragon.platform.bossbar;

import java.util.logging.Logger;

/**
 * Fábrica para instanciar la implementación adecuada de {@link VanillaBossBarController}.
 * Realiza detección ambiental para Paper 26.1.2-74 y efectúa un fallback elegante
 * hacia {@link NoneVanillaBossBarController} si la plataforma es incompatible.
 *
 * @author maurxp
 */
public final class VanillaBossBarControllerFactory {

    private VanillaBossBarControllerFactory() {}

    /**
     * Crea e inicializa el mejor {@link VanillaBossBarController} disponible.
     *
     * @param logger logger del plugin
     * @return instancia de {@link NmsVanillaBossBarController} si es compatible, o {@link NoneVanillaBossBarController} en fallback
     */
    public static VanillaBossBarController create(Logger logger) {
        Logger log = logger != null ? logger : Logger.getLogger("BetterDragon");

        try {
            NmsVanillaBossBarController nmsController = new NmsVanillaBossBarController(log);
            if (nmsController.isAvailable()) {
                log.info("[BetterDragon] VanillaBossBarController inicializado: NMS (Paper 26.1.2-74)");
                return nmsController;
            }
        } catch (Throwable t) {
            log.warning("[BetterDragon] No se pudo inicializar NmsVanillaBossBarController: " + t.getMessage());
        }

        log.warning("[BetterDragon] VanillaBossBarController no disponible (entorno no compatible o internals ausentes); activando fallback NONE.");
        return new NoneVanillaBossBarController(log);
    }
}
