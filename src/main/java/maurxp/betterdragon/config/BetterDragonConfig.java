package maurxp.betterdragon.config;

import maurxp.betterdragon.arena.ArenaDefinition;

import java.util.Objects;
import java.util.Set;

/**
 * Modelo inmutable de la configuración global activa de BetterDragon.
 * <p>
 * Principios:
 * <ul>
 * <li><b>Tipado Fuerte:</b> Los valores han sido parseados y validados; no
 * expone {@code FileConfiguration}.</li>
 * <li><b>Inmutabilidad:</b> Sus valores son definitivos y no pueden ser mutados
 * externamente.</li>
 * <li><b>Catálogo de Dragones:</b> Administra múltiples perfiles de dragón a través de {@link DragonCatalog}.</li>
 * <li><b>Generador de Snapshots:</b> Es capaz de producir un
 * {@link BattleConfigurationSnapshot}
 * aislado para ser inyectado en una {@code BattleSession}.</li>
 * </ul>
 *
 * @param portalEnabled si la gestión del portal central de bedrock está habilitada tras victoria
 * @param loggingLevel  nivel de log en consola (INFO, WARNING, SEVERE, OFF)
 * @param debugLogging  si se emiten logs detallados de depuración
 * @param dragonCatalog catálogo tipado de dragones configurados
 * @param rewardConfig  configuración inmutable de recompensas
 * @author maurxp
 */
public record BetterDragonConfig(
        boolean portalEnabled,
        String loggingLevel,
        boolean debugLogging,
        DragonCatalog dragonCatalog,
        RewardConfigurationSnapshot rewardConfig) {

    public static final boolean DEFAULT_PORTAL_ENABLED = false;
    public static final String DEFAULT_LOGGING_LEVEL = "INFO";
    public static final boolean DEFAULT_DEBUG_LOGGING = false;

    private static final Set<String> VALID_LOG_LEVELS = Set.of("INFO", "WARNING", "SEVERE", "OFF");

    public BetterDragonConfig {
        Objects.requireNonNull(loggingLevel, "loggingLevel no puede ser nulo");
        String upperLevel = loggingLevel.toUpperCase().trim();
        if (!VALID_LOG_LEVELS.contains(upperLevel)) {
            throw new IllegalArgumentException(
                    "Nivel de log inválido: '" + loggingLevel + "'. Valores permitidos: " + VALID_LOG_LEVELS);
        }
        loggingLevel = upperLevel;
        dragonCatalog = dragonCatalog != null ? dragonCatalog : DragonCatalog.defaults();
        rewardConfig = rewardConfig != null ? rewardConfig : RewardConfigurationSnapshot.defaults();
    }

    /**
     * Constructor retrocompatible que acepta una única definición de dragón.
     */
    public BetterDragonConfig(
            boolean portalEnabled,
            String loggingLevel,
            boolean debugLogging,
            DragonDefinition dragonDefinition,
            RewardConfigurationSnapshot rewardConfig
    ) {
        this(
                portalEnabled,
                loggingLevel,
                debugLogging,
                dragonDefinition != null ? DragonCatalog.of(dragonDefinition) : DragonCatalog.defaults(),
                rewardConfig
        );
    }

    /**
     * Constructor retrocompatible para compatibilidad con llamadas de fases anteriores.
     */
    public BetterDragonConfig(boolean portalEnabled, String loggingLevel, boolean debugLogging, DragonDefinition dragonDefinition) {
        this(portalEnabled, loggingLevel, debugLogging, dragonDefinition, RewardConfigurationSnapshot.defaults());
    }

    /**
     * Constructor retrocompatible para inicializaciones sin definición explícita de dragón ni recompensas.
     */
    public BetterDragonConfig(boolean portalEnabled, String loggingLevel, boolean debugLogging) {
        this(portalEnabled, loggingLevel, debugLogging, DragonCatalog.defaults(), RewardConfigurationSnapshot.defaults());
    }

    /**
     * Getter de compatibilidad con llamadas que solicitan el dragón predeterminado.
     *
     * @return DragonDefinition default del catálogo
     */
    public DragonDefinition dragonDefinition() {
        return dragonCatalog.getDefaultDefinition();
    }

    /**
     * Retorna la configuración global con los valores predeterminados seguros.
     *
     * @return configuración por defecto
     */
    public static BetterDragonConfig defaults() {
        return new BetterDragonConfig(
                DEFAULT_PORTAL_ENABLED,
                DEFAULT_LOGGING_LEVEL,
                DEFAULT_DEBUG_LOGGING,
                DragonCatalog.defaults(),
                RewardConfigurationSnapshot.defaults());
    }

    /**
     * Produce una instantánea (snapshot) inmutable para una nueva sesión de batalla usando la arena y dragón default.
     *
     * @return snapshot congelado independiente de futuros cambios globales
     */
    public BattleConfigurationSnapshot toBattleSnapshot() {
        DragonDefinition def = dragonCatalog.getDefaultDefinition();
        EffectiveDragonStats stats = EffectiveDragonStats.from(def, 1);
        return new BattleConfigurationSnapshot(
                this.portalEnabled,
                this.debugLogging,
                def,
                ArenaDefinition.defaults(),
                this.rewardConfig,
                stats);
    }
}
