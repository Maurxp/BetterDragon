package maurxp.betterdragon.arena;

import maurxp.betterdragon.ability.AbilityDefinition;
import maurxp.betterdragon.ability.AbilityEffectType;
import maurxp.betterdragon.ability.AbilityTrigger;
import maurxp.betterdragon.ability.BattleSpatialContext;
import maurxp.betterdragon.ability.EffectOriginType;
import maurxp.betterdragon.ability.LocationResolver;
import maurxp.betterdragon.ability.TargetSelector;
import maurxp.betterdragon.ability.TargetSelectorType;
import maurxp.betterdragon.battle.BattleManager;
import maurxp.betterdragon.battle.BattleSession;
import maurxp.betterdragon.battle.BattleSessionManager;
import maurxp.betterdragon.battle.DragonSpawner;
import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.battle.model.BattleState;
import maurxp.betterdragon.combat.CombatRuntime;
import maurxp.betterdragon.config.ArenaConfigurationSnapshot;
import maurxp.betterdragon.config.BattleConfigurationSnapshot;
import maurxp.betterdragon.config.BetterDragonConfig;
import maurxp.betterdragon.config.ConfigurationService;
import maurxp.betterdragon.util.BetterDragonKeys;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de integración de dominio para Arena, SpatialContext, Reload Fail-Safe y TargetSelector.
 *
 * @author maurxp
 */
class ArenaBattleIntegrationTest {

    @TempDir
    Path tempDir;

    private Logger logger;
    private BattleSessionManager sessionManager;
    private World fakeEndWorld;
    private World fakeOtherEndWorld;
    private List<Player> worldPlayers;

    @BeforeEach
    void setUp() {
        logger = Logger.getLogger("ArenaBattleIntegrationTest");
        sessionManager = new BattleSessionManager();
        worldPlayers = new ArrayList<>();

        fakeEndWorld = createFakeWorld("world_the_end", UUID.randomUUID(), World.Environment.THE_END);
        fakeOtherEndWorld = createFakeWorld("world_the_end_nether", UUID.randomUUID(), World.Environment.THE_END);
    }

