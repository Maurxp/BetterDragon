package maurxp.betterdragon.config;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servicio centralizado para la gestión de la configuración global activa en memoria.
 * <p>
 * Principios:
 * <ul>
 *   <li><b>Fail-Safe / Recarga Atómica:</b> Si un intento de recarga contiene errores de sintaxis
 *       o validación, la nueva configuración se rechaza completamente y la configuración activa previa
 *       permanece 100% intacta en memoria.</li>
 *   <li><b>Emisión de Snapshots Desacoplados:</b> Proporciona instantáneas inmutables
 *       ({@link BattleConfigurationSnapshot}) para que las sesiones activas no dependan
 *       de referencias mutables globales.</li>
 *   <li><b>Sin God Object:</b> No maneja combate, ni comandos, ni spawn, ni persistencia en SQLite.</li>
 * </ul>
 *
 * @author maurxp
 */
public class ConfigurationService {

    private final Logger logger;
    private BetterDragonConfig activeConfig;

    public ConfigurationService(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "El logger no puede ser nulo");
        this.activeConfig = BetterDragonConfig.defaults();
    }

    public ConfigurationService(Logger logger, BetterDragonConfig initialConfig) {
        this.logger = Objects.requireNonNull(logger, "El logger no puede ser nulo");
        this.activeConfig = Objects.requireNonNull(initialConfig, "La configuración inicial no puede ser nula");
    }

    /**
     * Carga inicial de configuración desde el archivo del plugin.
     * Si el archivo no existe o contiene errores críticos, lanza la excepción correspondiente.
     *
     * @param configFile archivo config.yml
     * @throws ConfigValidationException si los datos en el archivo son inválidos
     * @throws IOException               si ocurre un error de lectura
     */
    public void loadInitial(File configFile) throws IOException, ConfigValidationException {
        Objects.requireNonNull(configFile, "El archivo configFile no puede ser nulo");
        this.activeConfig = ConfigurationLoader.load(configFile);
        logger.info("[BetterDragon] Configuración global cargada: portal.enabled="
                + activeConfig.portalEnabled() + ", logging.level="
                + activeConfig.loggingLevel() + ", logging.debug="
                + activeConfig.debugLogging());
    }

    /**
     * Intenta recargar la configuración desde el archivo de forma atómica.
     * <p>
     * Si la nueva configuración es válida, reemplaza la activa en memoria.
     * Si la nueva configuración es inválida o corrupta, <b>la configuración anterior se mantiene
     * activa</b> y se registra el error correspondiente sin afectar el estado en ejecución.
     *
     * @param configFile archivo config.yml a releer
     * @return true si la recarga fue exitosa y la configuración fue reemplazada; false si fue rechazada
     */
    public boolean reload(File configFile) {
        Objects.requireNonNull(configFile, "El archivo configFile no puede ser nulo");
        try {
            BetterDragonConfig newConfig = ConfigurationLoader.load(configFile);
            this.activeConfig = newConfig;
            logger.info("[BetterDragon] Configuración global recargada exitosamente.");
            return true;
        } catch (ConfigValidationException e) {
            logger.log(Level.SEVERE, "[BetterDragon] Recarga abortada. La configuración contiene valores inválidos:\n" + e.getMessage());
            logger.warning("[BetterDragon] Se mantiene activa la configuración anterior.");
            return false;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[BetterDragon] Error inesperado al recargar " + configFile.getName() + ": " + e.getMessage(), e);
            logger.warning("[BetterDragon] Se mantiene activa la configuración anterior.");
            return false;
        }
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
     * Genera un snapshot inmutable para una nueva sesión de batalla a partir
     * de la configuración global activa en este instante.
     *
     * @return nuevo snapshot congelado
     */
    public BattleConfigurationSnapshot createBattleSnapshot() {
        return activeConfig.toBattleSnapshot();
    }
}
