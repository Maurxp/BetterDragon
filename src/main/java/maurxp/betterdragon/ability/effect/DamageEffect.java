package maurxp.betterdragon.ability.effect;

import maurxp.betterdragon.ability.AbilityExecutionContext;
import org.bukkit.entity.Player;

import java.util.Objects;

/**
 * Efecto que inflige daño directo a los jugadores objetivos.
 * <p>
 * Regla Crítica:
 * El daño es infligido por el dragón al jugador ({@code player.damage(amount, dragon)}),
 * lo que previene explícitamente cualquier feedback loop hacia {@code CombatRuntime}
 * (que exclusivamente monitorea daño infligido POR jugadores HACIA el dragón).
 *
 * @author maurxp
 */
public class DamageEffect implements AbilityEffect {

    public static final double DEFAULT_DAMAGE = 6.0; // 3 corazones

    @Override
    public void execute(AbilityExecutionContext context) {
        Objects.requireNonNull(context, "context no puede ser nulo");
        double amount = context.ability().getDoubleProperty("damage", DEFAULT_DAMAGE);
        if (!Double.isFinite(amount) || amount <= 0.0) {
            return;
        }

        for (Player target : context.resolvedTargets()) {
            if (target != null && target.isValid() && !target.isDead()) {
                target.damage(amount, context.dragon());
            }
        }
    }
}
