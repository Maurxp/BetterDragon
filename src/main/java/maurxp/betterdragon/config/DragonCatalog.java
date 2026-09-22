package maurxp.betterdragon.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Catálogo inmutable de múltiples definiciones de dragón de BetterDragon.
 * <p>
 * Principios:
 * <ul>
 *   <li><b>Invariante de Existencia de Default:</b> El catálogo nunca está vacío y garantiza
 *       la presencia de una definición por defecto válida.</li>
 *   <li><b>Búsqueda Determinista por ID:</b> Las claves se normalizan (lowercase/trim) y no dependen
 *       del orden accidental de declaración en YAML.</li>
 *   <li><b>Inmutabilidad Estricta:</b> Expone vistas inmutables protegidas contra modificaciones externas.</li>
 * </ul>
 *
 * @param definitions         mapa inmutable de definiciones indexadas por su ID normalizado
 * @param defaultDefinitionId identificador de la definición que actúa como default
 * @author maurxp
 */
public record DragonCatalog(
        Map<String, DragonDefinition> definitions,
        String defaultDefinitionId
) {

    public DragonCatalog {
        Objects.requireNonNull(definitions, "El mapa de definiciones no puede ser nulo");
        if (definitions.isEmpty()) {
            throw new IllegalArgumentException("El catálogo de dragones no puede estar vacío");
        }
        String normDefault = (defaultDefinitionId != null && !defaultDefinitionId.isBlank())
                ? defaultDefinitionId.trim().toLowerCase()
                : null;

        Map<String, DragonDefinition> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, DragonDefinition> entry : definitions.entrySet()) {
            String key = entry.getKey() != null ? entry.getKey().trim().toLowerCase() : "";
            DragonDefinition def = entry.getValue();
            if (key.isBlank() || def == null) {
                throw new IllegalArgumentException("El catálogo contiene entradas nulas o con claves vacías");
            }
            if (!key.equals(def.id())) {
                throw new IllegalArgumentException("La clave en el catálogo '" + key + "' no coincide con el id del dragón '" + def.id() + "'");
            }
            normalized.put(key, def);
        }

        if (normDefault != null && !normalized.containsKey(normDefault)) {
            throw new IllegalArgumentException("La definición por defecto '" + normDefault + "' no existe en el catálogo de dragones: " + normalized.keySet());
        }

        definitions = Collections.unmodifiableMap(normalized);
        defaultDefinitionId = normDefault;
    }

    /**
     * Busca una definición por su identificador.
     *
     * @param id identificador a buscar
     * @return Optional con la definición si existe
     */
    public Optional<DragonDefinition> getDefinition(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitions.get(id.trim().toLowerCase()));
    }

    /**
     * Comprueba si el catálogo tiene una definición por defecto configurada.
     */
    public boolean hasDefaultDefinition() {
        return defaultDefinitionId != null && definitions.containsKey(defaultDefinitionId);
    }

    /**
     * Obtiene la definición por defecto en un Optional.
     */
    public Optional<DragonDefinition> defaultDefinition() {
        return defaultDefinitionId != null ? Optional.ofNullable(definitions.get(defaultDefinitionId)) : Optional.empty();
    }

    /**
     * Obtiene la definición indicada o retorna la definición por defecto si el ID es nulo, vacío o no existe.
     * Si no existe definición por defecto, lanza IllegalStateException.
     *
     * @param id identificador solicitado
     * @return definición resuelta o default
     */
    public DragonDefinition getDefinitionOrDefault(String id) {
        return getDefinition(id).orElseGet(() -> defaultDefinition().orElseThrow(() ->
                new IllegalStateException("No existe una definición de dragón por defecto ('default') en el catálogo.")));
    }

    /**
     * Retorna la definición por defecto configurada en el catálogo o null si no fue configurada.
     */
    public DragonDefinition getDefaultDefinition() {
        return defaultDefinitionId != null ? definitions.get(defaultDefinitionId) : null;
    }

    /**
     * Retorna todas las definiciones disponibles indexadas por ID.
     */
    public Map<String, DragonDefinition> getAllDefinitions() {
        return definitions;
    }

    /**
     * Comprueba si el catálogo contiene una definición con el ID especificado.
     */
    public boolean hasDefinition(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        return definitions.containsKey(id.trim().toLowerCase());
    }

    /**
     * Cantidad de definiciones de dragón registradas en el catálogo.
     */
    public int size() {
        return definitions.size();
    }

    /**
     * Construye un catálogo que contiene una única definición dada, estableciéndola como default.
     */
    public static DragonCatalog of(DragonDefinition definition) {
        Objects.requireNonNull(definition, "La definición no puede ser nula");
        return new DragonCatalog(Map.of(definition.id(), definition), definition.id());
    }

    /**
     * Construye un catálogo a partir de múltiples definiciones, usando la primera o "default" como predeterminada.
     */
    public static DragonCatalog of(DragonDefinition... defs) {
        Objects.requireNonNull(defs, "defs no puede ser nulo");
        if (defs.length == 0) {
            throw new IllegalArgumentException("Debe proveerse al menos una definición");
        }
        Map<String, DragonDefinition> map = new LinkedHashMap<>();
        String defaultId = null;
        for (DragonDefinition d : defs) {
            map.put(d.id(), d);
            if ("default".equals(d.id()) || defaultId == null) {
                defaultId = d.id();
            }
        }
        return new DragonCatalog(map, defaultId);
    }

    /**
     * Genera el catálogo estándar por defecto de BetterDragon con el dragón "default".
     */
    public static DragonCatalog defaults() {
        DragonDefinition def = DragonDefinition.defaults();
        return new DragonCatalog(Map.of(def.id(), def), def.id());
    }
}
