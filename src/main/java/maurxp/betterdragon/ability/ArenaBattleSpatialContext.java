package maurxp.betterdragon.ability;

import maurxp.betterdragon.arena.ArenaBounds;
import maurxp.betterdragon.arena.ArenaDefinition;
import maurxp.betterdragon.arena.ArenaRuleSet;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

/**
 * Proveedor de contexto espacial respaldado directamente por una {@link ArenaDefinition} de dominio.
 * <p>
 * Principios:
 * <ul>
 *   <li><b>Cero Coordenadas Provisionales:</b> Sustituye definitivamente los fallbacks hardcodeados
 *       {@code (0, 100, 0)} por las coordenadas exactas configuradas para la arena.</li>
 *   <li><b>Independencia de Centros:</b> {@code getArenaCenter} y {@code getPodiumCenter} son resueltos
 *       estrictamente a partir de sus definiciones individuales sin delegación recíproca.</li>
 *   <li><b>Soporte de Bounding Box Real:</b> Expone los límites geométricos de la arena para su uso en
 *       selectores de objetivos como {@code ALL_IN_ARENA}.</li>
 * </ul>
 *
 * @author maurxp
 */
public class ArenaBattleSpatialContext implements BattleSpatialContext {

    private final ArenaDefinition arena;

    public ArenaBattleSpatialContext(ArenaDefinition arena) {
        this.arena = Objects.requireNonNull(arena, "La definición de arena no puede ser nula");
    }

    @Override
    public Location getPodiumCenter(World world) {
        Objects.requireNonNull(world, "World no puede ser nulo para resolver el podio");
        return arena.podium().toLocation(world);
    }

    @Override
    public Location getArenaCenter(World world) {
        Objects.requireNonNull(world, "World no puede ser nulo para resolver el centro de la arena");
        return arena.center().toLocation(world);
    }

    @Override
    public ArenaBounds getBounds() {
        return arena.bounds();
    }

    @Override
    public ArenaRuleSet getRules() {
        return arena.rules();
    }

    @Override
    public boolean isInArena(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        if (!arena.worldName().equalsIgnoreCase(location.getWorld().getName())) {
            return false;
        }
        return arena.bounds().contains(location);
    }

    /**
     * Retorna la definición de arena congelada en este contexto.
     *
     * @return ArenaDefinition activa
     */
    public ArenaDefinition getArena() {
        return arena;
    }
}
