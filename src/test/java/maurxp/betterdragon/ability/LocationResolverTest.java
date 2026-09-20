package maurxp.betterdragon.ability;

import maurxp.betterdragon.arena.ArenaBounds;
import maurxp.betterdragon.arena.ArenaDefinition;
import maurxp.betterdragon.arena.ArenaRuleSet;
import maurxp.betterdragon.arena.Vector3d;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias para {@link LocationResolver}.
 *
 * @author maurxp
 */
class LocationResolverTest {

    private LocationResolver resolver;
    private EnderDragon fakeDragon;
    private World fakeWorld;
    private Location dragonLoc;
    private Location eyeLoc;

    @BeforeEach
    void setUp() {
        ArenaDefinition defaultArena = ArenaDefinition.defaults();
        BattleSpatialContext spatialContext = new ArenaBattleSpatialContext(defaultArena);
        resolver = new LocationResolver(spatialContext);

        fakeWorld = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getName")) return "world_the_end";
                    if (method.getName().equals("equals")) return proxy == args[0];
                    return null;
                }
        );

        dragonLoc = new Location(fakeWorld, 10.0, 70.0, 10.0);
        dragonLoc.setDirection(new Vector(0, 0, 1)); // mirando hacia +Z
        eyeLoc = new Location(fakeWorld, 10.0, 72.0, 10.0);
        eyeLoc.setDirection(new Vector(0, 0, 1));

        fakeDragon = (EnderDragon) Proxy.newProxyInstance(
                EnderDragon.class.getClassLoader(),
                new Class<?>[]{EnderDragon.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("getWorld")) return fakeWorld;
                    if (name.equals("getLocation")) return dragonLoc;
                    if (name.equals("getEyeLocation")) return eyeLoc;
                    if (name.equals("isValid")) return true;
                    return null;
                }
        );
    }

    private Player createFakePlayer(Location loc, boolean valid) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("getLocation")) return loc;
                    if (name.equals("isValid")) return valid;
                    if (name.equals("getUniqueId")) return UUID.randomUUID();
                    return null;
                }
        );
    }

    @Test
    @DisplayName("DRAGON_HEAD proyecta ubicación hacia el frente")
    void testDragonHead() {
        Location head = resolver.resolve(EffectOriginType.DRAGON_HEAD, fakeDragon, List.of(), Optional.empty());
        assertNotNull(head);
        assertEquals(10.0, head.getX(), 0.001);
        assertEquals(72.0, head.getY(), 0.001);
        assertEquals(13.0, head.getZ(), 0.001); // 10.0 + 3.0
    }

    @Test
    @DisplayName("DRAGON_BODY retorna clon de la ubicación del dragón")
    void testDragonBody() {
        Location body = resolver.resolve(EffectOriginType.DRAGON_BODY, fakeDragon, List.of(), Optional.empty());
        assertEquals(dragonLoc, body);
    }

    @Test
    @DisplayName("TARGET_FEET usa los pies del target o realiza fallback seguro al dragón si no hay targets")
    void testTargetFeet() {
        Location targetLoc = new Location(fakeWorld, 25.0, 64.0, -15.0);
        Player fakeTarget = createFakePlayer(targetLoc, true);

        // Con target válido
        Location feet = resolver.resolve(EffectOriginType.TARGET_FEET, fakeDragon, List.of(fakeTarget), Optional.empty());
        assertEquals(targetLoc, feet);

        // Sin target (lista vacía) -> fallback al dragón sin lanzar NPE
        Location fallback = resolver.resolve(EffectOriginType.TARGET_FEET, fakeDragon, List.of(), Optional.empty());
        assertEquals(dragonLoc, fallback);
    }

    @Test
    @DisplayName("PODIUM_CENTER y ARENA_CENTER resuelven coordenadas distintas y respetan su separación semántica")
    void testCenterLocationsSemanticSeparation() {
        Location podium = resolver.resolve(EffectOriginType.PODIUM_CENTER, fakeDragon, List.of(), Optional.empty());
        assertEquals(0.0, podium.getX(), 0.001);
        assertEquals(65.0, podium.getY(), 0.001); // Nivel de pedestal de bedrock
        assertEquals(0.0, podium.getZ(), 0.001);

        Location arena = resolver.resolve(EffectOriginType.ARENA_CENTER, fakeDragon, List.of(), Optional.empty());
        assertEquals(0.0, arena.getX(), 0.001);
        assertEquals(100.0, arena.getY(), 0.001); // Nivel aéreo de combate de la arena
        assertEquals(0.0, arena.getZ(), 0.001);

        assertNotEquals(podium, arena, "PODIUM_CENTER y ARENA_CENTER deben ser semántica y espacialmente distintos");

        // Proveedor personalizado de contexto espacial
        Location customPodium = new Location(fakeWorld, 10.0, 60.0, 10.0);
        Location customArena = new Location(fakeWorld, 50.0, 120.0, -30.0);
        ArenaDefinition customArenaDef = new ArenaDefinition(
                "custom",
                "world_the_end",
                new Vector3d(50.0, 120.0, -30.0),
                new Vector3d(10.0, 60.0, 10.0),
                new ArenaBounds(-100, 0, -100, 100, 256, 100),
                ArenaRuleSet.defaults()
        );
        BattleSpatialContext customContext = new ArenaBattleSpatialContext(customArenaDef);
        LocationResolver customResolver = new LocationResolver(customContext);

        Location resolvedCustomPodium = customResolver.resolve(EffectOriginType.PODIUM_CENTER, fakeDragon, List.of(), Optional.empty());
        Location resolvedCustomArena = customResolver.resolve(EffectOriginType.ARENA_CENTER, fakeDragon, List.of(), Optional.empty());

        assertEquals(customPodium, resolvedCustomPodium);
        assertEquals(customArena, resolvedCustomArena);
    }

    @Test
    @DisplayName("Constructor de LocationResolver rechaza spatialContext nulo sin fallbacks mágicos")
    void testNullSpatialContextThrowsNpe() {
        assertThrows(NullPointerException.class, () -> new LocationResolver(null));
    }

    @Test
    @DisplayName("TRIGGER_LOCATION retorna ubicación del trigger o fallback seguro al dragón")
    void testTriggerLocation() {
        Location customTrigger = new Location(fakeWorld, 5.0, 60.0, 5.0);

        Location resolved = resolver.resolve(EffectOriginType.TRIGGER_LOCATION, fakeDragon, List.of(), Optional.of(customTrigger));
        assertEquals(customTrigger, resolved);

        Location fallback = resolver.resolve(EffectOriginType.TRIGGER_LOCATION, fakeDragon, List.of(), Optional.empty());
        assertEquals(dragonLoc, fallback);
    }
}
