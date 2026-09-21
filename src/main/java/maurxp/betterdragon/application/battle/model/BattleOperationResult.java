package maurxp.betterdragon.application.battle.model;

import maurxp.betterdragon.battle.model.BattleId;

import java.util.Objects;
import java.util.Optional;

/**
 * Resultado estructurado de una operación administrativa sobre una batalla.
 *
 * @author maurxp
 */
public record BattleOperationResult(
        boolean success,
        String message,
        Optional<BattleId> battleId
) {
    public BattleOperationResult {
        Objects.requireNonNull(message, "message no puede ser nulo");
        Objects.requireNonNull(battleId, "battleId no puede ser nulo");
    }

    public static BattleOperationResult success(String message, BattleId battleId) {
        return new BattleOperationResult(true, message, Optional.ofNullable(battleId));
    }

    public static BattleOperationResult success(String message) {
        return new BattleOperationResult(true, message, Optional.empty());
    }

    public static BattleOperationResult failure(String message) {
        return new BattleOperationResult(false, message, Optional.empty());
    }
}
