package maurxp.betterdragon.battle;

import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.battle.model.BattleState;
import maurxp.betterdragon.battle.model.DragonIdentity;
import maurxp.betterdragon.config.ConfigurationService;
import maurxp.betterdragon.util.BetterDragonKeys;
import org.bukkit.NamespacedKey;
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

/**
 * Tests unitarios de resolución y recuperación diferida de ciclo de vida (Fase 3.3-R1).
 *
 * @author maurxp
 */
class BattleRecoveryTest {

    private BattleSessionManager sessionManager;
    private ConfigurationService configService;
    private Logger logger;
    private BattleManager battleManager;

    @BeforeEach
    void setUp() {
        this.sessionManager = new BattleSessionManager();
        this.logger = Logger.getLogger("BattleRecoveryTest");
        this.configService = new ConfigurationService(logger);
        this.battleManager = new BattleManager(sessionManager, configService, new DragonSpawner(), logger);
    }

    @Test
    @DisplayName("Caso 1: ACTIVE -> DEFERRED -> dragón válido resuelto -> ACTIVE")
    void testActiveToDeferredToActive() {
        BattleId battleId = BattleId.random();
        UUID dragonId = UUID.randomUUID();
        BattleSession session = BattleSession.create(battleId, "world_the_end", UUID.randomUUID());
        session.start();
        DragonIdentity identity = DragonIdentity.of(dragonId, battleId);
        session.activate(identity);
        sessionManager.register(session);

        assertEquals(BattleState.ACTIVE, session.getState());

        // Chunk se descarga
        session.deferPendingChunkLoad();
        assertEquals(BattleState.DEFERRED_PENDING_CHUNK_LOAD, session.getState());
        assertEquals(BattleState.ACTIVE, session.getStateBeforeChunkDeferral().orElse(null));

        // Dragón legítimo se resuelve
        FakePdc pdc = new FakePdc();
        EnderDragon dragon = createFakeDragon(dragonId, pdc);
        DragonPdcHandler.applyIdentity(dragon, identity);

        boolean resolved = battleManager.resolveDeferredDragon(session, dragon);
        assertTrue(resolved);
        assertEquals(BattleState.ACTIVE, session.getState());
        assertTrue(session.getStateBeforeChunkDeferral().isEmpty());
    }

    @Test
    @DisplayName("Caso 2: DYING -> DEFERRED -> dragón válido resuelto -> DYING")
    void testDyingToDeferredToDying() {
        BattleId battleId = BattleId.random();
        UUID dragonId = UUID.randomUUID();
        BattleSession session = BattleSession.create(battleId, "world_the_end", UUID.randomUUID());
        session.start();
        DragonIdentity identity = DragonIdentity.of(dragonId, battleId);
        session.activate(identity);
        session.beginDying();
        sessionManager.register(session);

        assertEquals(BattleState.DYING, session.getState());

        // Chunk se descarga durante el proceso de muerte
        session.deferPendingChunkLoad();
        assertEquals(BattleState.DEFERRED_PENDING_CHUNK_LOAD, session.getState());
        assertEquals(BattleState.DYING, session.getStateBeforeChunkDeferral().orElse(null));

        // Dragón legítimo se resuelve
        FakePdc pdc = new FakePdc();
        EnderDragon dragon = createFakeDragon(dragonId, pdc);
        DragonPdcHandler.applyIdentity(dragon, identity);

        boolean resolved = battleManager.resolveDeferredDragon(session, dragon);
        assertTrue(resolved);
        assertEquals(BattleState.DYING, session.getState());
    }

    @Test
    @DisplayName("Caso 3: DEFERRED + entidad ausente (null) -> ABORTED / ENTITY_MISSING")
    void testDeferredWithMissingEntityAborts() {
        BattleId battleId = BattleId.random();
        UUID dragonId = UUID.randomUUID();
        BattleSession session = BattleSession.create(battleId, "world_the_end", UUID.randomUUID());
        session.start();
        session.activate(DragonIdentity.of(dragonId, battleId));
        sessionManager.register(session);

        session.deferPendingChunkLoad();

        boolean resolved = battleManager.resolveDeferredDragon(session, (EnderDragon) null);
        assertFalse(resolved);
        assertEquals(BattleState.ABORTED, session.getState());
        assertTrue(session.isTerminal());
        assertTrue(sessionManager.getSession(battleId).isEmpty());
    }

    @Test
    @DisplayName("Caso 4: DEFERRED + entidad equivocada (UUID distinto) -> ABORTED / ENTITY_MISSING")
    void testDeferredWithWrongEntityUuidAborts() {
        BattleId battleId = BattleId.random();
        UUID expectedDragonId = UUID.randomUUID();
        UUID wrongDragonId = UUID.randomUUID();

        BattleSession session = BattleSession.create(battleId, "world_the_end", UUID.randomUUID());
        session.start();
        session.activate(DragonIdentity.of(expectedDragonId, battleId));
        sessionManager.register(session);

        session.deferPendingChunkLoad();

        FakePdc pdc = new FakePdc();
        EnderDragon wrongDragon = createFakeDragon(wrongDragonId, pdc);
        DragonPdcHandler.applyIdentity(wrongDragon, DragonIdentity.of(wrongDragonId, battleId));

        boolean resolved = battleManager.resolveDeferredDragon(session, wrongDragon);
        assertFalse(resolved);
        assertEquals(BattleState.ABORTED, session.getState());
        assertTrue(sessionManager.getSession(battleId).isEmpty());
    }

