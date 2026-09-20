package maurxp.betterdragon.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Validador y cargador tipado de configuración.
 * <p>
 * Transforma un {@link FileConfiguration} o archivo YAML en un modelo
 * {@link BetterDragonConfig}
 * inmutable y fuertemente tipado.
 * <p>
 * Principios:
 * <ul>
 * <li><b>Validación Estricta de Tipos:</b> Detecta valores erróneos (e.g.
 * {@code portal.enabled: "banana"})
 * sin realizar conversiones silenciosas ni fallar silenciosamente.</li>
 * <li><b>Fail-Fast Informativo:</b> Recolecta todos los errores de validación
 * encontrados y
 * los reporta conjuntamente en un {@link ConfigValidationException}.</li>
 * <li><b>Tolerancia a Claves Desconocidas:</b> No rechaza secciones o claves
 * adicionales
 * para permitir extensiones futuras o comentarios sin romper el plugin.</li>
 * </ul>
 *
 * @author maurxp
 */
public final class ConfigurationLoader {

    private static final Set<String> ALLOWED_LOG_LEVELS = Set.of("INFO", "WARNING", "SEVERE", "OFF");

    private ConfigurationLoader() {
        // Clase de utilidad / stateless
    }

    /**
     * Carga y valida la configuración desde un archivo YAML.
     *
     * @param file archivo de configuración
     * @return configuración tipada y validada
     * @throws ConfigValidationException si el YAML está mal formado o contiene
     *                                   valores inválidos
     * @throws IOException               si ocurre un error de lectura de archivo
     */
    public static BetterDragonConfig load(File file) throws IOException, ConfigValidationException {
        Objects.requireNonNull(file, "El archivo de configuración no puede ser nulo");
        if (!file.exists()) {
            throw new IllegalArgumentException("El archivo de configuración no existe: " + file.getAbsolutePath());
        }

        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (InvalidConfigurationException e) {
            throw new ConfigValidationException("El archivo YAML está mal formado o corrupto: " + e.getMessage());
        }

        return validate(yaml);
    }

    /**
     * Carga y valida la configuración desde un {@link Reader}.
     *
     * @param reader lector de contenido YAML
     * @return configuración tipada y validada
     * @throws ConfigValidationException si el YAML está mal formado o contiene
     *                                   valores inválidos
     * @throws IOException               si ocurre un error de lectura
     */
    public static BetterDragonConfig load(Reader reader) throws IOException, ConfigValidationException {
        Objects.requireNonNull(reader, "El Reader no puede ser nulo");

        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(reader);
        } catch (InvalidConfigurationException e) {
            throw new ConfigValidationException("El contenido YAML está mal formado o corrupto: " + e.getMessage());
        }

        return validate(yaml);
    }

    /**
     * Valida una instancia existente de {@link FileConfiguration} y extrae un
     * modelo tipado.
     *
     * @param config configuración Bukkit
     * @return configuración validada
     * @throws ConfigValidationException si se detectan tipos o valores inválidos
     */
    public static BetterDragonConfig validate(FileConfiguration config) throws ConfigValidationException {
        Objects.requireNonNull(config, "FileConfiguration no puede ser nulo");
        List<String> errors = new ArrayList<>();

        // 1. portal.enabled
        boolean portalEnabled = BetterDragonConfig.DEFAULT_PORTAL_ENABLED;
        if (config.contains("portal.enabled")) {
            Object rawPortal = config.get("portal.enabled");
            if (rawPortal instanceof Boolean b) {
                portalEnabled = b;
            } else {
                errors.add("portal.enabled debe ser un booleano (true o false). Se encontró: '" + rawPortal + "'");
            }
        }

        // 2. logging.level
        String loggingLevel = BetterDragonConfig.DEFAULT_LOGGING_LEVEL;
        if (config.contains("logging.level")) {
            Object rawLevel = config.get("logging.level");
            if (rawLevel instanceof String strLevel) {
                String upper = strLevel.toUpperCase().trim();
                if (ALLOWED_LOG_LEVELS.contains(upper)) {
                    loggingLevel = upper;
                } else {
                    errors.add("logging.level inválido: '" + strLevel + "'. Valores permitidos: " + ALLOWED_LOG_LEVELS);
                }
            } else {
                errors.add("logging.level debe ser un texto. Se encontró: '" + rawLevel + "'");
            }
        }

        // 3. logging.debug
        boolean debugLogging = BetterDragonConfig.DEFAULT_DEBUG_LOGGING;
        if (config.contains("logging.debug")) {
            Object rawDebug = config.get("logging.debug");
            if (rawDebug instanceof Boolean b) {
                debugLogging = b;
            } else {
                errors.add("logging.debug debe ser un booleano (true o false). Se encontró: '" + rawDebug + "'");
            }
        }

        if (!errors.isEmpty()) {
            throw new ConfigValidationException("Errores de validación en la configuración de BetterDragon", errors);
        }

        return new BetterDragonConfig(portalEnabled, loggingLevel, debugLogging);
    }
}
