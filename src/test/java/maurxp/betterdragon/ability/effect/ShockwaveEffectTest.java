package maurxp.betterdragon.ability.effect;

import maurxp.betterdragon.ability.AbilityDefinition;
import maurxp.betterdragon.ability.AbilityEffectType;
import maurxp.betterdragon.ability.AbilityExecutionContext;
import maurxp.betterdragon.ability.AbilityTrigger;
import maurxp.betterdragon.ability.EffectOriginType;
import maurxp.betterdragon.ability.TargetSelectorType;
import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.phase.PhaseDefinition;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias para {@link ShockwaveEffect}.
 *
 * @author maurxp
 */
class ShockwaveEffectTest {

    private BattleId battleId;
    private World fakeWorld;
    private EnderDragon fakeDragon;
    private Location origin;
    private PhaseDefinition phase;

    @BeforeEach
    void setUp() {
        battleId = BattleId.random();

        fakeWorld = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("getName")) return "world_the_end";
                    if (name.equals("spawnParticle")) return null;
                    if (name.equals("playSound")) return null;
                    if (name.equals("equals")) return proxy == args[0];
                    return null;
                }
        );

        origin = new Location(fakeWorld, 0.0, 65.0, 0.0);
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

    private Player createFakePlayer(Location loc, AtomicReference<Vector> receivedVelocity, AtomicBoolean receivedDamage) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("getLocation")) return loc;
                    if (name.equals("isOnline")) return true;
                    if (name.equals("isDead")) return false;
                    if (name.equals("setVelocity")) {
                        receivedVelocity.set((Vector) args[0]);
                        return null;
                    }
                    if (name.equals("damage")) {
                        receivedDamage.set(true);
                        return null;
                    }
                    if (name.equals("equals")) return proxy == args[0];
                    return null;
                }
        );
    }

    @Test
    @DisplayName("Jugador dentro del radio recibe impulso vectorial y daño; jugador fuera del radio es ignorado")
    void testShockwaveRadialApplication() {
        AtomicReference<Vector> p1Velocity = new AtomicReference<>();
        AtomicBoolean p1Damaged = new AtomicBoolean(false);
        Player nearbyPlayer = createFakePlayer(new Location(fakeWorld, 5.0, 65.0, 0.0), p1Velocity, p1Damaged); // 5m dist

        AtomicReference<Vector> p2Velocity = new AtomicReference<>();
        AtomicBoolean p2Damaged = new AtomicBoolean(false);
        Player farawayPlayer = createFakePlayer(new Location(fakeWorld, 50.0, 65.0, 0.0), p2Velocity, p2Damaged); // 50m dist

        AbilityDefinition ability = new AbilityDefinition(
                "perch_shockwave",
                AbilityTrigger.ON_FLIGHT_PHASE,
                100L,
                TargetSelectorType.ALL_IN_ARENA,
                EffectOriginType.PODIUM_CENTER,
                AbilityEffectType.SHOCKWAVE,
                Map.of("radius", 15.0, "damage", 10.0, "knockback", 1.5, "vertical_knockback", 0.8),
                null
        );

        AbilityExecutionContext context = new AbilityExecutionContext(
                battleId,
                "world_the_end",
                fakeDragon,
                AbilityTrigger.ON_FLIGHT_PHASE,
                Optional.empty(),
                List.of(nearbyPlayer, farawayPlayer),
                origin,
                100L,
                ability,
                phase
        );

        ShockwaveEffect effect = new ShockwaveEffect();
        effect.execute(context);

        // Nearby player impacted
        assertNotNull(p1Velocity.get(), "Jugador en radio debe recibir vector de velocidad");
        assertTrue(p1Velocity.get().getY() > 0.0, "Vector de velocidad debe tener impulso vertical");
        assertTrue(p1Damaged.get(), "Jugador en radio debe recibir daño");

        // Faraway player spared
        assertNull(p2Velocity.get(), "Jugador fuera de radio no debe ser afectado");
        assertFalse(p2Damaged.get(), "Jugador fuera de radio no debe recibir daño");
    }

    @Test
    @DisplayName("Contexto nulo lanza NullPointerException")
    void testNullContextThrows() {
        ShockwaveEffect effect = new ShockwaveEffect();
        assertThrows(NullPointerException.class, () -> effect.execute(null));
    }
}
