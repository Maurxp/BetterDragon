package maurxp.betterdragon.combat;

import maurxp.betterdragon.battle.BattleSessionManager;
import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.projectiles.ProjectileSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class DragonCombatListenerTest {

    private DragonCombatListener listener;

    @BeforeEach
    void setUp() {
        BattleSessionManager sessionManager = new BattleSessionManager();
        Logger logger = Logger.getLogger("DragonCombatListenerTest");
        this.listener = new DragonCombatListener(sessionManager, logger);
    }

    @Test
    @DisplayName("1. resolveDragon resuelve directamente una entidad EnderDragon")
    void testResolveDirectDragon() {
        EnderDragon dragon = createFakeDragon(UUID.randomUUID());
        EnderDragon resolved = listener.resolveDragon(dragon);

        assertNotNull(resolved);
        assertEquals(dragon, resolved);
    }

    @Test
    @DisplayName("2. resolveDragon resuelve un ComplexEntityPart hacia su EnderDragon padre")
    void testResolveComplexEntityPartParent() {
        EnderDragon parentDragon = createFakeDragon(UUID.randomUUID());
        ComplexEntityPart complexPart = createFakeComplexPart(parentDragon);

        EnderDragon resolved = listener.resolveDragon(complexPart);
        assertNotNull(resolved);
        assertEquals(parentDragon, resolved);
    }

    @Test
    @DisplayName("3. resolveDragon retorna null para entidades que no son dragón ni parte de dragón")
    void testResolveNonDragonReturnsNull() {
        Entity nonDragon = createFakeEntity(UUID.randomUUID());
        assertNull(listener.resolveDragon(nonDragon));
        assertNull(listener.resolveDragon(null));
    }

    @Test
    @DisplayName("4. resolveDamagingPlayer resuelve un atacante directo de tipo Player")
    void testResolveDirectPlayer() {
        Player player = createFakePlayer(UUID.randomUUID(), "Alice");
        Player resolved = listener.resolveDamagingPlayer(player);

        assertNotNull(resolved);
        assertEquals(player, resolved);
    }

    @Test
    @DisplayName("5. resolveDamagingPlayer resuelve el tirador de un Projectile si es Player")
    void testResolveProjectilePlayerShooter() {
        Player shooter = createFakePlayer(UUID.randomUUID(), "RobinHood");
        Projectile arrow = createFakeProjectile(shooter);

        Player resolved = listener.resolveDamagingPlayer(arrow);
        assertNotNull(resolved);
        assertEquals(shooter, resolved);
    }

    @Test
    @DisplayName("6. resolveDamagingPlayer retorna null si el tirador no es Player")
    void testResolveProjectileNonPlayerShooterReturnsNull() {
        Entity skeletonShooter = createFakeEntity(UUID.randomUUID());
        Projectile arrow = createFakeProjectile((ProjectileSource) Proxy.newProxyInstance(
                ProjectileSource.class.getClassLoader(),
                new Class<?>[]{ProjectileSource.class},
                (proxy, method, args) -> null
        ));

        assertNull(listener.resolveDamagingPlayer(arrow));
    }

    @Test
    @DisplayName("7. resolveDamagingPlayer retorna null para atacantes que no son Player ni Projectile")
    void testResolveNonPlayerDamagerReturnsNull() {
        Entity zombie = createFakeEntity(UUID.randomUUID());
        assertNull(listener.resolveDamagingPlayer(zombie));
        assertNull(listener.resolveDamagingPlayer(null));
    }

    // --- Helpers de Mocks con Proxy ---

    private Entity createFakeEntity(UUID id) {
        return (Entity) Proxy.newProxyInstance(
                Entity.class.getClassLoader(),
                new Class<?>[]{Entity.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("getUniqueId")) return id;
                    if (name.equals("equals")) return proxy == args[0];
                    if (name.equals("hashCode")) return id.hashCode();
                    return null;
                }
        );
    }

    private EnderDragon createFakeDragon(UUID id) {
        return (EnderDragon) Proxy.newProxyInstance(
                EnderDragon.class.getClassLoader(),
                new Class<?>[]{EnderDragon.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("getUniqueId")) return id;
                    if (name.equals("equals")) return proxy == args[0];
                    if (name.equals("hashCode")) return id.hashCode();
                    return null;
                }
        );
    }

    private Player createFakePlayer(UUID id, String playerName) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("getUniqueId")) return id;
                    if (name.equals("getName")) return playerName;
                    if (name.equals("equals")) return proxy == args[0];
                    if (name.equals("hashCode")) return id.hashCode();
                    return null;
                }
        );
    }

    private Projectile createFakeProjectile(ProjectileSource shooter) {
        return (Projectile) Proxy.newProxyInstance(
                Projectile.class.getClassLoader(),
                new Class<?>[]{Projectile.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("getShooter")) return shooter;
                    if (name.equals("getUniqueId")) return UUID.randomUUID();
                    return null;
                }
        );
    }

    private ComplexEntityPart createFakeComplexPart(EnderDragon parent) {
        return (ComplexEntityPart) Proxy.newProxyInstance(
                ComplexEntityPart.class.getClassLoader(),
                new Class<?>[]{ComplexEntityPart.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("getParent")) return parent;
                    if (name.equals("getUniqueId")) return UUID.randomUUID();
                    return null;
                }
        );
    }
}
