package maurxp.betterdragon.ability;

import maurxp.betterdragon.ability.effect.AbilityEffect;
import maurxp.betterdragon.ability.effect.CarpetBombEffect;
import maurxp.betterdragon.ability.effect.DamageEffect;
import maurxp.betterdragon.ability.effect.KnockbackEffect;
import maurxp.betterdragon.ability.effect.ParticleEffect;
import maurxp.betterdragon.ability.effect.ShockwaveEffect;
import maurxp.betterdragon.ability.effect.SoundEffect;
import maurxp.betterdragon.ability.effect.SummonEffect;
import maurxp.betterdragon.battle.BattleSession;
import maurxp.betterdragon.phase.PhaseDefinition;
import maurxp.betterdragon.arena.ArenaDefinition;
import maurxp.betterdragon.util.CancellableTask;
import maurxp.betterdragon.util.DelayedTaskScheduler;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Motor central de ejecución de habilidades para una sesión de batalla.
 * <p>
 * Responsabilidades:
 * <ul>
 * <li>Consultar catálogo de habilidades provisto por el snapshot inmutable.</li>
 * <li>Comprobar y actualizar tiempos de recarga (cooldowns) en ticks lógicos (global y por atacante).</li>
 * <li>Resolver objetivos y ubicaciones de origen.</li>
 * <li>Despachar efectos y telegrafiado sensorial encapsulando excepciones para resiliencia total del servidor.</li>
 * <li><b>0% NMS y Cero Persistencia/Rewards:</b> Desacoplado totalmente de almacenamiento y reglas de juego finales.</li>
 * </ul>
 *
 * @author maurxp
 */
public class AbilityEngine {

    private final Map<String, AbilityDefinition> abilityCatalog;
    private final AbilityCooldownTracker cooldownTracker;
    private final TargetSelector targetSelector;
    private final LocationResolver locationResolver;
    private final Map<AbilityEffectType, AbilityEffect> effectRegistry;
    private final DelayedTaskScheduler delayedTaskScheduler;
    private final Logger logger;

    public AbilityEngine(
            Map<String, AbilityDefinition> abilityCatalog,
            AbilityCooldownTracker cooldownTracker,
            TargetSelector targetSelector,
            LocationResolver locationResolver,
            Map<AbilityEffectType, AbilityEffect> customEffectRegistry,
            DelayedTaskScheduler delayedTaskScheduler,
            Logger logger) {
        this.abilityCatalog = abilityCatalog != null ? Map.copyOf(abilityCatalog) : Map.of();
        this.cooldownTracker = Objects.requireNonNull(cooldownTracker, "cooldownTracker no puede ser nulo");
        this.targetSelector = Objects.requireNonNull(targetSelector, "targetSelector no puede ser nulo");
        this.locationResolver = Objects.requireNonNull(locationResolver, "locationResolver no puede ser nulo");
        this.delayedTaskScheduler = delayedTaskScheduler != null ? delayedTaskScheduler : (runnable, delay) -> {
            if (delay <= 0) {
                runnable.run();
                return () -> {};
            }
            try {
                org.bukkit.scheduler.BukkitTask bt = org.bukkit.Bukkit.getScheduler().runTaskLater(
                        org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(AbilityEngine.class),
                        runnable,
                        delay
                );
                return bt::cancel;
            } catch (Exception | LinkageError e) {
                runnable.run();
                return () -> {};
            }
        };
        this.logger = logger != null ? logger : Logger.getLogger("BetterDragon-AbilityEngine");

        this.effectRegistry = new EnumMap<>(AbilityEffectType.class);
        registerDefaultEffects();
        if (customEffectRegistry != null) {
            this.effectRegistry.putAll(customEffectRegistry);
        }
    }

    public AbilityEngine(
            Map<String, AbilityDefinition> abilityCatalog,
            AbilityCooldownTracker cooldownTracker,
            TargetSelector targetSelector,
            LocationResolver locationResolver,
            Map<AbilityEffectType, AbilityEffect> customEffectRegistry,
            Logger logger) {
        this(abilityCatalog, cooldownTracker, targetSelector, locationResolver, customEffectRegistry, null, logger);
    }

