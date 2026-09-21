package maurxp.betterdragon.reward;

import maurxp.betterdragon.config.BetterDragonConfig;
import maurxp.betterdragon.config.ConfigValidationException;
import maurxp.betterdragon.config.ConfigurationLoader;
import maurxp.betterdragon.config.RewardConfigurationSnapshot;
import maurxp.betterdragon.config.RewardItemDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias de Carga y Validación de Configuración de Rewards (Fase 3.8)")
class RewardConfigurationLoaderTest {

    @Test
    @DisplayName("Prueba directa de Material.matchMaterial sin servidor Bukkit")
    void testDirectMatchMaterial() {
        org.bukkit.Material diamond = org.bukkit.Material.matchMaterial("DIAMOND");
        assertNotNull(diamond, "DIAMOND debe resolverse");
        assertEquals(org.bukkit.Material.DIAMOND, diamond);
        assertNull(org.bukkit.Material.matchMaterial("FOO_BAR"), "FOO_BAR no debe existir");
    }

    @Test
    @DisplayName("Carga exitosa de configuración de recompensas válida con rewardId explícito")
    void testLoadValidRewardsYaml() throws Exception {
        String yaml = """
                portal:
                  enabled: true
                logging:
                  level: "INFO"
                  debug: false
                rewards:
                  enabled: true
                  min_participation_percent: 15.5
                  participation_pool:
                    items:
                      - id: "pool_diamond"
                        material: "DIAMOND"
                        amount: 64
                      - id: "pool_emerald"
                        material: "EMERALD"
                        amount: 32
                  slayer_reward:
                    enabled: true
                    requires_eligibility: false
                    items:
                      - id: "slayer_netherite"
                        material: "NETHERITE_INGOT"
                        amount: 3
                """;

        BetterDragonConfig config = ConfigurationLoader.load(new StringReader(yaml));
        RewardConfigurationSnapshot rewards = config.rewardConfig();

        assertNotNull(rewards);
        assertTrue(rewards.enabled());
        assertEquals(15.5, rewards.minParticipationPercent(), 0.001);
        assertEquals(2, rewards.participantRewards().size());
        assertEquals("pool_diamond", rewards.participantRewards().get(0).id());
        assertEquals("DIAMOND", rewards.participantRewards().get(0).material());
        assertEquals(64, rewards.participantRewards().get(0).amount());
        assertEquals("pool_emerald", rewards.participantRewards().get(1).id());
        assertEquals("EMERALD", rewards.participantRewards().get(1).material());
        assertEquals(32, rewards.participantRewards().get(1).amount());

        assertTrue(rewards.slayerReward().enabled());
        assertFalse(rewards.slayerReward().requiresEligibility());
        assertEquals(1, rewards.slayerReward().items().size());
        assertEquals("slayer_netherite", rewards.slayerReward().items().getFirst().id());
        assertEquals("NETHERITE_INGOT", rewards.slayerReward().items().getFirst().material());
        assertEquals(3, rewards.slayerReward().items().getFirst().amount());
    }

    @Test
    @DisplayName("Ausencia de sección 'rewards' utiliza defaults técnicos seguros (disabled, 0.0, pools vacíos)")
    void testMissingRewardsSectionUsesDefaults() throws Exception {
        String yaml = """
                portal:
                  enabled: false
                logging:
                  level: "INFO"
                  debug: false
                """;

        BetterDragonConfig config = ConfigurationLoader.load(new StringReader(yaml));
        RewardConfigurationSnapshot rewards = config.rewardConfig();

        assertNotNull(rewards);
        assertFalse(rewards.enabled(), "Rewards debe estar deshabilitado por defecto");
        assertEquals(0.0, rewards.minParticipationPercent(), 0.001, "Threshold técnico neutro debe ser 0.0%");
        assertTrue(rewards.participantRewards().isEmpty(), "Pool de participación debe estar vacío por defecto");
        assertFalse(rewards.slayerReward().enabled(), "Slayer reward debe estar deshabilitada por defecto");
        assertTrue(rewards.slayerReward().items().isEmpty(), "Ítems de Slayer deben estar vacíos por defecto");
    }

    @Test
    @DisplayName("Rechazo de min_participation_percent menor a 0.0")
    void testRejectNegativeMinParticipation() {
        String yaml = """
                rewards:
                  enabled: true
                  min_participation_percent: -10.0
                """;

        ConfigValidationException ex = assertThrows(ConfigValidationException.class, () ->
                ConfigurationLoader.load(new StringReader(yaml)));
        assertTrue(ex.getMessage().contains("min_participation_percent"));
    }

