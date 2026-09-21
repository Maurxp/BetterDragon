package maurxp.betterdragon.application.battle.model;

import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.battle.model.BattleState;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Modelo inmutable de presentación que representa el estado en tiempo real de una batalla.
 * <p>
 * Diseñado para ser consumido uniformemente por comandos de chat, logs de consola
 * y futuras interfaces gráficas de usuario (GUIs).
 *
 * @author maurxp
 */
public record BattleStatusView(
        BattleId battleId,
        String worldName,
        BattleState state,
        String arenaId,
        String dragonDefinitionId,
        String activePhaseId,
        Optional<UUID> dragonUuid,
        double currentHealth,
        double maxHealth,
        Duration duration,
        int participantCount,
        Optional<String> topDamagerName,
        double topDamage
) {
    public BattleStatusView {
        Objects.requireNonNull(battleId, "battleId no puede ser nulo");
        Objects.requireNonNull(worldName, "worldName no puede ser nulo");
        Objects.requireNonNull(state, "state no puede ser nulo");
        Objects.requireNonNull(arenaId, "arenaId no puede ser nulo");
        Objects.requireNonNull(dragonDefinitionId, "dragonDefinitionId no puede ser nulo");
        Objects.requireNonNull(activePhaseId, "activePhaseId no puede ser nulo");
        Objects.requireNonNull(dragonUuid, "dragonUuid no puede ser nulo");
        Objects.requireNonNull(duration, "duration no puede ser nula");
        Objects.requireNonNull(topDamagerName, "topDamagerName no puede ser nulo");
    }

    /**
     * Porcentaje de salud restante del dragón (0.0 a 1.0).
     */
    public double getHealthPercentage() {
        if (maxHealth <= 0.0) return 0.0;
        return Math.clamp(currentHealth / maxHealth, 0.0, 1.0);
    }
}
