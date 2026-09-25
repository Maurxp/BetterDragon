package maurxp.betterdragon.presentation;

import maurxp.betterdragon.battle.BattleSession;
import maurxp.betterdragon.battle.BattleSessionManager;
import maurxp.betterdragon.phase.event.BetterDragonPhaseChangeEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Escucha eventos del servidor para actualizar y sincronizar la presentación visual (BossBar)
 * y el feedback sensorial (sonidos de fase y Enrage) de las batallas activas.
 * <p>
 * Principios:
 * <ul>
 *   <li><b>Gestión Robusta de Espectadores:</b> Remueve de inmediato a jugadores que se desconectan
 *       o mueren, y sincroniza espectadores en teletransportes sin dejar barras fantasma.</li>
 *   <li><b>Feedback Sensorial Desacoplado:</b> Conecta {@link BetterDragonPhaseChangeEvent} con la BossBar
 *       propia sin intermediarios ni frameworks genéricos de eventos.</li>
 *   <li><b>Main-Thread Confinement:</b> Opera exclusivamente en el hilo principal de Bukkit.</li>
 * </ul>
 *
 * @author maurxp
 */
public class DragonPresentationListener implements Listener {

    private final BattleSessionManager sessionManager;
    private final Logger logger;

    public DragonPresentationListener(BattleSessionManager sessionManager, Logger logger) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager no puede ser nulo");
        this.logger = logger != null ? logger : Logger.getLogger("BetterDragon-Presentation");
    }

    /**
     * Responde a transiciones de fase de combate emitidas por {@link maurxp.betterdragon.phase.PhaseRuntime},
     * actualizando el título de la BossBar y reproduciendo el feedback auditivo a los espectadores.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPhaseChange(BetterDragonPhaseChangeEvent event) {
        if (event == null || event.getBattleId() == null) {
            return;
        }

        Optional<BattleSession> sessionOpt = sessionManager.getSession(event.getBattleId());
        if (sessionOpt.isEmpty()) {
            return;
        }

        BattleSession session = sessionOpt.get();
        DragonBossBar bossBar = session.getBossBar();
        if (bossBar != null) {
            bossBar.updatePhase(event.getNewPhase());
            bossBar.playPhaseFeedback(event.getNewPhase());
        }
    }

    /**
     * Limpia la BossBar cuando un jugador se desconecta del servidor.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        for (BattleSession session : sessionManager.getAllSessions().values()) {
            if (session != null && session.getBossBar() != null) {
                session.getBossBar().removeViewer(player);
            }
        }
    }

    /**
     * Sincroniza espectadores cuando un jugador se teletransporta hacia o desde el área de la arena.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        for (BattleSession session : sessionManager.getAllSessions().values()) {
            if (session != null && session.isActive()) {
                session.syncBossBarViewers();
            }
        }
    }

    /**
     * Remueve la BossBar de un jugador al morir para evitar barras en pantalla de reaparición.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (player == null) {
            return;
        }

        for (BattleSession session : sessionManager.getAllSessions().values()) {
            if (session != null && session.getBossBar() != null) {
                session.getBossBar().removeViewer(player);
            }
        }
    }

    /**
     * Reevalúa la visibilidad de la BossBar cuando el jugador reaparece.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        for (BattleSession session : sessionManager.getAllSessions().values()) {
            if (session != null && session.isActive()) {
                session.syncBossBarViewers();
            }
        }
    }
}
