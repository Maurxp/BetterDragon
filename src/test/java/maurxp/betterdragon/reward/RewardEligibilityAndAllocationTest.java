package maurxp.betterdragon.reward;

import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.battle.model.BattleResult;
import maurxp.betterdragon.combat.CombatSnapshot;
import maurxp.betterdragon.combat.ParticipantSnapshot;
import maurxp.betterdragon.config.RewardConfigurationSnapshot;
import maurxp.betterdragon.config.RewardItemDefinition;
import maurxp.betterdragon.config.SlayerRewardDefinition;
import maurxp.betterdragon.reward.allocation.RewardAllocationEngine;
import maurxp.betterdragon.reward.model.RewardAllocation;
import maurxp.betterdragon.reward.model.RewardAllocationPlan;
import maurxp.betterdragon.reward.model.RewardSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias de Elegibilidad y Asignación de Recompensas (Fase 3.8)")
class RewardEligibilityAndAllocationTest {

    private RewardAllocationEngine engine;
    private BattleId battleId;
    private Instant now;

    @BeforeEach
    void setUp() {
        engine = new RewardAllocationEngine();
        battleId = BattleId.random();
        now = Instant.now();
    }

    private BattleResult createResult(List<ParticipantSnapshot> participants, UUID slayerId, String slayerName) {
        double totalDamage = participants.stream().mapToDouble(ParticipantSnapshot::totalDamage).sum();
        CombatSnapshot snapshot = new CombatSnapshot(battleId, participants, 100L, totalDamage);
        return BattleResult.completed(
                battleId,
                now.minusSeconds(60),
                now,
                slayerId,
                slayerName,
                snapshot
        );
    }

    @Test
    @DisplayName("Un único participante recibe el 100% de la recompensa de participación y la de Slayer")
    void testSingleParticipantReceivesFullReward() {
        UUID p1 = UUID.randomUUID();
        List<ParticipantSnapshot> participants = List.of(
                new ParticipantSnapshot(p1, "SoloHero", "SoloHero", 500.0, 1L, 10L, 100L)
        );
        BattleResult result = createResult(participants, p1, "SoloHero");

        RewardConfigurationSnapshot config = new RewardConfigurationSnapshot(
                true,
                20.0,
                List.of(new RewardItemDefinition("pool_diamond", "DIAMOND", 100)),
                new SlayerRewardDefinition(true, true, List.of(new RewardItemDefinition("slayer_netherite", "NETHERITE_INGOT", 2)))
        );

        RewardAllocationPlan plan = engine.calculatePlan(result, config);

        assertFalse(plan.isEmpty());
        assertEquals(2, plan.allocations().size(), "Debe recibir 1 de participación y 1 de Slayer");

        RewardAllocation part = plan.allocations().stream()
                .filter(a -> a.source() == RewardSource.PARTICIPATION).findFirst().orElseThrow();
        assertEquals(p1, part.participantId());
        assertEquals("pool_diamond", part.rewardId());
        assertEquals(100, part.item().amount());
        assertEquals("DIAMOND", part.item().material());
        assertEquals(100.0, part.participationPercent(), 0.001);

        RewardAllocation slayer = plan.allocations().stream()
                .filter(a -> a.source() == RewardSource.SLAYER).findFirst().orElseThrow();
        assertEquals(p1, slayer.participantId());
        assertEquals("slayer_netherite", slayer.rewardId());
        assertEquals(2, slayer.item().amount());
        assertEquals("NETHERITE_INGOT", slayer.item().material());
    }

    @Test
    @DisplayName("Varios participantes elegibles sin remanente reciben sus cuotas proporcionales exactas")
    void testMultipleEligibleParticipantsExactSplit() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        List<ParticipantSnapshot> participants = List.of(
                new ParticipantSnapshot(p1, "PlayerA", "PlayerA", 700.0, 1L, 5L, 100L), // 70%
                new ParticipantSnapshot(p2, "PlayerB", "PlayerB", 300.0, 2L, 6L, 101L)  // 30%
        );
        BattleResult result = createResult(participants, p1, "PlayerA");

        RewardConfigurationSnapshot config = new RewardConfigurationSnapshot(
                true,
                20.0,
                List.of(new RewardItemDefinition("pool_diamond", "DIAMOND", 100)),
                new SlayerRewardDefinition(false, false, List.of())
        );