    @Test
    @DisplayName("Rechazo de min_participation_percent mayor a 100.0")
    void testRejectExceedingMinParticipation() {
        String yaml = """
                rewards:
                  enabled: true
                  min_participation_percent: 105.0
                """;

        ConfigValidationException ex = assertThrows(ConfigValidationException.class, () ->
                ConfigurationLoader.load(new StringReader(yaml)));
        assertTrue(ex.getMessage().contains("min_participation_percent"));
    }

    @Test
    @DisplayName("Rechazo de material inexistente en Minecraft")
    void testRejectInvalidMaterial() {
        String yaml = """
                rewards:
                  enabled: true
                  participation_pool:
                    items:
                      - id: "fake_reward"
                        material: "SUPER_FAKE_NON_EXISTENT_MATERIAL"
                        amount: 10
                """;

        ConfigValidationException ex = assertThrows(ConfigValidationException.class, () ->
                ConfigurationLoader.load(new StringReader(yaml)));
        assertTrue(ex.getMessage().contains("Material de Minecraft válido"));
    }

    @Test
    @DisplayName("Rechazo de cantidad no positiva")
    void testRejectNonPositiveAmount() {
        String yaml = """
                rewards:
                  enabled: true
                  participation_pool:
                    items:
                      - id: "zero_diamond"
                        material: "DIAMOND"
                        amount: 0
                """;

        ConfigValidationException ex = assertThrows(ConfigValidationException.class, () ->
                ConfigurationLoader.load(new StringReader(yaml)));
        assertTrue(ex.getMessage().contains("entero positivo mayor a 0"));
    }

    @Test
    @DisplayName("Rechazo de definición de recompensa sin 'id' obligatorio")
    void testRejectMissingRewardId() {
        String yaml = """
                rewards:
                  enabled: true
                  participation_pool:
                    items:
                      - material: "DIAMOND"
                        amount: 5
                """;

        ConfigValidationException ex = assertThrows(ConfigValidationException.class, () ->
                ConfigurationLoader.load(new StringReader(yaml)));
        assertTrue(ex.getMessage().contains("falta el campo obligatorio 'id'"));
    }

    @Test
    @DisplayName("Rechazo de definición de recompensa con 'id' vacío o en blanco")
    void testRejectEmptyRewardId() {
        String yaml = """
                rewards:
                  enabled: true
                  participation_pool:
                    items:
                      - id: "   "
                        material: "DIAMOND"
                        amount: 5
                """;

        ConfigValidationException ex = assertThrows(ConfigValidationException.class, () ->
                ConfigurationLoader.load(new StringReader(yaml)));
        assertTrue(ex.getMessage().contains("falta el campo obligatorio 'id'"));
    }

    @Test
    @DisplayName("Rechazo de definición de recompensa con caracteres inválidos en 'id' (ej. dos puntos o espacios)")
    void testRejectInvalidCharactersInRewardId() {
        String yaml = """
                rewards:
                  enabled: true
                  participation_pool:
                    items:
                      - id: "part:diamond"
                        material: "DIAMOND"
                        amount: 5
                """;

        ConfigValidationException ex = assertThrows(ConfigValidationException.class, () ->
                ConfigurationLoader.load(new StringReader(yaml)));
        assertTrue(ex.getMessage().contains("'id' inválido"));
    }

    @Test
    @DisplayName("Rechazo de definiciones de recompensa con 'id' duplicado dentro del pool")
    void testRejectDuplicateRewardIdWithinPool() {
        String yaml = """
                rewards:
                  enabled: true
                  participation_pool:
                    items:
                      - id: "my_diamond"
                        material: "DIAMOND"
                        amount: 5
                      - id: "my_diamond"
                        material: "DIAMOND"
                        amount: 10
                """;

        ConfigValidationException ex = assertThrows(ConfigValidationException.class, () ->
                ConfigurationLoader.load(new StringReader(yaml)));
        assertTrue(ex.getMessage().contains("duplicado"));
    }

