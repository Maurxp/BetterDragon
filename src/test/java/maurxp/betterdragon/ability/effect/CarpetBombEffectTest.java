package maurxp.betterdragon.ability.effect;

import maurxp.betterdragon.ability.AbilityDefinition;
import maurxp.betterdragon.ability.AbilityEffectType;
import maurxp.betterdragon.ability.AbilityExecutionContext;
import maurxp.betterdragon.ability.AbilityTrigger;
import maurxp.betterdragon.ability.EffectOriginType;
import maurxp.betterdragon.ability.TargetSelectorType;
import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.phase.PhaseDefinition;
import maurxp.betterdragon.util.BetterDragonKeys;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias para {@link CarpetBombEffect}.
 *
 * @author maurxp
 */
class CarpetBombEffectTest {

    private BattleId battleId;
    private World fakeWorld;
    private EnderDragon fakeDragon;
    private Location origin;
    private PhaseDefinition phase;
    private List<TNTPrimed> spawnedTntList;

    @BeforeEach
    void setUp() {
        battleId = BattleId.random();
        spawnedTntList = new ArrayList<>();

        fakeWorld = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("getName")) return "world_the_end";
                    if (name.equals("spawn")) {
                        Class<?> entityClass = (Class<?>) args[1];
                        if (TNTPrimed.class.isAssignableFrom(entityClass)) {
                            TNTPrimed tnt = createFakeTnt((Location) args[0]);
                            spawnedTntList.add(tnt);
                            return tnt;
                        }
                    }
                    if (name.equals("playSound")) return null;
                    if (name.equals("equals")) return proxy == args[0];
                    return null;
                }
        );

        origin = new Location(fakeWorld, 0.0, 75.0, 0.0);
        phase = new PhaseDefinition("p1", 0, 1.0, List.of());

        fakeDragon = (EnderDragon) Proxy.newProxyInstance(
                EnderDragon.class.getClassLoader(),
                new Class<?>[]{EnderDragon.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("getWorld")) return fakeWorld;
                    if (name.equals("getLocation")) return origin;
                    if (name.equals("isValid")) return true;
                    if (name.equals("equals")) return proxy == args[0];
                    return null;
                }
        );
    }

    private TNTPrimed createFakeTnt(Location loc) {
        Map<NamespacedKey, Object> pdcMap = new HashMap<>();
        AtomicInteger fuseTicks = new AtomicInteger(80);

        PersistentDataContainer pdc = (PersistentDataContainer) Proxy.newProxyInstance(
                PersistentDataContainer.class.getClassLoader(),
                new Class<?>[]{PersistentDataContainer.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("set")) {
                        pdcMap.put((NamespacedKey) args[0], args[2]);
                        return null;
                    }
                    if (name.equals("has")) {
                        return pdcMap.containsKey((NamespacedKey) args[0]);
                    }
                    if (name.equals("get")) {
                        return pdcMap.get((NamespacedKey) args[0]);
                    }
                    return null;
                }
        );

        return (TNTPrimed) Proxy.newProxyInstance(
                TNTPrimed.class.getClassLoader(),
                new Class<?>[]{TNTPrimed.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("getLocation")) return loc;
                    if (name.equals("getPersistentDataContainer")) return pdc;
                    if (name.equals("setFuseTicks")) {
                        fuseTicks.set((int) args[0]);
                        return null;
                    }
                    if (name.equals("getFuseTicks")) return fuseTicks.get();
                    if (name.equals("isValid")) return true;
                    if (name.equals("equals")) return proxy == args[0];
                    return null;
                }
        );
    }

    @Test
    @DisplayName("Ejecución engendra la cantidad de TNT especificada con las 3 claves PDC de BetterDragon")
    void testCarpetBombSpawnsTntWithPdcTags() {
        AbilityDefinition ability = new AbilityDefinition(
                "bomb_ability",
                AbilityTrigger.ON_FLIGHT_PHASE,
                100L,
                TargetSelectorType.ALL_IN_ARENA,
                EffectOriginType.DRAGON_BODY,
                AbilityEffectType.CARPET_BOMB,
                Map.of("count", 4, "fuse_ticks", 45, "spread", 5.0),
                null
        );

        AbilityExecutionContext context = new AbilityExecutionContext(
                battleId,
                "world_the_end",
                fakeDragon,
                AbilityTrigger.ON_FLIGHT_PHASE,
                Optional.empty(),
                List.of(),
                origin,
                100L,
                ability,
                phase
        );

        CarpetBombEffect effect = new CarpetBombEffect();
        effect.execute(context);

        assertEquals(4, spawnedTntList.size(), "Debe engendrar 4 entidades TNT");

        for (TNTPrimed tnt : spawnedTntList) {
            assertEquals(45, tnt.getFuseTicks(), "Debe respetar fuse_ticks configurado");
            var pdc = tnt.getPersistentDataContainer();
            assertTrue(pdc.has(BetterDragonKeys.MANAGED, PersistentDataType.BOOLEAN));
            assertTrue(pdc.has(BetterDragonKeys.BATTLE_ID, PersistentDataType.STRING));
            assertTrue(pdc.has(BetterDragonKeys.EXPLOSIVE, PersistentDataType.BOOLEAN));
            assertEquals(battleId.asString(), pdc.get(BetterDragonKeys.BATTLE_ID, PersistentDataType.STRING));
        }
    }

    @Test
    @DisplayName("Contexto nulo lanza NullPointerException")
    void testNullContextThrows() {
        CarpetBombEffect effect = new CarpetBombEffect();
        assertThrows(NullPointerException.class, () -> effect.execute(null));
    }

    @Test
    @DisplayName("Parámetros por defecto se aplican cuando no se especifican propiedades")
    void testCarpetBombDefaultProperties() {
        AbilityDefinition ability = new AbilityDefinition(
                "default_bomb",
                AbilityTrigger.ON_FLIGHT_PHASE,
                100L,
                TargetSelectorType.ALL_IN_ARENA,
                EffectOriginType.DRAGON_BODY,
                AbilityEffectType.CARPET_BOMB,
                Map.of(),
                null
        );

        AbilityExecutionContext context = new AbilityExecutionContext(
                battleId,
                "world_the_end",
                fakeDragon,
                AbilityTrigger.ON_FLIGHT_PHASE,
                Optional.empty(),
                List.of(),
                origin,
                100L,
                ability,
                phase
        );

        CarpetBombEffect effect = new CarpetBombEffect();
        effect.execute(context);

        assertEquals(CarpetBombEffect.DEFAULT_BOMB_COUNT, spawnedTntList.size());
        assertEquals(CarpetBombEffect.DEFAULT_FUSE_TICKS, spawnedTntList.get(0).getFuseTicks());
    }
}