    public AbilityEngine(Map<String, AbilityDefinition> abilityCatalog, BattleSpatialContext spatialContext, Logger logger) {
        this(abilityCatalog,
                new AbilityCooldownTracker(),
                new TargetSelector(Objects.requireNonNull(spatialContext, "spatialContext no puede ser nulo")),
                new LocationResolver(Objects.requireNonNull(spatialContext, "spatialContext no puede ser nulo")),
                null,
                null,
                logger);
    }

    public AbilityEngine(Map<String, AbilityDefinition> abilityCatalog, Logger logger) {
        this(abilityCatalog, new ArenaBattleSpatialContext(ArenaDefinition.defaults()), logger);
    }

    private void registerDefaultEffects() {
        effectRegistry.put(AbilityEffectType.DAMAGE, new DamageEffect());
        effectRegistry.put(AbilityEffectType.KNOCKBACK, new KnockbackEffect());
        effectRegistry.put(AbilityEffectType.PARTICLE, new ParticleEffect());
        effectRegistry.put(AbilityEffectType.SOUND, new SoundEffect());
        effectRegistry.put(AbilityEffectType.CARPET_BOMB, new CarpetBombEffect());
        effectRegistry.put(AbilityEffectType.SHOCKWAVE, new ShockwaveEffect());
        effectRegistry.put(AbilityEffectType.SUMMON, new SummonEffect());
    }

    /**
     * Ejecuta una habilidad específica si está lista y es válida.
     *
     * @param abilityId        identificador de la habilidad
     * @param phase            fase activa
     * @param session          sesión de batalla
     * @param dragon           entidad física del dragón
     * @param trigger          tipo de disparador
     * @param triggeringPlayer jugador causante (si existe)
     * @param triggerLocation  coordenada del trigger (si existe)
     * @param currentTick      tick lógico del servidor
     * @return true si la habilidad fue ejecutada exitosamente; false si no estaba lista o no existe
     */
    public boolean executeAbility(
            String abilityId,
            PhaseDefinition phase,
            BattleSession session,
            EnderDragon dragon,
            AbilityTrigger trigger,
            Optional<Player> triggeringPlayer,
            Optional<Location> triggerLocation,
            long currentTick) {

        Objects.requireNonNull(abilityId, "abilityId no puede ser nulo");
        Objects.requireNonNull(phase, "phase no puede ser nula");
        Objects.requireNonNull(session, "session no puede ser nula");
        Objects.requireNonNull(dragon, "dragon no puede ser nulo");

        AbilityDefinition ability = abilityCatalog.get(abilityId);
        if (ability == null) {
            logger.warning("[BetterDragon] Habilidad desconocida en fase " + phase.id() + ": '" + abilityId + "'");
            return false;
        }

        return executeAbility(ability, phase, session, dragon, trigger, triggeringPlayer, triggerLocation, currentTick);
    }

