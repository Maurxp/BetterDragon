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

@DisplayName("Pruebas Unitarias de WaterPolicy")
class WaterPolicyTest {

    private WaterPolicy policy;
    private World fakeWorld;
    private UUID worldId;
    private BattleId battleId;
    private Player fakePlayer;
    private UUID playerId;

    @BeforeEach
    void setUp() {
        policy = new WaterPolicy();
        worldId = UUID.randomUUID();
        battleId = BattleId.random();
        playerId = UUID.randomUUID();

        fakeWorld = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    if ("getName".equals(method.getName())) return "world_the_end";
                    if ("getUID".equals(method.getName())) return worldId;
                    if ("equals".equals(method.getName())) return proxy == args[0];
                    return null;
                }
        );

        fakePlayer = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if ("getUniqueId".equals(method.getName())) return playerId;
                    if ("getWorld".equals(method.getName())) return fakeWorld;
                    return null;
                }
        );
    }

    private BattleSession createSession(boolean waterAllowed) {
        ArenaRuleSet rules = new ArenaRuleSet(
                waterAllowed,
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
                new ArenaBounds(-150.0, 0.0, -150.0, 150.0, 256.0, 150.0),
                rules
        );
        BattleConfigurationSnapshot snapshot = new BattleConfigurationSnapshot(
                false,
                false,
                maurxp.betterdragon.config.DragonDefinition.defaults(),
                arena
        );
        BattleSession session = BattleSession.create(battleId, "world_the_end", worldId, snapshot);
        session.start();
        DragonIdentity identity = DragonIdentity.of(UUID.randomUUID(), battleId, "default");
        session.activate(identity);
        return session;
    }

    @Test
    @DisplayName("Colocación dentro de la arena: denegada si waterAllowed=false, permitida si waterAllowed=true")
    void testPlacementInsideArena() {
        Location inside = new Location(fakeWorld, 10.0, 65.0, 10.0);

        // Denegado
        BattleSession sessionDenied = createSession(false);
        WaterDecision decisionDenied = policy.evaluatePlacement(inside, fakePlayer, sessionDenied);
        assertEquals(WaterDecision.DENY, decisionDenied);

        // Permitido
        BattleSession sessionAllowed = createSession(true);
        WaterDecision decisionAllowed = policy.evaluatePlacement(inside, fakePlayer, sessionAllowed);
        assertEquals(WaterDecision.ALLOW, decisionAllowed);
    }

    @Test
    @DisplayName("Colocación fuera de la arena: permitida sin importar el estado de waterAllowed")
    void testPlacementOutsideArena() {
        Location outside = new Location(fakeWorld, 400.0, 65.0, 400.0);
        BattleSession session = createSession(false);

        WaterDecision decision = policy.evaluatePlacement(outside, fakePlayer, session);
        assertEquals(WaterDecision.ALLOW, decision, "Fuera de la arena BetterDragon jamás restringe agua");
    }

    @Test
    @DisplayName("Propagación y flujo: denegado hacia el interior de la arena si waterAllowed=false")
    void testFlowInsideArena() {
        Location from = new Location(fakeWorld, 0.0, 65.0, 0.0);
        Location toInside = new Location(fakeWorld, 1.0, 65.0, 0.0);
        Location toOutside = new Location(fakeWorld, 500.0, 65.0, 500.0);

        BattleSession sessionDenied = createSession(false);
        assertEquals(WaterDecision.DENY, policy.evaluateFlow(from, toInside, sessionDenied));
        assertEquals(WaterDecision.ALLOW, policy.evaluateFlow(from, toOutside, sessionDenied));

        BattleSession sessionAllowed = createSession(true);
        assertEquals(WaterDecision.ALLOW, policy.evaluateFlow(from, toInside, sessionAllowed));
    }

    @Test
    @DisplayName("Batalla terminada o abortada: permite agua en cualquier lugar")
    void testBattleTerminated() {
        Location inside = new Location(fakeWorld, 10.0, 65.0, 10.0);
        BattleSession session = createSession(false);

        // Al abortar
        session.abort("TEST");
        assertTrue(session.isTerminal());
        assertEquals(WaterDecision.ALLOW, policy.evaluatePlacement(inside, fakePlayer, session));
        assertEquals(WaterDecision.ALLOW, policy.evaluateFlow(inside, inside, session));

        // Sesión nula
        assertEquals(WaterDecision.ALLOW, policy.evaluatePlacement(inside, fakePlayer, null));
    }

    @Test
    @DisplayName("Sin participante activo fuera de la arena: no interfiere")
    void testNonParticipantOutside() {
        Location outside = new Location(fakeWorld, 300.0, 65.0, 300.0);
        BattleSession session = createSession(false);

        assertEquals(WaterDecision.ALLOW, policy.evaluatePlacement(outside, null, session));
    }
}
