package maurxp.betterdragon.ability.effect;

import maurxp.betterdragon.ability.AbilityExecutionContext;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Objects;

/**
 * Efecto de onda expansiva al aterrizar o tocar el podio (CAND-04).
 * <p>
 * Principios:
 * <ul>
 * <li><b>0% NMS:</b> Emplea partículas nativas, sonidos y vectores de empuje de Bukkit API.</li>
 * <li><b>Dispersión Calibrada:</b> Desplaza a los combatientes que acampan en el podio mediante
 * daño y empuje vectorial moderado sin recurrir a fijación arbitraria de 1 HP.</li>
 * <li><b>Tuning Candidates:</b> Radio (default 20.0m), daño (default 8.0), knockback horizontal (1.2)
 * y vertical (0.6) configurables y sujetos a calibración en playtesting.</li>
 * </ul>
 *
 * @author maurxp
 */
public class ShockwaveEffect implements AbilityEffect {

    public static final double DEFAULT_RADIUS = 20.0;               // TUNING_CANDIDATE
    public static final double DEFAULT_DAMAGE = 8.0;                 // TUNING_CANDIDATE
    public static final double DEFAULT_KNOCKBACK = 1.2;              // TUNING_CANDIDATE
    public static final double DEFAULT_VERTICAL_KNOCKBACK = 0.6;     // TUNING_CANDIDATE
    public static final String DEFAULT_PARTICLE_NAME = "SONIC_BOOM"; // TUNING_CANDIDATE
    public static final String DEFAULT_SOUND_NAME = "ENTITY_WARDEN_SONIC_BOOM"; // TUNING_CANDIDATE

    @Override
    public void execute(AbilityExecutionContext context) {
        Objects.requireNonNull(context, "context no puede ser nulo");
        Location origin = context.resolvedOrigin();
        World world = origin.getWorld();
        if (world == null) {
            return;
        }

        double radius = Math.max(0.5, context.ability().getDoubleProperty("radius", DEFAULT_RADIUS));
        double damage = Math.max(0.0, context.ability().getDoubleProperty("damage", DEFAULT_DAMAGE));
        double horizontalKnockback = Math.max(0.0, context.ability().getDoubleProperty("knockback", DEFAULT_KNOCKBACK));
        double verticalKnockback = Math.max(0.0, context.ability().getDoubleProperty("vertical_knockback", DEFAULT_VERTICAL_KNOCKBACK));

        String particleName = context.ability().getStringProperty("particle", DEFAULT_PARTICLE_NAME);
        String soundName = context.ability().getStringProperty("sound", DEFAULT_SOUND_NAME);

        // 1. Proyectar feedback sensorial de la onda
        try {
            Particle particle;
            try {
                particle = Particle.valueOf(particleName.toUpperCase().trim());
            } catch (Exception e) {
                particle = Particle.SONIC_BOOM;
            }

            Sound sound;
            try {
                sound = Sound.valueOf(soundName.toUpperCase().trim());
            } catch (Exception e) {
                sound = Sound.ENTITY_WARDEN_SONIC_BOOM;
            }

            world.spawnParticle(particle, origin, 1, 0.0, 0.0, 0.0, 0.0);
            world.playSound(origin, sound, 2.0f, 1.0f);
        } catch (Throwable ignored) {
        }

        // 2. Resolver objetivos afectados
        List<Player> candidateTargets = context.resolvedTargets();
        if (candidateTargets == null || candidateTargets.isEmpty()) {
            try {
                if (world.getPlayers() != null) {
                    candidateTargets = world.getPlayers();
                }
            } catch (Exception ignored) {
                candidateTargets = List.of();
            }
        }

        double radiusSquared = radius * radius;

        for (Player player : candidateTargets) {
            if (player == null || !player.isOnline() || player.isDead()) {
                continue;
            }

            Location playerLoc = player.getLocation();
            if (!playerLoc.getWorld().equals(world)) {
                continue;
            }

            double distSq = origin.distanceSquared(playerLoc);
            if (distSq <= radiusSquared) {
                // Vector de empuje hacia afuera y hacia arriba
                Vector pushDirection = playerLoc.toVector().subtract(origin.toVector());
                pushDirection.setY(0);

                if (pushDirection.lengthSquared() < 0.0001) {
                    pushDirection = new Vector(0.1, 0.0, 0.1);
                }
                pushDirection.normalize().multiply(horizontalKnockback);
                pushDirection.setY(verticalKnockback);

                try {
                    player.setVelocity(pushDirection);
                    if (damage > 0.0) {
                        if (context.dragon() != null) {
                            player.damage(damage, context.dragon());
                        } else {
                            player.damage(damage);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }
}