    /**
     * Ejecuta una definición de habilidad directamente si está lista y el trigger coincide.
     *
     * @param ability          definición de la habilidad
     * @param phase            fase activa
     * @param session          sesión de batalla
     * @param dragon           entidad física del dragón
     * @param trigger          tipo de disparador de la invocación
     * @param triggeringPlayer jugador causante (si existe)
     * @param triggerLocation  coordenada del trigger (si existe)
     * @param currentTick      tick lógico del servidor
     * @return true si la habilidad fue ejecutada exitosamente; false si no estaba lista o fue rechazada
     */
    public boolean executeAbility(
            AbilityDefinition ability,
            PhaseDefinition phase,
            BattleSession session,
            EnderDragon dragon,
            AbilityTrigger trigger,
            Optional<Player> triggeringPlayer,
            Optional<Location> triggerLocation,
            long currentTick) {

        Objects.requireNonNull(ability, "ability no puede ser nula");
        Objects.requireNonNull(phase, "phase no puede ser nula");
        Objects.requireNonNull(session, "session no puede ser nula");
        Objects.requireNonNull(dragon, "dragon no puede ser nulo");
        Objects.requireNonNull(trigger, "trigger no puede ser nulo");

        String abilityId = ability.id();

        // 1. Validar coincidencia de trigger (R1: trigger mismatch rejection)
        if (ability.trigger() != trigger) {
            logger.warning("[BetterDragon] Habilidad '" + abilityId + "' declarada con trigger "
                    + ability.trigger() + " fue invocada con trigger incompatible " + trigger + ". Ejecución abortada.");
            return false;
        }

        // 2. Validar cooldown en ticks lógicos (global y específico por atacante si aplica)
        UUID attackerUuid = (triggeringPlayer != null && triggeringPlayer.isPresent())
                ? triggeringPlayer.get().getUniqueId()
                : null;

        if (trigger == AbilityTrigger.ON_DAMAGE && attackerUuid != null) {
            if (!cooldownTracker.isReady(abilityId, attackerUuid, currentTick)) {
                return false;
            }
        } else {
            if (!cooldownTracker.isReady(abilityId, currentTick)) {
                return false;
            }
        }

        // 3. Resolver origen y objetivos de forma espacialmente coherente (R1: orden Origin -> Target)
        Location resolvedOrigin;
        List<Player> targets;

        if (ability.effectOrigin() == EffectOriginType.TARGET_FEET) {
            // TARGET_FEET requiere targets previos; se usa la ubicación del dragón como referencia inicial
            Location referenceLoc = dragon.getLocation();
            targets = targetSelector.resolveTargets(
                    ability.targetSelector(),
                    ability,
                    dragon.getWorld(),
                    referenceLoc,
                    session.getCombatRuntime(),
                    triggeringPlayer);
            resolvedOrigin = locationResolver.resolve(
                    EffectOriginType.TARGET_FEET,
                    dragon,
                    targets,
                    triggerLocation);
        } else {
            // El origen configurado no depende del target: se resuelve primero
            resolvedOrigin = locationResolver.resolve(
                    ability.effectOrigin(),
                    dragon,
                    List.of(),
                    triggerLocation);
            // Se utiliza el origen resuelto exacto como referencia espacial para el selector (ej. NEAREST_PLAYER)
            targets = targetSelector.resolveTargets(
                    ability.targetSelector(),
                    ability,
                    dragon.getWorld(),
                    resolvedOrigin,
                    session.getCombatRuntime(),
                    triggeringPlayer);
        }

        // 4. Construir contexto inmutable con puente para registro de entidades secundarias
        AbilityExecutionContext context = new AbilityExecutionContext(
                session.getBattleId(),
                session.getWorldName(),
                dragon,
                trigger,
                triggeringPlayer != null ? triggeringPlayer : Optional.empty(),
                targets,
                resolvedOrigin,
                currentTick,
                ability,
                phase,
                session::registerMinion);

        // 5. Obtener efecto del registro
        AbilityEffect effect = effectRegistry.get(ability.effectType());
        if (effect == null) {
            logger.warning("[BetterDragon] No existe ejecutor para el tipo de efecto: " + ability.effectType());
            return false;
        }

        // 6. Ejecutar telegrafiado sensorial previo y efecto físico (PRIN-02, EXP-006)
        if (ability.hasTelegraph()) {
            TelegraphDefinition telegraph = ability.telegraph();
            World world = resolvedOrigin.getWorld();
            if (world != null) {
                try {
                    world.spawnParticle(
                            telegraph.particle(),
                            resolvedOrigin,
                            Math.max(1, telegraph.particleCount()),
                            telegraph.particleRadius(),
                            0.5,
                            telegraph.particleRadius(),
                            0.05);
                    world.playSound(
                            resolvedOrigin,
                            telegraph.sound(),
                            telegraph.soundVolume(),
                            telegraph.soundPitch());
                } catch (Exception ignored) {
                }
            }

            if (telegraph.durationTicks() > 0) {
                CancellableTask cancellable = delayedTaskScheduler.schedule(() -> {
                    if (session == null || !session.isActive() || session.isTerminal()) {
                        return;
                    }
                    try {
                        effect.execute(context);
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "[BetterDragon] Error durante la ejecución diferida de '" + abilityId + "': " + e.getMessage(), e);
                    }
                }, telegraph.durationTicks());
                session.registerPendingTask(cancellable);
            } else {
                try {
                    effect.execute(context);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "[BetterDragon] Error durante la ejecución de la habilidad '" + abilityId + "': " + e.getMessage(), e);
                    return false;
                }
            }
        } else {
            try {
                effect.execute(context);
            } catch (Exception e) {
                logger.log(Level.WARNING, "[BetterDragon] Error durante la ejecución de la habilidad '" + abilityId + "': " + e.getMessage(), e);
                return false;
            }
        }

        // 7. Actualizar cooldown tras ejecución exitosa (Soft Enrage Cooldown Multiplier)
        long baseCooldown = ability.cooldownTicks();
        double multiplier = (session != null && session.getConfigSnapshot() != null
                && session.getConfigSnapshot().dragonDefinition() != null
                && session.getConfigSnapshot().dragonDefinition().enrage() != null)
                ? session.getConfigSnapshot().dragonDefinition().enrage().cooldownMultiplier()
                : 1.0;
        boolean enrageActive = session != null && session.isEnrageActive();
        long effectiveCooldown = calculateEffectiveCooldown(baseCooldown, enrageActive, multiplier);
        cooldownTracker.setCooldown(abilityId, currentTick, effectiveCooldown);

        if (trigger == AbilityTrigger.ON_DAMAGE && attackerUuid != null) {
            long attackerCooldown = Math.max(1L, ability.getIntProperty("attacker_cooldown_ticks", 100)); // TUNING_CANDIDATE: 5.0s
            cooldownTracker.setAttackerCooldown(abilityId, attackerUuid, currentTick, attackerCooldown);
        }
        return true;
    }

    /**
     * Evalúa y ejecuta las habilidades sincronizadas con la fase de vuelo de Paper (CAND-03, CAND-04).
     *
     * @param flightPhase fase de vuelo nativa de Paper a la que transicionó el dragón
     * @param phase       fase de combate activa
     * @param session     sesión de batalla activa
     * @param dragon      entidad física del dragón
     * @param currentTick tick lógico actual
     */
    public void triggerFlightPhaseAbilities(
            org.bukkit.entity.EnderDragon.Phase flightPhase,
            PhaseDefinition phase,
            BattleSession session,
            EnderDragon dragon,
            long currentTick) {
        if (flightPhase == null || phase == null || session == null || dragon == null) {
            return;
        }

        for (String abilityId : phase.abilityIds()) {
            AbilityDefinition ability = abilityCatalog.get(abilityId);
            if (ability != null && ability.trigger() == AbilityTrigger.ON_FLIGHT_PHASE) {
                String targetFlightPhase = ability.getStringProperty("flight_phase", "");
                if (targetFlightPhase.equalsIgnoreCase(flightPhase.name())) {
                    if (cooldownTracker.isReady(abilityId, currentTick)) {
                        executeAbility(abilityId, phase, session, dragon, AbilityTrigger.ON_FLIGHT_PHASE,
                                Optional.empty(), Optional.empty(), currentTick);
                    }
                }
            }
        }
    }

    /**
     * Evalúa y ejecuta contrataques reactivos por daño recibido (CAND-05).
     *
     * @param phase          fase de combate activa
     * @param session        sesión de batalla activa
     * @param dragon         entidad física del dragón
     * @param attacker       jugador responsable del ataque
     * @param isRanged       true si el daño provino de un proyectil o ataque a distancia
     * @param damageLocation ubicación donde impactó el ataque
     * @param currentTick    tick lógico actual
     */
    public void triggerDamageAbilities(
            PhaseDefinition phase,
            BattleSession session,
            EnderDragon dragon,
            Player attacker,
            boolean isRanged,
            Location damageLocation,
            long currentTick) {
        if (phase == null || session == null || dragon == null || attacker == null) {
            return;
        }

        for (String abilityId : phase.abilityIds()) {
            AbilityDefinition ability = abilityCatalog.get(abilityId);
            if (ability != null && ability.trigger() == AbilityTrigger.ON_DAMAGE) {
                boolean rangedOnly = ability.getBooleanProperty("ranged_only", true); // CAND-05: disuade campeo a distancia
                if (rangedOnly && !isRanged) {
                    continue;
                }

                double chance = Math.clamp(ability.getDoubleProperty("chance", 1.0), 0.0, 1.0);
                if (chance < 1.0 && java.util.concurrent.ThreadLocalRandom.current().nextDouble() > chance) {
                    continue;
                }

                if (cooldownTracker.isReady(abilityId, attacker.getUniqueId(), currentTick)) {
                    executeAbility(abilityId, phase, session, dragon, AbilityTrigger.ON_DAMAGE,
                            Optional.of(attacker), Optional.ofNullable(damageLocation), currentTick);
                }
            }
        }
    }

    /**
     * Evalúa y ejecuta las habilidades de tipo PERIODIC configuradas para la fase activa.
     */
    public void tickPeriodicAbilities(PhaseDefinition phase, BattleSession session, EnderDragon dragon, long currentTick) {
        if (phase == null || session == null || dragon == null) {
            return;
        }

        for (String abilityId : phase.abilityIds()) {
            AbilityDefinition ability = abilityCatalog.get(abilityId);
            if (ability != null && ability.trigger() == AbilityTrigger.PERIODIC) {
                if (cooldownTracker.isReady(abilityId, currentTick)) {
                    executeAbility(abilityId, phase, session, dragon, AbilityTrigger.PERIODIC,
                            Optional.empty(), Optional.empty(), currentTick);
                }
            }
        }
    }

    /**
     * Ejecuta las habilidades de tipo ON_PHASE_ENTER para la fase activa.
     */
    public void triggerPhaseEnterAbilities(PhaseDefinition phase, BattleSession session, EnderDragon dragon, long currentTick) {
        if (phase == null || session == null || dragon == null) {
            return;
        }

        for (String abilityId : phase.abilityIds()) {
            AbilityDefinition ability = abilityCatalog.get(abilityId);
            if (ability != null && ability.trigger() == AbilityTrigger.ON_PHASE_ENTER) {
                executeAbility(abilityId, phase, session, dragon, AbilityTrigger.ON_PHASE_ENTER,
                        Optional.empty(), Optional.empty(), currentTick);
            }
        }
    }

    /**
     * Reinicia los cooldowns de todas las habilidades (al transicionar de fase).
     */
    public void resetCooldowns() {
        cooldownTracker.reset();
    }

    public AbilityCooldownTracker getCooldownTracker() {
        return cooldownTracker;
    }

    public Map<String, AbilityDefinition> getAbilityCatalog() {
        return abilityCatalog;
    }

    public TargetSelector getTargetSelector() {
        return targetSelector;
    }

    public LocationResolver getLocationResolver() {
        return locationResolver;
    }

    /**
     * Calcula de forma matemáticamente segura el cooldown efectivo aplicando el multiplicador
     * transversal de Soft Enrage.
     * <p>
     * Principios:
     * <ul>
     *   <li>Si Enrage no está activo o multiplier es inválido/no positivo, preserva el cooldown base.</li>
     *   <li>Si baseCooldown > 0, garantiza que el cooldown efectivo sea al menos de 1 tick.</li>
     *   <li>Si baseCooldown <= 0, retorna 0 (sin cooldown).</li>
     *   <li>No modifica la definición inmutable de la habilidad.</li>
     * </ul>
     *
     * @param baseCooldown       cooldown base configurado en la habilidad
     * @param enrageActive       true si la batalla se encuentra en estado Enrage
     * @param cooldownMultiplier multiplicador de tiempo de recarga
     * @return cooldown efectivo en ticks
     */
    public static long calculateEffectiveCooldown(long baseCooldown, boolean enrageActive, double cooldownMultiplier) {
        if (baseCooldown <= 0) {
            return 0;
        }
        if (!enrageActive || !Double.isFinite(cooldownMultiplier) || cooldownMultiplier <= 0.0) {
            return baseCooldown;
        }
        return Math.max(1L, Math.round(baseCooldown * cooldownMultiplier));
    }
}
