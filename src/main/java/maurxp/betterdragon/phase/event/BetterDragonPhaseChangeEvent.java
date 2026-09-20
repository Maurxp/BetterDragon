package maurxp.betterdragon.phase.event;

import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.phase.PhaseDefinition;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;
import java.util.Optional;

/**
 * Evento emitido cuando una sesión de batalla transiciona de una fase de combate a otra.
 * <p>
 * Principios:
 * <ul>
 * <li><b>Informativo y No Cancelable:</b> No implementa {@code Cancellable} para proteger la integridad
 * de la máquina de estados y la progresión monotónica del combate.</li>
 * <li><b>0% NMS:</b> Utiliza exclusivamente modelos de dominio desacoplados y la API pública de Bukkit.</li>
 * </ul>
 *
 * @author maurxp
 */
public class BetterDragonPhaseChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final BattleId battleId;
    private final PhaseDefinition previousPhase;
    private final PhaseDefinition newPhase;

    public BetterDragonPhaseChangeEvent(BattleId battleId, PhaseDefinition previousPhase, PhaseDefinition newPhase) {
        this.battleId = Objects.requireNonNull(battleId, "El battleId no puede ser nulo");
        this.previousPhase = previousPhase; // Puede ser null en la inicialización de la primera fase
        this.newPhase = Objects.requireNonNull(newPhase, "La nueva fase no puede ser nula");
    }

    public BattleId getBattleId() {
        return battleId;
    }

    public Optional<PhaseDefinition> getPreviousPhase() {
        return Optional.ofNullable(previousPhase);
    }

    public PhaseDefinition getNewPhase() {
        return newPhase;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