    @Test
    @DisplayName("Caso 5: DEFERRED + PDC incorrecto (managed=false) -> ABORTED / ENTITY_MISSING")
    void testDeferredWithUnmanagedEntityAborts() {
        BattleId battleId = BattleId.random();
        UUID dragonId = UUID.randomUUID();

        BattleSession session = BattleSession.create(battleId, "world_the_end", UUID.randomUUID());
        session.start();
        session.activate(DragonIdentity.of(dragonId, battleId));
        sessionManager.register(session);

        session.deferPendingChunkLoad();

        FakePdc pdc = new FakePdc();
        EnderDragon dragon = createFakeDragon(dragonId, pdc);
        pdc.set(BetterDragonKeys.MANAGED, PersistentDataType.BOOLEAN, false);
        pdc.set(BetterDragonKeys.BATTLE_ID, PersistentDataType.STRING, battleId.asString());

        boolean resolved = battleManager.resolveDeferredDragon(session, dragon);
        assertFalse(resolved);
        assertEquals(BattleState.ABORTED, session.getState());
        assertTrue(sessionManager.getSession(battleId).isEmpty());
    }

    @Test
    @DisplayName("Caso 6: DEFERRED + battle_id incorrecto -> ABORTED / ENTITY_MISSING")
    void testDeferredWithMismatchedBattleIdAborts() {
        BattleId sessionBattleId = BattleId.random();
        BattleId differentBattleId = BattleId.random();
        UUID dragonId = UUID.randomUUID();

        BattleSession session = BattleSession.create(sessionBattleId, "world_the_end", UUID.randomUUID());
        session.start();
        session.activate(DragonIdentity.of(dragonId, sessionBattleId));
        sessionManager.register(session);

        session.deferPendingChunkLoad();

        FakePdc pdc = new FakePdc();
        EnderDragon dragon = createFakeDragon(dragonId, pdc);
        DragonPdcHandler.applyIdentity(dragon, DragonIdentity.of(dragonId, differentBattleId));

        boolean resolved = battleManager.resolveDeferredDragon(session, dragon);
        assertFalse(resolved);
        assertEquals(BattleState.ABORTED, session.getState());
        assertTrue(sessionManager.getSession(sessionBattleId).isEmpty());
    }

    @Test
    @DisplayName("Caso 7: DEFERRED + entidad correcta restaura estado previo y no adopta entidad inválida")
    void testDeferredRestoresCorrectPreviousState() {
        BattleId battleId = BattleId.random();
        UUID dragonId = UUID.randomUUID();
        BattleSession session = BattleSession.create(battleId, "world_the_end", UUID.randomUUID());
        session.start();
        DragonIdentity identity = DragonIdentity.of(dragonId, battleId);
        session.activate(identity);
        sessionManager.register(session);

        // Deferral
        session.deferPendingChunkLoad();

        // Entidad legítima
        FakePdc pdc = new FakePdc();
        EnderDragon dragon = createFakeDragon(dragonId, pdc);
        DragonPdcHandler.applyIdentity(dragon, identity);

        assertTrue(battleManager.resolveDeferredDragon(session, dragon));
        assertEquals(BattleState.ACTIVE, session.getState());
        assertFalse(session.isTerminal());
        assertTrue(sessionManager.getSession(battleId).isPresent());
    }

    // --- Helpers ---

    private EnderDragon createFakeDragon(UUID uuid, PersistentDataContainer pdc) {
        return (EnderDragon) Proxy.newProxyInstance(
                EnderDragon.class.getClassLoader(),
                new Class<?>[]{EnderDragon.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) {
                        return uuid;
                    }
                    if (method.getName().equals("getPersistentDataContainer")) {
                        return pdc;
                    }
                    if (method.getName().equals("isValid")) {
                        return true;
                    }
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
        public boolean has(@NotNull NamespacedKey key) {
            return map.containsKey(key);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T, Z> @Nullable Z get(@NotNull NamespacedKey key, @NotNull PersistentDataType<T, Z> type) {
            return (Z) map.get(key);
        }

        @Override
        public <T, Z> @NotNull Z getOrDefault(@NotNull NamespacedKey key, @NotNull PersistentDataType<T, Z> type, @NotNull Z defaultValue) {
            Z val = get(key, type);
            return val != null ? val : defaultValue;
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
        public boolean isEmpty() {
            return map.isEmpty();
        }

        @Override
        public void copyTo(@NotNull PersistentDataContainer other, boolean replace) {
        }

        @Override
        public @NotNull PersistentDataAdapterContext getAdapterContext() {
            return null;
        }

        @Override
        public byte[] serializeToBytes() {
            return new byte[0];
        }

        @Override
        public void readFromBytes(byte[] bytes, boolean clear) {
        }

        @Override
        public int getSize() {
            return map.size();
        }
    }
}
