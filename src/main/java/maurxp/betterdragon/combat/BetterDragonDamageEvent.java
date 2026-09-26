package maurxp.betterdragon.combat;

import maurxp.betterdragon.battle.BattleSession;
import maurxp.betterdragon.battle.model.BattleId;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

/**
 * Evento emitido por BetterDragon cuando un jugador realiza un daño válido contra
 * un BetterDragon gestionado, antes de que dicho daño sea acumulado en el runtime.
 * <p>
 * Semántica de Cancelación:
 * <ul>
 *   <li>Si el evento es cancelado, el daño <b>no</b> se acumula en el {@link CombatRuntime}.</li>
 *   <li>La cancelación <b>no consume</b> el contador monotónico {@code hitSequence}.</li>
 *   <li>No se crea ni se modifica el estado del participante si el evento es cancelado.</li>
 * </ul>
 *
 * @author maurxp
 */
public class BetterDragonDamageEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final BattleSession session;
    private final UUID dragonUniqueId;
    private final UUID playerUniqueId;
    private final String playerName;
    private final double damage;
    private final long hitSequence;

    private boolean cancelled = false;

    public BetterDragonDamageEvent(
            BattleSession session,
            UUID dragonUniqueId,
            UUID playerUniqueId,
            String playerName,
            double damage,
            long hitSequence
    ) {
        super(false); // Síncrono en hilo principal
        this.session = Objects.requireNonNull(session, "session no puede ser nula");
        this.dragonUniqueId = Objects.requireNonNull(dragonUniqueId, "dragonUniqueId no puede ser nulo");
        this.playerUniqueId = Objects.requireNonNull(playerUniqueId, "playerUniqueId no puede ser nulo");
        this.playerName = Objects.requireNonNull(playerName, "playerName no puede ser nulo");
        this.damage = damage;
        this.hitSequence = hitSequence;
    }

    public BattleSession getSession() {
        return session;
    }

    public BattleId getBattleId() {
        return session.getBattleId();
    }

    public UUID getDragonUniqueId() {
        return dragonUniqueId;
    }

    public UUID getPlayerUniqueId() {
        return playerUniqueId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public double getDamage() {
        return damage;
    }

    public long getHitSequence() {
        return hitSequence;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
