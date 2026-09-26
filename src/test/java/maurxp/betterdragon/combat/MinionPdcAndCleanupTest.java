package maurxp.betterdragon.combat;

import maurxp.betterdragon.battle.BattleSession;
import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.util.BetterDragonKeys;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias para identificación por 4 claves PDC y limpieza determinista de esbirros.
 *
 * @author maurxp
 */
class MinionPdcAndCleanupTest {

    private BattleId battleId;
    private BattleSession session;
    private List<Entity> worldEntities;
    private World fakeWorld;

    @BeforeEach
    void setUp() {
        battleId = BattleId.random();
        worldEntities = new ArrayList<>();

        fakeWorld = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("getEntities")) return new ArrayList<>(worldEntities);
                    if (name.equals("getName")) return "world_the_end";
                    if (name.equals("equals")) return proxy == args[0];
                    return null;
                }
        );

        session = BattleSession.create(battleId, "world_the_end", UUID.randomUUID());
    }

    @Test
    @DisplayName("PDC de minions contiene las 4 firmas requeridas por la especificación")
    void testMinionPdcSignatures() {
        Map<String, Object> pdcStore = new HashMap<>();
        pdcStore.put(BetterDragonKeys.MANAGED.toString(), (byte) 1);
        pdcStore.put(BetterDragonKeys.BATTLE_ID.toString(), battleId.asString());
        pdcStore.put(BetterDragonKeys.MINION.toString(), (byte) 1);
        pdcStore.put(BetterDragonKeys.MINION_TYPE.toString(), "ENDERMITE");

        PersistentDataContainer pdc = createMockPdc(pdcStore);

        assertTrue(pdc.has(BetterDragonKeys.MANAGED));
        assertTrue(pdc.has(BetterDragonKeys.BATTLE_ID));
        assertTrue(pdc.has(BetterDragonKeys.MINION));
        assertTrue(pdc.has(BetterDragonKeys.MINION_TYPE));
        assertEquals(battleId.asString(), pdc.get(BetterDragonKeys.BATTLE_ID, PersistentDataType.STRING));
        assertEquals("ENDERMITE", pdc.get(BetterDragonKeys.MINION_TYPE, PersistentDataType.STRING));
    }

    @Test
    @DisplayName("Limpieza de sesión remueve esbirros propios y no toca mobs externos ni de otra batalla")
    void testDeterministicCleanupPreservesExternalMobs() {
        // 1. Minion de la batalla actual
        AtomicBoolean ownMinionRemoved = new AtomicBoolean(false);
        UUID ownMinionUuid = UUID.randomUUID();
        Entity ownMinion = createMockEntity(ownMinionUuid, Map.of(
                BetterDragonKeys.MANAGED.toString(), (byte) 1,
                BetterDragonKeys.BATTLE_ID.toString(), battleId.asString(),
                BetterDragonKeys.MINION.toString(), (byte) 1,
                BetterDragonKeys.MINION_TYPE.toString(), "ENDERMITE"
        ), ownMinionRemoved);

        // 2. Minion de OTRA batalla diferente
        AtomicBoolean otherBattleMinionRemoved = new AtomicBoolean(false);
        UUID otherBattleMinionUuid = UUID.randomUUID();
        Entity otherMinion = createMockEntity(otherBattleMinionUuid, Map.of(
                BetterDragonKeys.MANAGED.toString(), (byte) 1,
                BetterDragonKeys.BATTLE_ID.toString(), UUID.randomUUID().toString(),
                BetterDragonKeys.MINION.toString(), (byte) 1,
                BetterDragonKeys.MINION_TYPE.toString(), "ENDERMITE"
        ), otherBattleMinionRemoved);

        // 3. Mob natural vanilla (sin BetterDragon PDC)
        AtomicBoolean vanillaMobRemoved = new AtomicBoolean(false);
        UUID vanillaMobUuid = UUID.randomUUID();
        Entity vanillaMob = createMockEntity(vanillaMobUuid, Map.of(), vanillaMobRemoved);

        worldEntities.add(ownMinion);
        worldEntities.add(otherMinion);
        worldEntities.add(vanillaMob);

        // Registrar el minion propio en la sesión
        session.registerMinion(ownMinion);

        // Ejecutar limpieza pasando el mundo
        session.cleanSessionMinions(fakeWorld);

        // Verificaciones
        assertTrue(ownMinionRemoved.get(), "El minion de la batalla actual debe eliminarse");
        assertFalse(otherBattleMinionRemoved.get(), "El minion de otra batalla NO debe eliminarse");
        assertFalse(vanillaMobRemoved.get(), "Los mobs nativos/vanilla NO deben eliminarse");
    }

    @Test
    @DisplayName("Abortar sesión dispara limpieza determinista de minions registrados")
    void testCleanupOnSessionAbort() {
        AtomicBoolean minionRemoved = new AtomicBoolean(false);
        Entity minion = createMockEntity(UUID.randomUUID(), Map.of(
                BetterDragonKeys.MANAGED.toString(), (byte) 1,
                BetterDragonKeys.BATTLE_ID.toString(), battleId.asString(),
                BetterDragonKeys.MINION.toString(), (byte) 1,
                BetterDragonKeys.MINION_TYPE.toString(), "ENDERMITE"
        ), minionRemoved);

        session.registerMinion(minion);
        session.start();
        session.activate(maurxp.betterdragon.battle.model.DragonIdentity.of(UUID.randomUUID(), battleId));

        session.abort();

        assertTrue(minionRemoved.get(), "Abortar la batalla debe remover todos sus minions asociados");
    }

    private PersistentDataContainer createMockPdc(Map<String, Object> store) {
        return (PersistentDataContainer) Proxy.newProxyInstance(
                PersistentDataContainer.class.getClassLoader(),
                new Class<?>[]{PersistentDataContainer.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("has")) {
                        Object key = args[0];
                        return store.containsKey(key.toString());
                    }
                    if (name.equals("get")) {
                        Object key = args[0];
                        return store.get(key.toString());
                    }
                    return null;
                }
        );
    }

    private Entity createMockEntity(UUID uuid, Map<String, Object> pdcStore, AtomicBoolean removedFlag) {
        PersistentDataContainer pdc = createMockPdc(pdcStore);
        return (Entity) Proxy.newProxyInstance(
                Entity.class.getClassLoader(),
                new Class<?>[]{Entity.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("getUniqueId")) return uuid;
                    if (name.equals("getPersistentDataContainer")) return pdc;
                    if (name.equals("isValid")) return !removedFlag.get();
                    if (name.equals("isDead")) return removedFlag.get();
                    if (name.equals("remove")) {
                        removedFlag.set(true);
                        return null;
                    }
                    if (name.equals("equals")) return proxy == args[0];
                    if (name.equals("hashCode")) return uuid.hashCode();
                    return null;
                }
        );
    }
}
