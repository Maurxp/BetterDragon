package maurxp.betterdragon.presentation;

import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.config.DragonBossBarDefinition;
import maurxp.betterdragon.phase.PhaseDefinition;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador de presentación de la BossBar propia de BetterDragon para una sesión de batalla.
 * <p>
 * Principios:
 * <ul>
 *   <li><b>Aislamiento por Sesión:</b> Una instancia dedicada por cada {@code BattleSession}. Cero singletons globales.</li>
 *   <li><b>Soberanía Visual:</b> Se presenta mientras la BossBar vanilla permanece suprimida.</li>
 *   <li><b>Cálculo Seguro de Progreso:</b> La relación {@code currentHealth / maxHealth} se normaliza estrictamente
 *       en el rango [0.0, 1.0], descartando de forma segura valores negativos, NaN, Infinity o desbordamientos.</li>
 *   <li><b>Ciclo de Vida Limpio:</b> Al completarse o abortarse la batalla, elimina a todos los espectadores y se oculta.</li>
 *   <li><b>Resiliencia sin Bukkit Server:</b> En entornos de prueba unitaria sin servidor activo, opera de forma segura
 *       en memoria sin lanzar NullPointerException.</li>
 * </ul>
 *
 * @author maurxp
 */
public class DragonBossBar {

    private static final Logger LOGGER = Logger.getLogger(DragonBossBar.class.getName());

    private final BattleId battleId;
    private final DragonBossBarDefinition config;
    private final String dragonDisplayName;
    private final BossBar bukkitBossBar;

    private String currentPhaseId = "phase_1";
    private boolean enraged = false;
    private double currentProgress = 1.0;
    private String currentTitle;
    private final Set<Player> trackedViewers = new HashSet<>();

    public DragonBossBar(
            BattleId battleId,
            DragonBossBarDefinition config,
            String dragonDisplayName,
            BossBar bukkitBossBar
    ) {
        this.battleId = Objects.requireNonNull(battleId, "El battleId no puede ser nulo");
        this.config = Objects.requireNonNull(config, "El config de BossBar no puede ser nulo");
        this.dragonDisplayName = (dragonDisplayName != null && !dragonDisplayName.isBlank())
                ? dragonDisplayName.trim()
                : "Ender Dragon";
        this.bukkitBossBar = bukkitBossBar;

        this.currentTitle = this.config.formatTitle(this.dragonDisplayName, this.currentPhaseId, this.enraged);
        if (this.bukkitBossBar != null) {
            this.bukkitBossBar.setTitle(this.currentTitle);
            this.bukkitBossBar.setColor(this.config.color());
            this.bukkitBossBar.setStyle(this.config.style());
            this.bukkitBossBar.setProgress(this.currentProgress);
            this.bukkitBossBar.setVisible(this.config.enabled());
        }
    }

    public DragonBossBar(BattleId battleId, DragonBossBarDefinition config, String dragonDisplayName) {
        this(battleId, config, dragonDisplayName, createBukkitBossBar(config, dragonDisplayName));
    }

