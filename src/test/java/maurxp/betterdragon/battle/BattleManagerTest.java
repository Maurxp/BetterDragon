package maurxp.betterdragon.battle;

import maurxp.betterdragon.battle.model.BattleAbortReason;
import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.battle.model.BattleState;
import maurxp.betterdragon.battle.model.DragonIdentity;
import maurxp.betterdragon.config.ConfigurationService;
import maurxp.betterdragon.util.BetterDragonKeys;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class BattleManagerTest {

    private BattleSessionManager sessionManager;
    private ConfigurationService configService;
    private Logger logger;

    @BeforeEach
    void setUp() {
        this.sessionManager = new BattleSessionManager();
        this.logger = Logger.getLogger("BattleManagerTest");
        this.configService = new ConfigurationService(logger);
    }

    @Test
    @DisplayName("startBattle rechaza mundos que no sean THE_END")
    void testStartBattleRejectsNonEndWorld() {
        World normalWorld = createFakeWorld("world_overworld", UUID.randomUUID(), World.Environment.NORMAL);
        DragonSpawner spawner = new DragonSpawner();
        BattleManager manager = new BattleManager(sessionManager, configService, spawner, logger);

        assertThrows(IllegalArgumentException.class, () -> manager.startBattle(normalWorld));
    }

    @Test
    @DisplayName("startBattle rechaza batallas duplicadas para el mismo mundo")
    void testStartBattleRejectsDuplicateBattle() {
        World endWorld = createFakeWorld("world_the_end", UUID.randomUUID(), World.Environment.THE_END);

        DragonSpawner fakeSpawner = new DragonSpawner() {
            @Override
            public EnderDragon spawnDragon(World world, Location location, BattleId battleId, String definitionId) {
                return createFakeDragon(UUID.randomUUID(), battleId, definitionId);
            }
        };

        BattleManager manager = new BattleManager(sessionManager, configService, fakeSpawner, logger);

        // 1. Primera batalla se activa
        BattleSession session1 = manager.startBattle(endWorld);
        assertEquals(BattleState.ACTIVE, session1.getState());

        // 2. Intento de segunda batalla en el mismo mundo debe fallar
        assertThrows(IllegalStateException.class, () -> manager.startBattle(endWorld));
    }

    @Test
    @DisplayName("startBattle completa flujo PREPARING -> ACTIVE y asocia DragonIdentity")
    void testStartBattleSuccessfulFlow() {
        World endWorld = createFakeWorld("test_world_the_end", UUID.randomUUID(), World.Environment.THE_END);

        UUID dragonUuid = UUID.randomUUID();
        DragonSpawner fakeSpawner = new DragonSpawner() {
            @Override
            public EnderDragon spawnDragon(World world, Location location, BattleId battleId, String definitionId) {
                return createFakeDragon(dragonUuid, battleId, definitionId);
            }
        };

        BattleManager manager = new BattleManager(sessionManager, configService, fakeSpawner, logger);
        BattleSession session = manager.startBattle(endWorld);

        assertNotNull(session);
        assertEquals(BattleState.ACTIVE, session.getState());
        assertTrue(session.getDragonIdentity().isPresent());
        assertEquals(dragonUuid, session.getDragonIdentity().get().entityUniqueId());
        assertEquals(session.getBattleId(), session.getDragonIdentity().get().battleId());
        assertTrue(sessionManager.hasActiveSession(endWorld.getName()));
    }

    @Test
    @DisplayName("Fallo durante el spawn no deja sesión activa huérfana en memoria")
    void testSpawnFailureCleansUpSession() {
        World endWorld = createFakeWorld("test_world_the_end", UUID.randomUUID(), World.Environment.THE_END);

        DragonSpawner failingSpawner = new DragonSpawner() {
            @Override
            public EnderDragon spawnDragon(World world, Location location, BattleId battleId, String definitionId) {
                throw new RuntimeException("Simulated spawn failure");
            }
        };

        BattleManager manager = new BattleManager(sessionManager, configService, failingSpawner, logger);

        assertThrows(RuntimeException.class, () -> manager.startBattle(endWorld));

        // Verificar que no quedó ninguna sesión registrada en sessionManager
        assertFalse(sessionManager.hasActiveSession(endWorld.getName()));
        assertEquals(0, sessionManager.getSessionCount());
    }

    @Test
    @DisplayName("abortBattle cancela la sesión con motivo tipado y la remueve de memoria")
    void testAbortBattle() {
        World endWorld = createFakeWorld("test_world_the_end", UUID.randomUUID(), World.Environment.THE_END);
        DragonSpawner fakeSpawner = new DragonSpawner() {
            @Override
            public EnderDragon spawnDragon(World world, Location location, BattleId battleId, String definitionId) {
                return createFakeDragon(UUID.randomUUID(), battleId, definitionId);
            }
        };

        BattleManager manager = new BattleManager(sessionManager, configService, fakeSpawner, logger);
        BattleSession session = manager.startBattle(endWorld);

        assertTrue(manager.getActiveSession(session.getBattleId()).isPresent());

        manager.abortBattle(session.getBattleId(), BattleAbortReason.MANUAL_ABORT);

        assertEquals(BattleState.ABORTED, session.getState());
        assertTrue(manager.getActiveSession(session.getBattleId()).isEmpty());
        assertFalse(sessionManager.hasActiveSession(endWorld.getName()));
    }

    // --- Helpers de prueba ---

    private World createFakeWorld(String name, UUID uid, World.Environment env) {
        return (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getName")) return name;
                    if (method.getName().equals("getUID")) return uid;
                    if (method.getName().equals("getEnvironment")) return env;
                    return null;
                }
        );
    }

    private EnderDragon createFakeDragon(UUID uuid, BattleId battleId, String definitionId) {
        FakePdc pdc = new FakePdc();
        pdc.set(BetterDragonKeys.MANAGED, PersistentDataType.BOOLEAN, true);
        pdc.set(BetterDragonKeys.BATTLE_ID, PersistentDataType.STRING, battleId.asString());
        pdc.set(BetterDragonKeys.DEFINITION_ID, PersistentDataType.STRING, definitionId);
        pdc.set(BetterDragonKeys.SCHEMA_VERSION, PersistentDataType.INTEGER, 1);

        return (EnderDragon) Proxy.newProxyInstance(
                EnderDragon.class.getClassLoader(),
                new Class<?>[]{EnderDragon.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return uuid;
                    if (method.getName().equals("getPersistentDataContainer")) return pdc;
                    if (method.getName().equals("isValid")) return true;
                    if (method.getName().equals("remove")) return null;
                    return null;
                }
        );
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
