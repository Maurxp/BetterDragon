package maurxp.betterdragon.anticheese;

import maurxp.betterdragon.battle.BattleSession;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Política centralizada para el control y denegación de agua dentro de la arena de combate.
 * <p>
 * Principios:
 * <ul>
 *   <li><b>Mitigación de Trivialización (Anti-Cheese):</b> Impide el uso de cubos de agua para anular el daño por caída
 *       (MLG), neutralizar esbirros (Endermen) o sofocar explosiones durante el encuentro.</li>
 *   <li><b>Jurisdicción Estricta:</b> Solo interviene dentro de los límites espaciales de la arena durante una batalla activa;
 *       las actividades fuera de la arena o tras la finalización del combate no sufren alteración.</li>
 *   <li><b>Control de Propagación:</b> Supervisa tanto la colocación directa de bloques/cubos como el flujo de líquido.</li>
 * </ul>
 *
 * @author maurxp
 */
public class WaterPolicy {

    /**
     * Evalúa si está permitida la colocación de agua en una ubicación dada.
     *
     * @param location ubicación donde se intenta colocar agua
     * @param player   jugador que realiza la acción (puede ser nulo en caso de mecanismos/dispensadores)
     * @param session  sesión de batalla asociada al mundo
     * @return {@link WaterDecision#ALLOW} si la acción está permitida, {@link WaterDecision#DENY} si debe cancelarse
     */
    public WaterDecision evaluatePlacement(Location location, Player player, BattleSession session) {
        if (session == null || !session.isActive()) {
            return WaterDecision.ALLOW;
        }

        if (location == null || session.getSpatialContext() == null || !session.getSpatialContext().isInArena(location)) {
            return WaterDecision.ALLOW;
        }

        if (session.getArena().rules().isWaterAllowed()) {
            return WaterDecision.ALLOW;
        }

        return WaterDecision.DENY;
    }

    /**
     * Evalúa si se permite el flujo o propagación de agua desde un bloque origen a un destino.
     *
     * @param from    ubicación de origen del líquido
     * @param to      ubicación de destino del flujo
     * @param session sesión de batalla asociada al mundo
     * @return {@link WaterDecision#ALLOW} si se permite fluir, {@link WaterDecision#DENY} si debe cancelarse
     */
    public WaterDecision evaluateFlow(Location from, Location to, BattleSession session) {
        if (session == null || !session.isActive()) {
            return WaterDecision.ALLOW;
        }

        // Si el bloque destino no está dentro de la arena, BetterDragon no interfiere
        if (to == null || session.getSpatialContext() == null || !session.getSpatialContext().isInArena(to)) {
            return WaterDecision.ALLOW;
        }

        if (session.getArena().rules().isWaterAllowed()) {
            return WaterDecision.ALLOW;
        }

        return WaterDecision.DENY;
    }
}
