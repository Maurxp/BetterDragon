package maurxp.betterdragon.anticheese;

import maurxp.betterdragon.battle.BattleSession;
import maurxp.betterdragon.battle.BattleSessionManager;
import maurxp.betterdragon.battle.DragonPdcHandler;
import maurxp.betterdragon.battle.model.DragonIdentity;
import maurxp.betterdragon.util.BetterDragonKeys;
import org.bukkit.Material;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import org.bukkit.block.Block;
import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EnderDragonPart;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Objects;
import java.util.Optional;

/**
 * Listener de Paper encargado de aplicar la {@link ExplosionPolicy} para impedir el uso de
 * explosiones ambientales (camas, anclas de respawn, etc.) como cheese contra el dragón o el terreno.
 * <p>
 * Principios:
 * <ul>
 *   <li><b>Cero Interferencia Fuera de Arena:</b> Si no hay batalla activa o la acción ocurre fuera de la arena,
 *       el evento no es alterado.</li>
 *   <li><b>Neutralización Proactiva:</b> En modo {@link ExplosionPolicyType#BLOCK}, cancela la interacción con camas
 *       y anclas de respawn en {@link PlayerInteractEvent}.</li>
 *   <li><b>Protección de Terreno y Boss:</b> En modo {@link ExplosionPolicyType#PROTECT_ARENA}, suprime la rotura de bloques
 *       y cancela el daño por explosiones de bloques contra el dragón.</li>
 * </ul>
 *
 * @author maurxp
 */
public class AntiCheeseExplosionListener implements Listener {

    private final BattleSessionManager sessionManager;
    private final ExplosionPolicy explosionPolicy;

