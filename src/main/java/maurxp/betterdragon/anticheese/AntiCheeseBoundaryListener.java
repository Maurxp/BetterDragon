package maurxp.betterdragon.anticheese;

import maurxp.betterdragon.battle.event.BetterDragonVictoryEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;

/**
 * Listener de Paper encargado de conectar los eventos de movimiento y daño por vacío
 * con el {@link VoidTetherService} para contención perimetral de participantes.
 * <p>
 * Principios:
 * <ul>
 *   <li><b>Optimización de Movimiento:</b> Descarta prematuramente eventos de rotación de cámara
 *       (pitch/yaw) evaluando únicamente transiciones efectivas de bloque.</li>
 *   <li><b>Intervención ante Daño de Vacío:</b> Rescata al participante y cancela el daño mortal por vacío
 *       si una caída rápida no fue interceptada antes.</li>
 *   <li><b>Limpieza en Monitor:</b> Maneja desconexiones y cambios de mundo sin interferir con otros plugins.</li>
 * </ul>
 *
 * @author maurxp
 */
public class AntiCheeseBoundaryListener implements Listener {

    private final VoidTetherService voidTetherService;

    public AntiCheeseBoundaryListener(VoidTetherService voidTetherService) {
        this.voidTetherService = Objects.requireNonNull(voidTetherService, "voidTetherService no puede ser nulo");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        // Optimización O(1): descartar movimientos que no cruzan fronteras de bloque
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        voidTetherService.checkAndEnforce(event.getPlayer(), from, to);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.VOID) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        boolean rescued = voidTetherService.rescueFromVoid(player);
        if (rescued) {
            event.setCancelled(true);
            event.setDamage(0.0);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        voidTetherService.onPlayerQuit(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        voidTetherService.onPlayerChangeWorld(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVictory(BetterDragonVictoryEvent event) {
        voidTetherService.onBattleEnd(event.getBattleId());
    }
}
