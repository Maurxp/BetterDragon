package maurxp.betterdragon.ability;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Resuelve ubicaciones espaciales en el mundo de batalla a partir del {@link EffectOriginType}.
 * <p>
 * Principios:
 * <ul>
 * <li><b>0% NMS:</b> Utiliza exclusivamente métodos de la API pública de Bukkit/Paper.</li>
 * <li><b>Resiliencia ante Ausencia:</b> Si un target no existe para {@code TARGET_FEET},
 * provee un fallback seguro sin lanzar NullPointerException.</li>
 * </ul>
 *
 * @author maurxp
 */
public class LocationResolver {

    private final BattleSpatialContext spatialContext;

    public LocationResolver(BattleSpatialContext spatialContext) {
        this.spatialContext = spatialContext != null ? spatialContext : new DefaultBattleSpatialContext();
    }

    public LocationResolver() {
        this(new DefaultBattleSpatialContext());
    }

    /**
     * Resuelve la coordenada de origen para una habilidad.
     *
     * @param originType      tipo de origen solicitado
     * @param dragon          entidad física del dragón
     * @param targets         lista de objetivos previamente resueltos
     * @param triggerLocation ubicación del trigger (si existe)
     * @return Location resuelta
     */
    public Location resolve(
            EffectOriginType originType,
            EnderDragon dragon,
            List<Player> targets,
            Optional<Location> triggerLocation) {

        Objects.requireNonNull(originType, "EffectOriginType no puede ser nulo");
        Objects.requireNonNull(dragon, "EnderDragon no puede ser nulo");
        World world = dragon.getWorld();

        return switch (originType) {
            case DRAGON_HEAD -> {
                // EyeLocation del dragón en Paper API o proyección frontal
                Location eye = dragon.getEyeLocation();
                Vector dir = dragon.getLocation().getDirection().normalize();
                yield eye.clone().add(dir.multiply(3.0));
            }

            case DRAGON_BODY -> dragon.getLocation().clone();

            case TARGET_FEET -> {
                if (targets != null && !targets.isEmpty()) {
                    Player primaryTarget = targets.getFirst();
                    if (primaryTarget != null && primaryTarget.isValid()) {
                        yield primaryTarget.getLocation().clone();
                    }
                }
                // Fallback seguro a la posición del dragón
                yield dragon.getLocation().clone();
            }

            case PODIUM_CENTER -> spatialContext.getPodiumCenter(world);

            case ARENA_CENTER -> spatialContext.getArenaCenter(world);

            case TRIGGER_LOCATION -> triggerLocation.map(Location::clone).orElseGet(() -> dragon.getLocation().clone());
        };
    }

    public BattleSpatialContext getSpatialContext() {
        return spatialContext;
    }
}
