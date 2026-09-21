package maurxp.betterdragon.reward.event;

import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.reward.delivery.RewardDeliveryService.DeliveryBatchResult;
import maurxp.betterdragon.reward.model.RewardAllocationPlan;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

/**
 * Evento emitido tras la adjudicación y procesamiento de entrega de recompensas
 * de una batalla victoriosa de BetterDragon.
 * <p>
 * Principios:
 * <ul>
 *   <li><b>Informativo y No Cancelable:</b> Las asignaciones ya han sido registradas
 *       e intentadas; no permite modificaciones mutables para evitar inconsistencias.</li>
 *   <li><b>0% Referencias Bukkit Vivas en Dominio:</b> Expone estructuras inmutables de dominio
 *       ({@link BattleId}, {@link RewardAllocationPlan}, {@link DeliveryBatchResult}).</li>
 * </ul>
 *
 * @author maurxp
 */
public class BetterDragonRewardEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final BattleId battleId;
    private final RewardAllocationPlan plan;
    private final DeliveryBatchResult deliveryResult;

    public BetterDragonRewardEvent(
            BattleId battleId,
            RewardAllocationPlan plan,
            DeliveryBatchResult deliveryResult
    ) {
        this.battleId = Objects.requireNonNull(battleId, "battleId no puede ser nulo");
        this.plan = Objects.requireNonNull(plan, "plan no puede ser nulo");
        this.deliveryResult = Objects.requireNonNull(deliveryResult, "deliveryResult no puede ser nulo");
    }

    public BattleId getBattleId() {
        return battleId;
    }

    public RewardAllocationPlan getPlan() {
        return plan;
    }

    public DeliveryBatchResult getDeliveryResult() {
        return deliveryResult;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
