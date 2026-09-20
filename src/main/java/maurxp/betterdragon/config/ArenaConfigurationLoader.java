package maurxp.betterdragon.config;

import maurxp.betterdragon.arena.ArenaBounds;
import maurxp.betterdragon.arena.ArenaDefinition;
import maurxp.betterdragon.arena.ArenaRuleSet;
import maurxp.betterdragon.arena.Vector3d;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Cargador y validador fuertemente tipado para el archivo {@code arenas.yml}.
 * <p>
 * Principios:
 * <ul>
 *   <li><b>Validación Estricta:</b> Recolecta todos los errores de sintaxis y coherencia espacial
 *       lanzando {@link ConfigValidationException}.</li>
 *   <li><b>Inmutabilidad:</b> Produce un {@link ArenaConfigurationSnapshot} completamente aislado.</li>
 *   <li><b>Sin Mutación Silenciosa:</b> No asigna valores por defecto inventados cuando la configuración está corrupta.</li>
 * </ul>
 *
 * @author maurxp
 */
public final class ArenaConfigurationLoader {

    private ArenaConfigurationLoader() {
        // Clase de utilidad
    }

    /**
     * Carga y valida la configuración de arenas desde un archivo.
     *
     * @param file archivo arenas.yml
     * @return snapshot inmutable de arenas
     * @throws ConfigValidationException si contiene errores de validación
     * @throws IOException               si ocurre un error de lectura de archivo
     */
    public static ArenaConfigurationSnapshot load(File file) throws IOException, ConfigValidationException {
        Objects.requireNonNull(file, "El archivo de configuración no puede ser nulo");
        if (!file.exists()) {
            throw new IllegalArgumentException("El archivo de arenas no existe: " + file.getAbsolutePath());
        }

        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (InvalidConfigurationException e) {
            throw new ConfigValidationException("El archivo YAML de arenas está mal formado o corrupto: " + e.getMessage());
        }

        return validate(yaml);
    }

    /**
     * Carga y valida la configuración de arenas desde un {@link Reader}.
     *
     * @param reader lector de contenido YAML
     * @return snapshot inmutable de arenas
     * @throws ConfigValidationException si contiene errores de validación
     * @throws IOException               si ocurre un error de lectura
     */
    public static ArenaConfigurationSnapshot load(Reader reader) throws IOException, ConfigValidationException {
        Objects.requireNonNull(reader, "El Reader no puede ser nulo");

        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(reader);
        } catch (InvalidConfigurationException e) {
            throw new ConfigValidationException("El contenido YAML de arenas está mal formado o corrupto: " + e.getMessage());
        }

