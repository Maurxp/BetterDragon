package maurxp.betterdragon.battle;

import maurxp.betterdragon.ability.AbilityEngine;
import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.battle.model.DragonIdentity;
import maurxp.betterdragon.config.BattleConfigurationSnapshot;
import maurxp.betterdragon.config.DragonBossBarDefinition;
import maurxp.betterdragon.config.DragonDefinition;
import maurxp.betterdragon.config.DragonEnrageDefinition;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias de Soft Enrage y Cooldown Scaling (Fase 3.14)")
class DragonSoftEnrageTest {

    private BattleSession createSession(boolean enrageEnabled, double threshold, double multiplier) {
        DragonDefinition dragonDef = new DragonDefinition(
                "test_dragon",
                "Test Dragon",
                maurxp.betterdragon.config.DragonAttributes.defaults(),
                maurxp.betterdragon.config.DragonScalingDefinition.disabled(),
                new DragonBossBarDefinition(true, "{dragon_name} - {phase}", BarColor.PURPLE, BarStyle.SOLID),
                new DragonEnrageDefinition(enrageEnabled, threshold, multiplier),
                DragonDefinition.defaults().phases(),
                DragonDefinition.defaults().abilities()
        );
        BattleConfigurationSnapshot snapshot = new BattleConfigurationSnapshot(
                false,
                false,
                dragonDef,
                maurxp.betterdragon.arena.ArenaDefinition.defaults(),
                maurxp.betterdragon.config.RewardConfigurationSnapshot.disabled()
        );
        return new BattleSession(BattleId.random(), "world_the_end", UUID.randomUUID(), snapshot);
    }

    @Test
    @DisplayName("1. Enrage inactivo por configuración no se dispara a ninguna salud")
    void testEnrageDisabledInConfig() {
        BattleSession session = createSession(false, 0.20, 0.75);
        session.start();
        session.activate(new DragonIdentity(UUID.randomUUID(), session.getBattleId(), "test_dragon", 1));

        assertFalse(session.checkEnrage(10.0, 200.0));
        assertFalse(session.isEnrageActive());
        assertFalse(session.getBossBar().isEnraged());
    }

    @Test
    @DisplayName("2. Enrage no se activa cuando la salud está estrictamente por encima del umbral")
    void testAboveThreshold() {
        BattleSession session = createSession(true, 0.20, 0.75);
        session.start();
        session.activate(new DragonIdentity(UUID.randomUUID(), session.getBattleId(), "test_dragon", 1));

        // 45 / 200 = 0.225 > 0.20
        assertFalse(session.checkEnrage(45.0, 200.0));
        assertFalse(session.isEnrageActive());
        assertFalse(session.getBossBar().isEnraged());
    }

    @Test
    @DisplayName("3. Enrage se activa en el caso exacto de igualdad de threshold (ratio == threshold)")
    void testExactThreshold() {
        BattleSession session = createSession(true, 0.20, 0.75);
        session.start();
        session.activate(new DragonIdentity(UUID.randomUUID(), session.getBattleId(), "test_dragon", 1));

        // 40 / 200 = 0.20 == 0.20
        assertTrue(session.checkEnrage(40.0, 200.0));
        assertTrue(session.isEnrageActive());
        assertTrue(session.getBossBar().isEnraged());
    }

    @Test
    @DisplayName("4. Enrage se activa cuando la salud está por debajo del umbral")
    void testBelowThreshold() {
        BattleSession session = createSession(true, 0.20, 0.75);
        session.start();
        session.activate(new DragonIdentity(UUID.randomUUID(), session.getBattleId(), "test_dragon", 1));

        // 30 / 200 = 0.15 < 0.20
        assertTrue(session.checkEnrage(30.0, 200.0));
        assertTrue(session.isEnrageActive());
        assertTrue(session.getBossBar().isEnraged());
    }

