package maurxp.betterdragon.anticheese;

import maurxp.betterdragon.battle.BattleSession;
import org.bukkit.Location;

import java.util.Objects;

/**
 * Política centralizada para la evaluación de detonaciones y mitigación de estrategias de daño no autorizadas (cheese).
 * <p>
 * Principios:
 * <ul>
 *   <li><b>Jurisdicción Estricta de Encuentro:</b> Fuera de los límites de la arena o sin una batalla activa,
 *       la política no interviene en las mecánicas vanilla (retorna {@link ExplosionDecision#allow()}).</li>
 *   <li><b>Desacoplamiento de Decisión y Efecto:</b> Modela la consecuencia formal (detonación física, rotura de terreno,
 *       daño a jugadores, daño al dragón) sin acoplarse directamente a eventos de Bukkit.</li>
 *   <li><b>Protección de Terreno y Entidad:</b> En modo {@link ExplosionPolicyType#PROTECT_ARENA}, salvaguarda el terreno
 *       y la salud del dragón sin anular el castigo físico a los jugadores que detonen camas o anclas.</li>
 * </ul>
 *
 * @author maurxp
 */
public class ExplosionPolicy {

    /**
     * Evalúa una detonación en una ubicación específica bajo el contexto de una batalla activa.
     *
     * @param source   origen o tipo de la explosión
     * @param location ubicación de la detonación
     * @param session  sesión de batalla asociada (puede ser nula si no hay batalla)
     * @return decisión formal inmutable con las consecuencias aplicables
     */
    public ExplosionDecision evaluate(ExplosionSourceType source, Location location, BattleSession session) {
        Objects.requireNonNull(source, "El source no puede ser nulo");

        // 1. Sin sesión o sesión no activa -> Sin jurisdicción
        if (session == null || !session.isActive()) {
            return ExplosionDecision.allow();
        }

        // 2. Ubicación fuera de la arena -> Sin jurisdicción
        if (location == null || session.getSpatialContext() == null || !session.getSpatialContext().isInArena(location)) {
            return ExplosionDecision.allow();
        }

        // 3. Habilidades propias de BetterDragon (ej. TNT de CarpetBomb)
        if (source == ExplosionSourceType.BETTERDRAGON_ABILITY) {
            return new ExplosionDecision(true, false, true, false);
        }

        // 4. Obtener la política configurada en las reglas inmutables de la arena
        ExplosionPolicyType policyType = session.getArena().rules().getExplosionPolicy();
        if (policyType == null) {
            policyType = ExplosionPolicyType.PROTECT_ARENA;
        }

        return switch (policyType) {
            case ALLOW -> ExplosionDecision.allow();
            case BLOCK -> ExplosionDecision.block();
            case PROTECT_ARENA -> ExplosionDecision.protectArena();
        };
    }
}
