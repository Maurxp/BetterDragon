package maurxp.betterdragon.reward.delivery;

import maurxp.betterdragon.reward.model.RewardItem;

import java.util.UUID;

/**
 * Adaptador de inventario de jugador para desacoplar la entrega física
 * del motor de lógica de recompensas.
 * <p>
 * Permite que las pruebas unitarias simulen con precisión inventarios
 * llenos, entregas parciales y estados de desconexión sin depender
 * de la API viva de Bukkit.
 *
 * @author maurxp
 */
public interface PlayerInventoryAdapter {

    /**
     * Verifica si el jugador se encuentra actualmente conectado en el servidor.
     *
     * @param playerId UUID del jugador
     * @return true si está conectado y disponible
     */
    boolean isPlayerOnline(UUID playerId);

    /**
     * Intenta depositar la cantidad indicada del ítem en el inventario del jugador.
     *
     * @param playerId UUID del jugador destino
     * @param item     definición inmutable del ítem y cantidad a depositar
     * @return cantidad de unidades efectivamente depositadas (0 si el inventario está lleno o el jugador desconectado)
     */
    int deliverItem(UUID playerId, RewardItem item);
}
