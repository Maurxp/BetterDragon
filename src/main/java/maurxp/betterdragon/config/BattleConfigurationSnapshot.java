package maurxp.betterdragon.config;

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
 * @param portalEnabled si BetterDragon tiene permitido gestionar el portal de
 *                      salida tras la victoria
 * @param debugLogging  si el combate debe emitir diagnósticos detallados en
 *                      consola
 * @author maurxp
 */
public record BattleConfigurationSnapshot(
        boolean portalEnabled,
        boolean debugLogging) {

    /**
     * Crea un snapshot con valores predeterminados seguros para tests o
     * inicializaciones mínimas.
     *
     * @return snapshot con defaults
     */
    public static BattleConfigurationSnapshot defaults() {
        return new BattleConfigurationSnapshot(false, false);
    }
}
