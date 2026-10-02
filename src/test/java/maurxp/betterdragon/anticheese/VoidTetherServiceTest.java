package maurxp.betterdragon.anticheese;

import maurxp.betterdragon.arena.ArenaBounds;
import maurxp.betterdragon.arena.ArenaDefinition;
import maurxp.betterdragon.arena.ArenaRuleSet;
import maurxp.betterdragon.arena.Vector3d;
import maurxp.betterdragon.battle.BattleSession;
import maurxp.betterdragon.battle.BattleSessionManager;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias de VoidTetherService")
class VoidTetherServiceTest {

    private BattleSessionManager sessionManager;
    private BoundaryPolicy boundaryPolicy;
    private SafeReturnLocationStrategy returnStrategy;
    private VoidTetherService tetherService;

    private World fakeEndWorld;
    private UUID endWorldId;
    private BattleId battleId;
    private BattleSession session;

    private UUID participantId;
    private UUID nonParticipantId;
    private Player fakeParticipant;
    private Player fakeNonParticipant;

    private AtomicReference<Location> participantLocation;
    private AtomicBoolean teleported;

    @BeforeEach
    void setUp() {
        sessionManager = new BattleSessionManager();
        boundaryPolicy = new BoundaryPolicy(5.0);
        returnStrategy = new SafeReturnLocationStrategy(8.0);
        tetherService = new VoidTetherService(sessionManager, boundaryPolicy, returnStrategy);

        endWorldId = UUID.randomUUID();
        battleId = BattleId.random();
        participantId = UUID.randomUUID();
        nonParticipantId = UUID.randomUUID();

        participantLocation = new AtomicReference<>(new Location(null, 0.0, 65.0, 0.0));
        teleported = new AtomicBoolean(false);

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

        participantLocation.get().setWorld(fakeEndWorld);

        fakeParticipant = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getUniqueId".equals(name)) return participantId;
                    if ("getWorld".equals(name)) return fakeEndWorld;
                    if ("getLocation".equals(name)) return participantLocation.get();
                    if ("teleport".equals(name) && args.length > 0 && args[0] instanceof Location loc) {
                        participantLocation.set(loc.clone());
                        teleported.set(true);
                        return true;
                    }
                    if ("setFallDistance".equals(name)) return null;
                    if ("setVelocity".equals(name)) return null;
                    if ("playSound".equals(name)) return null;
                    return null;
                }
        );

        fakeNonParticipant = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getUniqueId".equals(name)) return nonParticipantId;
                    if ("getWorld".equals(name)) return fakeEndWorld;
                    return null;
                }
        );

        ArenaRuleSet rules = new ArenaRuleSet(
                false,
                true,
                true,
                ExplosionPolicyType.PROTECT_ARENA,
                true
        );
        ArenaDefinition arena = new ArenaDefinition(
                "default",
                "world_the_end",
                new Vector3d(0.0, 100.0, 0.0),
                new Vector3d(0.0, 65.0, 0.0),
                new ArenaBounds(-100.0, 0.0, -100.0, 100.0, 200.0, 100.0),
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
        sessionManager.register(session);

        // Registrar participante legítimo con daño en el CombatRuntime
        session.getCombatRuntime().recordDamage(participantId, "Hero", 25.0, 100L);
    }

    @Test
    @DisplayName("Participante sale de la arena: es detectado, recuperado y teletransportado a ubicación segura")
    void testParticipantLeavesArena() {
        Location fromInside = new Location(fakeEndWorld, 50.0, 65.0, 50.0);
        Location toOutside = new Location(fakeEndWorld, 150.0, 65.0, 150.0); // Brecha perimetral

        boolean tethered = tetherService.checkAndEnforce(fakeParticipant, fromInside, toOutside);
        assertTrue(tethered, "El Void Tether debe intervenir cuando un participante abandona la arena");
        assertTrue(teleported.get(), "El participante debe haber sido teletransportado");
        assertTrue(session.getArena().bounds().contains(participantLocation.get()), "La nueva posición debe estar en la arena");
    }

    @Test
    @DisplayName("Participante cae al vacío (Y <= 0): es rescatado inmediatamente")
    void testParticipantFallsIntoVoid() {
        Location fromInside = new Location(fakeEndWorld, 0.0, 10.0, 0.0);
        Location toVoid = new Location(fakeEndWorld, 0.0, -2.0, 0.0);

        boolean tethered = tetherService.checkAndEnforce(fakeParticipant, fromInside, toVoid);
        assertTrue(tethered, "Debe rescatar al participante que cae al vacío");
        assertTrue(teleported.get());
        assertTrue(participantLocation.get().getY() >= 55.0, "Debe retornar a cota segura");
    }

    @Test
    @DisplayName("Jugador no participante: NO es afectado por el Void Tether al salir o caer al vacío")
    void testNonParticipantIgnored() {
        Location from = new Location(fakeEndWorld, 0.0, 65.0, 0.0);
        Location toOutside = new Location(fakeEndWorld, 200.0, 65.0, 200.0);

        boolean tethered = tetherService.checkAndEnforce(fakeNonParticipant, from, toOutside);
        assertFalse(tethered, "Los no participantes no deben ser restringidos por Void Tether");
    }

    @Test
    @DisplayName("Última posición válida: retorna a la última posición segura registrada dentro de la arena")
    void testLastKnownValidPositionRestored() {
        Location safeInside = new Location(fakeEndWorld, 30.0, 68.0, 30.0);
        // Movimiento interior legítimo que registra la posición válida
        tetherService.checkAndEnforce(fakeParticipant, safeInside, safeInside);
        assertTrue(tetherService.getLastKnownValidPosition(participantId).isPresent());

        // Ahora cae al vacío
        Location toVoid = new Location(fakeEndWorld, 30.0, -5.0, 30.0);
        boolean tethered = tetherService.checkAndEnforce(fakeParticipant, safeInside, toVoid);
        assertTrue(tethered);

        assertEquals(30.0, participantLocation.get().getX(), 0.001);
        assertEquals(68.0, participantLocation.get().getY(), 0.001);
        assertEquals(30.0, participantLocation.get().getZ(), 0.001);
    }

    @Test
    @DisplayName("Fallback seguro: si no hay última posición válida, recurre al podio de la arena")
    void testFallbackToPodium() {
        Location outside = new Location(fakeEndWorld, 150.0, 65.0, 150.0);

        boolean tethered = tetherService.checkAndEnforce(fakeParticipant, outside, outside);
        assertTrue(tethered);
        // Podio default (0.5, 66.0, 0.5)
        assertEquals(0.5, participantLocation.get().getX(), 0.001);
        assertEquals(66.0, participantLocation.get().getY(), 0.001);
        assertEquals(0.5, participantLocation.get().getZ(), 0.001);
    }

    @Test
    @DisplayName("Mundo no disponible o sin batalla activa: no interviene")
    void testNoActiveBattleInWorld() {
        World otherWorld = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    if ("getName".equals(method.getName())) return "world_nether";
                    if ("getUID".equals(method.getName())) return UUID.randomUUID();
                    return null;
                }
        );
        Location outsideInOtherWorld = new Location(otherWorld, 500.0, 65.0, 500.0);

        boolean tethered = tetherService.checkAndEnforce(fakeParticipant, outsideInOtherWorld, outsideInOtherWorld);
        assertFalse(tethered, "Sin batalla activa en ese mundo, Void Tether no interviene");
    }

    @Test
    @DisplayName("Batalla terminada o abortada: el Void Tether se desactiva inmediatamente")
    void testBattleTerminatedDisablesTether() {
        session.abort("TEST");
        assertTrue(session.isTerminal());

        Location outside = new Location(fakeEndWorld, 200.0, 65.0, 200.0);
        boolean tethered = tetherService.checkAndEnforce(fakeParticipant, outside, outside);
        assertFalse(tethered, "Una batalla abortada o completada desactiva inmediatamente el Void Tether");
    }

    @Test
    @DisplayName("Desconexión y cambio de mundo: limpian el registro de rastreo en memoria")
    void testPlayerQuitAndChangeWorldCleanups() {
        Location safe = new Location(fakeEndWorld, 10.0, 65.0, 10.0);
        tetherService.checkAndEnforce(fakeParticipant, safe, safe);
        assertTrue(tetherService.getLastKnownValidPosition(participantId).isPresent());

        // Jugador se desconecta
        tetherService.onPlayerQuit(participantId);
        assertFalse(tetherService.getLastKnownValidPosition(participantId).isPresent());

        // Vuelve a registrarse y cambia de mundo
        tetherService.checkAndEnforce(fakeParticipant, safe, safe);
        assertTrue(tetherService.getLastKnownValidPosition(participantId).isPresent());
        tetherService.onPlayerChangeWorld(participantId);
        assertFalse(tetherService.getLastKnownValidPosition(participantId).isPresent());
    }

    @Test
    @DisplayName("Prevención de loops (debounce): descarta ejecuciones consecutivas inmediatas (< 1000ms)")
    void testLoopPreventionDebounce() {
        Location outside = new Location(fakeEndWorld, 150.0, 65.0, 150.0);

        boolean first = tetherService.checkAndEnforce(fakeParticipant, outside, outside);
        assertTrue(first, "El primer intento debe ejecutarse");

        boolean second = tetherService.checkAndEnforce(fakeParticipant, outside, outside);
        assertFalse(second, "El segundo intento inmediato debe descartarse por prevención de loops");
    }

    @Test
    @DisplayName("rescueFromVoid: rescata directamente al participante ante daño de vacío")
    void testRescueFromVoid() {
        boolean rescued = tetherService.rescueFromVoid(fakeParticipant);
        assertTrue(rescued, "rescueFromVoid debe rescatar al participante activo");
        assertTrue(teleported.get());
    }
}
