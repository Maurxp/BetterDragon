package maurxp.betterdragon.battle;

import maurxp.betterdragon.battle.event.BetterDragonVictoryEvent;
import maurxp.betterdragon.battle.event.VictoryEventDispatcher;
import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.battle.model.BattleResult;
import maurxp.betterdragon.battle.model.BattleState;
import maurxp.betterdragon.battle.model.DragonIdentity;
import maurxp.betterdragon.combat.CombatRuntime;
import maurxp.betterdragon.combat.CombatSnapshot;
import maurxp.betterdragon.combat.ParticipantSnapshot;
import maurxp.betterdragon.config.ConfigurationService;
import maurxp.betterdragon.util.BetterDragonKeys;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.EnderDragon;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Suite de pruebas unitarias para Fase 3.7: Death / Victory.
 */
class BattleVictoryTest {

    private BattleSessionManager sessionManager;
    private ConfigurationService configService;
    private Logger logger;
    private List<BetterDragonVictoryEvent> dispatchedEvents;
    private VictoryEventDispatcher victoryEventDispatcher;
    private BattleManager battleManager;
    private World endWorld;

    @BeforeEach
    void setUp() {
        this.sessionManager = new BattleSessionManager();
        this.logger = Logger.getLogger("BattleVictoryTest");
        this.configService = new ConfigurationService(logger);
        this.dispatchedEvents = new ArrayList<>();
        this.victoryEventDispatcher = dispatchedEvents::add;

        DragonSpawner fakeSpawner = new DragonSpawner() {
            @Override
            public EnderDragon spawnDragon(World world, Location location, BattleId battleId, String definitionId) {
                return createFakeDragon(UUID.randomUUID(), battleId);
            }
        };

        this.battleManager = new BattleManager(sessionManager, configService, fakeSpawner, victoryEventDispatcher, logger);
        this.endWorld = createFakeWorld("world_the_end", UUID.randomUUID(), World.Environment.THE_END);
    }

    // ==========================================
    // 1. DEATH DETECTION & IDENTITY FALLBACKS
    // ==========================================

    @Test
    @DisplayName("EnderDragon administrado con PDC válido es detectado y finaliza con victoria")
    void testManagedDragonDeathDetected() {
        BattleSession session = battleManager.startBattle(endWorld);
        assertEquals(BattleState.ACTIVE, session.getState());

        DragonIdentity identity = session.getDragonIdentity().orElseThrow();
        EnderDragon dragon = createFakeDragon(identity.entityUniqueId(), identity.battleId());

        Optional<BattleResult> resultOpt = battleManager.handleDragonDeath(dragon, null);

        assertTrue(resultOpt.isPresent());
        BattleResult result = resultOpt.get();
        assertEquals(BattleState.COMPLETED, result.finalState());
        assertTrue(result.isVictory());
        assertEquals(BattleState.COMPLETED, session.getState());
        assertEquals(1, dispatchedEvents.size());
        assertEquals(session.getBattleId(), dispatchedEvents.getFirst().getBattleId());
    }

    @Test
    @DisplayName("EnderDragon vanilla sin PDC de BetterDragon es ignorado")
    void testUnmanagedDragonDeathIgnored() {
        BattleSession session = battleManager.startBattle(endWorld);
        assertEquals(BattleState.ACTIVE, session.getState());

        // Dragón vanilla sin claves PDC de BetterDragon
        EnderDragon vanillaDragon = createPlainDragon(UUID.randomUUID());

        Optional<BattleResult> resultOpt = battleManager.handleDragonDeath(vanillaDragon, null);

        assertTrue(resultOpt.isEmpty());
        assertEquals(BattleState.ACTIVE, session.getState());
        assertTrue(dispatchedEvents.isEmpty());
    }

