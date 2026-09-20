package maurxp.betterdragon.ability;

import maurxp.betterdragon.combat.CombatRuntime;
import maurxp.betterdragon.combat.ParticipantSnapshot;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Selector determinista de objetivos para habilidades de BetterDragon.
 * <p>
 * Principios:
 * <ul>
 * <li><b>Filtrado de Jugadores Válidos:</b> Solo selecciona jugadores conectados en el mundo de la batalla,
 * con vida > 0 y en modalidades válidas (SURVIVAL o ADVENTURE, excluyendo SPECTATOR).</li>
 * <li><b>Determinismo y Desempate:</b> Emplea criterios deterministas (UUID) en empates de distancia.</li>
 * <li><b>Integración sin Duplicación:</b> Consulta {@link CombatRuntime} para el selector {@code DAMAGER}.</li>
 * </ul>
 *
 * @author maurxp
 */
public class TargetSelector {

    private final BattleSpatialContext spatialContext;
    private final Random random;

    public TargetSelector(BattleSpatialContext spatialContext, Random random) {
        this.spatialContext = Objects.requireNonNull(spatialContext, "BattleSpatialContext no puede ser nulo");
        this.random = Objects.requireNonNull(random, "Random no puede ser nulo");
    }

    public TargetSelector(BattleSpatialContext spatialContext) {
        this(spatialContext, new Random());
    }

    /**
     * Resuelve los objetivos para una habilidad según su configuración.
     *
     * @param type             tipo de selector
     * @param ability          definición de la habilidad (para parámetros adicionales como count)
     * @param world            mundo de la batalla
     * @param originLocation   ubicación de origen resuelta
     * @param combatRuntime    runtime de combate para consultas de damagers
     * @param triggeringPlayer jugador que originó el trigger (si existe)
     * @return lista inmutable de jugadores objetivos
     */
    public List<Player> resolveTargets(
            TargetSelectorType type,
            AbilityDefinition ability,
            World world,
            Location originLocation,
            CombatRuntime combatRuntime,
            Optional<Player> triggeringPlayer) {

        Objects.requireNonNull(type, "TargetSelectorType no puede ser nulo");
        Objects.requireNonNull(world, "World no puede ser nulo");

        Location arenaCenter = spatialContext.getArenaCenter(world);
        List<Player> arenaPlayers = getValidPlayersInArena(world);

        return switch (type) {
            case ALL_IN_ARENA -> List.copyOf(arenaPlayers);

            case RANDOM_PLAYER -> {
                if (arenaPlayers.isEmpty()) {
                    yield List.of();
                }
                int index = random.nextInt(arenaPlayers.size());
                yield List.of(arenaPlayers.get(index));
            }

            case RANDOM_SUBSET -> {
                if (arenaPlayers.isEmpty()) {
                    yield List.of();
                }
                int count = ability.getIntProperty("count", 1);
                if (count <= 0) {
                    yield List.of();
                }
                if (arenaPlayers.size() <= count) {
                    yield List.copyOf(arenaPlayers);
                }
                List<Player> copy = new ArrayList<>(arenaPlayers);
                Collections.shuffle(copy, random);
                yield List.copyOf(copy.subList(0, count));
            }

            case NEAREST_PLAYER -> {
                if (arenaPlayers.isEmpty()) {
                    yield List.of();
                }
                Location ref = originLocation != null ? originLocation : arenaCenter;
                Player nearest = arenaPlayers.stream()
                        .min(Comparator
                                .comparingDouble((Player p) -> p.getLocation().distanceSquared(ref))
                                .thenComparing(p -> p.getUniqueId().toString()))
                        .orElse(null);
                yield nearest != null ? List.of(nearest) : List.of();
            }

            case DAMAGER -> {
                if (combatRuntime == null) {
                    yield List.of();
                }
                Optional<ParticipantSnapshot> topDamage = combatRuntime.getTopDamageParticipant();
                if (topDamage.isEmpty()) {
                    yield List.of();
                }
                UUID damagerId = topDamage.get().playerId();
                Player damagerPlayer = world.getPlayers().stream()
                        .filter(p -> p.getUniqueId().equals(damagerId))
                        .findFirst()
                        .orElse(null);
                if (damagerPlayer != null && isValidTarget(damagerPlayer)) {
                    yield List.of(damagerPlayer);
                }
                yield List.of();
            }

            case TRIGGERING_PLAYER -> {
                if (triggeringPlayer.isPresent() && isValidTarget(triggeringPlayer.get())) {
                    yield List.of(triggeringPlayer.get());
                }
                yield List.of();
            }
        };
    }

    /**
     * Obtiene los jugadores válidos dentro de los límites espaciales reales de la arena.
     * Evalúa a los jugadores del mundo contra el método {@link BattleSpatialContext#isInArena(Location)},
     * garantizando que coincidan tanto el mundo como los límites volumétricos sin emplear radios ficticios.
     *
     * @param world mundo de la batalla
     * @return lista inmutable de jugadores válidos dentro de la arena
     */
    public List<Player> getValidPlayersInArena(World world) {
        Objects.requireNonNull(world, "World no puede ser nulo");
        if (spatialContext.getBounds() == null) {
            return List.of();
        }
        List<Player> result = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            if (isValidTarget(player) && spatialContext.isInArena(player.getLocation())) {
                result.add(player);
            }
        }
        return Collections.unmodifiableList(result);
    }


    /**
     * Comprueba si un jugador es un objetivo válido y activo.
     */
    public boolean isValidTarget(Player player) {
        if (player == null || !player.isOnline() || player.isDead()) {
            return false;
        }
        GameMode gm = player.getGameMode();
        return gm == GameMode.SURVIVAL || gm == GameMode.ADVENTURE;
    }
}
