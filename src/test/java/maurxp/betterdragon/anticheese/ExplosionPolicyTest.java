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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias de ExplosionPolicy")
class ExplosionPolicyTest {

    private ExplosionPolicy policy;
    private World fakeWorld;
    private UUID worldId;
    private BattleId battleId;

    @BeforeEach
    void setUp() {
        policy = new ExplosionPolicy();
        worldId = UUID.randomUUID();
        battleId = BattleId.random();
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
    }

    private BattleSession createSession(ExplosionPolicyType policyType) {
        ArenaRuleSet rules = new ArenaRuleSet(
                false,
                true,
                true,
                policyType,
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
    @DisplayName("Sin batalla activa: retorna ALLOW sin alterar mecánicas vanilla")
    void testNoBattleActive() {
        Location loc = new Location(fakeWorld, 10.0, 70.0, 10.0);

        // Sesión nula
        ExplosionDecision decisionNull = policy.evaluate(ExplosionSourceType.BED, loc, null);
        assertTrue(decisionNull.shouldExplode());
        assertTrue(decisionNull.allowBlockDamage());
        assertTrue(decisionNull.allowPlayerDamage());
        assertTrue(decisionNull.allowDragonDamage());

        // Sesión creada pero no activa (IDLE)
        BattleSession idleSession = BattleSession.create(battleId, "world_the_end", worldId);
        ExplosionDecision decisionIdle = policy.evaluate(ExplosionSourceType.BED, loc, idleSession);
        assertTrue(decisionIdle.shouldExplode());
        assertTrue(decisionIdle.allowBlockDamage());
    }

    @Test
    @DisplayName("Fuera de la arena: retorna ALLOW sin intervenir en otras zonas del mundo")
    void testOutsideArena() {
        BattleSession session = createSession(ExplosionPolicyType.PROTECT_ARENA);
        Location outside = new Location(fakeWorld, 500.0, 70.0, 500.0);

        ExplosionDecision decision = policy.evaluate(ExplosionSourceType.BED, outside, session);
        assertTrue(decision.shouldExplode());
        assertTrue(decision.allowBlockDamage());
        assertTrue(decision.allowPlayerDamage());
        assertTrue(decision.allowDragonDamage());
    }

    @Test
    @DisplayName("Modo ALLOW: permite explosiones, daño a bloques, a jugadores y al dragón")
    void testAllowMode() {
        BattleSession session = createSession(ExplosionPolicyType.ALLOW);
        Location inside = new Location(fakeWorld, 0.0, 70.0, 0.0);

        ExplosionDecision decision = policy.evaluate(ExplosionSourceType.BED, inside, session);
        assertTrue(decision.shouldExplode(), "shouldExplode debe ser true");
        assertTrue(decision.allowBlockDamage(), "allowBlockDamage debe ser true");
        assertTrue(decision.allowPlayerDamage(), "allowPlayerDamage debe ser true");
        assertTrue(decision.allowDragonDamage(), "allowDragonDamage debe ser true");
    }

    @Test
    @DisplayName("Modo BLOCK: neutraliza y cancela proactivamente la detonación")
    void testBlockMode() {
        BattleSession session = createSession(ExplosionPolicyType.BLOCK);
        Location inside = new Location(fakeWorld, 0.0, 70.0, 0.0);

        ExplosionDecision decisionBed = policy.evaluate(ExplosionSourceType.BED, inside, session);
        assertFalse(decisionBed.shouldExplode(), "shouldExplode debe ser false");
        assertFalse(decisionBed.allowBlockDamage(), "allowBlockDamage debe ser false");
        assertFalse(decisionBed.allowPlayerDamage(), "allowPlayerDamage debe ser false");
        assertFalse(decisionBed.allowDragonDamage(), "allowDragonDamage debe ser false");

        ExplosionDecision decisionAnchor = policy.evaluate(ExplosionSourceType.RESPAWN_ANCHOR, inside, session);
        assertFalse(decisionAnchor.shouldExplode(), "shouldExplode debe ser false para ancla de respawn");
    }

    @Test
    @DisplayName("Modo PROTECT_ARENA: permite detonar y dañar jugadores pero suprime rotura de bloques y daño al dragón")
    void testProtectArenaMode() {
        BattleSession session = createSession(ExplosionPolicyType.PROTECT_ARENA);
        Location inside = new Location(fakeWorld, 0.0, 70.0, 0.0);

        ExplosionDecision decision = policy.evaluate(ExplosionSourceType.BED, inside, session);
        assertTrue(decision.shouldExplode(), "shouldExplode debe ser true");
        assertFalse(decision.allowBlockDamage(), "allowBlockDamage debe ser false");
        assertTrue(decision.allowPlayerDamage(), "allowPlayerDamage debe ser true");
        assertFalse(decision.allowDragonDamage(), "allowDragonDamage debe ser false");
    }

    @Test
    @DisplayName("Habilidad de BetterDragon: siempre protege terreno y dragón pero daña jugadores")
    void testBetterDragonAbilityExplosion() {
        BattleSession session = createSession(ExplosionPolicyType.ALLOW);
        Location inside = new Location(fakeWorld, 0.0, 70.0, 0.0);

        ExplosionDecision decision = policy.evaluate(ExplosionSourceType.BETTERDRAGON_ABILITY, inside, session);
        assertTrue(decision.shouldExplode());
        assertFalse(decision.allowBlockDamage());
        assertTrue(decision.allowPlayerDamage());
        assertFalse(decision.allowDragonDamage());
    }
}
