package maurxp.betterdragon.ability;

import maurxp.betterdragon.ability.effect.AbilityEffect;
import maurxp.betterdragon.ability.effect.DamageEffect;
import maurxp.betterdragon.ability.effect.KnockbackEffect;
import maurxp.betterdragon.ability.effect.ParticleEffect;
import maurxp.betterdragon.ability.effect.SoundEffect;
import maurxp.betterdragon.battle.BattleSession;
import maurxp.betterdragon.phase.PhaseDefinition;
import org.bukkit.Location;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Motor central de ejecución de habilidades para una sesión de batalla.
 * <p>
 * Responsabilidades:
 * <ul>
 * <li>Consultar catálogo de habilidades provisto por el snapshot inmutable.</li>
 * <li>Comprobar y actualizar tiempos de recarga (cooldowns) en ticks lógicos.</li>
 * <li>Resolver objetivos y ubicaciones de origen.</li>
 * <li>Despachar efectos encapsulando excepciones para resiliencia total del servidor.</li>
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
    private final Logger logger;

    public AbilityEngine(
            Map<String, AbilityDefinition> abilityCatalog,
            AbilityCooldownTracker cooldownTracker,
            TargetSelector targetSelector,
            LocationResolver locationResolver,
            Map<AbilityEffectType, AbilityEffect> customEffectRegistry,
            Logger logger) {
        this.abilityCatalog = abilityCatalog != null ? Map.copyOf(abilityCatalog) : Map.of();
        this.cooldownTracker = Objects.requireNonNull(cooldownTracker, "cooldownTracker no puede ser nulo");
        this.targetSelector = Objects.requireNonNull(targetSelector, "targetSelector no puede ser nulo");
        this.locationResolver = Objects.requireNonNull(locationResolver, "locationResolver no puede ser nulo");
        this.logger = logger != null ? logger : Logger.getLogger("BetterDragon-AbilityEngine");

        this.effectRegistry = new EnumMap<>(AbilityEffectType.class);
        registerDefaultEffects();
        if (customEffectRegistry != null) {
            this.effectRegistry.putAll(customEffectRegistry);
        }
    }

    public AbilityEngine(Map<String, AbilityDefinition> abilityCatalog, Logger logger) {
        this(abilityCatalog, new AbilityCooldownTracker(), new TargetSelector(), new LocationResolver(), null, logger);
    }

    private void registerDefaultEffects() {
        effectRegistry.put(AbilityEffectType.DAMAGE, new DamageEffect());
        effectRegistry.put(AbilityEffectType.KNOCKBACK, new KnockbackEffect());
        effectRegistry.put(AbilityEffectType.PARTICLE, new ParticleEffect());
        effectRegistry.put(AbilityEffectType.SOUND, new SoundEffect());
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

        // 2. Validar cooldown en ticks lógicos
        if (!cooldownTracker.isReady(abilityId, currentTick)) {
            return false;
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

        // 4. Construir contexto inmutable
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
                phase);

        // 5. Obtener efecto del registro
        AbilityEffect effect = effectRegistry.get(ability.effectType());
        if (effect == null) {
            logger.warning("[BetterDragon] No existe ejecutor para el tipo de efecto: " + ability.effectType());
            return false;
        }

        // 6. Ejecutar de forma segura capturando solo Exception (R1: no silenciar Throwable/Error graves)
        try {
            effect.execute(context);
        } catch (Exception e) {
            logger.log(Level.WARNING, "[BetterDragon] Error durante la ejecución de la habilidad '" + abilityId + "': " + e.getMessage(), e);
            return false;
        }

        // 7. Actualizar cooldown tras ejecución exitosa
        cooldownTracker.setCooldown(abilityId, currentTick, ability.cooldownTicks());
        return true;
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
}
