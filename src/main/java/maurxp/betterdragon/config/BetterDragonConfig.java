package maurxp.betterdragon.config;

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
 * <li><b>Generador de Snapshots:</b> Es capaz de producir un
 * {@link BattleConfigurationSnapshot}
 * aislado para ser inyectado en una {@code BattleSession}.</li>
 * </ul>
 *
 * @param portalEnabled    si la gestión del portal central de bedrock está
 *                         habilitada tras victoria
 * @param loggingLevel     nivel de log en consola (INFO, WARNING, SEVERE, OFF)
 * @param debugLogging     si se emiten logs detallados de depuración
 * @param dragonDefinition definición de fases y habilidades del dragón
 * @author maurxp
 */
public record BetterDragonConfig(
        boolean portalEnabled,
        String loggingLevel,
        boolean debugLogging,
        DragonDefinition dragonDefinition) {

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
        Objects.requireNonNull(dragonDefinition, "dragonDefinition no puede ser nulo");
    }

    /**
     * Constructor retrocompatible para inicializaciones sin definición explícita de dragón.
     */
    public BetterDragonConfig(boolean portalEnabled, String loggingLevel, boolean debugLogging) {
        this(portalEnabled, loggingLevel, debugLogging, DragonDefinition.defaults());
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
                DragonDefinition.defaults());
    }

    /**
     * Produce una instantánea (snapshot) inmutable para una nueva sesión de
     * batalla.
     *
     * @return snapshot congelado independiente de futuros cambios globales
     */
    public BattleConfigurationSnapshot toBattleSnapshot() {
        return new BattleConfigurationSnapshot(
                this.portalEnabled,
                this.debugLogging,
                this.dragonDefinition);
    }
}