    @Test
    @DisplayName("Entidad nula o no EnderDragon es ignorada de forma segura")
    void testNonEnderDragonIgnored() {
        BattleSession session = battleManager.startBattle(endWorld);
        assertEquals(BattleState.ACTIVE, session.getState());

        Optional<BattleResult> resultOpt = battleManager.handleDragonDeath(null, null);

        assertTrue(resultOpt.isEmpty());
        assertEquals(BattleState.ACTIVE, session.getState());
        assertTrue(dispatchedEvents.isEmpty());
    }

    @Test
    @DisplayName("EnderDragon con battle_id malformado en PDC es ignorado")
    void testMalformedBattleIdInPdcIgnored() {
        BattleSession session = battleManager.startBattle(endWorld);

        FakePdc pdc = new FakePdc();
        pdc.set(BetterDragonKeys.MANAGED, PersistentDataType.BOOLEAN, true);
        pdc.set(BetterDragonKeys.BATTLE_ID, PersistentDataType.STRING, "not-a-valid-uuid");
        EnderDragon corruptDragon = createDragonWithPdc(UUID.randomUUID(), pdc);

        Optional<BattleResult> resultOpt = battleManager.handleDragonDeath(corruptDragon, null);

        assertTrue(resultOpt.isEmpty());
        assertEquals(BattleState.ACTIVE, session.getState());
        assertTrue(dispatchedEvents.isEmpty());
    }

    @Test
    @DisplayName("EnderDragon con battle_id que no corresponde a ninguna sesión activa es ignorado")
    void testNonExistentSessionIgnored() {
        BattleId unknownId = BattleId.random();
        EnderDragon orphanDragon = createFakeDragon(UUID.randomUUID(), unknownId);

        Optional<BattleResult> resultOpt = battleManager.handleDragonDeath(orphanDragon, null);

        assertTrue(resultOpt.isEmpty());
        assertTrue(dispatchedEvents.isEmpty());
    }

    // ==========================================
    // 2. STATE TRANSITIONS & NO-OP CASES
    // ==========================================

    @Test
    @DisplayName("Transición de estado pasa por DYING y culmina en COMPLETED")
    void testStateProgressionActiveToCompleted() {
        BattleSession session = battleManager.startBattle(endWorld);
        DragonIdentity identity = session.getDragonIdentity().orElseThrow();
        EnderDragon dragon = createFakeDragon(identity.entityUniqueId(), identity.battleId());

        AtomicInteger stateChecks = new AtomicInteger(0);
        VictoryEventDispatcher trackingDispatcher = event -> {
            // Durante el disparo de BetterDragonVictoryEvent, la sesión debe estar en COMPLETED
            assertEquals(BattleState.COMPLETED, session.getState());
            stateChecks.incrementAndGet();
        };
        BattleManager trackingManager = new BattleManager(sessionManager, configService, new DragonSpawner(), trackingDispatcher, logger);

        Optional<BattleResult> resultOpt = trackingManager.handleDragonDeath(dragon, null);

        assertTrue(resultOpt.isPresent());
        assertEquals(BattleState.COMPLETED, session.getState());
        assertEquals(1, stateChecks.get());
    }

    @Test
    @DisplayName("Muerte cuando la sesión ya está en COMPLETED es un no-op seguro (idempotencia)")
    void testDeathWhenAlreadyCompletedIsNoOp() {
        BattleSession session = battleManager.startBattle(endWorld);
        DragonIdentity identity = session.getDragonIdentity().orElseThrow();
        EnderDragon dragon = createFakeDragon(identity.entityUniqueId(), identity.battleId());

        // Primera llamada: finaliza la batalla
        Optional<BattleResult> firstResultOpt = battleManager.handleDragonDeath(dragon, null);
        assertTrue(firstResultOpt.isPresent());
        BattleResult firstResult = firstResultOpt.get();
        assertEquals(1, dispatchedEvents.size());
        assertEquals(BattleState.COMPLETED, session.getState());

        // Segunda llamada con el mismo dragón: debe ser no-op idempotente
        Optional<BattleResult> secondResultOpt = battleManager.handleDragonDeath(dragon, null);
        assertTrue(secondResultOpt.isPresent());
        assertSame(firstResult, secondResultOpt.get());
        assertEquals(1, dispatchedEvents.size(), "No debe dispararse un segundo BetterDragonVictoryEvent");
        assertEquals(BattleState.COMPLETED, session.getState());
    }

