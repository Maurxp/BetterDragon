package maurxp.betterdragon.config;

import maurxp.betterdragon.arena.ArenaDefinition;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Instantánea (Snapshot) inmutable del conjunto de arenas configuradas en el sistema.
 * <p>
 * Invariante Fundamental:
 * Una vez creada una sesión de batalla, su definición de arena permanece inalterada
 * y aislada de futuras recargas del archivo {@code arenas.yml}.
 *
 * @param arenas         mapa inmutable de definiciones de arena por ID
 * @param defaultArenaId identificador de la arena predeterminada
 * @author maurxp
 */
public record ArenaConfigurationSnapshot(
        Map<String, ArenaDefinition> arenas,
        String defaultArenaId
) {

    public ArenaConfigurationSnapshot {
        Objects.requireNonNull(arenas, "El mapa de arenas no puede ser nulo");
        Objects.requireNonNull(defaultArenaId, "El defaultArenaId no puede ser nulo");
        arenas = Map.copyOf(arenas);
    }

    /**
     * Retorna una definición de arena por su identificador.
     *
     * @param id identificador de la arena
     * @return Optional con la definición de la arena, o vacío si no existe
     */
    public Optional<ArenaDefinition> getArena(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(arenas.get(id));
    }

    /**
     * Busca una definición de arena cuyo mundo configurado coincida con el nombre de mundo indicado.
     *
     * @param worldName nombre del mundo
     * @return Optional con la arena asociada a ese mundo, o vacío si no existe
     */
    public Optional<ArenaDefinition> getArenaForWorld(String worldName) {
        if (worldName == null) {
            return Optional.empty();
        }
        return arenas.values().stream()
                .filter(a -> a.worldName().equalsIgnoreCase(worldName))
                .findFirst();
    }

    /**
     * Retorna la arena predeterminada configurada en este snapshot.
     * Si la arena predeterminada no se encuentra en el mapa, retorna {@link ArenaDefinition#defaults()}.
     *
     * @return definición de la arena predeterminada
     */
    public ArenaDefinition getDefaultArena() {
        return getArena(defaultArenaId).orElseGet(ArenaDefinition::defaults);
    }


    /**
     * Retorna la arena con el ID especificado, o la predeterminada si no existe.
     *
     * @param id identificador solicitado
     * @return ArenaDefinition correspondiente o por defecto
     */
    public ArenaDefinition getArenaOrDefault(String id) {
        return getArena(id).orElseGet(this::getDefaultArena);
    }

    /**
     * Crea un snapshot con la arena estándar por defecto para pruebas o inicializaciones seguras.
     *
     * @return snapshot con valores predeterminados
     */
    public static ArenaConfigurationSnapshot defaults() {
        ArenaDefinition defaultArena = ArenaDefinition.defaults();
        return new ArenaConfigurationSnapshot(
                Map.of(defaultArena.id(), defaultArena),
                defaultArena.id()
        );
    }
}
