package maurxp.betterdragon.anticheese;

import maurxp.betterdragon.arena.ArenaBounds;
import maurxp.betterdragon.arena.ArenaDefinition;
import maurxp.betterdragon.arena.ArenaRuleSet;
import maurxp.betterdragon.arena.Vector3d;
import maurxp.betterdragon.battle.BattleSession;
import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.battle.model.DragonIdentity;
import maurxp.betterdragon.config.BattleConfigurationSnapshot;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias de SafeReturnLocationStrategy")
class SafeReturnLocationStrategyTest {

    private SafeReturnLocationStrategy strategy;
    private World fakeEndWorld;
    private World fakeOverworld;
    private UUID endWorldId;
    private UUID overworldId;
    private BattleId battleId;
    private BattleSession session;
    private Player fakePlayer;

    @BeforeEach
    void setUp() {
        strategy = new SafeReturnLocationStrategy(8.0);
        endWorldId = UUID.randomUUID();
        overworldId = UUID.randomUUID();
        battleId = BattleId.random();

        fakeEndWorld = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    if ("getName".equals(method.getName())) return "world_the_end";
                    if ("getUID".equals(method.getName())) return endWorldId;
                    if ("isChunkLoaded".equals(method.getName())) return true;
                    if ("equals".equals(method.getName())) return proxy == args[0];
                    return null;
                }
        );

        fakeOverworld = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    if ("getName".equals(method.getName())) return "world";
                    if ("getUID".equals(method.getName())) return overworldId;
                    if ("equals".equals(method.getName())) return proxy == args[0];
                    return null;
                }
        );

        fakePlayer = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if ("getWorld".equals(method.getName())) return fakeEndWorld;
                    return null;
                }
        );

        ArenaRuleSet rules = ArenaRuleSet.defaults();
        ArenaDefinition arena = new ArenaDefinition(
                "default",
                "world_the_end",
                new Vector3d(0.0, 100.0, 0.0),
                new Vector3d(0.0, 65.0, 0.0),
                new ArenaBounds(-150.0, 0.0, -150.0, 150.0, 256.0, 150.0),
                rules
        );
        BattleConfigurationSnapshot snapshot = new BattleConfigurationSnapshot(
                false,
                false,
                maurxp.betterdragon.config.DragonDefinition.defaults(),
                arena
        );
        session = BattleSession.create(battleId, "world_the_end", endWorldId, snapshot);
        session.start();
        DragonIdentity identity = DragonIdentity.of(UUID.randomUUID(), battleId, "default");
        session.activate(identity);
    }

    @Test
    @DisplayName("Prioridad 1: utiliza la última posición válida si es físicamente segura")
    void testPriority1LastKnownValid() {
        Location validLast = new Location(fakeEndWorld, 20.0, 68.0, 20.0);

        Location resolved = strategy.resolveSafeLocation(fakePlayer, session, validLast);
        assertNotNull(resolved);
        assertEquals(20.0, resolved.getX(), 0.001);
        assertEquals(68.0, resolved.getY(), 0.001);
        assertEquals(20.0, resolved.getZ(), 0.001);
    }

    @Test
    @DisplayName("Prioridad 2: si la última posición válida está en el vacío o fuera de límites, usa el podio")
    void testPriority2PodiumFallbackWhenLastInvalid() {
        Location inVoid = new Location(fakeEndWorld, 20.0, -10.0, 20.0); // Y < 55.0

        Location resolved = strategy.resolveSafeLocation(fakePlayer, session, inVoid);
        assertNotNull(resolved);
        // El podio está configurado en (0.0, 65.0, 0.0) -> candidate (0.5, 66.0, 0.5)
        assertEquals(0.5, resolved.getX(), 0.001);
        assertEquals(66.0, resolved.getY(), 0.001);
        assertEquals(0.5, resolved.getZ(), 0.001);
    }

    @Test
    @DisplayName("isSafe rechaza posiciones con mundo incorrecto, cota de vacío o cercanas al límite")
    void testIsSafeValidations() {
        // Mundo incorrecto
        Location wrongWorld = new Location(fakeOverworld, 0.0, 66.0, 0.0);
        assertFalse(strategy.isSafe(wrongWorld, session, fakeEndWorld));

        // En el vacío (Y < 55.0)
        Location voidLoc = new Location(fakeEndWorld, 0.0, 40.0, 0.0);
        assertFalse(strategy.isSafe(voidLoc, session, fakeEndWorld));

        // Demasiado cerca del borde perimetral (bounds max 150, inset 8 -> límite 142)
        Location nearEdge = new Location(fakeEndWorld, 145.0, 66.0, 0.0);
        assertFalse(strategy.isSafe(nearEdge, session, fakeEndWorld));

        // Ubicación segura en el podio
        Location safe = new Location(fakeEndWorld, 0.5, 66.0, 0.5);
        assertTrue(strategy.isSafe(safe, session, fakeEndWorld));
    }

    @Test
    @DisplayName("SafeReturnLocationStrategy: preserva exactamente yaw y pitch del jugador sin desorientar")
    void testResolveSafeLocationPreservesPlayerYawAndPitch() {
        Location playerLoc = new Location(fakeEndWorld, 20.0, 68.0, 20.0, 137.0f, -23.0f);
        Player playerWithAngles = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if ("getWorld".equals(method.getName())) return fakeEndWorld;
                    if ("getLocation".equals(method.getName())) return playerLoc.clone();
                    return null;
                }
        );

        Location validLast = new Location(fakeEndWorld, 20.0, 68.0, 20.0);
        Location resolved = strategy.resolveSafeLocation(playerWithAngles, session, validLast);

        assertNotNull(resolved);
        assertEquals(137.0f, resolved.getYaw(), 0.001f, "El yaw debe preservarse exactamente igual al del jugador");
        assertEquals(-23.0f, resolved.getPitch(), 0.001f, "El pitch debe preservarse exactamente igual al del jugador");
    }

    @Test
    @DisplayName("Prioridad 4: arena desplazada del origen usa el centro real y no (0, 0, 0)")
    void testTier4FallbackWithDisplacedArena() {
        ArenaBounds displacedBounds = new ArenaBounds(350.0, 0.0, -850.0, 650.0, 256.0, -550.0);
        Vector3d displacedCenter = new Vector3d(500.0, 70.0, -700.0);
        // Colocamos podio en cota de vacío inválida para forzar caída hasta Tier 4
        Vector3d invalidPodium = new Vector3d(500.0, 20.0, -700.0);
        ArenaDefinition displacedArena = new ArenaDefinition(
                "displaced",
                "world_the_end",
                displacedCenter,
                invalidPodium,
                displacedBounds,
                ArenaRuleSet.defaults()
        );
        BattleConfigurationSnapshot displacedSnapshot = new BattleConfigurationSnapshot(
                false,
                false,
                maurxp.betterdragon.config.DragonDefinition.defaults(),
                displacedArena
        );
        BattleSession displacedSession = BattleSession.create(BattleId.random(), "world_the_end", endWorldId, displacedSnapshot);
        displacedSession.start();
        displacedSession.activate(DragonIdentity.of(UUID.randomUUID(), displacedSession.getBattleId(), "default"));

        // lastKnownValid nulo e invalidPodium para activar Tier 4
        Location resolved = strategy.resolveSafeLocation(fakePlayer, displacedSession, null);

        assertNotNull(resolved);
        // Debe estar alrededor de X=500 y Z=-700, nunca cerca del origen
        assertEquals(500.0, resolved.getX(), 5.0, "X debe estar centrado en la arena desplazada");
        assertEquals(-700.0, resolved.getZ(), 5.0, "Z debe estar centrado en la arena desplazada");
        assertTrue(Math.abs(resolved.getX()) > 400.0, "X debe estar inequívocamente desplazado del origen");
        assertTrue(Math.abs(resolved.getZ()) > 600.0, "Z debe estar inequívocamente desplazado del origen");
        assertTrue(resolved.getY() >= SafeReturnLocationStrategy.MIN_SAFE_Y, "Y debe ser mayor o igual a MIN_SAFE_Y");
    }

    @Test
    @DisplayName("SafeReturnLocationStrategy: arena estrecha no produce inversión de cotas en resolveSafeLocation")
    void testResolveSafeLocationNarrowArenaNoInversion() {
        // Dimensión 8.0 < 2 * safeInset (2 * 8.0 = 16.0)
        ArenaBounds narrowBounds = new ArenaBounds(0.0, 0.0, 0.0, 8.0, 120.0, 8.0);
        ArenaDefinition narrowArena = new ArenaDefinition(
                "narrow",
                "world_the_end",
                new Vector3d(4.0, 70.0, 4.0),
                new Vector3d(4.0, 65.0, 4.0),
                narrowBounds,
                ArenaRuleSet.defaults()
        );
        BattleConfigurationSnapshot narrowSnapshot = new BattleConfigurationSnapshot(
                false,
                false,
                maurxp.betterdragon.config.DragonDefinition.defaults(),
                narrowArena
        );
        BattleSession narrowSession = BattleSession.create(BattleId.random(), "world_the_end", endWorldId, narrowSnapshot);
        narrowSession.start();
        narrowSession.activate(DragonIdentity.of(UUID.randomUUID(), narrowSession.getBattleId(), "default"));

        Location resolved = strategy.resolveSafeLocation(fakePlayer, narrowSession, null);
        assertNotNull(resolved);
        assertTrue(resolved.getX() >= narrowBounds.minX() && resolved.getX() <= narrowBounds.maxX(),
                "X debe estar dentro de la arena estrecha");
        assertTrue(resolved.getZ() >= narrowBounds.minZ() && resolved.getZ() <= narrowBounds.maxZ(),
                "Z debe estar dentro de la arena estrecha");
        assertTrue(resolved.getY() >= SafeReturnLocationStrategy.MIN_SAFE_Y,
                "Y debe respetar la cota mínima de vacío");
    }

    @Test
    @DisplayName("isSafe: rechaza ubicaciones con WITHER_ROSE como peligro físico")
    void testIsSafeRejectsWitherRoseHazard() {
        org.bukkit.block.Block roseBlock = (org.bukkit.block.Block) Proxy.newProxyInstance(
                org.bukkit.block.Block.class.getClassLoader(),
                new Class<?>[]{org.bukkit.block.Block.class},
                (proxy, method, args) -> {
                    if ("getType".equals(method.getName())) return org.bukkit.Material.WITHER_ROSE;
                    if ("isPassable".equals(method.getName())) return true;
                    return null;
                }
        );

        World worldWithRose = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    if ("getName".equals(method.getName())) return "world_the_end";
                    if ("isChunkLoaded".equals(method.getName())) return true;
                    if ("getBlockAt".equals(method.getName())) return roseBlock;
                    return null;
                }
        );

        Location locWithRose = new Location(worldWithRose, 0.5, 66.0, 0.5);
        assertFalse(strategy.isSafe(locWithRose, session, worldWithRose),
                "Una ubicación con WITHER_ROSE en los pies debe ser rechazada por isSafe()");
    }

    @Test
    @DisplayName("Tier 4: evita bloques peligrosos ajustando la posición cuando el chunk está cargado")
    void testTier4AvoidsHazardousBlockWhenChunkLoaded() {
        org.bukkit.block.Block hazardBlock = (org.bukkit.block.Block) Proxy.newProxyInstance(
                org.bukkit.block.Block.class.getClassLoader(),
                new Class<?>[]{org.bukkit.block.Block.class},
                (proxy, method, args) -> {
                    if ("getType".equals(method.getName())) return org.bukkit.Material.LAVA;
                    if ("isPassable".equals(method.getName())) return false;
                    return null;
                }
        );

        World worldWithHazard = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    if ("getName".equals(method.getName())) return "world_the_end";
                    if ("isChunkLoaded".equals(method.getName())) return true;
                    if ("getBlockAt".equals(method.getName())) {
                        Location reqLoc = (Location) args[0];
                        if (reqLoc != null && reqLoc.getBlockY() == 70) {
                            return hazardBlock;
                        }
                    }
                    return null;
                }
        );

        Player hazardPlayer = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if ("getWorld".equals(method.getName())) return worldWithHazard;
                    return null;
                }
        );

        ArenaBounds testBounds = new ArenaBounds(-50.0, 0.0, -50.0, 50.0, 150.0, 50.0);
        Vector3d testCenter = new Vector3d(0.0, 70.0, 0.0);
        ArenaDefinition testArena = new ArenaDefinition(
                "hazard_test",
                "world_the_end",
                testCenter,
                new Vector3d(0.0, 20.0, 0.0), // podio en el vacío para forzar Tier 4
                testBounds,
                ArenaRuleSet.defaults()
        );
        BattleConfigurationSnapshot testSnapshot = new BattleConfigurationSnapshot(
                false, false, maurxp.betterdragon.config.DragonDefinition.defaults(), testArena
        );
        BattleSession testSession = BattleSession.create(BattleId.random(), "world_the_end", endWorldId, testSnapshot);
        testSession.start();
        testSession.activate(DragonIdentity.of(UUID.randomUUID(), testSession.getBattleId(), "default"));

        Location resolved = strategy.resolveSafeLocation(hazardPlayer, testSession, null);
        assertNotNull(resolved);
        assertNotEquals(70.0, resolved.getY(), "Tier 4 no debe colocar al jugador en la cota con lava");
    }
}