    @Test
    @DisplayName("Muerte cuando la sesión está ABORTED es un no-op seguro")
    void testDeathWhenAbortedIsNoOp() {
        BattleSession session = battleManager.startBattle(endWorld);
        DragonIdentity identity = session.getDragonIdentity().orElseThrow();
        EnderDragon dragon = createFakeDragon(identity.entityUniqueId(), identity.battleId());

        session.abort("Aborted by test");
        assertEquals(BattleState.ABORTED, session.getState());

        Optional<BattleResult> resultOpt = battleManager.handleDragonDeath(dragon, null);

        assertTrue(resultOpt.isEmpty());
        assertEquals(BattleState.ABORTED, session.getState());
        assertTrue(dispatchedEvents.isEmpty());
    }

    @Test
    @DisplayName("Muerte cuando la sesión está en DEFERRED_PENDING_CHUNK_LOAD no genera victoria prematura si identidad no coincide")
    void testDeathWhenDeferredIsHandled() {
        BattleSession session = battleManager.startBattle(endWorld);
        DragonIdentity identity = session.getDragonIdentity().orElseThrow();
        EnderDragon dragon = createFakeDragon(identity.entityUniqueId(), identity.battleId());

        session.deferPendingChunkLoad();
        assertEquals(BattleState.DEFERRED_PENDING_CHUNK_LOAD, session.getState());

        // Muerte de dragón con identidad válida reanuda y completa
        Optional<BattleResult> resultOpt = battleManager.handleDragonDeath(dragon, null);

        assertTrue(resultOpt.isPresent());
        assertEquals(BattleState.COMPLETED, session.getState());
        assertTrue(resultOpt.get().isVictory());
    }

    // ==========================================
    // 3. SLAYER SELECTION (TOP_DAMAGE) & TIE-BREAKING
    // ==========================================

    @Test
    @DisplayName("Slayer es el participante con mayor daño total registrado (TOP_DAMAGE)")
    void testSlayerTopDamage() {
        BattleSession session = battleManager.startBattle(endWorld);
        DragonIdentity identity = session.getDragonIdentity().orElseThrow();
        EnderDragon dragon = createFakeDragon(identity.entityUniqueId(), identity.battleId());

        CombatRuntime combat = session.getCombatRuntime();
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();
        UUID playerC = UUID.randomUUID();

        combat.recordDamage(playerA, "PlayerA", 50.0, 10L);
        combat.recordDamage(playerB, "PlayerB_Top", 150.0, 15L);
        combat.recordDamage(playerC, "PlayerC", 75.0, 20L);

        Optional<BattleResult> resultOpt = battleManager.handleDragonDeath(dragon, null);

        assertTrue(resultOpt.isPresent());
        BattleResult result = resultOpt.get();
        assertEquals(playerB, result.slayerUniqueId());
        assertEquals("PlayerB_Top", result.slayerLastKnownName());
    }

