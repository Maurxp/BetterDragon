package maurxp.betterdragon.reward.service;

import maurxp.betterdragon.battle.event.BetterDragonVictoryEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Listener Bukkit encargado de capturar la victoria de BetterDragon
 * y la reconexión de jugadores para la entrega oportuna de recompensas.
 * <p>
 * Principios:
 * <ul>
 *   <li><b>Prioridad MONITOR:</b> No interfiere ni cancela eventos físicos; actúa como observador.</li>
 *   <li><b>Entrega en Reconexión:</b> Si un jugador estaba desconectado al terminar la batalla,
 *       reintenta automáticamente la entrega de sus ítems pendientes al ingresar al servidor.</li>
 *   <li><b>Idempotencia:</b> Los reintentos nunca duplican ítems previamente entregados.</li>
 * </ul>
 *
 * @author maurxp
 */
public class DragonRewardListener implements Listener {

    private final RewardService rewardService;
    private final Logger logger;

    public DragonRewardListener(RewardService rewardService, Logger logger) {
        this.rewardService = Objects.requireNonNull(rewardService, "rewardService no puede ser nulo");
        this.logger = Objects.requireNonNull(logger, "logger no puede ser nulo");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDragonVictory(BetterDragonVictoryEvent event) {
        if (event == null || event.getResult() == null) {
            return;
        }

        try {
            rewardService.processVictory(event.getResult());
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "[BetterDragon] Error al procesar recompensas tras victoria de batalla "
                    + event.getBattleId() + ": " + ex.getMessage(), ex);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (event == null || event.getPlayer() == null) {
            return;
        }

        UUID playerId = event.getPlayer().getUniqueId();
        try {
            int retriedItems = rewardService.retryPendingClaims(playerId);
            if (retriedItems > 0) {
                logger.info("[BetterDragon] Se entregaron " + retriedItems + " ítems pendientes a "
                        + event.getPlayer().getName() + " al iniciar sesión.");
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "[BetterDragon] Error al reintentar recompensas pendientes para "
                    + event.getPlayer().getName() + ": " + ex.getMessage(), ex);
        }
    }
}
