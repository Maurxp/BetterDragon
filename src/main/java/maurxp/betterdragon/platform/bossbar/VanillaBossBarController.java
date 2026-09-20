package maurxp.betterdragon.platform.bossbar;

import org.bukkit.World;

/**
 * Contrato de plataforma para el control y neutralización de la BossBar fantasma
 * generada por {@code EnderDragonFight} vanilla en mundos del End.
 * <p>
 * El Core de BetterDragon (combate, modelos, persistencia, recompensas) depende
 * <b>únicamente</b> de esta interfaz, garantizando un 0% de acoplamiento a NMS
 * o reflection sobre internals de Minecraft.
 * <p>
 * Nota arquitectónica (Fase 2.5.7-R1 / Fase 3.0):
 * El método {@code restoreVanillaBossBar(World)} fue eliminado permanentemente.
 * BetterDragon mantiene la soberanía exclusiva del ciclo de vida y no restaura
 * el combate vanilla en producción.
 *
 * @author maurxp
 */
public interface VanillaBossBarController {

    /**
     * Verifica si esta implementación del controlador está disponible y operativa
     * en el entorno de servidor actual.
     *
     * @return true si la implementación está activa y es funcional, false si está en modo fallback
     */
    boolean isAvailable();

    /**
     * Neutraliza y suprime de forma determinista la BossBar vanilla de {@code EnderDragonFight}
     * para el mundo del End especificado.
     * <p>
     * Si el mundo no es de tipo {@link org.bukkit.World.Environment#THE_END} o carece de batalla
     * de dragón, la implementación gestiona el caso de forma segura sin lanzar excepciones no controladas.
     *
     * @param world el mundo Bukkit a neutralizar
     * @return true si la supresión fue aplicada o el mundo ya estaba libre de batalla vanilla, false si ocurrió un error
     */
    boolean suppressVanillaBossBar(World world);

    /**
     * Comprueba si la BossBar vanilla en el mundo del End especificado se encuentra actualmente suprimida.
     *
     * @param world el mundo Bukkit a consultar
     * @return true si se verificó que la BossBar vanilla está neutralizada, false en caso contrario
     */
    boolean isSuppressed(World world);
}
