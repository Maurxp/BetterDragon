package maurxp.betterdragon.leaderboard.service;

import maurxp.betterdragon.battle.event.BetterDragonVictoryEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Listener Bukkit encargado de capturar {@link BetterDragonVictoryEvent}
 * y transferir el {@link maurxp.betterdragon.battle.model.BattleResult} al {@link LeaderboardService}
 * para su persistencia asíncrona en SQLite.
 * <p>
 * Principios:
 * <ul>
 *   <li><b>Prioridad MONITOR:</b> No interfiere ni cancela eventos; actúa estrictamente como consumidor observador.</li>
 *   <li><b>Sin Bloqueo del Main Thread:</b> La persistencia es completamente asíncrona mediante Futures delegados.</li>
 *   <li><b>Manejo Robusto de Excepciones:</b> Captura y registra errores sin comprometer la estabilidad del servidor.</li>
 * </ul>
 *
 * @author maurxp
 */
public class DragonLeaderboardListener implements Listener {

    private final LeaderboardService leaderboardService;
    private final Logger logger;

    public DragonLeaderboardListener(LeaderboardService leaderboardService, Logger logger) {
        this.leaderboardService = Objects.requireNonNull(leaderboardService, "leaderboardService no puede ser nulo");
        this.logger = Objects.requireNonNull(logger, "logger no puede ser nulo");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDragonVictory(BetterDragonVictoryEvent event) {
        if (event == null || event.getResult() == null) {
            return;
        }

        try {
            leaderboardService.recordVictory(event.getResult(), event.getWorldName())
                    .whenComplete((recorded, ex) -> {
                        if (ex != null) {
                            logger.log(Level.SEVERE, "[BetterDragon] Error al registrar victoria en leaderboard para batalla "
                                    + event.getBattleId() + ": " + ex.getMessage(), ex);
                        } else if (Boolean.TRUE.equals(recorded)) {
                            logger.info("[BetterDragon] Victoria de batalla " + event.getBattleId()
                                    + " registrada exitosamente en el leaderboard.");
                        } else {
                            logger.fine("[BetterDragon] Victoria de batalla " + event.getBattleId()
                                    + " ya se encontraba registrada (idempotencia aplicada).");
                        }
                    });
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "[BetterDragon] Excepción al despachar registro de victoria en leaderboard: "
                    + ex.getMessage(), ex);
        }
    }
}
