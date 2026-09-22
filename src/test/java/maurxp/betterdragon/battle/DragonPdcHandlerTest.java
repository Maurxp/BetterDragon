package maurxp.betterdragon.battle;

import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.battle.model.DragonIdentity;
import maurxp.betterdragon.config.BattleConfigurationSnapshot;
import maurxp.betterdragon.util.BetterDragonKeys;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DragonPdcHandlerTest {

    @Test
    @DisplayName("applyIdentity y extractIdentity con datos válidos")
    void testApplyAndExtractValidIdentity() {
        UUID entityId = UUID.randomUUID();
        BattleId battleId = BattleId.random();
        DragonIdentity identity = DragonIdentity.of(entityId, battleId, "elder_dragon");

        FakeDataContainer pdc = new FakeDataContainer();
        EnderDragon dragon = createFakeDragon(entityId, pdc);

        DragonPdcHandler.applyIdentity(dragon, identity);

        // Fase 3.13: Se escriben MANAGED, BATTLE_ID, DEFINITION_ID y SCHEMA_VERSION
        assertTrue(pdc.has(BetterDragonKeys.MANAGED, PersistentDataType.BOOLEAN));
        assertTrue(pdc.has(BetterDragonKeys.BATTLE_ID, PersistentDataType.STRING));
        assertTrue(pdc.has(BetterDragonKeys.DEFINITION_ID, PersistentDataType.STRING));
        assertTrue(pdc.has(BetterDragonKeys.SCHEMA_VERSION, PersistentDataType.INTEGER));

        Optional<DragonIdentity> extractedOpt = DragonPdcHandler.extractIdentity(dragon);
        assertTrue(extractedOpt.isPresent());

        DragonIdentity extracted = extractedOpt.get();
        assertEquals(entityId, extracted.entityUniqueId());
        assertEquals(battleId, extracted.battleId());
        assertEquals("elder_dragon", extracted.definitionId());
        assertEquals(1, extracted.schemaVersion());
        assertTrue(DragonPdcHandler.isBetterDragon(dragon));
    }

    @Test
    @DisplayName("extractIdentity con entidad que no es EnderDragon retorna Optional.empty()")
    void testExtractNonEnderDragonReturnsEmpty() {
        Entity nonDragon = (Entity) Proxy.newProxyInstance(
                Entity.class.getClassLoader(),
                new Class<?>[]{Entity.class},
                (proxy, method, args) -> null
        );

        Optional<DragonIdentity> result = DragonPdcHandler.extractIdentity(nonDragon);
        assertTrue(result.isEmpty());
        assertFalse(DragonPdcHandler.isBetterDragon(nonDragon));
    }

    @Test
    @DisplayName("extractIdentity sin managed flag retorna empty")
    void testExtractMissingManagedReturnsEmpty() {
        UUID entityId = UUID.randomUUID();
        FakeDataContainer pdc = new FakeDataContainer();
        EnderDragon dragon = createFakeDragon(entityId, pdc);

        pdc.set(BetterDragonKeys.BATTLE_ID, PersistentDataType.STRING, BattleId.random().asString());

        Optional<DragonIdentity> result = DragonPdcHandler.extractIdentity(dragon);
        assertTrue(result.isEmpty());
        assertFalse(DragonPdcHandler.isBetterDragon(dragon));
    }

    @Test
    @DisplayName("extractIdentity con managed=false retorna empty")
    void testExtractManagedFalseReturnsEmpty() {
        UUID entityId = UUID.randomUUID();
        FakeDataContainer pdc = new FakeDataContainer();
        EnderDragon dragon = createFakeDragon(entityId, pdc);

        pdc.set(BetterDragonKeys.MANAGED, PersistentDataType.BOOLEAN, false);
        pdc.set(BetterDragonKeys.BATTLE_ID, PersistentDataType.STRING, BattleId.random().asString());

        Optional<DragonIdentity> result = DragonPdcHandler.extractIdentity(dragon);
        assertTrue(result.isEmpty());
        assertFalse(DragonPdcHandler.isBetterDragon(dragon));
    }

    @Test
    @DisplayName("extractIdentity con battle_id ausente retorna empty")
    void testExtractMissingBattleIdReturnsEmpty() {
        UUID entityId = UUID.randomUUID();
        FakeDataContainer pdc = new FakeDataContainer();
        EnderDragon dragon = createFakeDragon(entityId, pdc);

        pdc.set(BetterDragonKeys.MANAGED, PersistentDataType.BOOLEAN, true);

        Optional<DragonIdentity> result = DragonPdcHandler.extractIdentity(dragon);
        assertTrue(result.isEmpty());
        assertFalse(DragonPdcHandler.isBetterDragon(dragon));
    }

    @Test
    @DisplayName("extractIdentity con formato de battle_id corrupto retorna empty")
    void testExtractInvalidBattleIdReturnsEmpty() {
        UUID entityId = UUID.randomUUID();
        FakeDataContainer pdc = new FakeDataContainer();
        EnderDragon dragon = createFakeDragon(entityId, pdc);

        pdc.set(BetterDragonKeys.MANAGED, PersistentDataType.BOOLEAN, true);
        pdc.set(BetterDragonKeys.BATTLE_ID, PersistentDataType.STRING, "not-a-valid-uuid");

        Optional<DragonIdentity> result = DragonPdcHandler.extractIdentity(dragon);
        assertTrue(result.isEmpty());
        assertFalse(DragonPdcHandler.isBetterDragon(dragon));
    }

    @Test
    @DisplayName("extractIdentity aplica defaults si definitionId o schemaVersion no están presentes")
    void testExtractDefaultsForDefinitionAndSchema() {
        UUID entityId = UUID.randomUUID();
        BattleId battleId = BattleId.random();
        FakeDataContainer pdc = new FakeDataContainer();
        EnderDragon dragon = createFakeDragon(entityId, pdc);

        pdc.set(BetterDragonKeys.MANAGED, PersistentDataType.BOOLEAN, true);
        pdc.set(BetterDragonKeys.BATTLE_ID, PersistentDataType.STRING, battleId.asString());

        Optional<DragonIdentity> result = DragonPdcHandler.extractIdentity(dragon);
        assertTrue(result.isPresent());
        assertEquals("default", result.get().definitionId());
        assertEquals(1, result.get().schemaVersion());
    }

    @Test
    @DisplayName("validateDragonForSession con entidad y sesión coincidentes retorna true")
    void testValidateDragonForSessionSuccess() {
        UUID entityId = UUID.randomUUID();
        BattleId battleId = BattleId.random();
        FakeDataContainer pdc = new FakeDataContainer();
        EnderDragon dragon = createFakeDragon(entityId, pdc);
        DragonIdentity identity = DragonIdentity.of(entityId, battleId);
        DragonPdcHandler.applyIdentity(dragon, identity);

        BattleSession session = BattleSession.create(battleId, "world_the_end", UUID.randomUUID());
        session.start();
        session.activate(identity);

        assertTrue(DragonPdcHandler.validateDragonForSession(dragon, session));
    }

    @Test
    @DisplayName("validateDragonForSession rechaza entidad con UUID no coincidente")
    void testValidateDragonForSessionWrongUuid() {
        UUID sessionEntityId = UUID.randomUUID();
        UUID differentEntityId = UUID.randomUUID();
        BattleId battleId = BattleId.random();

        FakeDataContainer pdc = new FakeDataContainer();
        EnderDragon dragon = createFakeDragon(differentEntityId, pdc);
        DragonIdentity dragonIdentity = DragonIdentity.of(differentEntityId, battleId);
        DragonPdcHandler.applyIdentity(dragon, dragonIdentity);

        BattleSession session = BattleSession.create(battleId, "world_the_end", UUID.randomUUID());
        session.start();
        session.activate(DragonIdentity.of(sessionEntityId, battleId));

        assertFalse(DragonPdcHandler.validateDragonForSession(dragon, session));
    }

    @Test
    @DisplayName("validateDragonForSession rechaza entidad no managed")
    void testValidateDragonForSessionNotManaged() {
        UUID entityId = UUID.randomUUID();
        BattleId battleId = BattleId.random();
        FakeDataContainer pdc = new FakeDataContainer();
        EnderDragon dragon = createFakeDragon(entityId, pdc);

        pdc.set(BetterDragonKeys.MANAGED, PersistentDataType.BOOLEAN, false);
        pdc.set(BetterDragonKeys.BATTLE_ID, PersistentDataType.STRING, battleId.asString());

        BattleSession session = BattleSession.create(battleId, "world_the_end", UUID.randomUUID());
        session.start();
        session.activate(DragonIdentity.of(entityId, battleId));

        assertFalse(DragonPdcHandler.validateDragonForSession(dragon, session));
    }

    @Test
    @DisplayName("validateDragonForSession rechaza entidad con battle_id no coincidente")
    void testValidateDragonForSessionWrongBattleId() {
        UUID entityId = UUID.randomUUID();
        BattleId sessionBattleId = BattleId.random();
        BattleId differentBattleId = BattleId.random();

        FakeDataContainer pdc = new FakeDataContainer();
        EnderDragon dragon = createFakeDragon(entityId, pdc);
        DragonPdcHandler.applyIdentity(dragon, DragonIdentity.of(entityId, differentBattleId));

        BattleSession session = BattleSession.create(sessionBattleId, "world_the_end", UUID.randomUUID());
        session.start();
        session.activate(DragonIdentity.of(entityId, sessionBattleId));

        assertFalse(DragonPdcHandler.validateDragonForSession(dragon, session));
    }

    @Test
    @DisplayName("validateDragonForSession rechaza entidad no EnderDragon o sesión sin identidad")
    void testValidateDragonForSessionEdgeCases() {
        BattleId battleId = BattleId.random();
        BattleSession session = BattleSession.create(battleId, "world_the_end", UUID.randomUUID());

        // Sesión sin identidad (en IDLE o PREPARING sin activate)
        UUID entityId = UUID.randomUUID();
        FakeDataContainer pdc = new FakeDataContainer();
        EnderDragon dragon = createFakeDragon(entityId, pdc);
        DragonPdcHandler.applyIdentity(dragon, DragonIdentity.of(entityId, battleId));

        assertFalse(DragonPdcHandler.validateDragonForSession(dragon, session));

        // Argumentos nulos
        assertFalse(DragonPdcHandler.validateDragonForSession(null, session));
        assertFalse(DragonPdcHandler.validateDragonForSession(dragon, null));
    }

    @Test
    @DisplayName("validateDragonForSession rechaza entidad con definition_id que no coincide con el snapshot de la sesión")
    void testValidateDragonForSessionRejectsMismatchedDefinitionId() {
        UUID entityId = UUID.randomUUID();
        BattleId battleId = BattleId.random();

        // Dragón con definition_id = "infernal" en PDC
        FakeDataContainer pdc = new FakeDataContainer();
        EnderDragon dragon = createFakeDragon(entityId, pdc);
        DragonIdentity dragonIdentity = DragonIdentity.of(entityId, battleId, "infernal");
        DragonPdcHandler.applyIdentity(dragon, dragonIdentity);

        // Sesión configurada con definición "default" en su snapshot
        BattleSession session = BattleSession.create(battleId, "world_the_end", UUID.randomUUID(), BattleConfigurationSnapshot.defaults());
        session.start();
        session.activate(dragonIdentity);

        // Debe fallar porque el dragón es "infernal" pero la sesión espera "default"
        assertFalse(DragonPdcHandler.validateDragonForSession(dragon, session));
    }

    @Test
    @DisplayName("extractIdentity con schema_version futura desconocida retorna Optional.empty()")
    void testExtractUnsupportedFutureSchemaVersionReturnsEmpty() {
        UUID entityId = UUID.randomUUID();
        BattleId battleId = BattleId.random();
        FakeDataContainer pdc = new FakeDataContainer();
        EnderDragon dragon = createFakeDragon(entityId, pdc);

        pdc.set(BetterDragonKeys.MANAGED, PersistentDataType.BOOLEAN, true);
        pdc.set(BetterDragonKeys.BATTLE_ID, PersistentDataType.STRING, battleId.asString());
        pdc.set(BetterDragonKeys.DEFINITION_ID, PersistentDataType.STRING, "default");
        pdc.set(BetterDragonKeys.SCHEMA_VERSION, PersistentDataType.INTEGER, 99); // Versión futura no soportada

        Optional<DragonIdentity> result = DragonPdcHandler.extractIdentity(dragon);
        assertTrue(result.isEmpty(), "Una entidad con schema_version no soportada debe ser rechazada");
    }

    @Test
    @DisplayName("extractIdentity con schema_version inválida menor a 1 retorna Optional.empty()")
    void testExtractInvalidSchemaVersionZeroReturnsEmpty() {
        UUID entityId = UUID.randomUUID();
        BattleId battleId = BattleId.random();
        FakeDataContainer pdc = new FakeDataContainer();
        EnderDragon dragon = createFakeDragon(entityId, pdc);

        pdc.set(BetterDragonKeys.MANAGED, PersistentDataType.BOOLEAN, true);
        pdc.set(BetterDragonKeys.BATTLE_ID, PersistentDataType.STRING, battleId.asString());
        pdc.set(BetterDragonKeys.DEFINITION_ID, PersistentDataType.STRING, "default");
        pdc.set(BetterDragonKeys.SCHEMA_VERSION, PersistentDataType.INTEGER, 0); // Versión inválida

        Optional<DragonIdentity> result = DragonPdcHandler.extractIdentity(dragon);
        assertTrue(result.isEmpty(), "Una entidad con schema_version < 1 debe ser rechazada");
    }

    // --- Helpers de prueba con Dynamic Proxy ---

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

    private static class FakeDataContainer implements PersistentDataContainer {
        private final Map<NamespacedKey, Object> data = new HashMap<>();

        @Override
        public <T, Z> void set(@NotNull NamespacedKey key, @NotNull PersistentDataType<T, Z> type, @NotNull Z value) {
            data.put(key, value);
        }

        @Override
        public <T, Z> boolean has(@NotNull NamespacedKey key, @NotNull PersistentDataType<T, Z> type) {
            return data.containsKey(key);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T, Z> @Nullable Z get(@NotNull NamespacedKey key, @NotNull PersistentDataType<T, Z> type) {
            return (Z) data.get(key);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T, Z> @NotNull Z getOrDefault(@NotNull NamespacedKey key, @NotNull PersistentDataType<T, Z> type, @NotNull Z defaultValue) {
            return (Z) data.getOrDefault(key, defaultValue);
        }

        @Override
        public @NotNull Set<NamespacedKey> getKeys() {
            return data.keySet();
        }

        @Override
        public void remove(@NotNull NamespacedKey key) {
            data.remove(key);
        }

        @Override
        public int getSize() {
            return data.size();
        }

        @Override
        public boolean has(@NotNull NamespacedKey key) {
            return data.containsKey(key);
        }

        @Override
        public boolean isEmpty() {
            return data.isEmpty();
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
