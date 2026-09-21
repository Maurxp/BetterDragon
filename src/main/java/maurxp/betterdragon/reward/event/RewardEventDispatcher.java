package maurxp.betterdragon.reward.event;

/**
 * Interfaz funcional para el despacho desacoplado de eventos de recompensas.
 * <p>
 * Permite ejecutar pruebas unitarias puras sin necesidad de un servidor Bukkit en ejecución.
 *
 * @author maurxp
 */
@FunctionalInterface
public interface RewardEventDispatcher {

    /**
     * Despacha el evento de recompensa.
     *
     * @param event evento de recompensa
     */
    void dispatch(BetterDragonRewardEvent event);
}