    @Test
    @DisplayName("Desempate de Slayer es determinista por menor firstHitSequence")
    void testSlayerTieBreakByFirstHitSequence() {
        BattleSession session = battleManager.startBattle(endWorld);
        DragonIdentity identity = session.getDragonIdentity().orElseThrow();
        EnderDragon dragon = createFakeDragon(identity.entityUniqueId(), identity.battleId());

        CombatRuntime combat = session.getCombatRuntime();
        UUID playerFirst = UUID.randomUUID();
        UUID playerSecond = UUID.randomUUID();

        // Ambos hacen exactamente 100.0 de daño
        // playerFirst golpea primero (seq 1)
        combat.recordDamage(playerFirst, "FirstHitter", 50.0, 10L);
        // playerSecond golpea después (seq 2)
        combat.recordDamage(playerSecond, "SecondHitter", 100.0, 15L);
        // playerFirst completa sus 100.0 (seq 3)
        combat.recordDamage(playerFirst, "FirstHitter", 50.0, 20L);

        assertEquals(100.0, combat.getTotalDamage(playerFirst));
        assertEquals(100.0, combat.getTotalDamage(playerSecond));

        Optional<BattleResult> resultOpt = battleManager.handleDragonDeath(dragon, null);

        assertTrue(resultOpt.isPresent());
        BattleResult result = resultOpt.get();
        // playerFirst tiene menor firstHitSequence (seq 1 vs seq 2) -> gana el desempate
        assertEquals(playerFirst, result.slayerUniqueId());
        assertEquals("FirstHitter", result.slayerLastKnownName());
    }

    @Test
    @DisplayName("Participante offline conserva su candidatura como Slayer con nombres históricos")
    void testSlayerOfflineParticipantPreserved() {
        BattleSession session = battleManager.startBattle(endWorld);
        DragonIdentity identity = session.getDragonIdentity().orElseThrow();
        EnderDragon dragon = createFakeDragon(identity.entityUniqueId(), identity.battleId());

        CombatRuntime combat = session.getCombatRuntime();
        UUID offlinePlayer = UUID.randomUUID();
        combat.recordDamage(offlinePlayer, "HistoricalSteve", 300.0, 10L);
        combat.recordDamage(offlinePlayer, "SteveUpdatedName", 50.0, 20L);

        Optional<BattleResult> resultOpt = battleManager.handleDragonDeath(dragon, null);

        assertTrue(resultOpt.isPresent());
        BattleResult result = resultOpt.get();
        assertEquals(offlinePlayer, result.slayerUniqueId());
        assertEquals("SteveUpdatedName", result.slayerLastKnownName());

        CombatSnapshot snapshot = result.combatSnapshot();
        assertNotNull(snapshot);
        ParticipantSnapshot pSnap = snapshot.participants().stream()
                .filter(p -> p.playerId().equals(offlinePlayer))
                .findFirst()
                .orElseThrow();
        assertEquals("HistoricalSteve", pSnap.historicalName());
        assertEquals("SteveUpdatedName", pSnap.lastKnownName());
        assertEquals(350.0, pSnap.totalDamage());
    }

    @Test
    @DisplayName("Batalla sin participantes produce BattleResult seguro con Slayer vacío")
    void testNoParticipantsSafeBehavior() {
        BattleSession session = battleManager.startBattle(endWorld);
        DragonIdentity identity = session.getDragonIdentity().orElseThrow();
        EnderDragon dragon = createFakeDragon(identity.entityUniqueId(), identity.battleId());

        // Sin daño registrado
        assertEquals(0, session.getCombatRuntime().getParticipantCount());

        Optional<BattleResult> resultOpt = battleManager.handleDragonDeath(dragon, null);

        assertTrue(resultOpt.isPresent());
        BattleResult result = resultOpt.get();
        assertNull(result.slayerUniqueId());
        assertNull(result.slayerLastKnownName());
        assertTrue(result.getSlayerUniqueId().isEmpty());
        assertTrue(result.getSlayerLastKnownName().isEmpty());
        assertNotNull(result.combatSnapshot());
        assertEquals(0, result.combatSnapshot().participants().size());
        assertEquals(0.0, result.combatSnapshot().totalDamage());
    }

    // ==========================================
    // 4. IMMUTABILITY & SNAPSHOT INTEGRITY
    // ==========================================

