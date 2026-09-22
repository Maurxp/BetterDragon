package maurxp.betterdragon.config;

import maurxp.betterdragon.phase.PhaseDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias de DragonScalingCalculator (Fase 3.13)")
class DragonScalingCalculatorTest {

    private DragonDefinition createDragonWithScaling(boolean enabled, ScalingMode mode, double healthPerPlayer, double maxMultiplier) {
        DragonAttributes attrs = new DragonAttributes(500.0, 0.3, 64.0, 10.0);
        DragonScalingDefinition scaling = new DragonScalingDefinition(enabled, mode, healthPerPlayer, maxMultiplier);
        return new DragonDefinition(
                "scaled_dragon",
                "Scaled Dragon",
                attrs,
                scaling,
                List.of(new PhaseDefinition("p1", 0, 1.0, List.of())),
                Map.of()
        );
    }

    @Test
    @DisplayName("Escalado deshabilitado retorna salud base sin importar recuento de jugadores")
    void testScalingDisabled() {
        DragonDefinition def = createDragonWithScaling(false, ScalingMode.LINEAR, 0.2, 3.0);

        EffectiveDragonStats stats0 = DragonScalingCalculator.calculate(def, 0);
        EffectiveDragonStats stats1 = DragonScalingCalculator.calculate(def, 1);
        EffectiveDragonStats stats5 = DragonScalingCalculator.calculate(def, 5);
        EffectiveDragonStats stats50 = DragonScalingCalculator.calculate(def, 50);

        assertEquals(500.0, stats0.maxHealth());
        assertEquals(1.0, stats0.healthMultiplier());
        assertEquals(500.0, stats1.maxHealth());
        assertEquals(1.0, stats1.healthMultiplier());
        assertEquals(500.0, stats5.maxHealth());
        assertEquals(1.0, stats5.healthMultiplier());
        assertEquals(500.0, stats50.maxHealth());
        assertEquals(1.0, stats50.healthMultiplier());
    }

    @Test
    @DisplayName("Escalado lineal con 0 y 1 jugador retorna multiplicador 1.0 y salud base")
    void testLinearScalingZeroOrOnePlayer() {
        DragonDefinition def = createDragonWithScaling(true, ScalingMode.LINEAR, 0.25, 3.0);

        EffectiveDragonStats stats0 = DragonScalingCalculator.calculate(def, 0);
        assertEquals(500.0, stats0.maxHealth());
        assertEquals(1.0, stats0.healthMultiplier());
        assertEquals(0, stats0.playerCountUsed());

        EffectiveDragonStats stats1 = DragonScalingCalculator.calculate(def, 1);
        assertEquals(500.0, stats1.maxHealth());
        assertEquals(1.0, stats1.healthMultiplier());
        assertEquals(1, stats1.playerCountUsed());
    }

    @Test
    @DisplayName("Escalado lineal con N jugadores calcula multiplicador: 1.0 + (N - 1) * alpha")
    void testLinearScalingFormula() {
        // Base 500, alpha = 0.2 (20% por jugador extra)
        // 2 jugadores: 1.0 + 0.2 * 1 = 1.2 -> 600.0 HP
        // 3 jugadores: 1.0 + 0.2 * 2 = 1.4 -> 700.0 HP
        // 5 jugadores: 1.0 + 0.2 * 4 = 1.8 -> 900.0 HP
        DragonDefinition def = createDragonWithScaling(true, ScalingMode.LINEAR, 0.2, 3.0);

        EffectiveDragonStats stats2 = DragonScalingCalculator.calculate(def, 2);
        assertEquals(1.2, stats2.healthMultiplier(), 0.0001);
        assertEquals(600.0, stats2.maxHealth(), 0.0001);

        EffectiveDragonStats stats3 = DragonScalingCalculator.calculate(def, 3);
        assertEquals(1.4, stats3.healthMultiplier(), 0.0001);
        assertEquals(700.0, stats3.maxHealth(), 0.0001);

        EffectiveDragonStats stats5 = DragonScalingCalculator.calculate(def, 5);
        assertEquals(1.8, stats5.healthMultiplier(), 0.0001);
        assertEquals(900.0, stats5.maxHealth(), 0.0001);
    }

    @Test
    @DisplayName("Escalado respeta el límite superior maxHealthMultiplier (Cap)")
    void testScalingCap() {
        // Base 500, alpha = 0.5, cap = 2.0 (máximo 1000.0 HP)
        // Con 10 jugadores: fórmula daría 1.0 + 9 * 0.5 = 5.5, pero debe quedar en 2.0
        DragonDefinition def = createDragonWithScaling(true, ScalingMode.LINEAR, 0.5, 2.0);

        EffectiveDragonStats stats10 = DragonScalingCalculator.calculate(def, 10);
        assertEquals(2.0, stats10.healthMultiplier(), 0.0001);
        assertEquals(1000.0, stats10.maxHealth(), 0.0001);
    }

    @Test
    @DisplayName("Determinismo: Mismos parámetros producen idénticos resultados")
    void testDeterminism() {
        DragonDefinition def = createDragonWithScaling(true, ScalingMode.LINEAR, 0.15, 2.5);

        EffectiveDragonStats run1 = DragonScalingCalculator.calculate(def, 7);
        EffectiveDragonStats run2 = DragonScalingCalculator.calculate(def, 7);

        assertEquals(run1, run2);
        assertEquals(run1.maxHealth(), run2.maxHealth());
        assertEquals(run1.healthMultiplier(), run2.healthMultiplier());
    }

    @Test
    @DisplayName("Atributos no escalados (velocidad, rango, daño) se transfieren intactos")
    void testNonScaledAttributesPreserved() {
        DragonDefinition def = createDragonWithScaling(true, ScalingMode.LINEAR, 0.2, 3.0);
        EffectiveDragonStats stats = DragonScalingCalculator.calculate(def, 4);

        assertEquals(0.3, stats.movementSpeed().orElseThrow());
        assertEquals(64.0, stats.followRange().orElseThrow());
        assertEquals(10.0, stats.attackDamage().orElseThrow());
    }
}