    public AntiCheeseExplosionListener(BattleSessionManager sessionManager, ExplosionPolicy explosionPolicy) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager no puede ser nulo");
        this.explosionPolicy = Objects.requireNonNull(explosionPolicy, "explosionPolicy no puede ser nula");
    }

    /**
     * Intercepta la interacción del jugador con camas y anclas de respawn para cancelar la detonación en modo BLOCK.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }

        ExplosionSourceType sourceType = resolveExplosionSource(clicked.getType());
        if (sourceType == ExplosionSourceType.OTHER) {
            return;
        }

        Optional<BattleSession> sessionOpt = resolveActiveSession(clicked.getWorld());
        if (sessionOpt.isEmpty()) {
            return;
        }

        BattleSession session = sessionOpt.get();
        ExplosionDecision decision = explosionPolicy.evaluate(sourceType, clicked.getLocation(), session);

        if (!decision.shouldExplode()) {
            event.setCancelled(true);
        }
    }

    /**
     * Intercepta la detonación de bloques (camas, anclas de respawn) en la arena.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        Block block = event.getBlock();
        Optional<BattleSession> sessionOpt = resolveActiveSession(block.getWorld());
        if (sessionOpt.isEmpty()) {
            return;
        }

        BattleSession session = sessionOpt.get();
        ExplosionSourceType sourceType = resolveExplosionSource(block.getType());
        ExplosionDecision decision = explosionPolicy.evaluate(sourceType, block.getLocation(), session);

        if (!decision.shouldExplode()) {
            event.setCancelled(true);
            return;
        }

        if (!decision.allowBlockDamage()) {
            event.blockList().clear();
        }
    }

    /**
     * Intercepta detonaciones de entidades dentro de la arena.
     * Respeta al 100% las explosiones de cristales de End y las habilidades propias de BetterDragon.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        if (entity == null || entity.getWorld() == null) {
            return;
        }

        // Cristales del End legítimos no deben ser cancelados ni alterados por el anti-cheese
        if (entity instanceof EnderCrystal) {
            return;
        }

        Optional<BattleSession> sessionOpt = resolveActiveSession(entity.getWorld());
        if (sessionOpt.isEmpty()) {
            return;
        }

        BattleSession session = sessionOpt.get();
        ExplosionSourceType sourceType = isBetterDragonAbility(entity)
                ? ExplosionSourceType.BETTERDRAGON_ABILITY
                : ExplosionSourceType.OTHER;
        ExplosionDecision decision = explosionPolicy.evaluate(sourceType, event.getLocation(), session);

        if (!decision.shouldExplode()) {
            event.setCancelled(true);
            return;
        }

        if (!decision.allowBlockDamage()) {
            event.blockList().clear();
        }
    }

    /**
     * Suprime el daño recibido por el dragón originado por explosiones de bloques (camas/anclas).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDragonDamageByBlock(EntityDamageByBlockEvent event) {
        EnderDragon dragon = resolveDragon(event.getEntity());
        if (dragon == null) {
            return;
        }

        Optional<DragonIdentity> identityOpt = DragonPdcHandler.extractIdentity(dragon);
        if (identityOpt.isEmpty()) {
            return;
        }

        Optional<BattleSession> sessionOpt = sessionManager.getSession(identityOpt.get().battleId());
        if (sessionOpt.isEmpty() || !sessionOpt.get().isActive()) {
            return;
        }

        BattleSession session = sessionOpt.get();
        Block damagerBlock = event.getDamager();
        ExplosionSourceType sourceType = damagerBlock != null ? resolveExplosionSource(damagerBlock.getType()) : ExplosionSourceType.OTHER;
        ExplosionDecision decision = explosionPolicy.evaluate(sourceType, dragon.getLocation(), session);

        if (!decision.allowDragonDamage()) {
            event.setCancelled(true);
            event.setDamage(0.0);
        }
    }

    /**
     * Suprime el daño recibido por el dragón originado por dinamita externa/vanilla no autorizada.
     * Respeta al 100% las explosiones legítimas de cristales de End (ENTITY_EXPLOSION) y habilidades propias.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDragonDamageByExplosionEntity(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (!(damager instanceof TNTPrimed)) {
            return;
        }

        EnderDragon dragon = resolveDragon(event.getEntity());
        if (dragon == null) {
            return;
        }

        Optional<DragonIdentity> identityOpt = DragonPdcHandler.extractIdentity(dragon);
        if (identityOpt.isEmpty()) {
            return;
        }

        Optional<BattleSession> sessionOpt = sessionManager.getSession(identityOpt.get().battleId());
        if (sessionOpt.isEmpty() || !sessionOpt.get().isActive()) {
            return;
        }

        BattleSession session = sessionOpt.get();
        ExplosionSourceType sourceType = isBetterDragonAbility(damager)
                ? ExplosionSourceType.BETTERDRAGON_ABILITY
                : ExplosionSourceType.OTHER;
        ExplosionDecision decision = explosionPolicy.evaluate(sourceType, dragon.getLocation(), session);

        if (!decision.allowDragonDamage()) {
            event.setCancelled(true);
            event.setDamage(0.0);
        }
    }

    boolean isBetterDragonAbility(Entity entity) {
        if (entity == null) {
            return false;
        }
        try {
            PersistentDataContainer pdc = entity.getPersistentDataContainer();
            boolean isManaged = false;
            try {
                if (pdc.has(BetterDragonKeys.MANAGED, PersistentDataType.BOOLEAN)) {
                    Object val = pdc.get(BetterDragonKeys.MANAGED, PersistentDataType.BOOLEAN);
                    if (Boolean.TRUE.equals(val) || (val instanceof Number n && n.byteValue() == 1)) {
                        isManaged = true;
                    }
                }
            } catch (Throwable ignored) {
            }
            if (!isManaged) {
                try {
                    if (pdc.has(BetterDragonKeys.MANAGED, PersistentDataType.BYTE)) {
                        Byte b = pdc.get(BetterDragonKeys.MANAGED, PersistentDataType.BYTE);
                        isManaged = (b != null && b == (byte) 1);
                    }
                } catch (Throwable ignored) {
                }
            }

            if (!isManaged) {
                return false;
            }

            boolean isExplosive = false;
            try {
                if (pdc.has(BetterDragonKeys.EXPLOSIVE, PersistentDataType.BOOLEAN)) {
                    Object val = pdc.get(BetterDragonKeys.EXPLOSIVE, PersistentDataType.BOOLEAN);
                    if (Boolean.TRUE.equals(val) || (val instanceof Number n && n.byteValue() == 1)) {
                        isExplosive = true;
                    }
                }
            } catch (Throwable ignored) {
            }
            if (!isExplosive) {
                try {
                    if (pdc.has(BetterDragonKeys.EXPLOSIVE, PersistentDataType.BYTE)) {
                        Byte b = pdc.get(BetterDragonKeys.EXPLOSIVE, PersistentDataType.BYTE);
                        isExplosive = (b != null && b == (byte) 1);
                    }
                } catch (Throwable ignored) {
                }
            }

            boolean hasBattleId = pdc.has(BetterDragonKeys.BATTLE_ID, PersistentDataType.STRING);

            return isExplosive || hasBattleId;
        } catch (Throwable ignored) {
        }
        return false;
    }

    private ExplosionSourceType resolveExplosionSource(Material material) {
        if (material == null) {
            return ExplosionSourceType.OTHER;
        }
        if (material.name().endsWith("_BED")) {
            return ExplosionSourceType.BED;
        }
        if (material == Material.RESPAWN_ANCHOR) {
            return ExplosionSourceType.RESPAWN_ANCHOR;
        }
        return ExplosionSourceType.OTHER;
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

    private EnderDragon resolveDragon(Entity entity) {
        if (entity instanceof EnderDragon dragon) {
            return dragon;
        }
        if (entity instanceof EnderDragonPart part && part.getParent() instanceof EnderDragon parentDragon) {
            return parentDragon;
        }
        if (entity instanceof ComplexEntityPart part && part.getParent() instanceof EnderDragon parentDragon) {
            return parentDragon;
        }
        return null;
    }
}