    private static BossBar createBukkitBossBar(DragonBossBarDefinition config, String dragonDisplayName) {
        if (Bukkit.getServer() == null) {
            return null;
        }
        try {
            String title = config.formatTitle(dragonDisplayName, "phase_1", false);
            return Bukkit.createBossBar(title, config.color(), config.style());
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "[BetterDragon] Error al crear Bukkit BossBar para " + dragonDisplayName + ": " + ex.getMessage(), ex);
            return null;
        }
    }

    /**
     * Calcula de forma matemáticamente segura el progreso de la BossBar (currentHealth / maxHealth),
     * garantizando un valor estrictamente dentro de [0.0, 1.0] sin NaN, Infinity ni negativos.
     *
     * @param currentHealth salud actual de la entidad
     * @param maxHealth     salud máxima efectiva
     * @return valor de progreso clamped en [0.0, 1.0]
     */
    public static double calculateProgress(double currentHealth, double maxHealth) {
        if (!Double.isFinite(currentHealth) || !Double.isFinite(maxHealth) || maxHealth <= 0.0) {
            return 0.0;
        }
        if (currentHealth <= 0.0) {
            return 0.0;
        }
        double progress = currentHealth / maxHealth;
        if (!Double.isFinite(progress) || Double.isNaN(progress)) {
            return 0.0;
        }
        return Math.clamp(progress, 0.0, 1.0);
    }

    /**
     * Actualiza el progreso de salud de la barra.
     *
     * @param currentHealth salud actual
     * @param maxHealth     salud máxima efectiva
     */
    public void updateHealth(double currentHealth, double maxHealth) {
        this.currentProgress = calculateProgress(currentHealth, maxHealth);
        if (this.bukkitBossBar != null) {
            this.bukkitBossBar.setProgress(this.currentProgress);
        }
    }

    /**
     * Actualiza la fase de combate activa y refresca el título.
     *
     * @param phase nueva fase de combate
     */
    public void updatePhase(PhaseDefinition phase) {
        if (phase != null) {
            this.currentPhaseId = phase.id();
        }
        refreshTitle();
    }

    /**
     * Actualiza el estado de Soft Enrage y refresca el título visual.
     *
     * @param enraged true si Soft Enrage está activo
     */
    public void setEnraged(boolean enraged) {
        this.enraged = enraged;
        refreshTitle();
    }

    private void refreshTitle() {
        this.currentTitle = this.config.formatTitle(this.dragonDisplayName, this.currentPhaseId, this.enraged);
        if (this.bukkitBossBar != null) {
            this.bukkitBossBar.setTitle(this.currentTitle);
        }
    }

    /**
     * Emite el feedback sensorial (sonido) a todos los espectadores de la BossBar
     * al ocurrir una transición de fase de combate.
     *
     * @param newPhase nueva fase alcanzada
     */
    public void playPhaseFeedback(PhaseDefinition newPhase) {
        if (!this.config.enabled()) {
            return;
        }
        for (Player viewer : getViewers()) {
            if (viewer != null && viewer.isOnline()) {
                try {
                    viewer.playSound(viewer.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
                } catch (Exception ex) {
                    LOGGER.log(Level.FINE, "[BetterDragon] No se pudo reproducir audio de fase para espectador " + viewer.getName() + ": " + ex.getMessage());
                }
            }
        }
    }

    /**
     * Emite el feedback sensorial (sonido de furia) al activarse Soft Enrage.
     */
    public void playEnrageFeedback() {
        if (!this.config.enabled()) {
            return;
        }
        for (Player viewer : getViewers()) {
            if (viewer != null && viewer.isOnline()) {
                try {
                    viewer.playSound(viewer.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.2f, 0.8f);
                } catch (Exception ex) {
                    LOGGER.log(Level.FINE, "[BetterDragon] No se pudo reproducir audio de Enrage para espectador " + viewer.getName() + ": " + ex.getMessage());
                }
            }
        }
    }

    /**
     * Sincroniza la lista de espectadores con la colección de jugadores elegibles en la arena.
     * Añade a los recién ingresados y remueve a quienes ya no son elegibles.
     *
     * @param eligiblePlayers jugadores válidos dentro de la arena
     */
    public void updateViewers(Collection<? extends Player> eligiblePlayers) {
        Set<Player> eligibleSet = new HashSet<>();
        if (eligiblePlayers != null) {
            for (Player p : eligiblePlayers) {
                if (p != null && p.isOnline() && !p.isDead()) {
                    eligibleSet.add(p);
                }
            }
        }

        // 1. Remover espectadores que ya no son elegibles
        Set<Player> toRemove = new HashSet<>();
        for (Player current : trackedViewers) {
            if (!eligibleSet.contains(current)) {
                toRemove.add(current);
            }
        }
        for (Player p : toRemove) {
            removeViewer(p);
        }

        // 2. Añadir nuevos espectadores elegibles
        for (Player p : eligibleSet) {
            if (!trackedViewers.contains(p)) {
                addViewer(p);
            }
        }
    }

    /**
     * Añade un jugador como espectador de la BossBar.
     */
    public void addViewer(Player player) {
        if (player == null) return;
        trackedViewers.add(player);
        if (this.bukkitBossBar != null) {
            try {
                this.bukkitBossBar.addPlayer(player);
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "[BetterDragon] Error al añadir espectador " + player.getName()
                        + " a la BossBar de batalla " + battleId + ": " + ex.getMessage(), ex);
            }
        }
    }

    /**
     * Remueve a un jugador de la BossBar.
     */
    public void removeViewer(Player player) {
        if (player == null) return;
        trackedViewers.remove(player);
        if (this.bukkitBossBar != null) {
            try {
                this.bukkitBossBar.removePlayer(player);
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "[BetterDragon] Error al remover espectador " + player.getName()
                        + " de la BossBar de batalla " + battleId + ": " + ex.getMessage(), ex);
            }
        }
    }

    /**
     * Limpia completamente todos los espectadores, oculta la barra y libera referencias.
     */
    public void cleanup() {
        trackedViewers.clear();
        if (this.bukkitBossBar != null) {
            try {
                this.bukkitBossBar.removeAll();
                this.bukkitBossBar.setVisible(false);
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "[BetterDragon] Error al limpiar BossBar de batalla "
                        + battleId + ": " + ex.getMessage(), ex);
            }
        }
    }

    public void setVisible(boolean visible) {
        if (this.bukkitBossBar != null) {
            this.bukkitBossBar.setVisible(visible && this.config.enabled());
        }
    }

    public boolean isVisible() {
        return this.bukkitBossBar != null && this.bukkitBossBar.isVisible();
    }

    public BattleId getBattleId() {
        return battleId;
    }

    public DragonBossBarDefinition getConfig() {
        return config;
    }

    public String getDragonDisplayName() {
        return dragonDisplayName;
    }

    public double getProgress() {
        return currentProgress;
    }

    public String getTitle() {
        return currentTitle;
    }

    public boolean isEnraged() {
        return enraged;
    }

    public String getCurrentPhaseId() {
        return currentPhaseId;
    }

    public Set<Player> getViewers() {
        return Collections.unmodifiableSet(trackedViewers);
    }

    public BossBar getBukkitBossBar() {
        return bukkitBossBar;
    }
}
