package maurxp.betterdragon.battle;

import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.battle.model.DragonIdentity;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;

import java.util.Objects;

/**
 * Encargado de la creación física y spawn del {@link EnderDragon} administrado mediante Paper API.
 * <p>
 * Principios:
 * <ul>
 *   <li><b>0% NMS y Cero Dependencia de DragonBattle:</b> No utiliza {@code DragonBattle.spawnNewDragon()}
 *       ni internals de {@code EndDragonFight}.</li>
 *   <li><b>Spawn Consumer Síncrono:</b> El etiquetado PDC y configuración básica se aplican
 *       síncronamente en el momento exacto de creación antes de que la entidad sea entregada al mundo.</li>
 *   <li><b>Restricción de Dimensión:</b> Valida que el mundo corresponda a una dimensión {@code THE_END}.</li>
 * </ul>
 *
 * @author maurxp
 */
public class DragonSpawner {

    public static final double DEFAULT_SPAWN_X = 0.5;
    public static final double DEFAULT_SPAWN_Y = 128.0;
    public static final double DEFAULT_SPAWN_Z = 0.5;

    /**
     * Genera un nuevo EnderDragon en el mundo y ubicación especificados, aplicando su firma PDC.
     *
     * @param world         mundo del End donde spawnear
     * @param location      coordenadas de spawn (si es nulo, usa el centro por defecto 0.5, 128, 0.5)
     * @param battleId      identificador único de la batalla
     * @param definitionId  perfil de definición del dragón
     * @return entidad EnderDragon recién generada y etiquetada
     * @throws IllegalArgumentException si el mundo no es de tipo THE_END
     */
    public EnderDragon spawnDragon(World world, Location location, BattleId battleId, String definitionId) {
        Objects.requireNonNull(world, "El mundo de spawn no puede ser nulo");
        Objects.requireNonNull(battleId, "El battleId no puede ser nulo");

        if (world.getEnvironment() != World.Environment.THE_END) {
            throw new IllegalArgumentException("BetterDragon solo puede spawnear en mundos con ambiente THE_END. Ambiente actual: "
                    + world.getEnvironment() + " en " + world.getName());
        }

        Location spawnLocation = location != null ? location.clone() : new Location(world, DEFAULT_SPAWN_X, DEFAULT_SPAWN_Y, DEFAULT_SPAWN_Z);

        if (!spawnLocation.getChunk().isLoaded()) {
            spawnLocation.getChunk().load();
        }

        String defId = (definitionId != null && !definitionId.isBlank()) ? definitionId : "default";

        // Spawn con Consumer síncrono para garantizar que la entidad porta el PDC antes de ser añadida al mundo
        return world.spawn(spawnLocation, EnderDragon.class, dragon -> {
            // 1. Establecer fase inicial de vuelo en círculo
            dragon.setPhase(EnderDragon.Phase.CIRCLING);

            // 2. Aplicar identidad PDC inmediata
            DragonIdentity identity = DragonIdentity.of(dragon.getUniqueId(), battleId, defId);
            DragonPdcHandler.applyIdentity(dragon, identity);
        });
    }

    /**
     * Sobrecarga para spawn con ubicación predeterminada en el centro del End.
     *
     * @param world        mundo del End
     * @param battleId     identificador de batalla
     * @param definitionId perfil del dragón
     * @return EnderDragon spawneado
     */
    public EnderDragon spawnDragon(World world, BattleId battleId, String definitionId) {
        return spawnDragon(world, null, battleId, definitionId);
    }
}
