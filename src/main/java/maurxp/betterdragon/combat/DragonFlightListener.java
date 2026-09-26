package maurxp.betterdragon.combat;

import maurxp.betterdragon.battle.BattleSession;
import maurxp.betterdragon.battle.BattleSessionManager;
import maurxp.betterdragon.battle.DragonPdcHandler;
import maurxp.betterdragon.battle.model.BattleState;
import maurxp.betterdragon.battle.model.DragonIdentity;
import org.bukkit.Bukkit;
import org.bukkit.entity.EnderDragon;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EnderDragonChangePhaseEvent;

import java.util.Objects;
import java.util.Optional;

/**
 * Escucha las transiciones nativas de vuelo del Ender Dragon en Paper (EnderDragonChangePhaseEvent)
 * para sincronizar habilidades declarativas de combate (CAND-03, CAND-04).
 * <p>
 * Principios:
 * <ul>
 * <li><b>Separación de Fases (PRIN-01):</b> La fase de vuelo vanilla representa la cinemática e IA interna
 * de Paper, mientras que la fase de combate representa la progresión narrativa. Este listener actúa como
 * puente unidireccional sin acoplar rígidamente ambos estados.</li>
 * <li><b>Aislamiento de Sesión:</b> Interviene exclusivamente si el dragón posee la firma PDC de BetterDragon
 * y pertenece a una {@link BattleSession} en estado {@code ACTIVE}.</li>
 * <li><b>0% NMS:</b> Utiliza exclusivamente Paper API pública.</li>
 * </ul>
 *
 * @author maurxp
 */
public class DragonFlightListener implements Listener {

    private final BattleSessionManager sessionManager;

    public DragonFlightListener(BattleSessionManager sessionManager) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager no puede ser nulo");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDragonFlightPhaseChange(EnderDragonChangePhaseEvent event) {
        EnderDragon dragon = event.getEntity();
        if (dragon == null) {
            return;
        }

        // 1. Extraer y validar identidad PDC
        Optional<DragonIdentity> identityOpt = DragonPdcHandler.extractIdentity(dragon);
        if (identityOpt.isEmpty()) {
            return; // Dragón vanilla no administrado
        }

        DragonIdentity identity = identityOpt.get();

        // 2. Resolver sesión de batalla activa
        Optional<BattleSession> sessionOpt = sessionManager.getSession(identity.battleId());
        if (sessionOpt.isEmpty()) {
            return;
        }

        BattleSession session = sessionOpt.get();
        if (session.getState() != BattleState.ACTIVE) {
            return;
        }

        // 3. Disparar habilidades asociadas a la nueva fase de vuelo
        EnderDragon.Phase newFlightPhase = event.getNewPhase();
        if (newFlightPhase != null && session.getPhaseRuntime() != null && session.getPhaseRuntime().getCurrentPhase() != null) {
            long currentTick;
            try {
                currentTick = Bukkit.getCurrentTick();
            } catch (Exception | LinkageError e) {
                currentTick = 0L;
            }

            session.getAbilityEngine().triggerFlightPhaseAbilities(
                    newFlightPhase,
                    session.getPhaseRuntime().getCurrentPhase(),
                    session,
                    dragon,
                    currentTick
            );
        }
    }
}
