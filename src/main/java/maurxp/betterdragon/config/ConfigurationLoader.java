package maurxp.betterdragon.config;

import maurxp.betterdragon.ability.AbilityDefinition;
import maurxp.betterdragon.ability.AbilityEffectType;
import maurxp.betterdragon.ability.AbilityTrigger;
import maurxp.betterdragon.ability.EffectOriginType;
import maurxp.betterdragon.ability.TargetSelectorType;
import maurxp.betterdragon.phase.PhaseDefinition;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

        // 4. abilities catalog
        Map<String, AbilityDefinition> abilitiesCatalog = new HashMap<>();
        if (config.contains("abilities") && config.get("abilities") != null) {
            ConfigurationSection abilitiesSec = config.getConfigurationSection("abilities");
            if (abilitiesSec == null && !(config.get("abilities") instanceof Map<?, ?> m && m.isEmpty())) {
                errors.add("La sección 'abilities' debe ser un mapa de configuraciones.");
            } else if (abilitiesSec != null) {
                for (String abilityKey : abilitiesSec.getKeys(false)) {
                    ConfigurationSection abSec = abilitiesSec.getConfigurationSection(abilityKey);
                    if (abSec == null) {
                        errors.add("La habilidad 'abilities." + abilityKey + "' debe ser un mapa.");
                        continue;
                    }

                    // trigger
                    String rawTrigger = abSec.getString("trigger");
                    AbilityTrigger trigger = null;
                    if (rawTrigger == null) {
                        errors.add("Habilidad '" + abilityKey + "': falta el campo obligatorio 'trigger'.");
                    } else {
                        try {
                            trigger = AbilityTrigger.valueOf(rawTrigger.toUpperCase().trim());
                        } catch (IllegalArgumentException e) {
                            errors.add("Habilidad '" + abilityKey + "': trigger inválido '" + rawTrigger
                                    + "'. Permitidos: ON_PHASE_ENTER, PERIODIC.");
                        }
                    }

                    // cooldown
                    long cooldown = 0L;
                    if (abSec.contains("cooldown")) {
                        Object rawCooldown = abSec.get("cooldown");
                        if (rawCooldown instanceof Number n && n.longValue() >= 0) {
                            cooldown = n.longValue();
                        } else {
                            errors.add("Habilidad '" + abilityKey + "': 'cooldown' debe ser un entero >= 0.");
                        }
                    }

                    // target
                    String rawTarget = abSec.getString("target");
                    TargetSelectorType targetSelector = null;
                    if (rawTarget == null) {
                        errors.add("Habilidad '" + abilityKey + "': falta el campo obligatorio 'target'.");
                    } else {
                        try {
                            targetSelector = TargetSelectorType.valueOf(rawTarget.toUpperCase().trim());
                        } catch (IllegalArgumentException e) {
                            errors.add("Habilidad '" + abilityKey + "': target inválido '" + rawTarget + "'.");
                        }
                    }

                    // origin
                    String rawOrigin = abSec.getString("origin");
                    EffectOriginType effectOrigin = null;
                    if (rawOrigin == null) {
                        errors.add("Habilidad '" + abilityKey + "': falta el campo obligatorio 'origin'.");
                    } else {
                        try {
                            effectOrigin = EffectOriginType.valueOf(rawOrigin.toUpperCase().trim());
                        } catch (IllegalArgumentException e) {
                            errors.add("Habilidad '" + abilityKey + "': origin inválido '" + rawOrigin + "'.");
                        }
                    }

                    // effect
                    AbilityEffectType effectType = null;
                    Map<String, Object> properties = new HashMap<>();
                    if (abSec.isConfigurationSection("effect")) {
                        ConfigurationSection effSec = abSec.getConfigurationSection("effect");
                        String rawType = effSec != null ? effSec.getString("type") : null;
                        if (rawType == null) {
                            errors.add("Habilidad '" + abilityKey + "': falta 'effect.type'.");
                        } else {
                            try {
                                effectType = AbilityEffectType.valueOf(rawType.toUpperCase().trim());
                            } catch (IllegalArgumentException e) {
                                errors.add("Habilidad '" + abilityKey + "': effect.type inválido '" + rawType + "'.");
                            }
                        }
                        if (effSec != null) {
                            for (String propKey : effSec.getKeys(false)) {
                                if (!propKey.equalsIgnoreCase("type")) {
                                    properties.put(propKey, effSec.get(propKey));
                                }
                            }
                        }
                    } else if (abSec.isString("effect")) {
                        String rawType = abSec.getString("effect");
                        try {
                            effectType = AbilityEffectType.valueOf(rawType.toUpperCase().trim());
                        } catch (IllegalArgumentException e) {
                            errors.add("Habilidad '" + abilityKey + "': effect inválido '" + rawType + "'.");
                        }
                    } else {
                        errors.add("Habilidad '" + abilityKey + "': falta la definición 'effect'.");
                    }

                    if (trigger != null && targetSelector != null && effectOrigin != null && effectType != null) {
                        abilitiesCatalog.put(abilityKey,
                                new AbilityDefinition(abilityKey, trigger, cooldown, targetSelector, effectOrigin, effectType, properties));
                    }
                }
            }
        }

        // 5. dragons catalog
        DragonCatalog dragonCatalog = null;
        if (config.contains("dragons")) {
            ConfigurationSection dragonsSec = config.getConfigurationSection("dragons");
            if (dragonsSec == null) {
                errors.add("La sección 'dragons' debe ser un mapa.");
            } else if (dragonsSec.getKeys(false).isEmpty()) {
                errors.add("La sección 'dragons' está vacía; debe definir al menos un dragón.");
            } else {
                Map<String, DragonDefinition> parsedDefinitions = new LinkedHashMap<>();
                Set<String> seenDragonIds = new HashSet<>();

                for (String rawDragonKey : dragonsSec.getKeys(false)) {
                    if (rawDragonKey == null || rawDragonKey.isBlank()) {
                        errors.add("Se encontró una clave de dragón nula o vacía en 'dragons'.");
                        continue;
                    }

                    String trimmedKey = rawDragonKey.trim();
                    if (!trimmedKey.matches("^[a-zA-Z0-9_-]+$")) {
                        errors.add("Identificador de dragón inválido '" + rawDragonKey
                                + "'. Solo puede contener letras, números, guiones y guiones bajos (sin espacios ni caracteres especiales).");
                    }

                    String normalizedDragonId = trimmedKey.toLowerCase();
                    if (!seenDragonIds.add(normalizedDragonId)) {
                        errors.add("Identificador de dragón duplicado o en colisión case-insensitive: '" + rawDragonKey + "'.");
                    }

                    ConfigurationSection dSec = dragonsSec.getConfigurationSection(rawDragonKey);
                    if (dSec == null) {
                        errors.add("El dragón '" + rawDragonKey + "' debe ser una sección de configuración.");
                        continue;
                    }

                    // display_name
                    String displayName = dSec.getString("display_name");
                    if (displayName == null) {
                        displayName = dSec.getString("displayName");
                    }

                    // attributes
                    DragonAttributes attributes = DragonAttributes.defaults();
                    if (dSec.contains("attributes")) {
                        ConfigurationSection attrSec = dSec.getConfigurationSection("attributes");
                        if (attrSec == null) {
                            errors.add("Dragón '" + rawDragonKey + "': la sección 'attributes' debe ser un mapa.");
                        } else {
                            double maxHealth = DragonAttributes.DEFAULT_MAX_HEALTH;
                            Object rawHealth = attrSec.contains("max_health") ? attrSec.get("max_health") : attrSec.get("max-health");
                            if (rawHealth != null) {
                                if (rawHealth instanceof Number n) {
                                    double val = n.doubleValue();
                                    if (Double.isNaN(val) || Double.isInfinite(val) || val <= 0.0) {
                                        errors.add("Dragón '" + rawDragonKey + "': 'attributes.max_health' debe ser un número positivo finito (> 0). Se encontró: " + val);
                                    } else {
                                        maxHealth = val;
                                    }
                                } else {
                                    errors.add("Dragón '" + rawDragonKey + "': 'attributes.max_health' debe ser un número.");
                                }
                            }

                            Double movementSpeed = null;
                            Object rawSpeed = attrSec.contains("movement_speed") ? attrSec.get("movement_speed") : attrSec.get("movement-speed");
                            if (rawSpeed != null) {
                                if (rawSpeed instanceof Number n) {
                                    double val = n.doubleValue();
                                    if (Double.isNaN(val) || Double.isInfinite(val) || val <= 0.0) {
                                        errors.add("Dragón '" + rawDragonKey + "': 'attributes.movement_speed' debe ser un número positivo finito (> 0). Se encontró: " + val);
                                    } else {
                                        movementSpeed = val;
                                    }
                                } else {
                                    errors.add("Dragón '" + rawDragonKey + "': 'attributes.movement_speed' debe ser un número.");
                                }
                            }

                            Double followRange = null;
                            Object rawRange = attrSec.contains("follow_range") ? attrSec.get("follow_range") : attrSec.get("follow-range");
                            if (rawRange != null) {
                                if (rawRange instanceof Number n) {
                                    double val = n.doubleValue();
                                    if (Double.isNaN(val) || Double.isInfinite(val) || val <= 0.0) {
                                        errors.add("Dragón '" + rawDragonKey + "': 'attributes.follow_range' debe ser un número positivo finito (> 0). Se encontró: " + val);
                                    } else {
                                        followRange = val;
                                    }
                                } else {
                                    errors.add("Dragón '" + rawDragonKey + "': 'attributes.follow_range' debe ser un número.");
                                }
                            }

                            Double attackDamage = null;
                            Object rawDamage = attrSec.contains("attack_damage") ? attrSec.get("attack_damage") : attrSec.get("attack-damage");
                            if (rawDamage != null) {
                                if (rawDamage instanceof Number n) {
                                    double val = n.doubleValue();
                                    if (Double.isNaN(val) || Double.isInfinite(val) || val < 0.0) {
                                        errors.add("Dragón '" + rawDragonKey + "': 'attributes.attack_damage' debe ser un número >= 0 finito. Se encontró: " + val);
                                    } else {
                                        attackDamage = val;
                                    }
                                } else {
                                    errors.add("Dragón '" + rawDragonKey + "': 'attributes.attack_damage' debe ser un número.");
                                }
                            }

                            try {
                                attributes = new DragonAttributes(maxHealth, movementSpeed, followRange, attackDamage);
                            } catch (IllegalArgumentException e) {
                                errors.add("Dragón '" + rawDragonKey + "': " + e.getMessage());
                            }
                        }
                    }

                    // scaling
                    DragonScalingDefinition scaling = DragonScalingDefinition.defaults();
                    if (dSec.contains("scaling")) {
                        ConfigurationSection scSec = dSec.getConfigurationSection("scaling");
                        if (scSec == null) {
                            errors.add("Dragón '" + rawDragonKey + "': la sección 'scaling' debe ser un mapa.");
                        } else {
                            boolean scalingEnabled = scSec.getBoolean("enabled", false);
                            ScalingMode scalingMode = null;
                            if (scSec.contains("mode")) {
                                String rawMode = scSec.getString("mode");
                                if (rawMode != null) {
                                    try {
                                        scalingMode = ScalingMode.valueOf(rawMode.toUpperCase().trim());
                                    } catch (IllegalArgumentException e) {
                                        errors.add("Dragón '" + rawDragonKey + "': 'scaling.mode' inválido '" + rawMode + "'. Permitidos: NONE, LINEAR.");
                                    }
                                }
                            }

                            if (scalingMode == null) {
                                // Opción A: enabled: true implica LINEAR cuando mode está ausente; enabled: false implica NONE
                                scalingMode = scalingEnabled ? ScalingMode.LINEAR : ScalingMode.NONE;
                            } else if (scalingEnabled && scalingMode == ScalingMode.NONE) {
                                errors.add("Dragón '" + rawDragonKey + "': 'scaling.mode' no puede ser NONE cuando 'scaling.enabled' es true.");
                            }

                            double healthPerPlayer = DragonScalingDefinition.DEFAULT_HEALTH_PER_PLAYER;
                            Object rawHpp = scSec.contains("health_per_player") ? scSec.get("health_per_player") : scSec.get("health-per-player");
                            if (rawHpp != null) {
                                if (rawHpp instanceof Number n) {
                                    double val = n.doubleValue();
                                    if (Double.isNaN(val) || Double.isInfinite(val) || val < 0.0) {
                                        errors.add("Dragón '" + rawDragonKey + "': 'scaling.health_per_player' debe ser un número >= 0.0 finito. Se encontró: " + val);
                                    } else {
                                        healthPerPlayer = val;
                                    }
                                } else {
                                    errors.add("Dragón '" + rawDragonKey + "': 'scaling.health_per_player' debe ser un número.");
                                }
                            }

                            double maxMultiplier = DragonScalingDefinition.DEFAULT_MAX_HEALTH_MULTIPLIER;
                            Object rawMult = scSec.contains("max_health_multiplier") ? scSec.get("max_health_multiplier") : scSec.get("max-health-multiplier");
                            if (rawMult != null) {
                                if (rawMult instanceof Number n) {
                                    double val = n.doubleValue();
                                    if (Double.isNaN(val) || Double.isInfinite(val) || val < 1.0) {
                                        errors.add("Dragón '" + rawDragonKey + "': 'scaling.max_health_multiplier' debe ser un número >= 1.0 finito. Se encontró: " + val);
                                    } else {
                                        maxMultiplier = val;
                                    }
                                } else {
                                    errors.add("Dragón '" + rawDragonKey + "': 'scaling.max_health_multiplier' debe ser un número.");
                                }
                            }

                            try {
                                scaling = new DragonScalingDefinition(scalingEnabled, scalingMode, healthPerPlayer, maxMultiplier);
                            } catch (IllegalArgumentException e) {
                                if (!errors.stream().anyMatch(err -> err.contains("scaling.mode"))) {
                                    errors.add("Dragón '" + rawDragonKey + "': " + e.getMessage());
                                }
                            }
                        }
                    }

                    // bossbar (Fase 3.14)
                    DragonBossBarDefinition bossbar = DragonBossBarDefinition.defaults();
                    if (dSec.contains("bossbar")) {
                        ConfigurationSection bbSec = dSec.getConfigurationSection("bossbar");
                        if (bbSec == null) {
                            errors.add("Dragón '" + rawDragonKey + "': la sección 'bossbar' debe ser un mapa.");
                        } else {
                            boolean bbEnabled = bbSec.getBoolean("enabled", true);
                            String bbTitle = bbSec.getString("title", DragonBossBarDefinition.DEFAULT_TITLE);
                            if (bbTitle == null || bbTitle.isBlank()) {
                                errors.add("Dragón '" + rawDragonKey + "': 'bossbar.title' no puede estar vacío.");
                                bbTitle = DragonBossBarDefinition.DEFAULT_TITLE;
                            }
                            BarColor bbColor = DragonBossBarDefinition.DEFAULT_COLOR;
                            if (bbSec.contains("color")) {
                                String rawColor = bbSec.getString("color");
                                if (rawColor != null) {
                                    try {
                                        bbColor = BarColor.valueOf(rawColor.toUpperCase().trim());
                                    } catch (IllegalArgumentException e) {
                                        errors.add("Dragón '" + rawDragonKey + "': 'bossbar.color' inválido '" + rawColor + "'.");
                                    }
                                }
                            }
                            BarStyle bbStyle = DragonBossBarDefinition.DEFAULT_STYLE;
                            if (bbSec.contains("style")) {
                                String rawStyle = bbSec.getString("style");
                                if (rawStyle != null) {
                                    try {
                                        bbStyle = BarStyle.valueOf(rawStyle.toUpperCase().trim());
                                    } catch (IllegalArgumentException e) {
                                        errors.add("Dragón '" + rawDragonKey + "': 'bossbar.style' inválido '" + rawStyle + "'.");
                                    }
                                }
                            }
                            try {
                                bossbar = new DragonBossBarDefinition(bbEnabled, bbTitle, bbColor, bbStyle);
                            } catch (IllegalArgumentException e) {
                                errors.add("Dragón '" + rawDragonKey + "': " + e.getMessage());
                            }
                        }
                    }

                    // enrage (Fase 3.14)
                    DragonEnrageDefinition enrage = DragonEnrageDefinition.defaults();
                    if (dSec.contains("enrage")) {
                        ConfigurationSection enrSec = dSec.getConfigurationSection("enrage");
                        if (enrSec == null) {
                            errors.add("Dragón '" + rawDragonKey + "': la sección 'enrage' debe ser un mapa.");
                        } else {
                            boolean enrEnabled = enrSec.getBoolean("enabled", true);
                            double enrThreshold = DragonEnrageDefinition.DEFAULT_THRESHOLD;
                            if (enrSec.contains("threshold")) {
                                Object rawThresh = enrSec.get("threshold");
                                if (rawThresh instanceof Number n) {
                                    double val = n.doubleValue();
                                    if (!Double.isFinite(val) || val <= 0.0 || val > 1.0) {
                                        errors.add("Dragón '" + rawDragonKey + "': 'enrage.threshold' debe ser un número finito en (0.0, 1.0]. Se encontró: " + val);
                                    } else {
                                        enrThreshold = val;
                                    }
                                } else {
                                    errors.add("Dragón '" + rawDragonKey + "': 'enrage.threshold' debe ser un número.");
                                }
                            }
                            double enrMultiplier = DragonEnrageDefinition.DEFAULT_COOLDOWN_MULTIPLIER;
                            Object rawMult = enrSec.contains("cooldown_multiplier") ? enrSec.get("cooldown_multiplier") : enrSec.get("cooldown-multiplier");
                            if (rawMult != null) {
                                if (rawMult instanceof Number n) {
                                    double val = n.doubleValue();
                                    if (!Double.isFinite(val) || val <= 0.0) {
                                        errors.add("Dragón '" + rawDragonKey + "': 'enrage.cooldown_multiplier' debe ser un número finito > 0.0. Se encontró: " + val);
                                    } else {
                                        enrMultiplier = val;
                                    }
                                } else {
                                    errors.add("Dragón '" + rawDragonKey + "': 'enrage.cooldown_multiplier' debe ser un número.");
                                }
                            }
                            try {
                                enrage = new DragonEnrageDefinition(enrEnabled, enrThreshold, enrMultiplier);
                            } catch (IllegalArgumentException e) {
                                errors.add("Dragón '" + rawDragonKey + "': " + e.getMessage());
                            }
                        }
                    }

                    // phases
                    List<?> rawPhases = dSec.getList("phases");
                    if (rawPhases == null || rawPhases.isEmpty()) {
                        errors.add("El dragón '" + rawDragonKey + "' debe contener una lista 'phases' no vacía.");
                    } else {
                        List<PhaseDefinition> phases = new ArrayList<>();
                        int order = 0;
                        for (Object obj : rawPhases) {
                            if (obj instanceof Map<?, ?> map) {
                                Object rawId = map.get("id");
                                Object rawThresh = map.get("threshold");
                                Object rawAbilities = map.get("abilities");

                                String pId = rawId != null ? rawId.toString() : null;
                                if (pId == null || pId.isBlank()) {
                                    errors.add("Dragón '" + rawDragonKey + "', fase en índice " + order + ": 'id' no puede estar vacío.");
                                }

                                double thresh = -1.0;
                                if (rawThresh instanceof Number n) {
                                    thresh = n.doubleValue();
                                    if (thresh <= 0.0 || thresh > 1.0) {
                                        errors.add("Dragón '" + rawDragonKey + "', fase '" + pId
                                                + "': threshold debe estar en (0.0, 1.0]. Se encontró: " + thresh);
                                    }
                                } else {
                                    errors.add("Dragón '" + rawDragonKey + "', fase '" + pId + "': threshold debe ser un número.");
                                }

                                List<String> abList = new ArrayList<>();
                                if (rawAbilities instanceof List<?> l) {
                                    for (Object abItem : l) {
                                        if (abItem != null) {
                                            abList.add(abItem.toString());
                                        }
                                    }
                                }

                                if (pId != null && !pId.isBlank() && thresh > 0.0 && thresh <= 1.0) {
                                    phases.add(new PhaseDefinition(pId, order, thresh, abList));
                                }
                            } else {
                                errors.add("Dragón '" + rawDragonKey + "': cada fase debe ser un mapa.");
                            }
                            order++;
                        }

                        if (!phases.isEmpty()) {
                            try {
                                DragonDefinition def = new DragonDefinition(
                                        normalizedDragonId,
                                        displayName,
                                        attributes,
                                        scaling,
                                        bossbar,
                                        enrage,
                                        phases,
                                        abilitiesCatalog
                                );
                                parsedDefinitions.put(normalizedDragonId, def);
                            } catch (IllegalArgumentException e) {
                                errors.add("Error de validación en el dragón '" + rawDragonKey + "': " + e.getMessage());
                            }
                        }
                    }
                }

                if (!parsedDefinitions.isEmpty()) {
                    String defaultId = parsedDefinitions.containsKey("default") ? "default" : null;
                    try {
                        dragonCatalog = new DragonCatalog(parsedDefinitions, defaultId);
                    } catch (IllegalArgumentException e) {
                        errors.add("Error al construir el catálogo de dragones: " + e.getMessage());
                    }
                }
            }
        } else {
            dragonCatalog = DragonCatalog.defaults();
        }

        // 6. rewards
        RewardConfigurationSnapshot rewardConfig = RewardConfigurationSnapshot.defaults();
        if (config.contains("rewards") && config.get("rewards") != null) {
            ConfigurationSection rewardsSec = config.getConfigurationSection("rewards");
            if (rewardsSec == null) {
                errors.add("La sección 'rewards' debe ser un mapa de configuración.");
            } else {
                boolean rewardsEnabled = RewardConfigurationSnapshot.DEFAULT_ENABLED;
                if (rewardsSec.contains("enabled")) {
                    Object rawEnabled = rewardsSec.get("enabled");
                    if (rawEnabled instanceof Boolean b) {
                        rewardsEnabled = b;
                    } else {
                        errors.add("rewards.enabled debe ser un booleano (true o false). Se encontró: '" + rawEnabled + "'");
                    }
                }

                double minParticipation = RewardConfigurationSnapshot.DEFAULT_MIN_PARTICIPATION_PERCENT;
                if (rewardsSec.contains("min_participation_percent")) {
                    Object rawMin = rewardsSec.get("min_participation_percent");
                    if (rawMin instanceof Number n) {
                        double val = n.doubleValue();
                        if (Double.isNaN(val) || Double.isInfinite(val) || val < 0.0 || val > 100.0) {
                            errors.add("rewards.min_participation_percent debe estar entre 0.0 y 100.0. Se encontró: " + val);
                        } else {
                            minParticipation = val;
                        }
                    } else {
                        errors.add("rewards.min_participation_percent debe ser numérico. Se encontró: '" + rawMin + "'");
                    }
                }

                Set<String> seenRewardIds = new HashSet<>();
                List<RewardItemDefinition> poolItems = new ArrayList<>();
                List<?> rawList = null;
                String listPath = null;

                if (rewardsSec.contains("participation_pool.items")) {
                    rawList = rewardsSec.getList("participation_pool.items");
                    listPath = "rewards.participation_pool.items";
                } else if (rewardsSec.contains("participant_rewards")) {
                    rawList = rewardsSec.getList("participant_rewards");
                    listPath = "rewards.participant_rewards";
                }

                if (rawList != null) {
                    int idx = 0;
                    for (Object elem : rawList) {
                        idx++;
                        if (elem instanceof Map<?, ?> itemMap) {
                            validateRewardItem(itemMap, listPath + "[" + idx + "]", poolItems, seenRewardIds, errors);
                        } else {
                            errors.add(listPath + "[" + idx + "] debe ser un mapa con 'id', 'material' y 'amount'.");
                        }
                    }
                } else if (rewardsSec.contains("participation_pool")) {
                    errors.add("La sección 'rewards.participation_pool' debe contener la lista 'items'.");
                } else if (rewardsSec.contains("participant_rewards")) {
                    errors.add("La sección 'rewards.participant_rewards' debe ser una lista de ítems.");
                } else {
                    poolItems = RewardConfigurationSnapshot.defaults().participationPool();
                }

                SlayerRewardDefinition slayerDef = SlayerRewardDefinition.defaults();
                if (rewardsSec.contains("slayer_reward")) {
                    ConfigurationSection slayerSec = rewardsSec.getConfigurationSection("slayer_reward");
                    if (slayerSec == null) {
                        errors.add("La sección 'rewards.slayer_reward' debe ser un mapa de configuración.");
                    } else {
                        boolean slayerEnabled = false;
                        if (slayerSec.contains("enabled")) {
                            Object rawSE = slayerSec.get("enabled");
                            if (rawSE instanceof Boolean b) {
                                slayerEnabled = b;
                            } else {
                                errors.add("rewards.slayer_reward.enabled debe ser un booleano. Se encontró: '" + rawSE + "'");
                            }
                        }

                        boolean requiresEligibility = true;
                        if (slayerSec.contains("requires_eligibility")) {
                            Object rawRE = slayerSec.get("requires_eligibility");
                            if (rawRE instanceof Boolean b) {
                                requiresEligibility = b;
                            } else {
                                errors.add("rewards.slayer_reward.requires_eligibility debe ser un booleano. Se encontró: '" + rawRE + "'");
                            }
                        }

                        List<RewardItemDefinition> slayerItems = new ArrayList<>();
                        if (slayerSec.contains("items")) {
                            List<?> rawSlayerList = slayerSec.getList("items");
                            if (rawSlayerList != null) {
                                int sIdx = 0;
                                for (Object sElem : rawSlayerList) {
                                    sIdx++;
                                    if (sElem instanceof Map<?, ?> sMap) {
                                        validateRewardItem(sMap, "rewards.slayer_reward.items[" + sIdx + "]", slayerItems, seenRewardIds, errors);
                                    } else {
                                        errors.add("rewards.slayer_reward.items[" + sIdx + "] debe ser un mapa con 'id', 'material' y 'amount'.");
                                    }
                                }
                            } else {
                                errors.add("rewards.slayer_reward.items debe ser una lista de ítems.");
                            }
                        } else {
                            slayerItems = SlayerRewardDefinition.defaults().items();
                        }

                        slayerDef = new SlayerRewardDefinition(slayerEnabled, requiresEligibility, slayerItems);
                    }
                }

                if (errors.isEmpty()) {
                    rewardConfig = new RewardConfigurationSnapshot(rewardsEnabled, minParticipation, poolItems, slayerDef);
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new ConfigValidationException("Errores de validación en la configuración de BetterDragon", errors);
        }

        return new BetterDragonConfig(portalEnabled, loggingLevel, debugLogging, dragonCatalog, rewardConfig);
    }

    private static void validateRewardItem(
            Map<?, ?> itemMap,
            String path,
            List<RewardItemDefinition> targetList,
            Set<String> seenRewardIds,
            List<String> errors
    ) {
        boolean valid = true;

        // 1. Identificador de recompensa (id)
        Object rawId = itemMap.get("id");
        String id = null;
        if (rawId instanceof String strId && !strId.isBlank()) {
            id = strId.trim();
            if (!id.matches("^[a-zA-Z0-9_-]+$")) {
                errors.add(path + ": 'id' inválido '" + strId + "'. Solo puede contener letras, números, guiones y guiones bajos (sin espacios ni dos puntos).");
                valid = false;
            } else if (!seenRewardIds.add(id.toLowerCase())) {
                errors.add(path + ": 'id' de recompensa duplicado '" + id + "'. Cada definición de recompensa debe tener un id único.");
                valid = false;
            }
        } else {
            errors.add(path + ": falta el campo obligatorio 'id' (identificador de recompensa no vacío).");
            valid = false;
        }

        // 2. Material de Minecraft
        Object rawMaterial = itemMap.get("material");
        String material = null;
        if (rawMaterial instanceof String strMat && !strMat.isBlank()) {
            material = strMat.toUpperCase().trim();
            org.bukkit.Material mat = org.bukkit.Material.matchMaterial(material);
            if (mat == null) {
                errors.add(path + ": material '" + strMat + "' no es un Material de Minecraft válido.");
                valid = false;
            } else if (mat == org.bukkit.Material.AIR || mat == org.bukkit.Material.CAVE_AIR || mat == org.bukkit.Material.VOID_AIR) {
                errors.add(path + ": material '" + strMat + "' no puede ser AIR.");
                valid = false;
            } else {
                material = mat.name();
            }
        } else {
            errors.add(path + ": falta el campo obligatorio 'material' (texto).");
            valid = false;
        }

        // 3. Cantidad entera positiva
        int amount = 1;
        if (itemMap.containsKey("amount")) {
            Object rawAmount = itemMap.get("amount");
            Integer parsedAmount = parseExactPositiveAmount(rawAmount);
            if (parsedAmount != null) {
                amount = parsedAmount;
            } else {
                String rewardDesc = (id != null && !id.isBlank()) ? "recompensa '" + id + "'" : path;
                errors.add(path + ": 'amount' debe ser un entero positivo mayor a 0 para " + rewardDesc + ". Se encontró: '" + rawAmount + "'");
                valid = false;
            }
        }

        if (valid && id != null && material != null) {
            targetList.add(new RewardItemDefinition(id, material, amount));
        }
    }

    static Integer parseExactPositiveAmount(Object rawAmount) {
        if (rawAmount == null) {
            return null;
        }
        if (rawAmount instanceof Integer i) {
            return i > 0 ? i : null;
        }
        if (rawAmount instanceof Long l) {
            if (l <= 0 || l > Integer.MAX_VALUE) {
                return null;
            }
            return l.intValue();
        }
        if (rawAmount instanceof Short s) {
            return s > 0 ? (int) s : null;
        }
        if (rawAmount instanceof Byte b) {
            return b > 0 ? (int) b : null;
        }
        if (rawAmount instanceof Double d) {
            if (d.isNaN() || d.isInfinite() || d <= 0.0 || d > Integer.MAX_VALUE) {
                return null;
            }
            if (Math.floor(d) != d) {
                return null;
            }
            return d.intValue();
        }
        if (rawAmount instanceof Float f) {
            if (f.isNaN() || f.isInfinite() || f <= 0.0f || f > Integer.MAX_VALUE) {
                return null;
            }
            if (Math.floor(f) != f) {
                return null;
            }
            return f.intValue();
        }
        if (rawAmount instanceof Number n) {
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d) || d <= 0.0 || d > Integer.MAX_VALUE) {
                return null;
            }
            if (Math.floor(d) != d) {
                return null;
            }
            long l = n.longValue();
            if (l <= 0 || l > Integer.MAX_VALUE) {
                return null;
            }
            return (int) l;
        }
        return null;
    }
}
