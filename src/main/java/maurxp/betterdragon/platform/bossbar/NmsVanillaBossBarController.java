package maurxp.betterdragon.platform.bossbar;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;

import java.lang.reflect.Field;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementación de {@link VanillaBossBarController} basada en NMS (Mojang Mappings).
 * Estrictamente delimitada y validada empíricamente para Paper 26.1.2-74 (Java 25).
 * <p>
 * Este adaptador aísla todos los internals de CraftBukkit y Mojang NMS detrás de la
 * interfaz limpia {@link VanillaBossBarController}, asegurando que el Core de BetterDragon
 * permanezca 100% desacoplado de internals del servidor.
 * <p>
 * Llamadas NMS Directas:
 * <ul>
 *   <li>{@link CraftWorld#getHandle()} -> {@link ServerLevel}</li>
 *   <li>{@link ServerLevel#getDragonFight()} -> {@link EnderDragonFight}</li>
 *   <li>{@code EnderDragonFight.dragonEvent.setVisible(false)}</li>
 *   <li>{@code EnderDragonFight.dragonUUID = null}</li>
 *   <li>{@code EnderDragonFight.setDirty()}</li>
 * </ul>
 * <p>
 * Reflection Encapsulada (Paper 26.1.2-74 Mojang Mappings):
 * <ul>
 *   <li>{@code EnderDragonFight.dragonKilled} (boolean privado) -> asignado a {@code true}</li>
 *   <li>{@code EnderDragonFight.needsStateScanning} (boolean privado) -> asignado a {@code false}</li>
 * </ul>
 *
 * @author maurxp
 */
public class NmsVanillaBossBarController implements VanillaBossBarController {

    private final Logger logger;
    private final boolean available;

    private Field dragonKilledField;
    private Field needsStateScanningField;

    public NmsVanillaBossBarController(Logger logger) {
        this.logger = logger != null ? logger : Logger.getLogger("BetterDragon");

        boolean initSuccess = false;
        try {
            Class<?> fightClass = EnderDragonFight.class;

            Field dkField = fightClass.getDeclaredField("dragonKilled");
            dkField.setAccessible(true);
            this.dragonKilledField = dkField;

            Field scanField = fightClass.getDeclaredField("needsStateScanning");
            scanField.setAccessible(true);
            this.needsStateScanningField = scanField;

            initSuccess = true;
            this.logger.info("[BetterDragon] NmsVanillaBossBarController inicializado exitosamente para Paper 26.1.2-74.");
        } catch (NoSuchFieldException | SecurityException e) {
            this.logger.log(Level.SEVERE, "[BetterDragon] Error al resolver campos internos de EnderDragonFight (¿build incompatible?): " + e.getMessage(), e);
            this.dragonKilledField = null;
            this.needsStateScanningField = null;
        } catch (Throwable t) {
            this.logger.log(Level.SEVERE, "[BetterDragon] Error inesperado durante la inicialización de NmsVanillaBossBarController: " + t.getMessage(), t);
            this.dragonKilledField = null;
            this.needsStateScanningField = null;
        }

        this.available = initSuccess;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public boolean suppressVanillaBossBar(World world) {
        if (!available || world == null) {
            return false;
        }

        if (world.getEnvironment() != World.Environment.THE_END) {
            return false;
        }

        try {
            if (!(world instanceof CraftWorld craftWorld)) {
                logger.warning("[BetterDragon] El mundo '" + world.getName() + "' no es una instancia de CraftWorld.");
                return false;
            }

            ServerLevel serverLevel = craftWorld.getHandle();
            if (serverLevel == null) {
                return false;
            }

            EnderDragonFight fight = serverLevel.getDragonFight();
            if (fight == null) {
                // No existe batalla vanilla en este mundo, nada que suprimir
                return true;
            }

            // 1. Direct NMS: Limpiar el UUID de dragón vanilla para desacoplar cualquier dragón independiente
            fight.dragonUUID = null;

            // 2. Direct NMS: Ocultar la barra de ServerBossEvent si existe
            if (fight.dragonEvent != null) {
                fight.dragonEvent.setVisible(false);
            }

            // 3. Encapsulated Reflection: Establecer flags internas
            dragonKilledField.setBoolean(fight, true);
            needsStateScanningField.setBoolean(fight, false);

            // 4. Marcar fight dirty para persistir el estado en SavedData
            fight.setDirty();

            logger.info("[BetterDragon] BossBar vanilla suprimida exitosamente en el mundo: " + world.getName());
            return true;
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "[BetterDragon] Excepción al suprimir BossBar vanilla en mundo '" + world.getName() + "': " + t.getMessage(), t);
            return false;
        }
    }

    @Override
    public boolean isSuppressed(World world) {
        if (!available || world == null || world.getEnvironment() != World.Environment.THE_END) {
            return false;
        }

        try {
            if (!(world instanceof CraftWorld craftWorld)) {
                return false;
            }

            ServerLevel serverLevel = craftWorld.getHandle();
            if (serverLevel == null) {
                return false;
            }

            EnderDragonFight fight = serverLevel.getDragonFight();
            if (fight == null) {
                return true;
            }

            boolean killed = dragonKilledField.getBoolean(fight);
            boolean scanning = needsStateScanningField.getBoolean(fight);
            boolean barInvisible = fight.dragonEvent == null || !fight.dragonEvent.isVisible();
            boolean uuidNull = fight.dragonUUID == null;

            return killed && !scanning && barInvisible && uuidNull;
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[BetterDragon] Error consultando isSuppressed para el mundo '" + world.getName() + "': " + t.getMessage(), t);
            return false;
        }
    }
}
