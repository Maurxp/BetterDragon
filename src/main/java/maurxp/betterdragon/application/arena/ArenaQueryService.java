package maurxp.betterdragon.application.arena;

import maurxp.betterdragon.application.arena.model.ArenaDetailView;
import maurxp.betterdragon.application.arena.model.ArenaSummaryView;
import maurxp.betterdragon.arena.ArenaBounds;
import maurxp.betterdragon.arena.ArenaDefinition;
import maurxp.betterdragon.config.ArenaConfigurationSnapshot;
import maurxp.betterdragon.config.ConfigurationService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Servicio de aplicación de solo lectura para la consulta de arenas de combate.
 * <p>
 * No expone mutadores directos de configuración para preservar la invariante
 * de inmutabilidad y evitar ediciones no auditadas de {@code arenas.yml}.
 *
 * @author maurxp
 */
public class ArenaQueryService {

    private final ConfigurationService configurationService;

    public ArenaQueryService(ConfigurationService configurationService) {
        this.configurationService = Objects.requireNonNull(configurationService, "configurationService no puede ser nulo");
    }

    /**
     * Retorna la lista de resúmenes de todas las arenas cargadas en memoria.
     */
    public List<ArenaSummaryView> listArenas() {
        ArenaConfigurationSnapshot snapshot = configurationService.getActiveArenas();
        String defaultId = snapshot.defaultArenaId();

        List<ArenaSummaryView> list = new ArrayList<>();
        for (ArenaDefinition arena : snapshot.arenas().values()) {
            boolean isDefault = arena.id().equalsIgnoreCase(defaultId);
            String boundsDesc = formatBounds(arena.bounds());
            list.add(new ArenaSummaryView(arena.id(), arena.worldName(), isDefault, boundsDesc));
        }

        return List.copyOf(list);
    }

    /**
     * Retorna el detalle completo de una arena por ID.
     *
     * @param arenaId identificador de la arena
     * @return Optional con el detalle si existe
     */
    public Optional<ArenaDetailView> getArenaDetail(String arenaId) {
        if (arenaId == null || arenaId.isBlank()) {
            return Optional.empty();
        }

        ArenaConfigurationSnapshot snapshot = configurationService.getActiveArenas();
        Optional<ArenaDefinition> arenaOpt = snapshot.getArena(arenaId.trim());
        if (arenaOpt.isEmpty()) {
            return Optional.empty();
        }

        ArenaDefinition arena = arenaOpt.get();
        boolean isDefault = arena.id().equalsIgnoreCase(snapshot.defaultArenaId());
        String boundsDesc = formatBounds(arena.bounds());

        List<String> activeRules = new ArrayList<>();
        activeRules.add("water_allowed: " + arena.rules().waterAllowed());
        activeRules.add("boundary_enabled: " + arena.rules().boundaryEnabled());
        activeRules.add("anti_tunnel_enabled: " + arena.rules().antiTunnelEnabled());

        return Optional.of(new ArenaDetailView(
                arena.id(),
                arena.worldName(),
                isDefault,
                arena.center(),
                arena.podium(),
                boundsDesc,
                activeRules
        ));
    }

    private String formatBounds(ArenaBounds bounds) {
        return String.format("[X: %.0f a %.0f, Y: %.0f a %.0f, Z: %.0f a %.0f]",
                bounds.minX(), bounds.maxX(),
                bounds.minY(), bounds.maxY(),
                bounds.minZ(), bounds.maxZ());
    }
}