        return validate(yaml);
    }

    /**
     * Valida la estructura y coherencia de las arenas declaradas.
     *
     * @param yaml configuración YamlConfiguration parseada
     * @return snapshot inmutable de arenas
     * @throws ConfigValidationException si se detectan errores
     */
    public static ArenaConfigurationSnapshot validate(YamlConfiguration yaml) throws ConfigValidationException {
        Objects.requireNonNull(yaml, "YamlConfiguration no puede ser nulo");

        List<String> errors = new ArrayList<>();
        ConfigurationSection arenasSection = yaml.getConfigurationSection("arenas");

        if (arenasSection == null) {
            errors.add("Falta la sección raíz obligatoria 'arenas' en arenas.yml.");
            throw new ConfigValidationException("Error de validación en arenas.yml", errors);
        }

        Map<String, ArenaDefinition> arenas = new HashMap<>();

        for (String arenaKey : arenasSection.getKeys(false)) {
            String prefix = "arenas." + arenaKey + ".";
            ConfigurationSection sec = arenasSection.getConfigurationSection(arenaKey);

            if (sec == null) {
                errors.add("La clave '" + prefix + "' debe ser una sección de configuración.");
                continue;
            }

            // 1. world
            String world = sec.getString("world");
            if (world == null || world.isBlank()) {
                errors.add("El campo '" + prefix + "world' es obligatorio y no puede estar vacío.");
            }

            // 2. center
            Vector3d center = parseVector3d(sec.getConfigurationSection("center"), prefix + "center", errors);

            // 3. podium
            Vector3d podium = parseVector3d(sec.getConfigurationSection("podium"), prefix + "podium", errors);

            // 4. bounds
            ArenaBounds bounds = parseBounds(sec.getConfigurationSection("bounds"), prefix + "bounds", errors);

            // 5. rules
            ArenaRuleSet rules = parseRules(sec.getConfigurationSection("rules"), prefix + "rules", errors);

            // Comprobar coherencia espacial si center, podium y bounds existen
            if (center != null && bounds != null && !bounds.contains(center)) {
                errors.add("En '" + prefix + "center': la coordenada (" + center.x() + ", " + center.y() + ", " + center.z()
                        + ") está fuera de los límites de la arena: " + bounds);
            }
            if (podium != null && bounds != null && !bounds.contains(podium)) {
                errors.add("En '" + prefix + "podium': la coordenada (" + podium.x() + ", " + podium.y() + ", " + podium.z()
                        + ") está fuera de los límites de la arena: " + bounds);
            }

            if (errors.isEmpty() && world != null && !world.isBlank() && center != null && podium != null && bounds != null && rules != null) {
                arenas.put(arenaKey, new ArenaDefinition(arenaKey, world, center, podium, bounds, rules));
            }
        }

        if (!errors.isEmpty()) {
            throw new ConfigValidationException("Errores de validación en arenas.yml", errors);
        }

        if (arenas.isEmpty()) {
            throw new ConfigValidationException("La sección 'arenas' debe definir al menos una arena válida.");
        }


        String defaultArenaId = arenas.containsKey("default") ? "default" : arenas.keySet().iterator().next();
        return new ArenaConfigurationSnapshot(arenas, defaultArenaId);
    }

    private static Vector3d parseVector3d(ConfigurationSection sec, String path, List<String> errors) {
        if (sec == null) {
            errors.add("Falta la sección obligatoria de coordenadas: '" + path + "'.");
            return null;
        }

        if (!sec.isDouble("x") && !sec.isInt("x")) {
            errors.add("El campo '" + path + ".x' debe ser un número válido.");
        }
        if (!sec.isDouble("y") && !sec.isInt("y")) {
            errors.add("El campo '" + path + ".y' debe ser un número válido.");
        }
        if (!sec.isDouble("z") && !sec.isInt("z")) {
            errors.add("El campo '" + path + ".z' debe ser un número válido.");
        }

        double x = sec.getDouble("x");
        double y = sec.getDouble("y");
        double z = sec.getDouble("z");

        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            errors.add("Las coordenadas en '" + path + "' deben ser números finitos (recibido: x=" + x + ", y=" + y + ", z=" + z + ").");
            return null;
        }

        return new Vector3d(x, y, z);
    }

    private static ArenaBounds parseBounds(ConfigurationSection sec, String path, List<String> errors) {
        if (sec == null) {
            errors.add("Falta la sección obligatoria de límites: '" + path + "'.");
            return null;
        }

        Vector3d min = parseVector3d(sec.getConfigurationSection("min"), path + ".min", errors);
        Vector3d max = parseVector3d(sec.getConfigurationSection("max"), path + ".max", errors);

        if (min == null || max == null) {
            return null;
        }

        if (min.x() > max.x()) {
            errors.add("En '" + path + "': min.x (" + min.x() + ") no puede ser mayor que max.x (" + max.x() + ").");
        }
        if (min.y() > max.y()) {
            errors.add("En '" + path + "': min.y (" + min.y() + ") no puede ser mayor que max.y (" + max.y() + ").");
        }
        if (min.z() > max.z()) {
            errors.add("En '" + path + "': min.z (" + min.z() + ") no puede ser mayor que max.z (" + max.z() + ").");
        }

        if (min.x() > max.x() || min.y() > max.y() || min.z() > max.z()) {
            return null;
        }

        return new ArenaBounds(min.x(), min.y(), min.z(), max.x(), max.y(), max.z());
    }

    private static ArenaRuleSet parseRules(ConfigurationSection sec, String path, List<String> errors) {
        if (sec == null) {
            errors.add("La sección '" + path + "' es obligatoria y debe definir explícitamente las reglas de la arena.");
            return null;
        }

        boolean waterAllowed = false;
        if (sec.contains("water_allowed") && sec.contains("water_denial")) {
            errors.add("En '" + path + "': configuración ambigua detectada. Se especificaron simultáneamente 'water_allowed' y 'water_denial'. Utilice exclusivamente el campo canónico 'water_allowed'.");
        } else if (sec.contains("water_allowed")) {
            if (sec.isBoolean("water_allowed")) {
                waterAllowed = sec.getBoolean("water_allowed");
            } else {
                errors.add("El campo '" + path + ".water_allowed' debe ser un booleano (true o false).");
            }
        } else if (sec.contains("water_denial")) {
            if (sec.isBoolean("water_denial")) {
                waterAllowed = !sec.getBoolean("water_denial");
            } else {
                errors.add("El campo '" + path + ".water_denial' debe ser un booleano (true o false).");
            }
        } else {
            errors.add("El campo '" + path + ".water_allowed' es obligatorio.");
        }

        boolean boundaryEnabled = false;
        boolean boundaryFound = false;
        if (sec.isConfigurationSection("boundary")) {
            ConfigurationSection bSec = sec.getConfigurationSection("boundary");
            if (bSec != null && bSec.isBoolean("enabled")) {
                boundaryEnabled = bSec.getBoolean("enabled");
                boundaryFound = true;
            } else if (bSec != null && bSec.contains("enabled")) {
                errors.add("El campo '" + path + ".boundary.enabled' debe ser un booleano.");
                boundaryFound = true;
            }
        } else if (sec.isBoolean("boundary_enabled")) {
            boundaryEnabled = sec.getBoolean("boundary_enabled");
            boundaryFound = true;
        } else if (sec.isBoolean("boundary")) {
            boundaryEnabled = sec.getBoolean("boundary");
            boundaryFound = true;
        } else if (sec.contains("boundary_enabled") || sec.contains("boundary")) {
            errors.add("El campo de límites en '" + path + "' debe ser un booleano.");
            boundaryFound = true;
        }
        if (!boundaryFound) {
            errors.add("El campo de límites ('" + path + ".boundary.enabled' o '" + path + ".boundary_enabled') es obligatorio.");
        }

        boolean antiTunnelEnabled = false;
        boolean antiTunnelFound = false;
        if (sec.isConfigurationSection("anti_tunnel")) {
            ConfigurationSection atSec = sec.getConfigurationSection("anti_tunnel");
            if (atSec != null && atSec.isBoolean("enabled")) {
                antiTunnelEnabled = atSec.getBoolean("enabled");
                antiTunnelFound = true;
            } else if (atSec != null && atSec.contains("enabled")) {
                errors.add("El campo '" + path + ".anti_tunnel.enabled' debe ser un booleano.");
                antiTunnelFound = true;
            }
        } else if (sec.isBoolean("anti_tunnel_enabled")) {
            antiTunnelEnabled = sec.getBoolean("anti_tunnel_enabled");
            antiTunnelFound = true;
        } else if (sec.isBoolean("anti_tunnel")) {
            antiTunnelEnabled = sec.getBoolean("anti_tunnel");
            antiTunnelFound = true;
        } else if (sec.contains("anti_tunnel_enabled") || sec.contains("anti_tunnel")) {
            errors.add("El campo anti-túnel en '" + path + "' debe ser un booleano.");
            antiTunnelFound = true;
        }
        if (!antiTunnelFound) {
            errors.add("El campo anti-túnel ('" + path + ".anti_tunnel.enabled' o '" + path + ".anti_tunnel_enabled') es obligatorio.");
        }

        return new ArenaRuleSet(waterAllowed, boundaryEnabled, antiTunnelEnabled);
    }
}