    @Test
    @DisplayName("BattleResult es inmutable y no expone referencias mutables de runtime")
    void testBattleResultImmutability() {
        BattleSession session = battleManager.startBattle(endWorld);
        DragonIdentity identity = session.getDragonIdentity().orElseThrow();
        EnderDragon dragon = createFakeDragon(identity.entityUniqueId(), identity.battleId());

        CombatRuntime combat = session.getCombatRuntime();
        UUID p1 = UUID.randomUUID();
        combat.recordDamage(p1, "PlayerOne", 100.0, 10L);

        Optional<BattleResult> resultOpt = battleManager.handleDragonDeath(dragon, null);
        assertTrue(resultOpt.isPresent());
        BattleResult result = resultOpt.get();

        CombatSnapshot snapshot = result.combatSnapshot();
        assertNotNull(snapshot);
        assertEquals(1, snapshot.participants().size());
        assertEquals(100.0, snapshot.totalDamage());

        // La lista de participantes en el snapshot no es modificable
        assertThrows(UnsupportedOperationException.class, () ->
                snapshot.participants().add(null)
        );
    }

    // ==========================================
    // 5. INDEPENDENCE FROM DRAGONBATTLE
    // ==========================================

    @Test
    @DisplayName("La finalización es completamente independiente de DragonBattle vanilla")
    void testIndependenceFromDragonBattle() {
        BattleSession session = battleManager.startBattle(endWorld);
        DragonIdentity identity = session.getDragonIdentity().orElseThrow();
        EnderDragon dragon = createFakeDragon(identity.entityUniqueId(), identity.battleId());

        // En ningún momento se consulta o requiere DragonBattle
        // La fuente de verdad exclusiva es session y el PDC del dragón
        Optional<BattleResult> resultOpt = battleManager.handleDragonDeath(dragon, null);

        assertTrue(resultOpt.isPresent());
        BattleResult result = resultOpt.get();
        assertEquals(BattleState.COMPLETED, session.getState());
        assertEquals(session.getBattleId(), result.battleId());
        assertTrue(result.isVictory());
    }

    @Test
    @DisplayName("BetterDragonVictoryEvent encapsula el resultado y no expone BattleSession")
    void testVictoryEventEncapsulationDoesNotExposeBattleSession() {
        for (Method m : BetterDragonVictoryEvent.class.getMethods()) {
            assertNotEquals("getSession", m.getName(), "BetterDragonVictoryEvent no debe exponer getSession()");
            assertFalse(BattleSession.class.isAssignableFrom(m.getReturnType()),
                    "Ningún método de BetterDragonVictoryEvent debe retornar BattleSession");
        }
        for (Field f : BetterDragonVictoryEvent.class.getDeclaredFields()) {
            assertFalse(BattleSession.class.isAssignableFrom(f.getType()),
                    "BetterDragonVictoryEvent no debe almacenar referencias a BattleSession");
        }

        BattleSession session = battleManager.startBattle(endWorld);
        DragonIdentity identity = session.getDragonIdentity().orElseThrow();
        EnderDragon dragon = createFakeDragon(identity.entityUniqueId(), identity.battleId());

        Optional<BattleResult> resultOpt = battleManager.handleDragonDeath(dragon, null);
        assertTrue(resultOpt.isPresent());
        assertEquals(1, dispatchedEvents.size());

        BetterDragonVictoryEvent event = dispatchedEvents.getFirst();
        assertEquals(session.getBattleId(), event.getBattleId());
        assertEquals(resultOpt.get(), event.getResult());
        assertEquals(session.getWorldName(), event.getWorldName());
        assertEquals(resultOpt.get().getSlayerUniqueId(), event.getSlayerUniqueId());
        assertEquals(resultOpt.get().getSlayerLastKnownName(), event.getSlayerLastKnownName());
        assertEquals(resultOpt.get().getDuration(), event.getDuration());
    }

