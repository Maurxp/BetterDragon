package maurxp.betterdragon.battle.event;
 
import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.battle.model.BattleResult;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Evento público de dominio emitido cuando una batalla de BetterDragon culmina
 * con victoria tras la muerte natural del dragón administrado.
 * <p>
 * Principios:
 * <ul>
 *   <li><b>Informativo y No Cancelable:</b> La victoria representa un hecho consumado
 *       en el mundo físico; no implementa {@code Cancellable} para evitar estados inconsistentes.</li>
 *   <li><b>Sin Referencias Bukkit Vivas en Dominio:</b> Expone datos de dominio tipados
 *       ({@link BattleId}, {@link BattleResult}) y metadatos seguros sin obligar a plugins
 *       consumidores a manipular entidades internas.</li>
 *   <li><b>0% NMS:</b> Utiliza exclusivamente Bukkit API y modelos desacoplados de BetterDragon.</li>
 * </ul>
 *
 * @author maurxp
 */
public class BetterDragonVictoryEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final BattleId battleId;
    private final BattleResult result;
    private final String worldName;

    public BetterDragonVictoryEvent(BattleId battleId, BattleResult result, String worldName) {
        this.battleId = Objects.requireNonNull(battleId, "El battleId no puede ser nulo");
        this.result = Objects.requireNonNull(result, "El BattleResult no puede ser nulo");
        this.worldName = Objects.requireNonNull(worldName, "El worldName no puede ser nulo");
    }

    public BattleId getBattleId() {
        return battleId;
    }

    public BattleResult getResult() {
        return result;
    }

    public String getWorldName() {
        return worldName;
    }

    public Optional<UUID> getSlayerUniqueId() {
        return result.getSlayerUniqueId();
    }

    public Optional<String> getSlayerLastKnownName() {
        return result.getSlayerLastKnownName();
    }

    public Duration getDuration() {
        return result.getDuration();
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
