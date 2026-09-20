package maurxp.betterdragon.ability;

import maurxp.betterdragon.battle.BattleSession;
import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.combat.CombatRuntime;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias para {@link TargetSelector}.
 *
 * @author maurxp
 */
class TargetSelectorTest {

    private TargetSelector selector;
    private World fakeWorld;
    private Location center;
    private CombatRuntime combatRuntime;
    private BattleSession session;
    private List<Player> worldPlayers;

    @BeforeEach
    void setUp() {
        worldPlayers = new ArrayList<>();

        fakeWorld = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("getName")) return "world_the_end";
                    if (name.equals("getPlayers")) return worldPlayers;
                    if (name.equals("equals")) return proxy == args[0];
                    return null;
                }
        );

        center = new Location(fakeWorld, 0, 65, 0);
        session = BattleSession.create(BattleId.random(), "world_the_end", UUID.randomUUID());
        combatRuntime = session.getCombatRuntime();
        selector = new TargetSelector(session.getSpatialContext(), new Random(42L));
    }

    private Player createFakePlayer(UUID uuid, Location loc, GameMode gm, boolean alive, boolean online) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("getUniqueId")) return uuid;
                    if (name.equals("getLocation")) return loc;
                    if (name.equals("getGameMode")) return gm;
                    if (name.equals("isDead")) return !alive;
                    if (name.equals("isOnline")) return online;
                    if (name.equals("isValid")) return alive && online;
                    if (name.equals("equals")) return proxy == args[0];
                    if (name.equals("hashCode")) return uuid.hashCode();
                    return null;
                }
        );
    }

    @Test
    @DisplayName("ALL_IN_ARENA filtra jugadores muertos, espectadores o fuera de la arena")
    void testAllInArenaFiltering() {
        Player valid1 = createFakePlayer(UUID.randomUUID(), new Location(fakeWorld, 10, 65, 10), GameMode.SURVIVAL, true, true);
        Player valid2 = createFakePlayer(UUID.randomUUID(), new Location(fakeWorld, -20, 65, 30), GameMode.ADVENTURE, true, true);
        Player dead = createFakePlayer(UUID.randomUUID(), new Location(fakeWorld, 0, 65, 0), GameMode.SURVIVAL, false, true);
        Player spectator = createFakePlayer(UUID.randomUUID(), new Location(fakeWorld, 5, 65, 5), GameMode.SPECTATOR, true, true);
        Player farAway = createFakePlayer(UUID.randomUUID(), new Location(fakeWorld, 500, 65, 500), GameMode.SURVIVAL, true, true);

        worldPlayers.addAll(List.of(valid1, valid2, dead, spectator, farAway));

        AbilityDefinition dummy = new AbilityDefinition("d", AbilityTrigger.PERIODIC, 0,
                TargetSelectorType.ALL_IN_ARENA, EffectOriginType.DRAGON_BODY, AbilityEffectType.DAMAGE);

        List<Player> targets = selector.resolveTargets(TargetSelectorType.ALL_IN_ARENA, dummy, fakeWorld, center, combatRuntime, Optional.empty());

        assertEquals(2, targets.size());
        assertTrue(targets.contains(valid1));
        assertTrue(targets.contains(valid2));
        assertFalse(targets.contains(dead));
        assertFalse(targets.contains(spectator));
        assertFalse(targets.contains(farAway));
    }

    @Test
    @DisplayName("RANDOM_PLAYER selecciona un único jugador válido de los disponibles")
    void testRandomPlayer() {
        Player p1 = createFakePlayer(UUID.randomUUID(), new Location(fakeWorld, 10, 65, 10), GameMode.SURVIVAL, true, true);
        Player p2 = createFakePlayer(UUID.randomUUID(), new Location(fakeWorld, 20, 65, 20), GameMode.SURVIVAL, true, true);
        worldPlayers.addAll(List.of(p1, p2));

        AbilityDefinition dummy = new AbilityDefinition("d", AbilityTrigger.PERIODIC, 0,
                TargetSelectorType.RANDOM_PLAYER, EffectOriginType.DRAGON_BODY, AbilityEffectType.DAMAGE);

        List<Player> targets = selector.resolveTargets(TargetSelectorType.RANDOM_PLAYER, dummy, fakeWorld, center, combatRuntime, Optional.empty());

        assertEquals(1, targets.size());
        assertTrue(targets.contains(p1) || targets.contains(p2));
    }

    @Test
    @DisplayName("RANDOM_SUBSET selecciona hasta N jugadores sin duplicados o todos si hay menos")
    void testRandomSubset() {
        Player p1 = createFakePlayer(UUID.randomUUID(), new Location(fakeWorld, 10, 65, 10), GameMode.SURVIVAL, true, true);
        Player p2 = createFakePlayer(UUID.randomUUID(), new Location(fakeWorld, 20, 65, 20), GameMode.SURVIVAL, true, true);
        Player p3 = createFakePlayer(UUID.randomUUID(), new Location(fakeWorld, 30, 65, 30), GameMode.SURVIVAL, true, true);
        worldPlayers.addAll(List.of(p1, p2, p3));

        AbilityDefinition subsetAbility = new AbilityDefinition("sub", AbilityTrigger.PERIODIC, 0,
                TargetSelectorType.RANDOM_SUBSET, EffectOriginType.DRAGON_BODY, AbilityEffectType.DAMAGE,
                Map.of("count", 2));

        List<Player> targets = selector.resolveTargets(TargetSelectorType.RANDOM_SUBSET, subsetAbility, fakeWorld, center, combatRuntime, Optional.empty());

        assertEquals(2, targets.size());
        assertNotEquals(targets.get(0), targets.get(1), "No debe contener duplicados");

        // Si pedimos 5 cuando solo hay 3 disponibles
        AbilityDefinition largeSubset = new AbilityDefinition("sub_large", AbilityTrigger.PERIODIC, 0,
                TargetSelectorType.RANDOM_SUBSET, EffectOriginType.DRAGON_BODY, AbilityEffectType.DAMAGE,
                Map.of("count", 5));

        List<Player> allTargets = selector.resolveTargets(TargetSelectorType.RANDOM_SUBSET, largeSubset, fakeWorld, center, combatRuntime, Optional.empty());
        assertEquals(3, allTargets.size());
    }

    @Test
    @DisplayName("NEAREST_PLAYER selecciona al más cercano y resuelve empates deterministamente")
    void testNearestPlayerWithTieBreak() {
        Location origin = new Location(fakeWorld, 0, 65, 0);

        Player near = createFakePlayer(UUID.randomUUID(), new Location(fakeWorld, 5, 65, 0), GameMode.SURVIVAL, true, true);
        Player far = createFakePlayer(UUID.randomUUID(), new Location(fakeWorld, 50, 65, 0), GameMode.SURVIVAL, true, true);

        worldPlayers.addAll(List.of(far, near));

        AbilityDefinition dummy = new AbilityDefinition("d", AbilityTrigger.PERIODIC, 0,
                TargetSelectorType.NEAREST_PLAYER, EffectOriginType.DRAGON_BODY, AbilityEffectType.DAMAGE);

        List<Player> targets = selector.resolveTargets(TargetSelectorType.NEAREST_PLAYER, dummy, fakeWorld, origin, combatRuntime, Optional.empty());

        assertEquals(1, targets.size());
        assertEquals(near, targets.getFirst());
    }

    @Test
    @DisplayName("DAMAGER selecciona al jugador con TOP_DAMAGE según CombatRuntime")
    void testDamagerSelection() {
        session.start();
        session.activate(new maurxp.betterdragon.battle.model.DragonIdentity(
                UUID.randomUUID(), session.getBattleId(), "default", 1));

        UUID p1Id = UUID.randomUUID();
        UUID p2Id = UUID.randomUUID();

        // p1: 100 daño, p2: 250 daño -> p2 es TOP_DAMAGE
        combatRuntime.recordDamage(p1Id, "Player1", 100.0, 10L);
        combatRuntime.recordDamage(p2Id, "Player2", 250.0, 11L);

        Player p1 = createFakePlayer(p1Id, new Location(fakeWorld, 10, 65, 10), GameMode.SURVIVAL, true, true);
        Player p2 = createFakePlayer(p2Id, new Location(fakeWorld, 20, 65, 20), GameMode.SURVIVAL, true, true);

        worldPlayers.addAll(List.of(p1, p2));

        AbilityDefinition dummy = new AbilityDefinition("d", AbilityTrigger.PERIODIC, 0,
                TargetSelectorType.DAMAGER, EffectOriginType.DRAGON_BODY, AbilityEffectType.DAMAGE);

        List<Player> targets = selector.resolveTargets(TargetSelectorType.DAMAGER, dummy, fakeWorld, center, combatRuntime, Optional.empty());

        assertEquals(1, targets.size());
        assertEquals(p2, targets.getFirst(), "Debe seleccionar a p2 por ser TOP_DAMAGE");
    }

    @Test
    @DisplayName("TRIGGERING_PLAYER selecciona al causante o retorna vacío si no existe")
    void testTriggeringPlayer() {
        Player triggerPlayer = createFakePlayer(UUID.randomUUID(), new Location(fakeWorld, 10, 65, 10), GameMode.SURVIVAL, true, true);
        worldPlayers.add(triggerPlayer);

        AbilityDefinition dummy = new AbilityDefinition("d", AbilityTrigger.PERIODIC, 0,
                TargetSelectorType.TRIGGERING_PLAYER, EffectOriginType.DRAGON_BODY, AbilityEffectType.DAMAGE);

        // Presente
        List<Player> targets = selector.resolveTargets(TargetSelectorType.TRIGGERING_PLAYER, dummy, fakeWorld, center, combatRuntime, Optional.of(triggerPlayer));
        assertEquals(1, targets.size());
        assertEquals(triggerPlayer, targets.getFirst());

        // Ausente
        List<Player> emptyTargets = selector.resolveTargets(TargetSelectorType.TRIGGERING_PLAYER, dummy, fakeWorld, center, combatRuntime, Optional.empty());
        assertTrue(emptyTargets.isEmpty());
    }

    @Test
    @DisplayName("TargetSelector rechaza spatialContext nulo sin fallbacks mágicos")
    void testNullSpatialContextThrowsNpe() {
        assertThrows(NullPointerException.class, () -> new TargetSelector(null, new Random()));
        assertThrows(NullPointerException.class, () -> new TargetSelector(null));
    }

    @Test
    @DisplayName("ALL_IN_ARENA utiliza exclusivamente los bounds reales y excluye jugadores en otros mundos")
    void testAllInArenaStrictlyUsesRealBoundsAndExcludesOtherWorld() {
        // Creamos una arena pequeña con bounds [-50, 50]
        maurxp.betterdragon.arena.ArenaDefinition smallArena = new maurxp.betterdragon.arena.ArenaDefinition(
                "small",
                "world_the_end",
                new maurxp.betterdragon.arena.Vector3d(0, 100, 0),
                new maurxp.betterdragon.arena.Vector3d(0, 65, 0),
                new maurxp.betterdragon.arena.ArenaBounds(-50, 0, -50, 50, 256, 50),
                maurxp.betterdragon.arena.ArenaRuleSet.defaults()
        );
        ArenaBattleSpatialContext smallContext = new ArenaBattleSpatialContext(smallArena);
        TargetSelector strictSelector = new TargetSelector(smallContext, new Random(1L));

        // Jugador dentro de bounds (-50 a 50)
        Player inside = createFakePlayer(UUID.randomUUID(), new Location(fakeWorld, 40, 65, 40), GameMode.SURVIVAL, true, true);

        // Jugador fuera de bounds (a 70 bloques, pero < 150 bloques del centro, comprobando que NO hay fallback a 150)
        Player outsideBounds = createFakePlayer(UUID.randomUUID(), new Location(fakeWorld, 70, 65, 0), GameMode.SURVIVAL, true, true);

        // Jugador con coordenadas dentro de bounds pero en otro mundo
        World otherWorld = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getName")) return "world_nether";
                    if (method.getName().equals("equals")) return proxy == args[0];
                    return null;
                }
        );
        Player otherWorldPlayer = createFakePlayer(UUID.randomUUID(), new Location(otherWorld, 10, 65, 10), GameMode.SURVIVAL, true, true);

        worldPlayers.clear();
        worldPlayers.addAll(List.of(inside, outsideBounds, otherWorldPlayer));

        AbilityDefinition dummy = new AbilityDefinition("d", AbilityTrigger.PERIODIC, 0,
                TargetSelectorType.ALL_IN_ARENA, EffectOriginType.DRAGON_BODY, AbilityEffectType.DAMAGE);

        List<Player> targets = strictSelector.resolveTargets(TargetSelectorType.ALL_IN_ARENA, dummy, fakeWorld, center, combatRuntime, Optional.empty());

        assertEquals(1, targets.size(), "Solo el jugador dentro del AABB real en el mundo correcto debe ser seleccionado");
        assertTrue(targets.contains(inside));
        assertFalse(targets.contains(outsideBounds), "Jugador a 70 bloques debe ser excluido porque los bounds son [-50, 50]");
        assertFalse(targets.contains(otherWorldPlayer), "Jugador en otro mundo debe ser excluido");
    }
}
