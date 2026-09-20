package maurxp.betterdragon.combat;

import maurxp.betterdragon.battle.BattleSession;
import maurxp.betterdragon.battle.BattleSessionManager;
import maurxp.betterdragon.battle.DragonPdcHandler;
import maurxp.betterdragon.battle.model.BattleState;
import maurxp.betterdragon.battle.model.DragonIdentity;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EnderDragonPart;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Intercepta los eventos de daño físico contra entidades BetterDragon y los encamina
 * hacia el {@link CombatRuntime} de la sesión activa correspondiente.
 * <p>
 * Principios:
 * <ul>
 *   <li><b>Resolución Robusta de Subpartes:</b> Detecta impactos dirigidos tanto a la entidad raíz
 *       {@link EnderDragon} como a sus subpartes ({@link EnderDragonPart} / {@link ComplexEntityPart}).</li>
 *   <li><b>Detección Estricta por PDC:</b> Ignora dragones vanilla externos o no administrados.</li>
 *   <li><b>Atribución de Causante:</b> Resuelve daño directo cuerpo a cuerpo de jugadores y proyectiles
 *       disparados por jugadores (flechas, tridentes, etc.).</li>
 *   <li><b>Única Ruta Oficial de Daño:</b> Previene duplicación de contabilidad de daño en el runtime.</li>
 * </ul>
 *
 * @author maurxp
 */
public class DragonCombatListener implements Listener {

    private final BattleSessionManager sessionManager;
    private final Logger logger;

    public DragonCombatListener(BattleSessionManager sessionManager, Logger logger) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager no puede ser nulo");
        this.logger = Objects.requireNonNull(logger, "logger no puede ser nulo");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDragonDamage(EntityDamageByEntityEvent event) {
        // 1. Resolver el EnderDragon físico (directo o mediante subparte)
        EnderDragon dragon = resolveDragon(event.getEntity());
        if (dragon == null) {
            return;
        }

        // 2. Comprobar firma PDC de BetterDragon
        Optional<DragonIdentity> identityOpt = DragonPdcHandler.extractIdentity(dragon);
        if (identityOpt.isEmpty()) {
            return;
        }

        DragonIdentity identity = identityOpt.get();

        // 3. Resolver la sesión de batalla activa
        Optional<BattleSession> sessionOpt = sessionManager.getSession(identity.battleId());
        if (sessionOpt.isEmpty()) {
            return;
        }

        BattleSession session = sessionOpt.get();

        // 4. Validar que la sesión acepte combate (exclusivamente ACTIVE)
        if (session.getState() != BattleState.ACTIVE) {
            return;
        }

        // 5. Resolver el jugador atacante (ataque directo o proyectil)
        Player player = resolveDamagingPlayer(event.getDamager());
        if (player == null) {
            // Daño ambiental, explosión externa o mob no jugador: ignorar en el runtime de participación
            return;
        }

        // 6. Validar daño numérico
        double damage = event.getFinalDamage();
        if (damage <= 0.0) {
            damage = event.getDamage();
        }

        if (Double.isNaN(damage) || Double.isInfinite(damage) || damage <= 0.0) {
            return;
        }

        // 7. Registrar en el CombatRuntime de la sesión
        long currentTick = Bukkit.getCurrentTick();
        Optional<ParticipantSnapshot> snapshotOpt = session.getCombatRuntime().recordDamage(
                player.getUniqueId(),
                player.getName(),
                damage,
                currentTick
        );

        if (snapshotOpt.isEmpty()) {
            // El evento BetterDragonDamageEvent fue cancelado por un consumidor
            event.setCancelled(true);
        } else {
            // Notificar al PhaseRuntime para actualización inmediata de fases ante daño confirmado
            double maxHealth = 200.0;
            if (dragon.getAttribute(Attribute.MAX_HEALTH) != null) {
                maxHealth = dragon.getAttribute(Attribute.MAX_HEALTH).getValue();
            }
            double prospectiveHealth = Math.max(0.0, dragon.getHealth() - damage);
            session.getPhaseRuntime().updateHealth(prospectiveHealth, maxHealth, currentTick, dragon);
        }
    }

    /**
     * Resuelve la entidad {@link EnderDragon} raíz a partir de la entidad impactada.
     */
    EnderDragon resolveDragon(Entity entity) {
        if (entity instanceof EnderDragon dragon) {
            return dragon;
        }
        if (entity instanceof EnderDragonPart part && part.getParent() != null) {
            return part.getParent();
        }
        if (entity instanceof ComplexEntityPart complexPart && complexPart.getParent() instanceof EnderDragon dragon) {
            return dragon;
        }
        return null;
    }

    /**
     * Resuelve el jugador responsable del daño, contemplando impactos directos y proyectiles.
     */
    Player resolveDamagingPlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }
}