        RewardAllocationPlan plan = engine.calculatePlan(result, config);

        assertEquals(2, plan.allocations().size());
        RewardAllocation a1 = plan.allocations().stream().filter(a -> a.participantId().equals(p1)).findFirst().orElseThrow();
        RewardAllocation a2 = plan.allocations().stream().filter(a -> a.participantId().equals(p2)).findFirst().orElseThrow();

        assertEquals(70, a1.item().amount());
        assertEquals(30, a2.item().amount());
    }

    @Test
    @DisplayName("Redistribución proporcional: la cuota del participante no elegible se reparte entre los elegibles")
    void testProportionalRedistributionOfIneligibleShare() {
        // Ejemplo de la especificación:
        // Total = 1000 items (o 100 items).
        // A = 60%, B = 30%, C = 10%.
        // Mínimo = 20%.
        // C no es elegible. A y B se reparten el 100% total con ratio 60/90 y 30/90.
        UUID pA = UUID.randomUUID();
        UUID pB = UUID.randomUUID();
        UUID pC = UUID.randomUUID();

        List<ParticipantSnapshot> participants = List.of(
                new ParticipantSnapshot(pA, "PlayerA", "PlayerA", 600.0, 1L, 10L, 100L),
                new ParticipantSnapshot(pB, "PlayerB", "PlayerB", 300.0, 2L, 11L, 101L),
                new ParticipantSnapshot(pC, "PlayerC", "PlayerC", 100.0, 3L, 12L, 102L)
        );
        BattleResult result = createResult(participants, pA, "PlayerA");

        // 90 items totales para división limpia: 60/90 = 60 items para A, 30/90 = 30 items para B
        RewardConfigurationSnapshot config = new RewardConfigurationSnapshot(
                true,
                20.0,
                List.of(new RewardItemDefinition("pool_emerald", "EMERALD", 90)),
                new SlayerRewardDefinition(false, false, List.of())
        );

        RewardAllocationPlan plan = engine.calculatePlan(result, config);

        assertEquals(2, plan.allocations().size(), "C no debe recibir recompensa");
        assertTrue(plan.allocations().stream().noneMatch(a -> a.participantId().equals(pC)));

        RewardAllocation allocA = plan.allocations().stream().filter(a -> a.participantId().equals(pA)).findFirst().orElseThrow();
        RewardAllocation allocB = plan.allocations().stream().filter(a -> a.participantId().equals(pB)).findFirst().orElseThrow();

        assertEquals(60, allocA.item().amount(), "A debe recibir 60 / (60+30) * 90 = 60");
        assertEquals(30, allocB.item().amount(), "B debe recibir 30 / (60+30) * 90 = 30");
    }

    @Test
    @DisplayName("Ningún participante es elegible: retorno seguro de plan vacío sin errores ni división por cero")
    void testNoneEligibleReturnsEmptyPlan() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        List<ParticipantSnapshot> participants = List.of(
                new ParticipantSnapshot(p1, "PlayerA", "PlayerA", 10.0, 1L, 2L, 100L),
                new ParticipantSnapshot(p2, "PlayerB", "PlayerB", 10.0, 2L, 3L, 101L)
        );
        BattleResult result = createResult(participants, p1, "PlayerA");

        // min_participation_percent = 60%, cada uno tiene 50%
        RewardConfigurationSnapshot config = new RewardConfigurationSnapshot(
                true,
                60.0,
                List.of(new RewardItemDefinition("pool_diamond", "DIAMOND", 100)),
                new SlayerRewardDefinition(true, true, List.of(new RewardItemDefinition("slayer_netherite", "NETHERITE_INGOT", 1)))
        );

        RewardAllocationPlan plan = engine.calculatePlan(result, config);

        assertTrue(plan.isEmpty());
        assertEquals(0, plan.allocations().size());
    }

    @Test
    @DisplayName("totalDamage == 0: comportamiento seguro y determinista sin división por cero")
    void testTotalDamageZeroReturnsEmptyPlan() {
        UUID p1 = UUID.randomUUID();
        List<ParticipantSnapshot> participants = List.of(
                new ParticipantSnapshot(p1, "ZeroDamage", "ZeroDamage", 0.0, 1L, 1L, 100L)
        );
        CombatSnapshot snapshot = new CombatSnapshot(battleId, participants, 1L, 0.0);
        BattleResult result = BattleResult.completed(battleId, now.minusSeconds(30), now, p1, "ZeroDamage", snapshot);

        RewardConfigurationSnapshot config = new RewardConfigurationSnapshot(
                true,
                10.0,
                List.of(new RewardItemDefinition("pool_diamond", "DIAMOND", 10)),
                new SlayerRewardDefinition(false, false, List.of())
        );

        RewardAllocationPlan plan = engine.calculatePlan(result, config);

        assertTrue(plan.isEmpty());
    }

    @Test
    @DisplayName("min_participation_percent == 0: todos los participantes con daño > 0 son elegibles")
    void testMinParticipationZeroAllowsAllNonZeroDamage() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        List<ParticipantSnapshot> participants = List.of(
                new ParticipantSnapshot(p1, "Player1", "Player1", 90.0, 1L, 2L, 100L),
                new ParticipantSnapshot(p2, "Player2", "Player2", 10.0, 2L, 3L, 101L)
        );
        BattleResult result = createResult(participants, p1, "Player1");

        RewardConfigurationSnapshot config = new RewardConfigurationSnapshot(
                true,
                0.0,
                List.of(new RewardItemDefinition("pool_iron", "IRON_INGOT", 100)),
                new SlayerRewardDefinition(false, false, List.of())
        );

        RewardAllocationPlan plan = engine.calculatePlan(result, config);

        assertEquals(2, plan.eligibleParticipants().size());
        assertEquals(2, plan.allocations().size());
        RewardAllocation a1 = plan.allocations().stream().filter(a -> a.participantId().equals(p1)).findFirst().orElseThrow();
        RewardAllocation a2 = plan.allocations().stream().filter(a -> a.participantId().equals(p2)).findFirst().orElseThrow();
        assertEquals(90, a1.item().amount());
        assertEquals(10, a2.item().amount());
    }

    @Test
    @DisplayName("Participante elegible pero cuya cuota fraccionaria redondea a 0 unidades no recibe ítems ficticios")
    void testEligibleParticipantWithFractionRoundingToZero() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        List<ParticipantSnapshot> participants = List.of(
                new ParticipantSnapshot(p1, "Player1", "Player1", 999.0, 1L, 2L, 100L),
                new ParticipantSnapshot(p2, "Player2", "Player2", 1.0, 2L, 3L, 101L)
        );
        BattleResult result = createResult(participants, p1, "Player1");

        RewardConfigurationSnapshot config = new RewardConfigurationSnapshot(
                true,
                0.0,
                List.of(new RewardItemDefinition("pool_iron", "IRON_INGOT", 100)),
                new SlayerRewardDefinition(false, false, List.of())
        );

        RewardAllocationPlan plan = engine.calculatePlan(result, config);

        // Ambos son elegibles porque min es 0%
        assertEquals(2, plan.eligibleParticipants().size());
        // Pero p2 tiene cuota de 0.1 ítems, que redondea hacia abajo a 0, por lo que no se crea asignación física de 0 ítems
        assertEquals(1, plan.allocations().size());
        assertEquals(100, plan.allocations().getFirst().item().amount());
    }

    @Test
    @DisplayName("min_participation_percent == 100: solo un participante que haya hecho el 100% califica")
    void testMinParticipationOneHundredRequiresSoleParticipant() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        List<ParticipantSnapshot> participants = List.of(
                new ParticipantSnapshot(p1, "Player1", "Player1", 99.0, 1L, 2L, 100L),
                new ParticipantSnapshot(p2, "Player2", "Player2", 1.0, 2L, 3L, 101L)
        );
        BattleResult result = createResult(participants, p1, "Player1");

        RewardConfigurationSnapshot config = new RewardConfigurationSnapshot(
                true,
                100.0,
                List.of(new RewardItemDefinition("pool_gold", "GOLD_INGOT", 50)),
                new SlayerRewardDefinition(false, false, List.of())
        );

        RewardAllocationPlan plan = engine.calculatePlan(result, config);

        assertTrue(plan.isEmpty(), "Ninguno alcanzó el 100% exacto");
    }

    @Test
    @DisplayName("Daño extremadamente pequeño y grande se manejan sin overflow ni desbordamiento")
    void testExtremeDamageValues() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        List<ParticipantSnapshot> participants = List.of(
                new ParticipantSnapshot(p1, "TinyDamage", "TinyDamage", 0.0000001, 1L, 2L, 100L),
                new ParticipantSnapshot(p2, "HugeDamage", "HugeDamage", 1_000_000_000_000.0, 2L, 3L, 101L)
        );
        BattleResult result = createResult(participants, p2, "HugeDamage");

        RewardConfigurationSnapshot config = new RewardConfigurationSnapshot(
                true,
                1.0,
                List.of(new RewardItemDefinition("pool_diamond", "DIAMOND", 64)),
                new SlayerRewardDefinition(false, false, List.of())
        );

        RewardAllocationPlan plan = engine.calculatePlan(result, config);

        assertEquals(1, plan.allocations().size());
        assertEquals(p2, plan.allocations().getFirst().participantId());
        assertEquals(64, plan.allocations().getFirst().item().amount());
    }

    @Test
    @DisplayName("Empate de daño exacto distribuye cantidades iguales y resuelve remanentes por firstHitSequence")
    void testDamageTieAndRemainderDistributionByFirstHitSequence() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p3 = UUID.randomUUID();

        // 3 participantes con 100 de daño cada uno (33.333% cada uno)
        // firstHitSequence: p1=1, p2=2, p3=3
        List<ParticipantSnapshot> participants = List.of(
                new ParticipantSnapshot(p1, "P1", "P1", 100.0, 1L, 10L, 100L),
                new ParticipantSnapshot(p2, "P2", "P2", 100.0, 2L, 11L, 101L),
                new ParticipantSnapshot(p3, "P3", "P3", 100.0, 3L, 12L, 102L)
        );
        BattleResult result = createResult(participants, p1, "P1");

        // 10 items a repartir entre 3 participantes: 10 / 3 = 3 cada uno (total 9), remanente = 1
        // Como todos tienen la misma fracción (0.333333), el desempate debe favorecer a p1 (menor firstHitSequence = 1)
        RewardConfigurationSnapshot config = new RewardConfigurationSnapshot(
                true,
                10.0,
                List.of(new RewardItemDefinition("pool_diamond", "DIAMOND", 10)),
                new SlayerRewardDefinition(false, false, List.of())
        );

        RewardAllocationPlan plan = engine.calculatePlan(result, config);

        assertEquals(3, plan.allocations().size());

        RewardAllocation a1 = plan.allocations().stream().filter(a -> a.participantId().equals(p1)).findFirst().orElseThrow();
        RewardAllocation a2 = plan.allocations().stream().filter(a -> a.participantId().equals(p2)).findFirst().orElseThrow();
        RewardAllocation a3 = plan.allocations().stream().filter(a -> a.participantId().equals(p3)).findFirst().orElseThrow();

        // Total debe sumar exactamente 10 (sin pérdidas de redondeo)
        assertEquals(10, a1.item().amount() + a2.item().amount() + a3.item().amount());

        assertEquals(4, a1.item().amount(), "p1 recibe el remanente (3 base + 1 bonus por firstHitSequence)");
        assertEquals(3, a2.item().amount(), "p2 recibe 3 base");
        assertEquals(3, a3.item().amount(), "p3 recibe 3 base");
    }

    @Test
    @DisplayName("Slayer con empate resuelto por firstHitSequence recibe recompensa de Slayer")
    void testSlayerTieBreakerByFirstHitSequence() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        // Ambos hicieron 500 de daño, pero p1 impactó primero (firstHitSequence = 1 vs 2)
        List<ParticipantSnapshot> participants = List.of(
                new ParticipantSnapshot(p1, "FirstHitter", "FirstHitter", 500.0, 1L, 10L, 100L),
                new ParticipantSnapshot(p2, "SecondHitter", "SecondHitter", 500.0, 2L, 11L, 101L)
        );
        // BattleResult ya determinó a p1 como Slayer por TOP_DAMAGE con desempate firstHitSequence
        BattleResult result = createResult(participants, p1, "FirstHitter");

        RewardConfigurationSnapshot config = new RewardConfigurationSnapshot(
                true,
                20.0,
                List.of(new RewardItemDefinition("pool_gold", "GOLD_INGOT", 20)),
                new SlayerRewardDefinition(true, true, List.of(new RewardItemDefinition("slayer_head", "DRAGON_HEAD", 1)))
        );

        RewardAllocationPlan plan = engine.calculatePlan(result, config);

        RewardAllocation slayerAlloc = plan.allocations().stream()
                .filter(a -> a.source() == RewardSource.SLAYER).findFirst().orElseThrow();

        assertEquals(p1, slayerAlloc.participantId());
        assertEquals("slayer_head", slayerAlloc.rewardId());
        assertEquals("DRAGON_HEAD", slayerAlloc.item().material());
        assertEquals(1, slayerAlloc.item().amount());
    }

    @Test
    @DisplayName("Slayer no elegible no recibe SlayerReward si requiresEligibility es true")
    void testSlayerIneligibleBlockedWhenRequiresEligibilityTrue() {
        UUID p1 = UUID.randomUUID();
        List<ParticipantSnapshot> participants = List.of(
                new ParticipantSnapshot(p1, "UnderThreshold", "UnderThreshold", 10.0, 1L, 1L, 100L)
        );
        BattleResult result = createResult(participants, p1, "UnderThreshold");

        // min_participation_percent = 50%, pero p1 tiene 100% de la batalla? No, hagamos que p1 tenga 10% y otros un mob
        // Si p1 tiene 10% de daño total y mínimo es 20%:
        UUID p2 = UUID.randomUUID();
        List<ParticipantSnapshot> multi = List.of(
                new ParticipantSnapshot(p1, "SlayerWithLowDamage", "SlayerWithLowDamage", 15.0, 1L, 2L, 100L),
                new ParticipantSnapshot(p2, "HeavyHitter", "HeavyHitter", 85.0, 2L, 3L, 101L)
        );
        // p2 hizo 85%, p2 es Slayer en realidad, pero supongamos que el resultado declaró a p1 como slayer
        BattleResult resultIneligibleSlayer = createResult(multi, p1, "SlayerWithLowDamage");

        RewardConfigurationSnapshot configRequiresEligibility = new RewardConfigurationSnapshot(
                true,
                20.0, // p1 tiene 15% < 20%
                List.of(new RewardItemDefinition("pool_diamond", "DIAMOND", 100)),
                new SlayerRewardDefinition(true, true, List.of(new RewardItemDefinition("slayer_sword", "NETHERITE_SWORD", 1)))
        );

        RewardAllocationPlan plan = engine.calculatePlan(resultIneligibleSlayer, configRequiresEligibility);

        // p1 no debe tener asignación de Slayer porque no es elegible
        assertTrue(plan.allocations().stream()
                .noneMatch(a -> a.participantId().equals(p1) && a.source() == RewardSource.SLAYER));
    }

    @Test
    @DisplayName("Slayer no elegible sí recibe SlayerReward si requiresEligibility es false")
    void testSlayerIneligibleAllowedWhenRequiresEligibilityFalse() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        List<ParticipantSnapshot> multi = List.of(
                new ParticipantSnapshot(p1, "SlayerLowDamage", "SlayerLowDamage", 15.0, 1L, 2L, 100L),
                new ParticipantSnapshot(p2, "BigDamage", "BigDamage", 85.0, 2L, 3L, 101L)
        );
        BattleResult resultIneligibleSlayer = createResult(multi, p1, "SlayerLowDamage");

        RewardConfigurationSnapshot configNoEligibilityRequired = new RewardConfigurationSnapshot(
                true,
                20.0,
                List.of(new RewardItemDefinition("pool_diamond", "DIAMOND", 100)),
                new SlayerRewardDefinition(true, false, List.of(new RewardItemDefinition("slayer_sword", "NETHERITE_SWORD", 1)))
        );

        RewardAllocationPlan plan = engine.calculatePlan(resultIneligibleSlayer, configNoEligibilityRequired);

        assertTrue(plan.allocations().stream()
                .anyMatch(a -> a.participantId().equals(p1) && a.source() == RewardSource.SLAYER));
    }
}
