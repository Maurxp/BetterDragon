package maurxp.betterdragon.ability.effect;

import maurxp.betterdragon.ability.AbilityExecutionContext;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

import java.util.Objects;

/**
 * Efecto visual que genera partículas en la ubicación de origen resuelta.
 * <p>
 * Principios:
 * <ul>
 * <li><b>0% NMS:</b> Emplea {@link World#spawnParticle}.</li>
 * <li><b>Resiliencia:</b> Si la partícula configurada no existe en la versión activa,
 * conmuta a {@link Particle#DRAGON_BREATH} de forma segura.</li>
 * </ul>
 *
 * @author maurxp
 */
public class ParticleEffect implements AbilityEffect {

    public static final String DEFAULT_PARTICLE_NAME = "DRAGON_BREATH";
    public static final int DEFAULT_COUNT = 30;

    @Override
    public void execute(AbilityExecutionContext context) {
        Objects.requireNonNull(context, "context no puede ser nulo");
        Location origin = context.resolvedOrigin();
        World world = origin.getWorld();
        if (world == null) {
            return;
        }

        String particleName = context.ability().getStringProperty("particle", DEFAULT_PARTICLE_NAME);
        int count = context.ability().getIntProperty("count", DEFAULT_COUNT);
        double offsetX = context.ability().getDoubleProperty("offsetX", 1.0);
        double offsetY = context.ability().getDoubleProperty("offsetY", 0.5);
        double offsetZ = context.ability().getDoubleProperty("offsetZ", 1.0);
        double speed = context.ability().getDoubleProperty("speed", 0.1);

        Particle particle;
        try {
            particle = Particle.valueOf(particleName.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            particle = Particle.DRAGON_BREATH;
        }

        world.spawnParticle(particle, origin, Math.max(1, count), offsetX, offsetY, offsetZ, speed);
    }
}
