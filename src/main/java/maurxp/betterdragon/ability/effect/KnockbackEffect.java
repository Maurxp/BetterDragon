package maurxp.betterdragon.ability.effect;

import maurxp.betterdragon.ability.AbilityExecutionContext;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Objects;

/**
 * Efecto que aplica un impulso vectorial físico a los jugadores objetivos.
 * <p>
 * Principios:
 * <ul>
 * <li><b>Velocidades Controladas:</b> Limita la magnitud máxima del vector a niveles seguros (evitando desconexiones por movimiento o salida del mapa).</li>
 * <li><b>0% NMS:</b> Utiliza {@link Player#setVelocity(Vector)}.</li>
 * </ul>
 *
 * @author maurxp
 */
public class KnockbackEffect implements AbilityEffect {

    public static final double DEFAULT_STRENGTH = 1.2;
    public static final double MAX_MAGNITUDE = 2.5;

    @Override
    public void execute(AbilityExecutionContext context) {
        Objects.requireNonNull(context, "context no puede ser nulo");
        double strength = context.ability().getDoubleProperty("strength", DEFAULT_STRENGTH);
        if (!Double.isFinite(strength) || strength <= 0.0) {
            return;
        }

        Location origin = context.resolvedOrigin();

        for (Player target : context.resolvedTargets()) {
            if (target != null && target.isValid() && !target.isDead()) {
                Vector direction = target.getLocation().toVector().subtract(origin.toVector());
                if (direction.lengthSquared() < 0.01) {
                    direction = new Vector(0, 1, 0);
                } else {
                    direction.normalize();
                }
                // Componente ascendente para un empuje natural
                direction.setY(Math.max(0.4, direction.getY() * 0.5 + 0.3));
                direction.multiply(strength);

                if (direction.length() > MAX_MAGNITUDE) {
                    direction.normalize().multiply(MAX_MAGNITUDE);
                }

                target.setVelocity(direction);
            }
        }
    }
}
