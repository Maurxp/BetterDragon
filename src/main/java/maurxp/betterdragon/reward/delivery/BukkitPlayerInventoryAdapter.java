package maurxp.betterdragon.reward.delivery;

import maurxp.betterdragon.reward.model.RewardItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Adaptador de inventario basado en la API nativa de Paper/Bukkit.
 * <p>
 * Realiza la entrega directa en el inventario del jugador usando
 * {@code inventory.addItem()}, contabilizando de forma precisa cualquier
 * sobrante (leftover) para evitar pérdida silenciosa de ítems.
 *
 * @author maurxp
 */
public class BukkitPlayerInventoryAdapter implements PlayerInventoryAdapter {

    private final Logger logger;

    public BukkitPlayerInventoryAdapter(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger no puede ser nulo");
    }

    @Override
    public boolean isPlayerOnline(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        Player player = Bukkit.getPlayer(playerId);
        return player != null && player.isOnline();
    }

    @Override
    public int deliverItem(UUID playerId, RewardItem item) {
        if (playerId == null || item == null || item.amount() <= 0) {
            return 0;
        }

        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return 0;
        }

        Material material = Material.matchMaterial(item.material());
        if (material == null || material == Material.AIR || material.name().endsWith("AIR")) {
            logger.warning("[BetterDragon] No se pudo encontrar el material para la recompensa: " + item.material());
            return 0;
        }

        int requestedAmount = item.amount();
        int maxStackSize = material.getMaxStackSize();
        int totalRemainingToGive = requestedAmount;
        int totalLeftover = 0;

        while (totalRemainingToGive > 0) {
            int currentBatch = Math.min(totalRemainingToGive, maxStackSize);
            ItemStack stack = new ItemStack(material, currentBatch);

            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack);
            if (!leftovers.isEmpty()) {
                int batchLeftover = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
                totalLeftover += batchLeftover;
                int notAttempted = totalRemainingToGive - currentBatch;
                totalLeftover += notAttempted;
                break;
            }
            totalRemainingToGive -= currentBatch;
        }

        int actuallyDelivered = requestedAmount - totalLeftover;
        return Math.max(0, actuallyDelivered);
    }
}
