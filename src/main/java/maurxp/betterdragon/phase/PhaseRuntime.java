package maurxp.betterdragon.phase;

import maurxp.betterdragon.ability.AbilityEngine;
import maurxp.betterdragon.battle.BattleSession;
import maurxp.betterdragon.phase.event.BetterDragonPhaseChangeEvent;
import maurxp.betterdragon.phase.event.PhaseChangeEventDispatcher;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EnderDragon;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Runtime de gestión de fases de combate para una sesión de batalla de BetterDragon.
 * <p>
 * Principios Arquitectónicos:
 * <ul>
 * <li><b>Aislamiento por Sesión:</b> Una instancia dedicada por cada {@link BattleSession}; dos batallas
 * simultáneas son completamente independientes.</li>
 * <li><b>Progresión Monotónica Estricta:</b> La batalla solo avanza hacia adelante (fase 1 -> fase 2 -> fase 3).
 * Si el dragón es curado por cristales del End, la fase <b>jamás</b> retrocede.</li>
 * <li><b>Determinismo ante Saltos Masivos:</b> Si un impacto de daño reduce la vida cruzando múltiples umbrales,
 * avanza de forma determinista hasta la fase correspondiente.</li>
 * <li><b>Separación de Responsabilidades:</b> No registra daño (eso lo hace {@code CombatRuntime}) ni decide el
 * ciclo de vida general (eso lo hace {@code BattleManager}).</li>
 * </ul>
 *
 * @author maurxp
 */
public class PhaseRuntime {

    private final BattleSession session;
    private final List<PhaseDefinition> orderedPhases;
    private final AbilityEngine abilityEngine;
    private final PhaseChangeEventDispatcher eventDispatcher;
    private final Logger logger;

    private PhaseDefinition currentPhase;
    private int currentPhaseIndex;
    private long phaseStartTick;
    private int transitionCount;
    private boolean initialized;

    public PhaseRuntime(
            BattleSession session,
            List<PhaseDefinition> phases,
            AbilityEngine abilityEngine,
            PhaseChangeEventDispatcher eventDispatcher,
            Logger logger) {
        this.session = Objects.requireNonNull(session, "session no puede ser nula");
        Objects.requireNonNull(phases, "phases no puede ser nula");
        if (phases.isEmpty()) {
            throw new IllegalArgumentException("Debe existir al menos una fase de combate");
        }
        this.orderedPhases = phases.stream()
                .sorted(Comparator.comparingInt(PhaseDefinition::order))
                .toList();

        this.abilityEngine = Objects.requireNonNull(abilityEngine, "abilityEngine no puede ser nulo");
        this.eventDispatcher = Objects.requireNonNull(eventDispatcher, "eventDispatcher no puede ser nulo");
        this.logger = logger != null ? logger : Logger.getLogger("BetterDragon-PhaseRuntime");

        // Fase inicial por defecto (primer elemento ordenado)
        this.currentPhase = orderedPhases.getFirst();
        this.currentPhaseIndex = 0;
        this.phaseStartTick = 0L;
        this.transitionCount = 0;
        this.initialized = false;
    }

    public PhaseRuntime(BattleSession session, List<PhaseDefinition> phases, AbilityEngine abilityEngine, Logger logger) {
        this(session, phases, abilityEngine, event -> {
            if (Bukkit.getServer() != null && Bukkit.getPluginManager() != null) {
                Bukkit.getPluginManager().callEvent(event);
            }
        }, logger);
    }

    /**
     * Calcula de forma robusta el ratio de salud (currentHealth / maxHealth).
     *
     * @param currentHealth salud actual
     * @param maxHealth     salud máxima
     * @return ratio normalizado en el rango [0.0, 1.0]
     */
    public static double calculateHealthRatio(double currentHealth, double maxHealth) {
        if (!Double.isFinite(currentHealth) || !Double.isFinite(maxHealth) || maxHealth <= 0.0) {
            return 0.0;
        }
        double ratio = currentHealth / maxHealth;
        if (Double.isNaN(ratio) || Double.isInfinite(ratio)) {
            return 0.0;
        }
        return Math.clamp(ratio, 0.0, 1.0);
    }

