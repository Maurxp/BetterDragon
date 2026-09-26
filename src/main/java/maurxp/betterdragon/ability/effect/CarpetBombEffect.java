package maurxp.betterdragon.ability.effect;

import maurxp.betterdragon.ability.AbilityExecutionContext;
import maurxp.betterdragon.util.BetterDragonKeys;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Efecto de bombardeo aéreo (CAND-03) que engendra proyectiles de dinamita (TNTPrimed)
 * firmados inequívocamente con PDC para supresión de destrucción de terreno.
 * <p>
 * Principios:
 * <ul>
 * <li><b>0% NMS:</b> Utiliza {@link World#spawn(Location, Class)} de Bukkit/Paper API.</li>
 * <li><b>Terreno Inmune:</b> Cada entidad TNT porta {@code betterdragon:managed = true},
 * {@code betterdragon:battle_id = <uuid>} y {@code betterdragon:explosive = true}.</li>
 * <li><b>Tuning Candidates:</b> Cantidad de bombas (default 3), fusible (default 60 ticks)
 * y radio de dispersión (default 8.0) configurables y sujetos a calibración en playtesting.</li>
 * </ul>
 *
 * @author maurxp
 */
public class CarpetBombEffect implements AbilityEffect {

    public static final int DEFAULT_BOMB_COUNT = 3;       // TUNING_CANDIDATE
    public static final int DEFAULT_FUSE_TICKS = 60;      // TUNING_CANDIDATE (~3.0s)
    public static final double DEFAULT_SPREAD_RADIUS = 8.0; // TUNING_CANDIDATE

    @Override
    public void execute(AbilityExecutionContext context) {
        Objects.requireNonNull(context, "context no puede ser nulo");
        Location origin = context.resolvedOrigin();
        World world = origin.getWorld();
        if (world == null) {
            return;
        }

        int bombCount = Math.max(1, context.ability().getIntProperty("bomb_count", context.ability().getIntProperty("count", DEFAULT_BOMB_COUNT)));
        int fuseTicks = Math.max(1, context.ability().getIntProperty("fuse_ticks", DEFAULT_FUSE_TICKS));
        double spreadRadius = Math.max(0.0, context.ability().getDoubleProperty("spread_radius", context.ability().getDoubleProperty("spread", DEFAULT_SPREAD_RADIUS)));

        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < bombCount; i++) {
            double offsetX = spreadRadius > 0.0 ? (random.nextDouble() * 2.0 - 1.0) * spreadRadius : 0.0;
            double offsetZ = spreadRadius > 0.0 ? (random.nextDouble() * 2.0 - 1.0) * spreadRadius : 0.0;
            Location spawnLoc = origin.clone().add(offsetX, 0.0, offsetZ);

            try {
                TNTPrimed tnt = world.spawn(spawnLoc, TNTPrimed.class);
                tnt.setFuseTicks(fuseTicks);

                PersistentDataContainer pdc = tnt.getPersistentDataContainer();
                pdc.set(BetterDragonKeys.MANAGED, PersistentDataType.BOOLEAN, true);
                pdc.set(BetterDragonKeys.BATTLE_ID, PersistentDataType.STRING, context.battleId().asString());
                pdc.set(BetterDragonKeys.EXPLOSIVE, PersistentDataType.BOOLEAN, true);
            } catch (Exception ignored) {
                // Protección defensiva en entornos de test sin backend físico de entidades
            }
        }

        try {
            world.playSound(origin, Sound.ENTITY_TNT_PRIMED, 1.5f, 1.0f);
        } catch (Throwable ignored) {
        }
    }
}
