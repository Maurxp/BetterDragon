package maurxp.betterdragon.battle.event;

/**
 * Interfaz funcional para el despacho desacoplado de eventos de victoria.
 * <p>
 * Permite ejecutar pruebas unitarias puras sin necesidad de un servidor Bukkit en ejecución.
 *
 * @author maurxp
 */
@FunctionalInterface
public interface VictoryEventDispatcher {

    /**
     * Despacha el evento de victoria.
     *
     * @param event evento de victoria
     */
    void dispatch(BetterDragonVictoryEvent event);
}