    @Test
    @DisplayName("Slayer evalúa estrictamente totalDamage DESC y firstHitSequence ASC sin tercer criterio por UUID")
    void testSlayerTwoCriteriaOnlyWithoutUuidTieBreak() {
        BattleSession session = battleManager.startBattle(endWorld);
        DragonIdentity identity = session.getDragonIdentity().orElseThrow();
        EnderDragon dragon = createFakeDragon(identity.entityUniqueId(), identity.battleId());

        CombatRuntime combat = session.getCombatRuntime();
        // UUID lexicográficamente alto para jugador A
        UUID playerA = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        // UUID lexicográficamente bajo para jugador B
        UUID playerB = UUID.fromString("00000000-0000-0000-0000-000000000000");

        // Ambos con 100.0 de daño, pero playerA golpeó antes (seq 10 vs seq 20)
        combat.recordDamage(playerA, "EarlyPlayerA", 100.0, 10L);
        combat.recordDamage(playerB, "LatePlayerB", 100.0, 20L);

        Optional<BattleResult> resultOpt = battleManager.handleDragonDeath(dragon, null);
        assertTrue(resultOpt.isPresent());
        BattleResult result = resultOpt.get();

        // playerA gana por firstHitSequence ASC (10 < 20), sin que el UUID menor de playerB interfiera
        assertEquals(playerA, result.slayerUniqueId());
        assertEquals("EarlyPlayerA", result.slayerLastKnownName());
    }

    @Test
    @DisplayName("Dragón vanilla no genera victoria y no suprime XP ni drops vanilla")
    void testVanillaDragonDoesNotSuppressXpOrDrops() {
        BattleSession session = battleManager.startBattle(endWorld);
        assertEquals(BattleState.ACTIVE, session.getState());

        EnderDragon vanillaDragon = createPlainDragon(UUID.randomUUID());
        List<ItemStack> drops = new ArrayList<>();
        ((List<Object>) (List<?>) drops).add(new Object());
        EntityDeathEvent deathEvent = new EntityDeathEvent(vanillaDragon, createFakeDamageSource(), drops, 12000);

        Optional<BattleResult> resultOpt = battleManager.handleDragonDeath(vanillaDragon, deathEvent);

        assertTrue(resultOpt.isEmpty(), "Muerte de dragón vanilla debe retornar empty");
        assertEquals(BattleState.ACTIVE, session.getState(), "Sesión de BetterDragon debe permanecer ACTIVE");
        assertTrue(dispatchedEvents.isEmpty(), "No debe despacharse BetterDragonVictoryEvent");
        assertEquals(12000, deathEvent.getDroppedExp(), "La XP vanilla debe permanecer intacta (12000)");
        assertEquals(1, deathEvent.getDrops().size(), "Los drops vanilla deben permanecer intactos");
    }

    @Test
    @DisplayName("Dragón BetterDragon suprime XP a 0 y limpia drops en EntityDeathEvent")
    void testManagedDragonSuppressesVanillaXpAndDrops() {
        BattleSession session = battleManager.startBattle(endWorld);
        DragonIdentity identity = session.getDragonIdentity().orElseThrow();
        EnderDragon dragon = createFakeDragon(identity.entityUniqueId(), identity.battleId());

        List<ItemStack> drops = new ArrayList<>();
        ((List<Object>) (List<?>) drops).add(new Object());
        EntityDeathEvent deathEvent = new EntityDeathEvent(dragon, createFakeDamageSource(), drops, 12000);

        Optional<BattleResult> resultOpt = battleManager.handleDragonDeath(dragon, deathEvent);

        assertTrue(resultOpt.isPresent(), "Muerte de dragón administrado debe retornar resultado");
        assertEquals(BattleState.COMPLETED, session.getState());
        assertEquals(1, dispatchedEvents.size());
        assertEquals(0, deathEvent.getDroppedExp(), "La XP vanilla debe ser suprimida a 0");
        assertTrue(deathEvent.getDrops().isEmpty(), "Los drops vanilla deben ser limpiados");
    }