    @Test
    @DisplayName("5. Monotonicidad estricta: una vez activado (false -> true), jamás se desactiva aunque recupere salud")
    void testMonotonicity() {
        BattleSession session = createSession(true, 0.20, 0.75);
        session.start();
        session.activate(new DragonIdentity(UUID.randomUUID(), session.getBattleId(), "test_dragon", 1));

        // Se activa con 10% de salud
        assertTrue(session.checkEnrage(20.0, 200.0));
        assertTrue(session.isEnrageActive());

        // Se cura al 100% de salud (por cristales de End)
        session.updateDragonHealth(200.0, 200.0);
        assertTrue(session.isEnrageActive());
        assertTrue(session.getBossBar().isEnraged());
    }

    @Test
    @DisplayName("6. calculateEffectiveCooldown aplica correctamente el multiplicador de recarga")
    void testCalculateEffectiveCooldown() {
        // Enrage inactivo: cooldown base se preserva intacto
        assertEquals(200L, AbilityEngine.calculateEffectiveCooldown(200L, false, 0.75));

        // Enrage activo con TUNING_CANDIDATE 0.75: 200 * 0.75 = 150
        assertEquals(150L, AbilityEngine.calculateEffectiveCooldown(200L, true, 0.75));

        // 100 * 0.75 = 75
        assertEquals(75L, AbilityEngine.calculateEffectiveCooldown(100L, true, 0.75));

        // Cooldown base en 0 permanece en 0 (sin cooldown)
        assertEquals(0L, AbilityEngine.calculateEffectiveCooldown(0L, true, 0.75));

        // Garantía de mínimo 1 tick si base > 0
        assertEquals(1L, AbilityEngine.calculateEffectiveCooldown(1L, true, 0.50));

        // Multiplicador inválido o no positivo preserva cooldown base
        assertEquals(200L, AbilityEngine.calculateEffectiveCooldown(200L, true, 0.0));
        assertEquals(200L, AbilityEngine.calculateEffectiveCooldown(200L, true, -1.0));
        assertEquals(200L, AbilityEngine.calculateEffectiveCooldown(200L, true, Double.NaN));
    }

    @Test
    @DisplayName("7. calculateEffectiveCooldown para casos <1.0 (más corto), =1.0 (idéntico) y >1.0 (más largo)")
    void testCalculateEffectiveCooldownScenarios() {
        long base = 100L;

        // < 1.0 -> más corto
        long faster = AbilityEngine.calculateEffectiveCooldown(base, true, 0.70);
        assertTrue(faster < base);
        assertEquals(70L, faster);

        // = 1.0 -> sin cambio
        long neutral = AbilityEngine.calculateEffectiveCooldown(base, true, 1.0);
        assertEquals(base, neutral);

        // > 1.0 -> más largo
        long slower = AbilityEngine.calculateEffectiveCooldown(base, true, 1.40);
        assertTrue(slower > base);
        assertEquals(140L, slower);
    }

    @Test
    @DisplayName("8. Soft Enrage y escalado no mutan la definición inmutable de la habilidad")
    void testAbilityDefinitionImmutabilityUnderEnrage() {
        maurxp.betterdragon.ability.AbilityDefinition ability = new maurxp.betterdragon.ability.AbilityDefinition(
                "fireball",
                maurxp.betterdragon.ability.AbilityTrigger.PERIODIC,
                100,
                maurxp.betterdragon.ability.TargetSelectorType.NEAREST_PLAYER,
                maurxp.betterdragon.ability.EffectOriginType.DRAGON_HEAD,
                maurxp.betterdragon.ability.AbilityEffectType.DAMAGE,
                java.util.Map.of("damage", 10.0)
        );

        long effective = AbilityEngine.calculateEffectiveCooldown(ability.cooldownTicks(), true, 0.75);
        assertEquals(75L, effective);
        assertEquals(100, ability.cooldownTicks()); // Definición original preservada intacta
    }
}
