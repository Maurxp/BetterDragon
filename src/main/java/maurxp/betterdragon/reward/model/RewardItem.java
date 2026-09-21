package maurxp.betterdragon.reward.model;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Representación inmutable de un ítem de recompensa en el dominio puro.
 * <p>
 * Principios:
 * <ul>
 *   <li><b>0% Bukkit en Dominio:</b> No utiliza {@code ItemStack} ni {@code Material} directamente
 *       para permitir cálculo y persistencia independientes de la plataforma.</li>
 *   <li><b>Inmutabilidad Completa:</b> Seguro para cálculo multihilo y persistencia.</li>
 * </ul>
 *
 * @param material    nombre canónico del material (ej. "DIAMOND", "NETHERITE_INGOT")
 * @param amount      cantidad finita positiva
 * @param displayName nombre visual personalizado opcional (puede ser null)
 * @param lore        líneas descriptivas opcionales
 * @author maurxp
 */
public record RewardItem(
        String material,
        int amount,
        String displayName,
        List<String> lore
) implements Serializable {

    public RewardItem {
        Objects.requireNonNull(material, "material no puede ser nulo");
        material = material.toUpperCase().trim();
        if (material.isEmpty()) {
            throw new IllegalArgumentException("material no puede estar vacío");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount debe ser un entero positivo. Valor: " + amount);
        }
        lore = lore != null ? List.copyOf(lore) : List.of();
    }

    public RewardItem(String material, int amount) {
        this(material, amount, null, List.of());
    }

    /**
     * Crea una copia con una cantidad modificada.
     *
     * @param newAmount nueva cantidad positiva
     * @return nueva instancia con la cantidad indicada
     */
    public RewardItem withAmount(int newAmount) {
        return new RewardItem(this.material, newAmount, this.displayName, this.lore);
    }
}
