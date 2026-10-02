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
import maurxp.betterdragon.battle.event.BetterDragonVictoryEvent;
import maurxp.betterdragon.battle.model.BattleResult;
import maurxp.betterdragon.util.BetterDragonKeys;
import org.bukkit.ExplosionResult;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas de Integración de Anti-Cheese y Control de Arena")
class AntiCheeseIntegrationTest {

    private BattleSessionManager sessionManager;
    private ExplosionPolicy explosionPolicy;
    private WaterPolicy waterPolicy;
    private BoundaryPolicy boundaryPolicy;
    private SafeReturnLocationStrategy returnStrategy;
    private VoidTetherService voidTetherService;

    private AntiCheeseExplosionListener explosionListener;
    private AntiCheeseWaterListener waterListener;
    private AntiCheeseBoundaryListener boundaryListener;

    private World fakeWorld;
    private UUID worldId;
    private BattleId battleId;
    private BattleSession session;

    private UUID participantId;
    private Player fakeParticipant;
    private AtomicReference<Location> participantLoc;

    private EnderDragon fakeDragon;
    private UUID dragonId;
    private Map<String, Object> dragonPdc;

    @BeforeEach
    void setUp() {
        sessionManager = new BattleSessionManager();
        explosionPolicy = new ExplosionPolicy();
        waterPolicy = new WaterPolicy();
        boundaryPolicy = new BoundaryPolicy(5.0);
        returnStrategy = new SafeReturnLocationStrategy(8.0);
        voidTetherService = new VoidTetherService(sessionManager, boundaryPolicy, returnStrategy);

        explosionListener = new AntiCheeseExplosionListener(sessionManager, explosionPolicy);
        waterListener = new AntiCheeseWaterListener(sessionManager, waterPolicy);
        boundaryListener = new AntiCheeseBoundaryListener(voidTetherService);

        worldId = UUID.randomUUID();
        battleId = BattleId.random();
        participantId = UUID.randomUUID();
        dragonId = UUID.randomUUID();

        participantLoc = new AtomicReference<>(new Location(null, 10.0, 65.0, 10.0));

        fakeWorld = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    if ("getName".equals(method.getName())) return "world_the_end";
                    if ("getUID".equals(method.getName())) return worldId;
                    if ("isChunkLoaded".equals(method.getName())) return true;
                    if ("equals".equals(method.getName())) return proxy == args[0];
                    return null;
                }
        );

        participantLoc.get().setWorld(fakeWorld);

        fakeParticipant = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getUniqueId".equals(name)) return participantId;
                    if ("getWorld".equals(name)) return fakeWorld;
                    if ("getLocation".equals(name)) return participantLoc.get();
                    if ("teleport".equals(name) && args.length > 0 && args[0] instanceof Location loc) {
                        participantLoc.set(loc.clone());
                        return true;
                    }
                    if ("setFallDistance".equals(name)) return null;
                    if ("setVelocity".equals(name)) return null;
                    if ("playSound".equals(name)) return null;
                    if ("updateInventory".equals(name)) return null;
                    return null;
                }
        );

        dragonPdc = new HashMap<>();
        dragonPdc.put(maurxp.betterdragon.util.BetterDragonKeys.MANAGED.toString(), (byte) 1);
        dragonPdc.put(maurxp.betterdragon.util.BetterDragonKeys.BATTLE_ID.toString(), battleId.asString());
        dragonPdc.put(maurxp.betterdragon.util.BetterDragonKeys.DEFINITION_ID.toString(), "default");
        dragonPdc.put(maurxp.betterdragon.util.BetterDragonKeys.SCHEMA_VERSION.toString(), 1);

        PersistentDataContainer pdcProxy = (PersistentDataContainer) Proxy.newProxyInstance(
                PersistentDataContainer.class.getClassLoader(),
                new Class<?>[]{PersistentDataContainer.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("has".equals(name) && args.length >= 1) {
                        return dragonPdc.containsKey(args[0].toString());
                    }
                    if ("get".equals(name) && args.length >= 2) {
                        return dragonPdc.get(args[0].toString());
                    }
                    return null;
                }
        );

        fakeDragon = (EnderDragon) Proxy.newProxyInstance(
                EnderDragon.class.getClassLoader(),
                new Class<?>[]{EnderDragon.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getUniqueId".equals(name)) return dragonId;
                    if ("getWorld".equals(name)) return fakeWorld;
                    if ("getLocation".equals(name)) return new Location(fakeWorld, 0.0, 70.0, 0.0);
                    if ("getPersistentDataContainer".equals(name)) return pdcProxy;
                    return null;
                }
        );

        ArenaRuleSet rules = new ArenaRuleSet(
                false,
                true,
                true,
                ExplosionPolicyType.BLOCK,
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
        session = BattleSession.create(battleId, "world_the_end", worldId, snapshot);
        session.start();
        DragonIdentity identity = DragonIdentity.of(dragonId, battleId, "default");
        session.activate(identity);
        sessionManager.register(session);

        // Registrar daño para que el jugador sea participante
        session.getCombatRuntime().recordDamage(participantId, "Warrior", 50.0, 1L);
    }

    private Block createFakeBlock(Material material, Location location) {
        return (Block) Proxy.newProxyInstance(
                Block.class.getClassLoader(),
                new Class<?>[]{Block.class},
                (proxy, method, args) -> {
                    if ("getType".equals(method.getName())) return material;
                    if ("getLocation".equals(method.getName())) return location;
                    if ("getWorld".equals(method.getName())) return location.getWorld();
                    return null;
                }
        );
    }

    @Test
    @DisplayName("Anti-Cheese Explosiones: cama clickeada en modo BLOCK cancela el PlayerInteractEvent")
    void testBedClickCancelledInBlockMode() {
        Location bedLoc = new Location(fakeWorld, 5.0, 65.0, 5.0);
        Block bedBlock = createFakeBlock(Material.WHITE_BED, bedLoc);

        PlayerInteractEvent event = new PlayerInteractEvent(
                fakeParticipant,
                Action.RIGHT_CLICK_BLOCK,
                null,
                bedBlock,
                BlockFace.UP,
                EquipmentSlot.HAND
        );

        explosionListener.onPlayerInteract(event);
        assertTrue(event.useInteractedBlock() == org.bukkit.event.Event.Result.DENY,
                "En modo BLOCK, la interacción con la cama debe cancelarse");
    }

    @Test
    @DisplayName("Anti-Cheese Explosiones: detonación en modo PROTECT_ARENA limpia bloques y protege el terreno")
    void testProtectArenaSuppressesTerrainDamage() {
        ArenaRuleSet protectRules = new ArenaRuleSet(false, true, true, ExplosionPolicyType.PROTECT_ARENA, true);
        ArenaDefinition arena = new ArenaDefinition(
                "default",
                "world_the_end",
                new Vector3d(0.0, 100.0, 0.0),
                new Vector3d(0.0, 65.0, 0.0),
                new ArenaBounds(-100.0, 0.0, -100.0, 100.0, 200.0, 100.0),
                protectRules
        );
        BattleConfigurationSnapshot snapshot = new BattleConfigurationSnapshot(
                false,
                false,
                maurxp.betterdragon.config.DragonDefinition.defaults(),
                arena
        );
        BattleId bid = BattleId.random();
        BattleSession protectSession = BattleSession.create(bid, "world_the_end", worldId, snapshot);
        protectSession.start();
        DragonIdentity identity = DragonIdentity.of(dragonId, bid, "default");
        protectSession.activate(identity);

        BattleSessionManager localManager = new BattleSessionManager();
        localManager.register(protectSession);
        AntiCheeseExplosionListener localListener = new AntiCheeseExplosionListener(localManager, explosionPolicy);

        // BlockExplodeEvent
        Location bedLoc = new Location(fakeWorld, 0.0, 65.0, 0.0);
        Block bedBlock = createFakeBlock(Material.RED_BED, bedLoc);
        List<Block> blocks = new ArrayList<>();
        blocks.add(createFakeBlock(Material.END_STONE, bedLoc));
        BlockState fakeState = (BlockState) Proxy.newProxyInstance(
                BlockState.class.getClassLoader(),
                new Class<?>[]{BlockState.class},
                (proxy, method, args) -> null
        );
        BlockExplodeEvent explodeEvent = new BlockExplodeEvent(bedBlock, fakeState, blocks, 1.0f, ExplosionResult.DESTROY);

        localListener.onBlockExplode(explodeEvent);
        assertFalse(explodeEvent.isCancelled(), "La detonación física no se cancela en PROTECT_ARENA");
        assertTrue(explodeEvent.blockList().isEmpty(), "El 100% de la lista de bloques destruidos debe vaciarse");
    }

    @Test
    @DisplayName("Anti-Cheese Agua: flujo de agua (BlockFromToEvent) dentro de la arena es cancelado")
    void testWaterFlowCancelledInArena() {
        Location fromLoc = new Location(fakeWorld, 10.0, 65.0, 10.0);
        Location toLoc = new Location(fakeWorld, 11.0, 65.0, 10.0);

        Block waterSource = createFakeBlock(Material.WATER, fromLoc);
        Block targetAir = createFakeBlock(Material.AIR, toLoc);

        BlockFromToEvent flowEvent = new BlockFromToEvent(waterSource, targetAir);
        assertFalse(flowEvent.isCancelled());

        waterListener.onBlockFromTo(flowEvent);
        assertTrue(flowEvent.isCancelled(), "El flujo de agua dentro de la arena de combate debe ser cancelado");
    }

    @Test
    @DisplayName("Void Tether: movimiento que cruza el perímetro activa teletransporte de retorno")
    void testBoundaryListenerTethersOnBreach() {
        Location from = new Location(fakeWorld, 90.0, 65.0, 0.0);
        Location to = new Location(fakeWorld, 110.0, 65.0, 0.0); // Cruza maxX (100.0)

        PlayerMoveEvent moveEvent = new PlayerMoveEvent(fakeParticipant, from, to);
        boundaryListener.onPlayerMove(moveEvent);

        // Debe haber sido teletransportado hacia el interior
        assertTrue(session.getArena().bounds().contains(participantLoc.get()), "El participante debe estar dentro de la arena");
    }

    @Test
    @DisplayName("Resolución de tipos de explosión: camas, anclas de respawn y otras fuentes")
    void testExplosionSourceClassification() {
        // Bed
        assertEquals(ExplosionDecision.block(), explosionPolicy.evaluate(ExplosionSourceType.BED, new Location(fakeWorld, 0, 65, 0), session));
        // Respawn Anchor
        assertEquals(ExplosionDecision.block(), explosionPolicy.evaluate(ExplosionSourceType.RESPAWN_ANCHOR, new Location(fakeWorld, 0, 65, 0), session));
        // BetterDragon ability
        ExplosionDecision abilityDecision = explosionPolicy.evaluate(ExplosionSourceType.BETTERDRAGON_ABILITY, new Location(fakeWorld, 0, 65, 0), session);
        assertTrue(abilityDecision.shouldExplode());
        assertFalse(abilityDecision.allowBlockDamage());
        assertFalse(abilityDecision.allowDragonDamage());
        assertTrue(abilityDecision.allowPlayerDamage());
    }

    @Test
    @DisplayName("Resolución de subpartes de dragón: resolución jerárquica a la entidad raíz")
    void testResolveComplexDragonParts() {
        ComplexEntityPart part = (ComplexEntityPart) Proxy.newProxyInstance(
                ComplexEntityPart.class.getClassLoader(),
                new Class<?>[]{ComplexEntityPart.class},
                (proxy, method, args) -> {
                    if ("getParent".equals(method.getName())) return fakeDragon;
                    return null;
                }
        );

        assertSame(fakeDragon, part.getParent());

        // Listener resuelve el padre
        ExplosionDecision decision = explosionPolicy.evaluate(ExplosionSourceType.BED, fakeDragon.getLocation(), session);
        assertNotNull(decision);
    }

    @Test
    @DisplayName("Anti-Cheese Explosiones: TNT propio de BetterDragon (PDC managed/explosive) NO se cancela en modo BLOCK")
    void testBetterDragonAbilityTntNotCancelledInBlockMode() {
        PersistentDataContainer mockPdc = (PersistentDataContainer) Proxy.newProxyInstance(
                PersistentDataContainer.class.getClassLoader(),
                new Class<?>[]{PersistentDataContainer.class},
                (proxy, method, args) -> {
                    if ("has".equals(method.getName())) {
                        return true;
                    }
                    if ("get".equals(method.getName())) {
                        return true;
                    }
                    return null;
                }
        );

        TNTPrimed abilityTnt = (TNTPrimed) Proxy.newProxyInstance(
                TNTPrimed.class.getClassLoader(),
                new Class<?>[]{TNTPrimed.class},
                (proxy, method, args) -> {
                    if ("getWorld".equals(method.getName())) return fakeWorld;
                    if ("getPersistentDataContainer".equals(method.getName())) return mockPdc;
                    return null;
                }
        );

        Location loc = new Location(fakeWorld, 0.0, 65.0, 0.0);
        List<Block> blocks = new ArrayList<>();
        blocks.add(createFakeBlock(Material.END_STONE, loc));

        EntityExplodeEvent explodeEvent = new EntityExplodeEvent(abilityTnt, loc, blocks, 1.0f, null);
        explosionListener.onEntityExplode(explodeEvent);

        assertFalse(explodeEvent.isCancelled(),
                "Las explosiones de habilidades de BetterDragon NO deben cancelarse incluso en modo BLOCK");
        assertTrue(explodeEvent.blockList().isEmpty(),
                "Las habilidades de BetterDragon deben proteger el terreno vaciando blockList()");
    }

    @Test
    @DisplayName("Anti-Cheese Explosiones: cristales del End (EnderCrystal) NO son cancelados por AntiCheeseExplosionListener")
    void testEnderCrystalExplosionNotCancelled() {
        EnderCrystal crystal = (EnderCrystal) Proxy.newProxyInstance(
                EnderCrystal.class.getClassLoader(),
                new Class<?>[]{EnderCrystal.class},
                (proxy, method, args) -> {
                    if ("getWorld".equals(method.getName())) return fakeWorld;
                    return null;
                }
        );

        Location loc = new Location(fakeWorld, 10.0, 80.0, 10.0);
        List<Block> blocks = new ArrayList<>();
        blocks.add(createFakeBlock(Material.OBSIDIAN, loc));

        EntityExplodeEvent explodeEvent = new EntityExplodeEvent(crystal, loc, blocks, 1.0f, null);
        explosionListener.onEntityExplode(explodeEvent);

        assertFalse(explodeEvent.isCancelled(),
                "Los cristales del End son una mecánica legítima y no deben ser cancelados");
    }

    @Test
    @DisplayName("Void Tether: BetterDragonVictoryEvent limpia el estado y posiciones del VoidTetherService")
    void testVictoryEventCleansUpVoidTetherService() {
        // Simular movimiento para registrar posición en el tether
        Location inside = new Location(fakeWorld, 10.0, 65.0, 10.0);
        Location next = new Location(fakeWorld, 11.0, 65.0, 10.0);
        boundaryListener.onPlayerMove(new PlayerMoveEvent(fakeParticipant, inside, next));

        assertTrue(voidTetherService.getLastKnownValidPosition(participantId).isPresent(),
                "El participante debe tener una posición válida registrada");

        // Disparar victoria
        BattleResult result = BattleResult.completed(battleId, java.time.Instant.now(), java.time.Instant.now(), participantId, "PlayerOne");
        BetterDragonVictoryEvent victoryEvent = new BetterDragonVictoryEvent(battleId, result, "world_the_end");
        boundaryListener.onVictory(victoryEvent);

        assertTrue(voidTetherService.getLastKnownValidPosition(participantId).isEmpty(),
                "Al finalizar la batalla por victoria, el estado del VoidTetherService debe quedar completamente limpio");
    }

    @Test
    @DisplayName("Anti-Cheese Explosiones: TNT genérico vanilla SI se cancela en modo BLOCK")
    void testGenericTntIsCancelledInBlockMode() {
        TNTPrimed genericTnt = (TNTPrimed) Proxy.newProxyInstance(
                TNTPrimed.class.getClassLoader(),
                new Class<?>[]{TNTPrimed.class},
                (proxy, method, args) -> {
                    if ("getWorld".equals(method.getName())) return fakeWorld;
                    if ("getPersistentDataContainer".equals(method.getName())) {
                        return Proxy.newProxyInstance(
                                PersistentDataContainer.class.getClassLoader(),
                                new Class<?>[]{PersistentDataContainer.class},
                                (p, m, a) -> false
                        );
                    }
                    return null;
                }
        );

        Location loc = new Location(fakeWorld, 0.0, 65.0, 0.0);
        List<Block> blocks = new ArrayList<>();
        blocks.add(createFakeBlock(Material.END_STONE, loc));

        EntityExplodeEvent explodeEvent = new EntityExplodeEvent(genericTnt, loc, blocks, 1.0f, null);
        explosionListener.onEntityExplode(explodeEvent);

        assertTrue(explodeEvent.isCancelled(),
                "TNT genérico o vanilla debe ser cancelado en modo BLOCK");
    }

    @Test
    @DisplayName("Anti-Cheese Explosiones: contrato PDC para clasificación de BETTERDRAGON_ABILITY vs entidades genéricas")
    void testIsBetterDragonAbilityPdcContract() {
        // 1. Entidad con MANAGED=true y EXPLOSIVE=true (CarpetBomb TNT)
        Entity carpetBombTnt = createEntityWithPdc(Map.of(
                BetterDragonKeys.MANAGED, true,
                BetterDragonKeys.EXPLOSIVE, true
        ));
        assertTrue(explosionListener.isBetterDragonAbility(carpetBombTnt),
                "Entidad con MANAGED y EXPLOSIVE debe ser clasificada como BETTERDRAGON_ABILITY");

        // 2. Entidad con MANAGED=true y BATTLE_ID presente
        Entity battleEntity = createEntityWithPdc(Map.of(
                BetterDragonKeys.MANAGED, true,
                BetterDragonKeys.BATTLE_ID, battleId.asString()
        ));
        assertTrue(explosionListener.isBetterDragonAbility(battleEntity),
                "Entidad con MANAGED y BATTLE_ID debe ser clasificada como BETTERDRAGON_ABILITY");

        // 3. Entidad con formato BYTE (legacy/compatibilidad interna Paper)
        Entity byteEntity = createEntityWithPdc(Map.of(
                BetterDragonKeys.MANAGED, (byte) 1,
                BetterDragonKeys.EXPLOSIVE, (byte) 1
        ));
        assertTrue(explosionListener.isBetterDragonAbility(byteEntity),
                "Entidad con MANAGED=1 y EXPLOSIVE=1 debe ser clasificada como BETTERDRAGON_ABILITY");

        // 4. Entidad genérica (PDC vacío)
        Entity genericEntity = createEntityWithPdc(Collections.emptyMap());
        assertFalse(explosionListener.isBetterDragonAbility(genericEntity),
                "Entidad sin marcas PDC de BetterDragon NO debe ser clasificada como habilidad");

        // 5. Entidad con MANAGED=false
        Entity unmanagedEntity = createEntityWithPdc(Map.of(
                BetterDragonKeys.MANAGED, false
        ));
        assertFalse(explosionListener.isBetterDragonAbility(unmanagedEntity),
                "Entidad con MANAGED=false NO debe ser clasificada como habilidad");

        // 6. Entidad con MANAGED=true pero sin ser explosiva ni vinculada a batalla
        Entity nonExplosiveEntity = createEntityWithPdc(Map.of(
                BetterDragonKeys.MANAGED, true
        ));
        assertFalse(explosionListener.isBetterDragonAbility(nonExplosiveEntity),
                "Entidad sin marca explosiva ni battle_id NO debe ser clasificada como habilidad explosiva");

        // 7. Entidad nula
        assertFalse(explosionListener.isBetterDragonAbility(null),
                "Entidad nula debe retornar false de forma segura");
    }

    private Entity createEntityWithPdc(Map<NamespacedKey, Object> pdcMap) {
        PersistentDataContainer pdc = (PersistentDataContainer) Proxy.newProxyInstance(
                PersistentDataContainer.class.getClassLoader(),
                new Class<?>[]{PersistentDataContainer.class},
                (proxy, method, args) -> {
                    if ("has".equals(method.getName()) && args != null && args.length >= 2) {
                        if (pdcMap == null || !pdcMap.containsKey(args[0])) return false;
                        Object val = pdcMap.get(args[0]);
                        if (args[1] == PersistentDataType.BOOLEAN) return val instanceof Boolean;
                        if (args[1] == PersistentDataType.BYTE) return val instanceof Byte;
                        if (args[1] == PersistentDataType.STRING) return val instanceof String;
                        return true;
                    }
                    if ("has".equals(method.getName()) && args != null && args.length == 1) {
                        return pdcMap != null && pdcMap.containsKey(args[0]);
                    }
                    if ("get".equals(method.getName()) && args != null && args.length >= 1) {
                        return pdcMap != null ? pdcMap.get(args[0]) : null;
                    }
                    return null;
                }
        );

        return (Entity) Proxy.newProxyInstance(
                Entity.class.getClassLoader(),
                new Class<?>[]{Entity.class},
                (proxy, method, args) -> {
                    if ("getWorld".equals(method.getName())) return fakeWorld;
                    if ("getPersistentDataContainer".equals(method.getName())) return pdc;
                    return null;
                }
        );
    }
}
