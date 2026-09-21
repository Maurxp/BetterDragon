package maurxp.betterdragon.config;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Definición declarativa de las recompensas exclusivas otorgadas al Slayer de la batalla.
 *
 * @param enabled             si la recompensa de Slayer está habilitada
 * @param requiresEligibility si el Slayer debe alcanzar min_participation_percent para recibirla
 * @param items               lista de ítems otorgados exclusivamente al Slayer
 * @author maurxp
 */
public record SlayerRewardDefinition(
        boolean enabled,
        boolean requiresEligibility,
        List<RewardItemDefinition> items
) implements Serializable {

    public SlayerRewardDefinition {
        Objects.requireNonNull(items, "items no puede ser nulo");
        items = List.copyOf(items);
    }

    /**
     * Configuración predeterminada neutral y segura para Slayer (deshabilitada por defecto sin ítems).
     */
    public static SlayerRewardDefinition defaults() {
        return new SlayerRewardDefinition(false, true, List.of());
    }
}