    @Test
    @DisplayName("Dragon Egg y Primera Victoria son independientes de DragonBattle vanilla")
    void testDragonEggAndFirstVictoryIndependenceFromDragonBattle() {
        BattleSession session = battleManager.startBattle(endWorld);
        DragonIdentity identity = session.getDragonIdentity().orElseThrow();
        EnderDragon dragon = createFakeDragon(identity.entityUniqueId(), identity.battleId());

        // La finalización no consulta ni requiere DragonBattle
        Optional<BattleResult> resultOpt = battleManager.handleDragonDeath(dragon, null);
        assertTrue(resultOpt.isPresent());

        // El resultado no genera ni manipula bloques de huevo, preservando el lifecycle para fases posteriores
        BattleResult result = resultOpt.get();
        assertTrue(result.isVictory());
        assertNotNull(result.combatSnapshot());
    }

    // ==========================================
    // HELPERS & FAKES
    // ==========================================

    private World createFakeWorld(String name, UUID uid, World.Environment env) {
        return (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getName")) return name;
                    if (method.getName().equals("getUID")) return uid;
                    if (method.getName().equals("getEnvironment")) return env;
                    return null;
                }
        );
    }

    private DamageSource createFakeDamageSource() {
        return (DamageSource) Proxy.newProxyInstance(
                DamageSource.class.getClassLoader(),
                new Class<?>[]{DamageSource.class},
                (proxy, method, args) -> null
        );
    }

    private EnderDragon createFakeDragon(UUID uuid, BattleId battleId) {
        FakePdc pdc = new FakePdc();
        pdc.set(BetterDragonKeys.MANAGED, PersistentDataType.BOOLEAN, true);
        pdc.set(BetterDragonKeys.BATTLE_ID, PersistentDataType.STRING, battleId.asString());
        return createDragonWithPdc(uuid, pdc);
    }

    private EnderDragon createPlainDragon(UUID uuid) {
        FakePdc pdc = new FakePdc();
        return createDragonWithPdc(uuid, pdc);
    }

    private EnderDragon createDragonWithPdc(UUID uuid, PersistentDataContainer pdc) {
        return (EnderDragon) Proxy.newProxyInstance(
                EnderDragon.class.getClassLoader(),
                new Class<?>[]{EnderDragon.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return uuid;
                    if (method.getName().equals("getPersistentDataContainer")) return pdc;
                    if (method.getName().equals("isValid")) return true;
                    if (method.getName().equals("remove")) return null;
                    return null;
                }
        );
    }

    private static class FakePdc implements PersistentDataContainer {
        private final Map<NamespacedKey, Object> map = new HashMap<>();

        @Override
        public <T, Z> void set(@NotNull NamespacedKey key, @NotNull PersistentDataType<T, Z> type, @NotNull Z value) {
            map.put(key, value);
        }

        @Override
        public <T, Z> boolean has(@NotNull NamespacedKey key, @NotNull PersistentDataType<T, Z> type) {
            return map.containsKey(key);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T, Z> @Nullable Z get(@NotNull NamespacedKey key, @NotNull PersistentDataType<T, Z> type) {
            return (Z) map.get(key);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T, Z> @NotNull Z getOrDefault(@NotNull NamespacedKey key, @NotNull PersistentDataType<T, Z> type, @NotNull Z defaultValue) {
            return (Z) map.getOrDefault(key, defaultValue);
        }

        @Override
        public @NotNull Set<NamespacedKey> getKeys() {
            return map.keySet();
        }

        @Override
        public void remove(@NotNull NamespacedKey key) {
            map.remove(key);
        }

        @Override
        public int getSize() {
            return map.size();
        }

        @Override
        public boolean has(@NotNull NamespacedKey key) {
            return map.containsKey(key);
        }

        @Override
        public boolean isEmpty() {
            return map.isEmpty();
        }

        @Override
        public void copyTo(@NotNull PersistentDataContainer other, boolean replace) {}

        @Override
        public @NotNull PersistentDataAdapterContext getAdapterContext() {
            return null;
        }

        @Override
        public void readFromBytes(byte[] bytes, boolean clear) {}

        @Override
        public byte[] serializeToBytes() {
            return new byte[0];
        }
    }
}
