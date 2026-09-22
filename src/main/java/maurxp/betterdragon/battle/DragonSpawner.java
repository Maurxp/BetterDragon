package maurxp.betterdragon.battle;

import maurxp.betterdragon.arena.ArenaDefinition;
import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.battle.model.DragonIdentity;
import maurxp.betterdragon.config.EffectiveDragonStats;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EnderDragon;

import java.util.Objects;

/**
 * Encargado de la creación física y spawn del {@link EnderDragon} administrado mediante Paper API.
 * <p>
 * Principios:
 * <ul>
 *   <li><b>0% NMS y Cero Dependencia de DragonBattle:</b> No utiliza {@code DragonBattle.spawnNewDragon()}
 *       ni internals de {@code EndDragonFight}.</li>
 *   <li><b>ArenaDefinition como Fuente de Verdad:</b> Coordenadas de spawn y podio son resueltas
 *       exclusivamente desde la arena configurada, eliminando coordenadas mágicas de fallback.</li>
 *   <li><b>Aplicación de Atributos Nativos:</b> Configura vida base/efectiva, velocidad, rango y daño
 *       en el hilo principal durante el spawn sin usar Attribute.SCALE.</li>
 *   <li><b>Spawn Consumer Síncrono:</b> El etiquetado PDC y configuración básica se aplican
 *       síncronamente en el momento exacto de creación antes de que la entidad sea entregada al mundo.</li>
 * </ul>
 *
 * @author maurxp
 */
public class DragonSpawner {

    /**
     * Genera un nuevo EnderDragon utilizando la ArenaDefinition y estadísticas efectivas calculadas.
     *
     * @param world                 mundo del End donde spawnear
     * @param arena                 definición de arena como fuente de verdad espacial
     * @param effectiveStats        estadísticas escaladas e inmutables del dragón
     * @param battleId              identificador de la batalla
     * @param spawnLocationOverride ubicación de spawn opcional (si es null, usa arena.center())
     * @return entidad EnderDragon recién generada y configurada
     */
    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(DragonSpawner.class.getName());

    public EnderDragon spawnDragon(
            World world,
            ArenaDefinition arena,
            EffectiveDragonStats effectiveStats,
            BattleId battleId,
            Location spawnLocationOverride
    ) {
        Objects.requireNonNull(world, "El mundo de spawn no puede ser nulo");
        Objects.requireNonNull(arena, "La definición de arena no puede ser nula");
        Objects.requireNonNull(effectiveStats, "Las estadísticas efectivas no pueden ser nulas");
        Objects.requireNonNull(battleId, "El battleId no puede ser nulo");

        Location spawnLocation = spawnLocationOverride != null
                ? spawnLocationOverride.clone()
                : arena.center().toLocation(world);

        EnderDragon dragon = spawnDragon(world, spawnLocation, battleId, effectiveStats.definitionId());

        if (dragon != null) {
            try {
                dragon.setPodium(arena.podium().toLocation(world));
            } catch (Exception | LinkageError e) {
                LOGGER.log(java.util.logging.Level.WARNING, "[BetterDragon] No se pudo fijar el podio del dragón en la arena '"
                        + arena.id() + "': " + e.getMessage());
            }
            applyAttributes(dragon, effectiveStats);
        }

        return dragon;
    }

    /**
     * Genera un nuevo EnderDragon en el mundo y ubicación especificados, aplicando su firma PDC.
     *
     * @param world        mundo del End donde spawnear
     * @param location     coordenadas de spawn obligatorias
     * @param battleId     identificador único de la batalla
     * @param definitionId perfil de definición del dragón
     * @return entidad EnderDragon recién generada y etiquetada
     * @throws IllegalArgumentException si el mundo no es de tipo THE_END o location es nula
     */
    public EnderDragon spawnDragon(World world, Location location, BattleId battleId, String definitionId) {
        Objects.requireNonNull(world, "El mundo de spawn no puede ser nulo");
        Objects.requireNonNull(location, "La ubicación de spawn no puede ser nula. Utilice ArenaDefinition como fuente de verdad.");
        Objects.requireNonNull(battleId, "El battleId no puede ser nulo");

        if (world.getEnvironment() != World.Environment.THE_END) {
            throw new IllegalArgumentException("BetterDragon solo puede spawnear en mundos con ambiente THE_END. Ambiente actual: "
                    + world.getEnvironment() + " en " + world.getName());
        }

        if (!location.getChunk().isLoaded()) {
            location.getChunk().load();
        }

        String defId = (definitionId != null && !definitionId.isBlank()) ? definitionId : "default";

        // Spawn con Consumer síncrono para garantizar que la entidad porta el PDC antes de ser añadida al mundo
        return world.spawn(location, EnderDragon.class, dragon -> {
            // 1. Establecer fase inicial de vuelo en círculo
            dragon.setPhase(EnderDragon.Phase.CIRCLING);

            // 2. Aplicar identidad PDC inmediata
            DragonIdentity identity = DragonIdentity.of(dragon.getUniqueId(), battleId, defId);
            DragonPdcHandler.applyIdentity(dragon, identity);
        });
    }

    /**
     * Aplica los atributos nativos de Paper al EnderDragon.
     *
     * @param dragon         entidad a configurar en el hilo principal
     * @param effectiveStats estadísticas efectivas calculadas
     */
    public void applyAttributes(EnderDragon dragon, EffectiveDragonStats effectiveStats) {
        if (dragon == null || effectiveStats == null) {
            return;
        }

        try {
            var maxHealthAttr = dragon.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealthAttr != null) {
                maxHealthAttr.setBaseValue(effectiveStats.maxHealth());
            }
        } catch (Exception | LinkageError e) {
            LOGGER.log(java.util.logging.Level.WARNING, "[BetterDragon] No se pudo configurar el atributo MAX_HEALTH: " + e.getMessage());
        }

        try {
            dragon.setHealth(effectiveStats.maxHealth());
        } catch (Exception | LinkageError e) {
            LOGGER.log(java.util.logging.Level.WARNING, "[BetterDragon] No se pudo establecer la salud inicial del dragón: " + e.getMessage());
        }

        effectiveStats.movementSpeed().ifPresent(speed -> {
            try {
                var speedAttr = dragon.getAttribute(Attribute.MOVEMENT_SPEED);
                if (speedAttr != null) {
                    speedAttr.setBaseValue(speed);
                }
            } catch (Exception | LinkageError e) {
                LOGGER.log(java.util.logging.Level.WARNING, "[BetterDragon] No se pudo configurar el atributo MOVEMENT_SPEED: " + e.getMessage());
            }
        });

        effectiveStats.followRange().ifPresent(range -> {
            try {
                var rangeAttr = dragon.getAttribute(Attribute.FOLLOW_RANGE);
                if (rangeAttr != null) {
                    rangeAttr.setBaseValue(range);
                }
            } catch (Exception | LinkageError e) {
                LOGGER.log(java.util.logging.Level.WARNING, "[BetterDragon] No se pudo configurar el atributo FOLLOW_RANGE: " + e.getMessage());
            }
        });

        effectiveStats.attackDamage().ifPresent(dmg -> {
            try {
                var dmgAttr = dragon.getAttribute(Attribute.ATTACK_DAMAGE);
                if (dmgAttr != null) {
                    dmgAttr.setBaseValue(dmg);
                }
            } catch (Exception | LinkageError e) {
                LOGGER.log(java.util.logging.Level.WARNING, "[BetterDragon] No se pudo configurar el atributo ATTACK_DAMAGE: " + e.getMessage());
            }
        });
    }
}
