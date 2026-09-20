package maurxp.betterdragon.config;

import maurxp.betterdragon.arena.ArenaDefinition;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servicio centralizado para la gestión de la configuración global activa en memoria.
 * <p>
 * Principios:
 * <ul>
 *   <li><b>Fail-Safe / Recarga Atómica:</b> Si un intento de recarga de {@code config.yml} o {@code arenas.yml}
 *       contiene errores de sintaxis o validación, la nueva configuración se rechaza completamente y
 *       la configuración activa previa permanece 100% intacta en memoria.</li>
 *   <li><b>Emisión de Snapshots Desacoplados:</b> Proporciona instantáneas inmutables
 *       ({@link BattleConfigurationSnapshot}) que congelan los parámetros del combate y de la arena
 *       para que las sesiones activas no dependan de referencias mutables globales.</li>
 *   <li><b>Sin God Object:</b> No maneja combate, ni comandos, ni spawn, ni persistencia en SQLite.</li>
 * </ul>
 *
 * @author maurxp
 */
public class ConfigurationService {

    private final Logger logger;
    private BetterDragonConfig activeConfig;
    private ArenaConfigurationSnapshot activeArenas;

    public ConfigurationService(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "El logger no puede ser nulo");
        this.activeConfig = BetterDragonConfig.defaults();
        this.activeArenas = ArenaConfigurationSnapshot.defaults();
    }

    public ConfigurationService(Logger logger, BetterDragonConfig initialConfig) {
        this.logger = Objects.requireNonNull(logger, "El logger no puede ser nulo");
        this.activeConfig = Objects.requireNonNull(initialConfig, "La configuración inicial no puede ser nula");
        this.activeArenas = ArenaConfigurationSnapshot.defaults();
    }

    public ConfigurationService(Logger logger, BetterDragonConfig initialConfig, ArenaConfigurationSnapshot initialArenas) {
        this.logger = Objects.requireNonNull(logger, "El logger no puede ser nulo");
        this.activeConfig = Objects.requireNonNull(initialConfig, "La configuración inicial no puede ser nula");
        this.activeArenas = Objects.requireNonNull(initialArenas, "La configuración de arenas no puede ser nula");
    }

    /**
     * Carga inicial de configuración desde los archivos de configuración y arenas.
     *
     * @param configFile archivo config.yml
     * @param arenasFile archivo arenas.yml
     * @throws ConfigValidationException si los datos en alguno de los archivos son inválidos
     * @throws IOException               si ocurre un error de lectura
     */
    public void loadInitial(File configFile, File arenasFile) throws IOException, ConfigValidationException {
        Objects.requireNonNull(configFile, "El archivo configFile no puede ser nulo");
        Objects.requireNonNull(arenasFile, "El archivo arenasFile no puede ser nulo");

        BetterDragonConfig config = ConfigurationLoader.load(configFile);
        ArenaConfigurationSnapshot arenas = arenasFile.exists()
                ? ArenaConfigurationLoader.load(arenasFile)
                : ArenaConfigurationSnapshot.defaults();

        this.activeConfig = config;
        this.activeArenas = arenas;

        logger.info("[BetterDragon] Configuración global cargada: portal.enabled="
                + activeConfig.portalEnabled() + ", logging.level="
                + activeConfig.loggingLevel() + ", logging.debug="
                + activeConfig.debugLogging() + ", arenas="
                + activeArenas.arenas().keySet());
    }

    /**
     * Carga inicial de configuración desde el archivo del plugin (retrocompatibilidad).
     *
     * @param configFile archivo config.yml
     * @throws ConfigValidationException si los datos en el archivo son inválidos
     * @throws IOException               si ocurre un error de lectura
     */
    public void loadInitial(File configFile) throws IOException, ConfigValidationException {
        Objects.requireNonNull(configFile, "El archivo configFile no puede ser nulo");
        File arenasFile = new File(configFile.getParentFile(), "arenas.yml");
        loadInitial(configFile, arenasFile);
    }

    /**
     * Intenta recargar atómicamente {@code config.yml} y {@code arenas.yml}.
     * <p>
     * Si ambos son válidos, reemplaza las configuraciones activas en memoria.
     * Si cualquiera de los dos es inválido o falla, <b>se mantienen intactas las configuraciones previas</b>
     * de ambos archivos y se reporta el error sin alterar ninguna sesión activa.
     *
     * @param configFile archivo config.yml
     * @param arenasFile archivo arenas.yml
     * @return true si la recarga fue exitosa; false si fue rechazada
     */
    public boolean reload(File configFile, File arenasFile) {
        Objects.requireNonNull(configFile, "El archivo configFile no puede ser nulo");
        Objects.requireNonNull(arenasFile, "El archivo arenasFile no puede ser nulo");

        try {
            BetterDragonConfig newConfig = ConfigurationLoader.load(configFile);
            ArenaConfigurationSnapshot newArenas = arenasFile.exists()
                    ? ArenaConfigurationLoader.load(arenasFile)
                    : this.activeArenas;

            this.activeConfig = newConfig;
            this.activeArenas = newArenas;
            logger.info("[BetterDragon] Configuración global y arenas recargadas exitosamente.");
            return true;
        } catch (ConfigValidationException e) {
            logger.log(Level.SEVERE, "[BetterDragon] Recarga abortada. La configuración contiene valores inválidos:\n" + e.getMessage());
            logger.warning("[BetterDragon] Se mantiene activa la configuración anterior.");
            return false;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[BetterDragon] Error inesperado al recargar configuraciones: " + e.getMessage(), e);
            logger.warning("[BetterDragon] Se mantiene activa la configuración anterior.");
            return false;
        }
    }

    /**
     * Intenta recargar la configuración desde el archivo de forma atómica (retrocompatibilidad).
     *
     * @param configFile archivo config.yml a releer
     * @return true si la recarga fue exitosa y la configuración fue reemplazada; false si fue rechazada
     */
    public boolean reload(File configFile) {
        Objects.requireNonNull(configFile, "El archivo configFile no puede ser nulo");
        File arenasFile = new File(configFile.getParentFile(), "arenas.yml");
        return reload(configFile, arenasFile);
    }

    /**
     * Intenta recargar la configuración desde el archivo de forma atómica, lanzando la excepción
     * si falla pero garantizando que la configuración activa previa no es alterada.
     *
     * @param configFile archivo config.yml
     * @return la nueva configuración activa si fue exitoso
     * @throws ConfigValidationException si la validación falla
     * @throws IOException               si falla la lectura I/O
     */
    public BetterDragonConfig reloadOrThrow(File configFile) throws IOException, ConfigValidationException {
        Objects.requireNonNull(configFile, "El archivo configFile no puede ser nulo");
        BetterDragonConfig newConfig = ConfigurationLoader.load(configFile);
        this.activeConfig = newConfig;
        return newConfig;
    }

    /**
     * Retorna la configuración global actualmente activa.
     *
     * @return configuración activa inmutable
     */
    public BetterDragonConfig getActiveConfig() {
        return activeConfig;
    }

    /**
     * Retorna el snapshot inmutable de arenas actualmente activo.
     *
     * @return snapshot inmutable de arenas
     */
    public ArenaConfigurationSnapshot getActiveArenas() {
        return activeArenas;
    }

    /**
     * Obtiene una definición de arena por ID desde la configuración activa.
     *
     * @param arenaId identificador de la arena
     * @return Optional con la arena, o vacío si no existe
     */
    public Optional<ArenaDefinition> getArena(String arenaId) {
        return activeArenas.getArena(arenaId);
    }

    /**
     * Retorna la definición de arena predeterminada activa.
     *
     * @return ArenaDefinition default
     */
    public ArenaDefinition getDefaultArena() {
        return activeArenas.getDefaultArena();
    }

    /**
     * Genera un snapshot inmutable para una nueva sesión de batalla asociando la arena indicada.
     *
     * @param arenaId identificador de la arena a congelar
     * @return nuevo snapshot congelado con la arena configurada
     */
    public BattleConfigurationSnapshot createBattleSnapshot(String arenaId) {
        ArenaDefinition arena = activeArenas.getArenaOrDefault(arenaId);
        return createBattleSnapshot(arena);
    }

    /**
     * Genera un snapshot inmutable para una nueva sesión de batalla asociando la definición de arena exacta.
     *
     * @param arena definición de arena a congelar
     * @return nuevo snapshot congelado con la arena indicada
     */
    public BattleConfigurationSnapshot createBattleSnapshot(ArenaDefinition arena) {
        Objects.requireNonNull(arena, "La definición de arena no puede ser nula");
        return new BattleConfigurationSnapshot(
                activeConfig.portalEnabled(),
                activeConfig.debugLogging(),
                activeConfig.dragonDefinition(),
                arena
        );
    }

    /**
     * Genera un snapshot inmutable para una nueva sesión de batalla con la arena por defecto.
     *
     * @return nuevo snapshot congelado
     */
    public BattleConfigurationSnapshot createBattleSnapshot() {
        return createBattleSnapshot(activeArenas.defaultArenaId());
    }
}