    @Test
    @DisplayName("Rechazo de 'id' duplicado entre pool de participación y slayer_reward")
    void testRejectDuplicateRewardIdBetweenPoolAndSlayer() {
        String yaml = """
                rewards:
                  enabled: true
                  participation_pool:
                    items:
                      - id: "shared_reward_id"
                        material: "DIAMOND"
                        amount: 5
                  slayer_reward:
                    enabled: true
                    items:
                      - id: "shared_reward_id"
                        material: "NETHERITE_INGOT"
                        amount: 1
                """;

        ConfigValidationException ex = assertThrows(ConfigValidationException.class, () ->
                ConfigurationLoader.load(new StringReader(yaml)));
        assertTrue(ex.getMessage().contains("duplicado"));
    }

    @Test
    @DisplayName("Rechazo de cantidad negativa en amount (ej. -1)")
    void testRejectNegativeAmount() {
        String yaml = """
                rewards:
                  enabled: true
                  participation_pool:
                    items:
                      - id: "neg_reward"
                        material: "DIAMOND"
                        amount: -1
                """;

        ConfigValidationException ex = assertThrows(ConfigValidationException.class, () ->
                ConfigurationLoader.load(new StringReader(yaml)));
        assertTrue(ex.getMessage().contains("amount"));
        assertTrue(ex.getMessage().contains("neg_reward"));
        assertTrue(ex.getMessage().contains("entero positivo mayor a 0"));
    }

    @Test
    @DisplayName("Rechazo de cantidad fraccionaria 1.7 (no debe truncarse silenciosamente a 1)")
    void testRejectFractionalAmount_1_7() {
        String yaml = """
                rewards:
                  enabled: true
                  participation_pool:
                    items:
                      - id: "frac_reward_1"
                        material: "DIAMOND"
                        amount: 1.7
                """;

        ConfigValidationException ex = assertThrows(ConfigValidationException.class, () ->
                ConfigurationLoader.load(new StringReader(yaml)));
        assertTrue(ex.getMessage().contains("amount"));
        assertTrue(ex.getMessage().contains("frac_reward_1"));
        assertTrue(ex.getMessage().contains("entero positivo mayor a 0"));
    }

    @Test
    @DisplayName("Rechazo de cantidad fraccionaria 1.5 y 2.5")
    void testRejectFractionalAmount_1_5_and_2_5() {
        String yaml = """
                rewards:
                  enabled: true
                  participation_pool:
                    items:
                      - id: "frac_reward_2"
                        material: "DIAMOND"
                        amount: 1.5
                """;

        ConfigValidationException ex1 = assertThrows(ConfigValidationException.class, () ->
                ConfigurationLoader.load(new StringReader(yaml)));
        assertTrue(ex1.getMessage().contains("frac_reward_2"));

        String yaml2 = """
                rewards:
                  enabled: true
                  participation_pool:
                    items:
                      - id: "frac_reward_3"
                        material: "EMERALD"
                        amount: 2.5
                """;

        ConfigValidationException ex2 = assertThrows(ConfigValidationException.class, () ->
                ConfigurationLoader.load(new StringReader(yaml2)));
        assertTrue(ex2.getMessage().contains("frac_reward_3"));
    }

    @Test
    @DisplayName("Aceptación de valor numérico con representación decimal que equivale exactamente a un entero (2.0)")
    void testAcceptExactIntegerDoubleAmount_2_0() throws Exception {
        String yaml = """
                rewards:
                  enabled: true
                  participation_pool:
                    items:
                      - id: "exact_double_reward"
                        material: "DIAMOND"
                        amount: 2.0
                """;

        BetterDragonConfig config = ConfigurationLoader.load(new StringReader(yaml));
        RewardItemDefinition def = config.rewardConfig().participantRewards().getFirst();
        assertEquals("exact_double_reward", def.id());
        assertEquals(2, def.amount(), "2.0 debe resolverse como exactamente el entero positivo 2");
    }

    @Test
    @DisplayName("Rechazo de desbordamiento de entero en amount (overflow)")
    void testRejectOverflowAmount() {
        String yaml = """
                rewards:
                  enabled: true
                  participation_pool:
                    items:
                      - id: "overflow_reward"
                        material: "DIAMOND"
                        amount: 3000000000
                """;

        ConfigValidationException ex = assertThrows(ConfigValidationException.class, () ->
                ConfigurationLoader.load(new StringReader(yaml)));
        assertTrue(ex.getMessage().contains("amount"));
        assertTrue(ex.getMessage().contains("overflow_reward"));
    }

