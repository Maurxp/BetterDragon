package maurxp.betterdragon.anticheese;

import maurxp.betterdragon.battle.BattleSession;
import maurxp.betterdragon.battle.BattleSessionManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;

import java.util.Objects;
import java.util.Optional;

/**
 * Listener de Paper encargado de aplicar la {@link WaterPolicy} para denegar el uso y flujo de agua
 * dentro de la arena de combate durante encuentros activos.
 * <p>
 * Principios:
 * <ul>
 *   <li><b>Cero Interferencia Fuera de Arena:</b> Jugadores en el resto del mundo o dimensiones no se ven afectados.</li>
 *   <li><b>Mitigación de MLG y Endermen:</b> Cancela el vaciado de cubos de agua y el flujo de agua dentro de la arena.</li>
 *   <li><b>Sincronización de Inventario:</b> Emite {@code updateInventory()} para evitar artefactos visuales/desincronización cliente-servidor.</li>
 * </ul>
 *
 * @author maurxp
 */
public class AntiCheeseWaterListener implements Listener {

    private final BattleSessionManager sessionManager;
    private final WaterPolicy waterPolicy;

    public AntiCheeseWaterListener(BattleSessionManager sessionManager, WaterPolicy waterPolicy) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager no puede ser nulo");
        this.waterPolicy = Objects.requireNonNull(waterPolicy, "waterPolicy no puede ser nula");
    }

    /**
     * Intercepta el vaciado de cubos de agua por parte de jugadores.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (event.getBucket() != Material.WATER_BUCKET) {
            return;
        }

        Block targetBlock = event.getBlock();
        Location targetLocation = targetBlock != null ? targetBlock.getLocation() : null;
        if (targetLocation == null) {
            return;
        }

        Optional<BattleSession> sessionOpt = resolveActiveSession(targetLocation.getWorld());
        if (sessionOpt.isEmpty()) {
            return;
        }

        BattleSession session = sessionOpt.get();
        WaterDecision decision = waterPolicy.evaluatePlacement(targetLocation, event.getPlayer(), session);

        if (decision == WaterDecision.DENY) {
            event.setCancelled(true);
            try {
                event.getPlayer().updateInventory();
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Intercepta la propagación o flujo de agua entre bloques.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        Block source = event.getBlock();
        if (source.getType() != Material.WATER && !(source.getBlockData() instanceof Waterlogged)) {
            return;
        }

        Block target = event.getToBlock();
        Location targetLoc = target.getLocation();

        Optional<BattleSession> sessionOpt = resolveActiveSession(targetLoc.getWorld());
        if (sessionOpt.isEmpty()) {
            return;
        }

        BattleSession session = sessionOpt.get();
        WaterDecision decision = waterPolicy.evaluateFlow(source.getLocation(), targetLoc, session);

        if (decision == WaterDecision.DENY) {
            event.setCancelled(true);
        }
    }

    /**
     * Intercepta la colocación de bloques que introducen agua directamente o en estado anegado (waterlogged).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        boolean introducesWater = (placed.getType() == Material.WATER);
        if (!introducesWater && placed.getBlockData() instanceof Waterlogged wl) {
            introducesWater = wl.isWaterlogged();
        }

        if (!introducesWater) {
            return;
        }

        Location loc = placed.getLocation();
        Optional<BattleSession> sessionOpt = resolveActiveSession(loc.getWorld());
        if (sessionOpt.isEmpty()) {
            return;
        }

        BattleSession session = sessionOpt.get();
        WaterDecision decision = waterPolicy.evaluatePlacement(loc, event.getPlayer(), session);

        if (decision == WaterDecision.DENY) {
            event.setCancelled(true);
            try {
                event.getPlayer().updateInventory();
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Intercepta dispensadores que intentan colocar cubos de agua dentro de la arena.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (event.getItem() == null || event.getItem().getType() != Material.WATER_BUCKET) {
            return;
        }

        Location loc = event.getBlock().getLocation();
        Optional<BattleSession> sessionOpt = resolveActiveSession(loc.getWorld());
        if (sessionOpt.isEmpty()) {
            return;
        }

        BattleSession session = sessionOpt.get();
        WaterDecision decision = waterPolicy.evaluatePlacement(loc, null, session);

        if (decision == WaterDecision.DENY) {
            event.setCancelled(true);
        }
    }

    private Optional<BattleSession> resolveActiveSession(org.bukkit.World world) {
        if (world == null) return Optional.empty();
        Optional<BattleSession> sessionOpt = sessionManager.getActiveSessionByWorld(world.getUID());
        if (sessionOpt.isPresent() && sessionOpt.get().isActive()) {
            return sessionOpt;
        }
        sessionOpt = sessionManager.getActiveSessionByWorld(world.getName());
        return (sessionOpt.isPresent() && sessionOpt.get().isActive()) ? sessionOpt : Optional.empty();
    }
}