    /**
     * Inicializa la fase inicial de combate de forma segura antes de ejecutar habilidades dependientes.
     *
     * @param initialHealth salud inicial del dragón
     * @param maxHealth     salud máxima del dragón
     * @param currentTick   tick lógico del servidor
     * @param dragon        entidad del dragón (opcional)
     */
    public void initialize(double initialHealth, double maxHealth, long currentTick, EnderDragon dragon) {
        if (initialized) {
            return;
        }
        initialized = true;
        this.phaseStartTick = currentTick;

        double ratio = calculateHealthRatio(initialHealth, maxHealth);

        // Determinar si la salud inicial califica directamente para una fase avanzada
        int matchingIndex = 0;
        for (int i = 0; i < orderedPhases.size(); i++) {
            if (ratio <= orderedPhases.get(i).healthRatioThreshold()) {
                matchingIndex = i;
            }
        }

        this.currentPhaseIndex = matchingIndex;
        this.currentPhase = orderedPhases.get(matchingIndex);

        logger.info("[BetterDragon] Fase inicial fijada: " + currentPhase.id()
                + " (orden=" + currentPhase.order() + ", threshold=" + currentPhase.healthRatioThreshold()
                + ", ratio=" + String.format("%.2f", ratio) + ")");

        // Notificar evento de fase inicial
        eventDispatcher.dispatch(new BetterDragonPhaseChangeEvent(session.getBattleId(), null, currentPhase));

        // Ejecutar habilidades ON_PHASE_ENTER de la fase inicial
        if (dragon != null && dragon.isValid()) {
            abilityEngine.triggerPhaseEnterAbilities(currentPhase, session, dragon, currentTick);
        }
    }

    /**
     * Evalúa la progresión de fase basada en el ratio de salud actual.
     * <p>
     * Garantía de Monotonicidad:
     * Solo avanza hacia adelante (mayor índice). Si la salud sube por encima del threshold
     * debido a cristales del End, la fase se mantiene.
     *
     * @param currentHealth salud actual
     * @param maxHealth     salud máxima
     * @param currentTick   tick lógico del servidor
     * @param dragon        entidad del dragón
     * @return true si ocurrió una transición de fase; false en caso contrario
     */
    public boolean updateHealth(double currentHealth, double maxHealth, long currentTick, EnderDragon dragon) {
        if (!session.isActive()) {
            return false;
        }
        if (!initialized) {
            initialize(currentHealth, maxHealth, currentTick, dragon);
            return false;
        }

        double ratio = calculateHealthRatio(currentHealth, maxHealth);

        // Buscar si califica para avanzar a alguna de las fases posteriores
        int targetIndex = currentPhaseIndex;
        for (int i = currentPhaseIndex + 1; i < orderedPhases.size(); i++) {
            if (ratio <= orderedPhases.get(i).healthRatioThreshold()) {
                targetIndex = i;
            }
        }

        if (targetIndex > currentPhaseIndex) {
            PhaseDefinition previous = currentPhase;
            this.currentPhaseIndex = targetIndex;
            this.currentPhase = orderedPhases.get(targetIndex);
            this.phaseStartTick = currentTick;
            this.transitionCount++;

            logger.info("[BetterDragon] Transición de fase en batalla " + session.getBattleId() + ": "
                    + previous.id() + " -> " + currentPhase.id()
                    + " (ratio=" + String.format("%.2f", ratio) + ", tick=" + currentTick + ")");

            // 1. Reset de cooldowns de la fase anterior
            abilityEngine.resetCooldowns();

            // 2. Notificación mediante evento de fase
            eventDispatcher.dispatch(new BetterDragonPhaseChangeEvent(session.getBattleId(), previous, currentPhase));

            // 3. Ejecutar habilidades ON_PHASE_ENTER de la nueva fase
            if (dragon != null && dragon.isValid()) {
                abilityEngine.triggerPhaseEnterAbilities(currentPhase, session, dragon, currentTick);
            }
            return true;
        }

        return false;
    }

    /**
     * Ejecuta el ciclo de tick en hilo principal para la evaluación de salud y habilidades periódicas.
     *
     * @param currentTick tick lógico del servidor
     * @param dragon      entidad física del dragón
     */
    public void tick(long currentTick, EnderDragon dragon) {
        if (!session.isActive() || dragon == null || !dragon.isValid()) {
            return;
        }

        // 1. Actualizar salud con atributos de Bukkit
        double maxHealth = 200.0;
        if (dragon.getAttribute(Attribute.MAX_HEALTH) != null) {
            maxHealth = dragon.getAttribute(Attribute.MAX_HEALTH).getValue();
        }
        updateHealth(dragon.getHealth(), maxHealth, currentTick, dragon);

        // 2. Ejecutar habilidades periódicas de la fase activa
        abilityEngine.tickPeriodicAbilities(currentPhase, session, dragon, currentTick);
    }

    public PhaseDefinition getCurrentPhase() {
        return currentPhase;
    }

    public int getCurrentPhaseIndex() {
        return currentPhaseIndex;
    }

    public long getPhaseStartTick() {
        return phaseStartTick;
    }

    public int getTransitionCount() {
        return transitionCount;
    }

    public List<PhaseDefinition> getOrderedPhases() {
        return orderedPhases;
    }

    public AbilityEngine getAbilityEngine() {
        return abilityEngine;
    }

    public boolean isInitialized() {
        return initialized;
    }
}
