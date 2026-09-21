package maurxp.betterdragon.config;

import java.io.Serializable;
import java.util.Objects;

/**
 * Definición declarativa de un ítem de recompensa en la configuración.
 *
 * @param id       identificador único y estable de la definición de recompensa (ej. "participation_diamond")
 * @param material nombre del material (ej. "DIAMOND", "NETHERITE_INGOT")
 * @param amount   cantidad a repartir/entregar (entero positivo)
 * @author maurxp
 */
public record RewardItemDefinition(
        String id,
        String material,
        int amount
) implements Serializable {

    public RewardItemDefinition {
        Objects.requireNonNull(id, "id no puede ser nulo");
        id = id.trim();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("id no puede estar vacío");
        }
        if (!id.matches("^[a-zA-Z0-9_-]+$")) {
            throw new IllegalArgumentException("id debe contener solo caracteres alfanuméricos, guiones y guiones bajos: " + id);
        }
        Objects.requireNonNull(material, "material no puede ser nulo");
        material = material.toUpperCase().trim();
        if (material.isEmpty()) {
            throw new IllegalArgumentException("material no puede estar vacío");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount debe ser un entero positivo. Valor: " + amount);
        }
    }
}
