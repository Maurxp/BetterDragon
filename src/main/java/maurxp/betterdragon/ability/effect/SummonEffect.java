package maurxp.betterdragon.ability.effect;

import maurxp.betterdragon.ability.AbilityExecutionContext;
import maurxp.betterdragon.util.BetterDragonKeys;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Efecto de invocación de esbirros menores (CAND-06).
 * <p>
 * Principios:
 * <ul>
 * <li><b>0% NMS:</b> Emplea {@link World#spawnEntity(Location, EntityType)} de Bukkit API.</li>
 * <li><b>Firma PDC Cuádruple:</b> Cada esbirro porta {@code betterdragon:managed = true},
 * {@code betterdragon:battle_id = <uuid>}, {@code betterdragon:minion = true} y {@code betterdragon:minion_type = <id>}.</li>
 * <li><b>Limpieza Garantizada:</b> Notifica a la sesión activa mediante {@link AbilityExecutionContext#registerSpawnedEntity}
 * para asegurar el barrido determinista sin afectar mobs pacíficos ni entidades externas.</li>
 * <li><b>Tuning Candidates:</b> Tipo de criatura (default "ENDERMITE"), cantidad (default 3)
 * y radio de dispersión (default 5.0) configurables como candidatos de calibración.</li>
 * </ul>
 *
 * @author maurxp
 */
public class SummonEffect implements AbilityEffect {

    public static final String DEFAULT_ENTITY_TYPE = "ENDERMITE"; // TUNING_CANDIDATE
    public static final int DEFAULT_COUNT = 3;                   // TUNING_CANDIDATE
    public static final double DEFAULT_SPREAD_RADIUS = 5.0;      // TUNING_CANDIDATE

    @Override
    public void execute(AbilityExecutionContext context) {
        Objects.requireNonNull(context, "context no puede ser nulo");
        Location origin = context.resolvedOrigin();
        World world = origin.getWorld();
        if (world == null) {
            return;
        }

        String rawType = context.ability().getStringProperty("entity_type", DEFAULT_ENTITY_TYPE);
        int count = Math.max(1, context.ability().getIntProperty("count", DEFAULT_COUNT));
        double spreadRadius = Math.max(0.0, context.ability().getDoubleProperty("spread_radius", DEFAULT_SPREAD_RADIUS));

        EntityType entityType;
        try {
            entityType = EntityType.valueOf(rawType.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            entityType = EntityType.ENDERMITE;
        }

        String minionType = context.ability().getStringProperty("minion_type", entityType.name());

        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < count; i++) {
            double offsetX = spreadRadius > 0.0 ? (random.nextDouble() * 2.0 - 1.0) * spreadRadius : 0.0;
            double offsetZ = spreadRadius > 0.0 ? (random.nextDouble() * 2.0 - 1.0) * spreadRadius : 0.0;
            Location spawnLoc = origin.clone().add(offsetX, 0.0, offsetZ);

            try {
                Entity minion = world.spawnEntity(spawnLoc, entityType);
                if (minion != null) {
                    PersistentDataContainer pdc = minion.getPersistentDataContainer();
                    pdc.set(BetterDragonKeys.MANAGED, PersistentDataType.BOOLEAN, true);
                    pdc.set(BetterDragonKeys.BATTLE_ID, PersistentDataType.STRING, context.battleId().asString());
                    pdc.set(BetterDragonKeys.MINION, PersistentDataType.BOOLEAN, true);
                    pdc.set(BetterDragonKeys.MINION_TYPE, PersistentDataType.STRING, minionType);

                    context.registerSpawnedEntity(minion);
                }
            } catch (Exception ignored) {
                // Entornos de prueba unitaria sin backend de entidades de Paper
            }
        }

        try {
            world.spawnParticle(Particle.PORTAL, origin, 30, 1.0, 1.0, 1.0, 0.1);
            world.playSound(origin, Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 0.8f);
        } catch (Exception ignored) {
        }
    }
}
