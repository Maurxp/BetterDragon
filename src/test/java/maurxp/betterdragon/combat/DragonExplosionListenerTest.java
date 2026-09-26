package maurxp.betterdragon.combat;

import maurxp.betterdragon.battle.BattleSession;
import maurxp.betterdragon.battle.BattleSessionManager;
import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.util.BetterDragonKeys;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias para {@link DragonExplosionListener} y protección selectiva de terreno.
 *
 * @author maurxp
 */
class DragonExplosionListenerTest {

    private BattleSessionManager sessionManager;
    private DragonExplosionListener listener;
    private BattleSession session;
    private BattleId battleId;

    @BeforeEach
    void setUp() {
        listener = new DragonExplosionListener();
        battleId = BattleId.random();
        session = BattleSession.create(battleId, "world_the_end", UUID.randomUUID());
    }

    @Test
    @DisplayName("Explosión generada por BetterDragon limpia blockList() protegiendo el terreno")
    void testBetterDragonExplosionClearsBlockList() {
        Map<String, Object> pdcStore = new HashMap<>();
        pdcStore.put(BetterDragonKeys.MANAGED.toString(), (byte) 1);
        pdcStore.put(BetterDragonKeys.BATTLE_ID.toString(), battleId.asString());

        PersistentDataContainer fakePdc = createMockPdc(pdcStore);
        Entity fakeTnt = createMockEntity(fakePdc);

        List<Block> blocks = new ArrayList<>(List.of(createMockBlock(), createMockBlock()));
        EntityExplodeEvent event = new EntityExplodeEvent(fakeTnt, new Location(null, 0, 64, 0), blocks, 0.0f, null);

        listener.onEntityExplode(event);

        assertTrue(event.blockList().isEmpty(), "La lista de bloques debe quedar vacía para explosiones BetterDragon");
        assertFalse(event.isCancelled(), "El evento no se cancela globalmente para permitir daño a jugadores");
    }

    @Test
    @DisplayName("Explosión externa (vanilla/creeper/cama) conserva blockList() intacto sin interferencia")
    void testExternalExplosionUntouched() {
        // PDC vacío (sin etiquetas BetterDragon)
        PersistentDataContainer emptyPdc = createMockPdc(new HashMap<>());
        Entity fakeExternalTnt = createMockEntity(emptyPdc);

        List<Block> blocks = new ArrayList<>(List.of(createMockBlock(), createMockBlock()));
        EntityExplodeEvent event = new EntityExplodeEvent(fakeExternalTnt, new Location(null, 0, 64, 0), blocks, 0.0f, null);

        listener.onEntityExplode(event);

        assertEquals(2, event.blockList().size(), "Las explosiones externas no deben ser alteradas");
    }

    @Test
    @DisplayName("Explosión con battle_id no registrado en sessionManager igualmente limpia bloques si porta firma BetterDragon")
    void testExplosionFromTerminatedBattleClearsBlocks() {
        Map<String, Object> pdcStore = new HashMap<>();
        pdcStore.put(BetterDragonKeys.MANAGED.toString(), (byte) 1);
        pdcStore.put(BetterDragonKeys.BATTLE_ID.toString(), UUID.randomUUID().toString()); // Otra batalla

        PersistentDataContainer fakePdc = createMockPdc(pdcStore);
        Entity fakeTnt = createMockEntity(fakePdc);

        List<Block> blocks = new ArrayList<>(List.of(createMockBlock()));
        EntityExplodeEvent event = new EntityExplodeEvent(fakeTnt, new Location(null, 0, 64, 0), blocks, 0.0f, null);

        listener.onEntityExplode(event);

        assertTrue(event.blockList().isEmpty(), "Cualquier explosivo BetterDragon debe tener su blockList suprimida");
    }

    private PersistentDataContainer createMockPdc(Map<String, Object> store) {
        return (PersistentDataContainer) Proxy.newProxyInstance(
                PersistentDataContainer.class.getClassLoader(),
                new Class<?>[]{PersistentDataContainer.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("has")) {
                        Object key = args[0];
                        if (!store.containsKey(key.toString())) return false;
                        if (args.length >= 2 && args[1] == PersistentDataType.BOOLEAN) {
                            return store.get(key.toString()) instanceof Boolean;
                        }
                        if (args.length >= 2 && args[1] == PersistentDataType.BYTE) {
                            return store.get(key.toString()) instanceof Byte;
                        }
                        return true;
                    }
                    if (name.equals("get")) {
                        Object key = args[0];
                        return store.get(key.toString());
                    }
                    return null;
                }
        );
    }

    private Entity createMockEntity(PersistentDataContainer pdc) {
        return (Entity) Proxy.newProxyInstance(
                Entity.class.getClassLoader(),
                new Class<?>[]{Entity.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getPersistentDataContainer")) return pdc;
                    if (method.getName().equals("isValid")) return true;
                    return null;
                }
        );
    }

    private Block createMockBlock() {
        return (Block) Proxy.newProxyInstance(
                Block.class.getClassLoader(),
                new Class<?>[]{Block.class},
                (proxy, method, args) -> null
        );
    }
}