    @Test
    @DisplayName("El mensaje de error de amount identifica claramente el rewardId y el campo amount")
    void testErrorMessageIdentifiesRewardIdAndAmount() {
        String yaml = """
                rewards:
                  enabled: true
                  participation_pool:
                    items:
                      - id: "special_netherite_token"
                        material: "NETHERITE_INGOT"
                        amount: 0
                """;

        ConfigValidationException ex = assertThrows(ConfigValidationException.class, () ->
                ConfigurationLoader.load(new StringReader(yaml)));
        String msg = ex.getMessage();
        assertTrue(msg.contains("'amount'"), "Debe mencionar el campo 'amount'");
        assertTrue(msg.contains("special_netherite_token"), "Debe identificar el rewardId");
    }

    @Test
    @DisplayName("Rechazo de material con formato plausible pero inexistente (FOO_BAR, FAKE_MATERIAL, INVALID_MATERIAL)")
    void testRejectPlausibleFormatNonExistentMaterials() {
        // FOO_BAR tiene formato alfanumérico correcto pero no existe en Bukkit
        String yaml1 = """
                rewards:
                  enabled: true
                  participation_pool:
                    items:
                      - id: "foo_reward"
                        material: "FOO_BAR"
                        amount: 1
                """;

        ConfigValidationException ex1 = assertThrows(ConfigValidationException.class, () ->
                ConfigurationLoader.load(new StringReader(yaml1)));
        assertTrue(ex1.getMessage().contains("Material de Minecraft válido"));

        // FAKE_MATERIAL
        String yaml2 = """
                rewards:
                  enabled: true
                  participation_pool:
                    items:
                      - id: "fake_mat_reward"
                        material: "FAKE_MATERIAL"
                        amount: 1
                """;

        ConfigValidationException ex2 = assertThrows(ConfigValidationException.class, () ->
                ConfigurationLoader.load(new StringReader(yaml2)));
        assertTrue(ex2.getMessage().contains("Material de Minecraft válido"));

        // INVALID_MATERIAL
        String yaml3 = """
                rewards:
                  enabled: true
                  participation_pool:
                    items:
                      - id: "invalid_mat_reward"
                        material: "INVALID_MATERIAL"
                        amount: 1
                """;

        ConfigValidationException ex3 = assertThrows(ConfigValidationException.class, () ->
                ConfigurationLoader.load(new StringReader(yaml3)));
        assertTrue(ex3.getMessage().contains("Material de Minecraft válido"));
    }

    @Test
    @DisplayName("Rechazo de AIR y CAVE_AIR como materiales de recompensa")
    void testRejectAirMaterials() {
        String yamlAir = """
                rewards:
                  enabled: true
                  participation_pool:
                    items:
                      - id: "air_reward"
                        material: "AIR"
                        amount: 1
                """;

        ConfigValidationException exAir = assertThrows(ConfigValidationException.class, () ->
                ConfigurationLoader.load(new StringReader(yamlAir)));
        assertTrue(exAir.getMessage().contains("no puede ser AIR"));

        String yamlCaveAir = """
                rewards:
                  enabled: true
                  participation_pool:
                    items:
                      - id: "cave_air_reward"
                        material: "CAVE_AIR"
                        amount: 1
                """;

        ConfigValidationException exCaveAir = assertThrows(ConfigValidationException.class, () ->
                ConfigurationLoader.load(new StringReader(yamlCaveAir)));
        assertTrue(exCaveAir.getMessage().contains("no puede ser AIR"));
    }

    @Test
    @DisplayName("Aceptación de múltiples materiales reales válidos de Minecraft")
    void testAcceptRealMinecraftMaterials() throws Exception {
        String yaml = """
                rewards:
                  enabled: true
                  participation_pool:
                    items:
                      - id: "sword_reward"
                        material: "NETHERITE_SWORD"
                        amount: 1
                      - id: "gapple_reward"
                        material: "GOLDEN_APPLE"
                        amount: 5
                      - id: "pearl_reward"
                        material: "ENDER_PEARL"
                        amount: 16
                """;

        BetterDragonConfig config = ConfigurationLoader.load(new StringReader(yaml));
        assertEquals(3, config.rewardConfig().participantRewards().size());
        assertEquals("NETHERITE_SWORD", config.rewardConfig().participantRewards().get(0).material());
        assertEquals("GOLDEN_APPLE", config.rewardConfig().participantRewards().get(1).material());
        assertEquals("ENDER_PEARL", config.rewardConfig().participantRewards().get(2).material());
    }
}
