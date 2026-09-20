package maurxp.betterdragon.config;

import java.util.Objects;

/**
 * Instantánea (Snapshot) inmutable de la configuración consumida por una sesión
 * de batalla.
 * <p>
 * Invariante Fundamental:
 * Una vez creada la batalla, este snapshot permanece inmutable durante todo su
 * ciclo de vida.
 * Las recargas globales posteriores de {@code config.yml} (mediante recarga de
 * servidor o comandos)
 * jamás alteran los parámetros de una batalla en curso.
 *
 * @param portalEnabled    si BetterDragon tiene permitido gestionar el portal de
 *                         salida tras la victoria
 * @param debugLogging     si el combate debe emitir diagnósticos detallados en
 *                         consola
 * @param dragonDefinition definición inmutable de fases y habilidades del dragón
 * @author maurxp
 */
public record BattleConfigurationSnapshot(
        boolean portalEnabled,
        boolean debugLogging,
        DragonDefinition dragonDefinition) {

    public BattleConfigurationSnapshot {
        Objects.requireNonNull(dragonDefinition, "dragonDefinition no puede ser nulo");
    }

    /**
     * Constructor de conveniencia retrocompatible para tests y llamadas previas.
     *
     * @param portalEnabled si la gestión del portal está habilitada
     * @param debugLogging  si el log de depuración está activo
     */
    public BattleConfigurationSnapshot(boolean portalEnabled, boolean debugLogging) {
        this(portalEnabled, debugLogging, DragonDefinition.defaults());
    }

    /**
     * Crea un snapshot con valores predeterminados seguros para tests o
     * inicializaciones mínimas.
     *
     * @return snapshot con defaults
     */
    public static BattleConfigurationSnapshot defaults() {
        return new BattleConfigurationSnapshot(false, false, DragonDefinition.defaults());
    }
}
