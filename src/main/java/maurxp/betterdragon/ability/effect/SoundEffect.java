package maurxp.betterdragon.ability.effect;

import maurxp.betterdragon.ability.AbilityExecutionContext;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;

import java.util.Objects;

/**
 * Efecto sonoro que reproduce audio espacial en la ubicación de origen resuelta.
 * <p>
 * Principios:
 * <ul>
 * <li><b>0% NMS:</b> Emplea {@link World#playSound}.</li>
 * <li><b>Resiliencia:</b> Si el sonido configurado no es reconocido,
 * conmuta a {@link Sound#ENTITY_ENDER_DRAGON_GROWL} de forma segura.</li>
 * </ul>
 *
 * @author maurxp
 */
public class SoundEffect implements AbilityEffect {

    public static final String DEFAULT_SOUND_NAME = "ENTITY_ENDER_DRAGON_GROWL";
    public static final float DEFAULT_VOLUME = 2.0f;
    public static final float DEFAULT_PITCH = 1.0f;

    @Override
    public void execute(AbilityExecutionContext context) {
        Objects.requireNonNull(context, "context no puede ser nulo");
        Location origin = context.resolvedOrigin();
        World world = origin.getWorld();
        if (world == null) {
            return;
        }

        String soundName = context.ability().getStringProperty("sound", DEFAULT_SOUND_NAME);
        float volume = (float) context.ability().getDoubleProperty("volume", DEFAULT_VOLUME);
        float pitch = (float) context.ability().getDoubleProperty("pitch", DEFAULT_PITCH);

        Sound sound;
        try {
            sound = Sound.valueOf(soundName.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            sound = Sound.ENTITY_ENDER_DRAGON_GROWL;
        }

        world.playSound(origin, sound, Math.max(0.1f, volume), Math.clamp(pitch, 0.5f, 2.0f));
    }
}