    private World createFakeWorld(String name, UUID uid, World.Environment env) {
        return (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    String m = method.getName();
                    if (m.equals("getName")) return name;
                    if (m.equals("getUID")) return uid;
                    if (m.equals("getEnvironment")) return env;
                    if (m.equals("getPlayers")) return worldPlayers;
                    if (m.equals("equals")) return proxy == args[0];
                    return null;
                }
        );
    }

    private Player createFakePlayer(UUID uuid, Location loc) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("getUniqueId")) return uuid;
                    if (name.equals("getLocation")) return loc;
                    if (name.equals("getGameMode")) return GameMode.SURVIVAL;
                    if (name.equals("isDead")) return false;
                    if (name.equals("isOnline")) return true;
                    if (name.equals("isValid")) return true;
                    if (name.equals("equals")) return proxy == args[0];
                    if (name.equals("hashCode")) return uuid.hashCode();
                    return null;
                }
        );
    }

    private DragonSpawner createStubSpawner() {
        return new DragonSpawner() {
            @Override
            public EnderDragon spawnDragon(World world, Location location, BattleId battleId, String definitionId) {
                FakePdc pdc = new FakePdc();
                pdc.set(BetterDragonKeys.MANAGED, PersistentDataType.BOOLEAN, true);
                pdc.set(BetterDragonKeys.BATTLE_ID, PersistentDataType.STRING, battleId.asString());
                pdc.set(BetterDragonKeys.DEFINITION_ID, PersistentDataType.STRING, definitionId);
                pdc.set(BetterDragonKeys.SCHEMA_VERSION, PersistentDataType.INTEGER, 1);

                return (EnderDragon) Proxy.newProxyInstance(
                        EnderDragon.class.getClassLoader(),
                        new Class<?>[]{EnderDragon.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("getUniqueId")) return UUID.randomUUID();
                            if (method.getName().equals("getPersistentDataContainer")) return pdc;
                            if (method.getName().equals("isValid")) return true;
                            if (method.getName().equals("getWorld")) return world;
                            if (method.getName().equals("getLocation")) return new Location(world, 0, 100, 0);
                            return null;
                        }
                );
            }
        };
    }

    @Test
    @DisplayName("BattleSession retiene snapshot congelado de arena independiente de recargas")
    void testBattleSessionRetainsArenaSnapshotAcrossReload() throws Exception {
        File configFile = tempDir.resolve("config.yml").toFile();
        File arenasFile = tempDir.resolve("arenas.yml").toFile();

        Files.writeString(configFile.toPath(), "portal:\n  enabled: false\nlogging:\n  level: 'INFO'\n");
        Files.writeString(arenasFile.toPath(), """
                arenas:
                  default:
                    world: "world_the_end"
                    center:
                      x: 10.0
                      y: 95.0
                      z: 10.0
                    podium:
                      x: 10.0
                      y: 60.0
                      z: 10.0
                    bounds:
                      min:
                        x: -100.0
                        y: 0.0
                        z: -100.0
                      max:
                        x: 100.0
                        y: 200.0
                        z: 100.0
                    rules:
                      water_allowed: false
                      boundary_enabled: true
                      anti_tunnel_enabled: true
                """);

        ConfigurationService configService = new ConfigurationService(logger);
        configService.loadInitial(configFile, arenasFile);

        BattleManager manager = new BattleManager(sessionManager, configService, createStubSpawner(), logger);
        BattleSession activeSession = manager.startBattle(fakeEndWorld);

        assertEquals("default", activeSession.getArenaId());
        assertEquals(95.0, activeSession.getArena().center().y());
        assertEquals(60.0, activeSession.getArena().podium().y());

        // 2. Modificar arenas.yml con nuevas coordenadas
        Files.writeString(arenasFile.toPath(), """
                arenas:
                  default:
                    world: "world_the_end"
                    center:
                      x: 25.0
                      y: 110.0
                      z: 25.0
                    podium:
                      x: 25.0
                      y: 70.0
                      z: 25.0
                    bounds:
                      min:
                        x: -100.0
                        y: 0.0
                        z: -100.0
                      max:
                        x: 100.0
                        y: 200.0
                        z: 100.0
                    rules:
                      water_allowed: false
                      boundary_enabled: true
                      anti_tunnel_enabled: true
                """);

        boolean reloadOk = configService.reload(configFile, arenasFile);
        assertTrue(reloadOk, "La recarga válida debió ser exitosa");

        // La sesión previa mantiene sus coordenadas congeladas intactas
        assertEquals(95.0, activeSession.getArena().center().y());
        assertEquals(60.0, activeSession.getArena().podium().y());

        // La configuración activa reporta las nuevas coordenadas
        ArenaDefinition updatedArena = configService.getDefaultArena();
        assertEquals(110.0, updatedArena.center().y());
        assertEquals(70.0, updatedArena.podium().y());
    }

    @Test
    @DisplayName("Reload inválido de arenas.yml mantiene la configuración previa activa intacta (fail-safe)")
    void testInvalidReloadPreservesPreviousConfiguration() throws Exception {
        File configFile = tempDir.resolve("config.yml").toFile();
        File arenasFile = tempDir.resolve("arenas.yml").toFile();

        Files.writeString(configFile.toPath(), "portal:\n  enabled: false\nlogging:\n  level: 'INFO'\n");
        Files.writeString(arenasFile.toPath(), """
                arenas:
                  default:
                    world: "world_the_end"
                    center:
                      x: 0.0
                      y: 100.0
                      z: 0.0
                    podium:
                      x: 0.0
                      y: 65.0
                      z: 0.0
                    bounds:
                      min:
                        x: -100.0
                        y: 0.0
                        z: -100.0
                      max:
                        x: 100.0
                        y: 200.0
                        z: 100.0
                    rules:
                      water_allowed: false
                      boundary_enabled: true
                      anti_tunnel_enabled: true
                """);

        ConfigurationService configService = new ConfigurationService(logger);
        configService.loadInitial(configFile, arenasFile);

        // Intentar recargar con arenas.yml corrupto (sin world)
        Files.writeString(arenasFile.toPath(), "arenas:\n  default:\n    world: ''\n");

        boolean reloadSuccess = configService.reload(configFile, arenasFile);
        assertFalse(reloadSuccess, "Recarga corrupta debió fallar");

        // La configuración previa permanece 100% activa
        ArenaDefinition active = configService.getDefaultArena();
        assertEquals("default", active.id());
        assertEquals("world_the_end", active.worldName());
        assertEquals(100.0, active.center().y());
    }

    @Test
    @DisplayName("ARENA_CENTER y PODIUM_CENTER usan coordenadas reales y no fallbacks arbitrarios")
    void testRealSpatialCoordinatesWithoutArbitraryFallbacks() {
        Vector3d realCenter = new Vector3d(42.5, 115.0, -80.0);
        Vector3d realPodium = new Vector3d(12.0, 58.0, 3.5);
        ArenaBounds bounds = new ArenaBounds(-200.0, 0.0, -200.0, 200.0, 256.0, 200.0);

        ArenaDefinition arena = new ArenaDefinition("custom", "world_the_end", realCenter, realPodium, bounds, ArenaRuleSet.defaults());
        BattleSpatialContext context = new maurxp.betterdragon.ability.ArenaBattleSpatialContext(arena);

        Location locCenter = context.getArenaCenter(fakeEndWorld);
        assertEquals(42.5, locCenter.getX(), 0.001);
        assertEquals(115.0, locCenter.getY(), 0.001);
        assertEquals(-80.0, locCenter.getZ(), 0.001);

        Location locPodium = context.getPodiumCenter(fakeEndWorld);
        assertEquals(12.0, locPodium.getX(), 0.001);
        assertEquals(58.0, locPodium.getY(), 0.001);
        assertEquals(3.5, locPodium.getZ(), 0.001);

        assertNotEquals(locCenter, locPodium);
    }

    @Test
    @DisplayName("isInArena devuelve false si el mundo no coincide aunque las coordenadas estén dentro del AABB")
    void testIsInArenaReturnsFalseForOtherWorldEvenWithSameCoordinates() {
        Vector3d center = new Vector3d(0.0, 100.0, 0.0);
        Vector3d podium = new Vector3d(0.0, 65.0, 0.0);
        ArenaBounds bounds = new ArenaBounds(-100.0, 0.0, -100.0, 100.0, 256.0, 100.0);
        ArenaDefinition arena = new ArenaDefinition("end_arena", "world_the_end", center, podium, bounds, ArenaRuleSet.defaults());
        BattleSpatialContext context = new maurxp.betterdragon.ability.ArenaBattleSpatialContext(arena);

        // Ubicación en el mundo correcto dentro de bounds
        Location inEndWorld = new Location(fakeEndWorld, 10.0, 65.0, 10.0);
        assertTrue(context.isInArena(inEndWorld), "Debe pertenecer a la arena si mundo y coordenadas coinciden");

        // Misma coordenada pero en mundo diferente
        Location inOtherWorld = new Location(fakeOtherEndWorld, 10.0, 65.0, 10.0);
        assertFalse(context.isInArena(inOtherWorld), "Debe retornar false para un mundo distinto aunque coordenadas coincidan");
    }

    @Test
    @DisplayName("startBattle con arena no-default preserva su ID exacto en BattleSession y Snapshot")
    void testStartBattleWithNonDefaultArenaSynchronizesIdAndSnapshot() throws Exception {
        File configFile = tempDir.resolve("config_multi.yml").toFile();
        File arenasFile = tempDir.resolve("arenas_multi.yml").toFile();

        Files.writeString(configFile.toPath(), "portal:\n  enabled: false\nlogging:\n  level: 'INFO'\n");
        Files.writeString(arenasFile.toPath(), """
                arenas:
                  default:
                    world: "world_the_end"
                    center:
                      x: 0.0
                      y: 100.0
                      z: 0.0
                    podium:
                      x: 0.0
                      y: 65.0
                      z: 0.0
                    bounds:
                      min:
                        x: -100.0
                        y: 0.0
                        z: -100.0
                      max:
                        x: 100.0
                        y: 200.0
                        z: 100.0
                    rules:
                      water_allowed: false
                      boundary_enabled: true
                      anti_tunnel_enabled: true
                  arena_pvp:
                    world: "world_the_end"
                    center:
                      x: 10.0
                      y: 90.0
                      z: 10.0
                    podium:
                      x: 10.0
                      y: 60.0
                      z: 10.0
                    bounds:
                      min:
                        x: -80.0
                        y: 0.0
                        z: -80.0
                      max:
                        x: 80.0
                        y: 180.0
                        z: 80.0
                    rules:
                      water_allowed: false
                      boundary_enabled: true
                      anti_tunnel_enabled: true
                """);

        ConfigurationService configService = new ConfigurationService(logger);
        configService.loadInitial(configFile, arenasFile);

        BattleManager manager = new BattleManager(sessionManager, configService, createStubSpawner(), logger);
        BattleSession session = manager.startBattle(fakeEndWorld, null, "default", "arena_pvp");

        assertEquals("arena_pvp", session.getArenaId(), "El arenaId de la sesión debe ser arena_pvp");
        assertEquals("arena_pvp", session.getArena().id(), "El id de ArenaDefinition debe ser arena_pvp");
        assertEquals("arena_pvp", session.getConfigSnapshot().arenaDefinition().id(), "El snapshot debe contener arena_pvp");
        assertEquals(90.0, session.getArena().center().y());
    }

    @Test
    @DisplayName("TargetSelector ALL_IN_ARENA utiliza los límites geométricos reales de la arena")
    void testTargetSelectorUsesRealArenaBounds() {
        ArenaBounds tightBounds = new ArenaBounds(-50.0, 0.0, -50.0, 50.0, 150.0, 50.0);
        ArenaDefinition customArena = new ArenaDefinition(
                "tight", "world_the_end",
                new Vector3d(0.0, 100.0, 0.0),
                new Vector3d(0.0, 65.0, 0.0),
                tightBounds,
                ArenaRuleSet.defaults()
        );

        BattleSpatialContext context = new maurxp.betterdragon.ability.ArenaBattleSpatialContext(customArena);
        TargetSelector selector = new TargetSelector(context, new Random(42L));

        Player playerInside = createFakePlayer(UUID.randomUUID(), new Location(fakeEndWorld, 30.0, 65.0, -20.0));
        Player playerOutside = createFakePlayer(UUID.randomUUID(), new Location(fakeEndWorld, 75.0, 65.0, 0.0)); // X > 50

        worldPlayers.add(playerInside);
        worldPlayers.add(playerOutside);

        AbilityDefinition dummy = new AbilityDefinition("test", AbilityTrigger.PERIODIC, 0L,
                TargetSelectorType.ALL_IN_ARENA, EffectOriginType.DRAGON_BODY, AbilityEffectType.SOUND);

        List<Player> targets = selector.resolveTargets(TargetSelectorType.ALL_IN_ARENA, dummy, fakeEndWorld,
                context.getArenaCenter(fakeEndWorld), null, Optional.empty());

        assertEquals(1, targets.size());
        assertTrue(targets.contains(playerInside));
        assertFalse(targets.contains(playerOutside));
    }

    @Test
    @DisplayName("startBattle rechaza iniciar si la arena solicitada no existe o mundo no coincide")
    void testStartBattleRejectsUnresolvableOrMismatchedArena() {
        ConfigurationService configService = new ConfigurationService(logger);
        BattleManager manager = new BattleManager(sessionManager, configService, createStubSpawner(), logger);

        // 1. Arena que no existe en la configuración
        IllegalStateException ex1 = assertThrows(IllegalStateException.class, () ->
                manager.startBattle(fakeEndWorld, null, "default", "non_existent_arena")
        );
        assertTrue(ex1.getMessage().contains("no existe en arenas.yml"));

        // 2. Mundo que no coincide con la arena default (configurada para world_the_end)
        IllegalStateException ex2 = assertThrows(IllegalStateException.class, () ->
                manager.startBattle(fakeOtherEndWorld, null, "default", "default")
        );
        assertTrue(ex2.getMessage().contains("está configurada para el mundo"));
    }

    private static class FakePdc implements PersistentDataContainer {
        private final Map<NamespacedKey, Object> map = new HashMap<>();

        @Override
        public <T, Z> void set(@NotNull NamespacedKey key, @NotNull PersistentDataType<T, Z> type, @NotNull Z value) {
            map.put(key, value);
        }

        @Override
        public <T, Z> boolean has(@NotNull NamespacedKey key, @NotNull PersistentDataType<T, Z> type) {
            return map.containsKey(key);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T, Z> @Nullable Z get(@NotNull NamespacedKey key, @NotNull PersistentDataType<T, Z> type) {
            return (Z) map.get(key);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T, Z> @NotNull Z getOrDefault(@NotNull NamespacedKey key, @NotNull PersistentDataType<T, Z> type, @NotNull Z defaultValue) {
            return (Z) map.getOrDefault(key, defaultValue);
        }

        @Override
        public @NotNull Set<NamespacedKey> getKeys() {
            return map.keySet();
        }

        @Override
        public void remove(@NotNull NamespacedKey key) {
            map.remove(key);
        }

        @Override
        public int getSize() {
            return map.size();
        }

        @Override
        public boolean has(@NotNull NamespacedKey key) {
            return map.containsKey(key);
        }

        @Override
        public boolean isEmpty() {
            return map.isEmpty();
        }

        @Override
        public void copyTo(@NotNull PersistentDataContainer other, boolean replace) {}

        @Override
        public @NotNull PersistentDataAdapterContext getAdapterContext() {
            return null;
        }

        @Override
        public void readFromBytes(byte[] bytes, boolean clear) {}

        @Override
        public byte[] serializeToBytes() {
            return new byte[0];
        }
    }
}
