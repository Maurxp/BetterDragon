package maurxp.betterdragon.config;

import maurxp.betterdragon.arena.ArenaDefinition;

import java.util.Objects;

/**
 * Instantánea (Snapshot) inmutable de la configuración consumida por una sesión
 * de batalla.
 * <p>
 * Invariante Fundamental:
 * Una vez creada la batalla, este snapshot permanece inmutable durante todo su
 * ciclo de vida.
 * Las recargas globales posteriores de {@code config.yml} y {@code arenas.yml}
 * jamás alteran los parámetros, la definición del dragón ni la definición de arena de una batalla en curso.
 *
 * @param portalEnabled    si BetterDragon tiene permitido gestionar el portal de
 *                         salida tras la victoria
 * @param debugLogging     si el combate debe emitir diagnósticos detallados en
 *                         consola
 * @param dragonDefinition definición inmutable de fases y habilidades del dragón
 * @param arenaDefinition  definición inmutable de la arena asociada a la batalla
 * @param rewardConfig     configuración inmutable de recompensas
 * @param effectiveStats   estadísticas efectivas del dragón computadas al inicio de batalla
 * @author maurxp
 */
public record BattleConfigurationSnapshot(
        boolean portalEnabled,
        boolean debugLogging,
        DragonDefinition dragonDefinition,
        ArenaDefinition arenaDefinition,
        RewardConfigurationSnapshot rewardConfig,
        EffectiveDragonStats effectiveStats) {

    public BattleConfigurationSnapshot {
        Objects.requireNonNull(dragonDefinition, "dragonDefinition no puede ser nulo");
        Objects.requireNonNull(arenaDefinition, "arenaDefinition no puede ser nulo");
        rewardConfig = rewardConfig != null ? rewardConfig : RewardConfigurationSnapshot.defaults();
        effectiveStats = effectiveStats != null ? effectiveStats : EffectiveDragonStats.from(dragonDefinition, 1);
    }

    /**
     * Constructor retrocompatible sin estadísticas efectivas explícitas (calculadas para 1 jugador).
     */
    public BattleConfigurationSnapshot(
            boolean portalEnabled,
            boolean debugLogging,
            DragonDefinition dragonDefinition,
            ArenaDefinition arenaDefinition,
            RewardConfigurationSnapshot rewardConfig) {
        this(portalEnabled, debugLogging, dragonDefinition, arenaDefinition, rewardConfig, EffectiveDragonStats.from(dragonDefinition, 1));
    }

    /**
     * Constructor retrocompatible para inicializaciones sin especificación explícita de recompensas ni estadísticas.
     */
    public BattleConfigurationSnapshot(
            boolean portalEnabled,
            boolean debugLogging,
            DragonDefinition dragonDefinition,
            ArenaDefinition arenaDefinition) {
        this(portalEnabled, debugLogging, dragonDefinition, arenaDefinition, RewardConfigurationSnapshot.defaults());
    }

    /**
     * Constructor de conveniencia retrocompatible para tests y llamadas previas
     * sin especificación explícita de arena.
     *
     * @param portalEnabled    si la gestión del portal está habilitada
     * @param debugLogging     si el log de depuración está activo
     * @param dragonDefinition definición del dragón
     */
    public BattleConfigurationSnapshot(
            boolean portalEnabled,
            boolean debugLogging,
            DragonDefinition dragonDefinition) {
        this(portalEnabled, debugLogging, dragonDefinition, ArenaDefinition.defaults(), RewardConfigurationSnapshot.defaults());
    }

    /**
     * Constructor de conveniencia retrocompatible para tests y llamadas previas.
     *
     * @param portalEnabled si la gestión del portal está habilitada
     * @param debugLogging  si el log de depuración está activo
     */
    public BattleConfigurationSnapshot(boolean portalEnabled, boolean debugLogging) {
        this(portalEnabled, debugLogging, DragonDefinition.defaults(), ArenaDefinition.defaults(), RewardConfigurationSnapshot.defaults());
    }

    /**
     * Retorna las estadísticas efectivas computadas del dragón para esta batalla.
     */
    public EffectiveDragonStats effectiveDragonStats() {
        return effectiveStats;
    }

    /**
     * Crea un snapshot con valores predeterminados seguros para tests o
     * inicializaciones mínimas.
     *
     * @return snapshot con defaults
     */
    public static BattleConfigurationSnapshot defaults() {
        return new BattleConfigurationSnapshot(
                false,
                false,
                DragonDefinition.defaults(),
                ArenaDefinition.defaults(),
                RewardConfigurationSnapshot.defaults(),
                EffectiveDragonStats.from(DragonDefinition.defaults(), 1)
        );
    }
}
