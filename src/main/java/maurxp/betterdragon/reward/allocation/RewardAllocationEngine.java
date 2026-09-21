package maurxp.betterdragon.reward.allocation;

import maurxp.betterdragon.battle.model.BattleResult;
import maurxp.betterdragon.combat.CombatSnapshot;
import maurxp.betterdragon.combat.ParticipantSnapshot;
import maurxp.betterdragon.config.RewardConfigurationSnapshot;
import maurxp.betterdragon.config.RewardItemDefinition;
import maurxp.betterdragon.config.SlayerRewardDefinition;
import maurxp.betterdragon.reward.model.RewardAllocation;
import maurxp.betterdragon.reward.model.RewardAllocationPlan;
import maurxp.betterdragon.reward.model.RewardItem;
import maurxp.betterdragon.reward.model.RewardSource;

import java.time.Instant;
import java.util.*;

/**
 * Motor puro y determinista para el cálculo y asignación de recompensas tras la victoria.
 * <p>
 * Principios:
 * <ul>
 *   <li><b>0% Bukkit / 0% Player:</b> Completamente desacoplado de la API de Minecraft
 *       y de entidades vivas.</li>
 *   <li><b>Determinismo Matemático:</b> Mismos inputs garantizan idéntico plan de asignación.</li>
 *   <li><b>Redistribución Proporcional:</b> El remanente de los participantes no elegibles
 *       se redistribuye proporcionalmente a los elegibles según su daño relativo.</li>
 *   <li><b>Conservación de Ítems y Redondeo Estable:</b> Las fracciones decimales no se descartan;
 *       los ítems sobrantes por redondeo entero se asignan a quienes tengan mayor fracción,
 *       desempatando por menor {@code firstHitSequence}.</li>
 * </ul>
 *
 * @author maurxp
 */
public class RewardAllocationEngine {

    /**
     * Calcula el plan completo de asignación de recompensas a partir del resultado de la batalla
     * y de la instantánea congelada de configuración.
     *
     * @param result resultado final inmutable de la batalla
     * @param config snapshot inmutable de configuración de recompensas
     * @return plan inmutable de asignación
     */
    public RewardAllocationPlan calculateAllocation(BattleResult result, RewardConfigurationSnapshot config) {
        return calculatePlan(result, config);
    }

    /**
     * Calcula el plan completo de asignación de recompensas a partir del resultado de la batalla
     * y de la instantánea congelada de configuración.
     *
     * @param result resultado final inmutable de la batalla
     * @param config snapshot inmutable de configuración de recompensas
     * @return plan inmutable de asignación
     */
    public RewardAllocationPlan calculatePlan(BattleResult result, RewardConfigurationSnapshot config) {
        Objects.requireNonNull(result, "result no puede ser nulo");
        Objects.requireNonNull(config, "config no puede ser nulo");

        if (!config.enabled() || !result.isVictory()) {
            return RewardAllocationPlan.empty(result.battleId());
        }

        Optional<CombatSnapshot> snapshotOpt = result.getCombatSnapshot();
        if (snapshotOpt.isEmpty()) {
            return RewardAllocationPlan.empty(result.battleId());
        }

        CombatSnapshot snapshot = snapshotOpt.get();
        double totalBattleDamage = snapshot.totalDamage();
        List<ParticipantSnapshot> participants = snapshot.participants();

        if (participants.isEmpty() || Double.isNaN(totalBattleDamage) || Double.isInfinite(totalBattleDamage) || totalBattleDamage <= 0.0) {
            return RewardAllocationPlan.empty(result.battleId());
        }

        double minPercent = config.minParticipationPercent();
        List<ParticipantSnapshot> eligible = new ArrayList<>();
        List<UUID> eligibleUuids = new ArrayList<>();
        List<UUID> ineligibleUuids = new ArrayList<>();
        double eligibleDamage = 0.0;

        for (ParticipantSnapshot p : participants) {
            double damage = p.totalDamage();
            if (Double.isNaN(damage) || Double.isInfinite(damage) || damage <= 0.0) {
                ineligibleUuids.add(p.playerId());
                continue;
            }
            double pct = (damage / totalBattleDamage) * 100.0;
            if (pct >= minPercent) {
                eligible.add(p);
                eligibleUuids.add(p.playerId());
                eligibleDamage += damage;
            } else {
                ineligibleUuids.add(p.playerId());
            }
        }

        Instant now = Instant.now();

        if (eligible.isEmpty() || eligibleDamage <= 0.0) {
            return new RewardAllocationPlan(
                    result.battleId(),
                    List.of(),
                    eligibleUuids,
                    ineligibleUuids,
                    totalBattleDamage,
                    0.0,
                    now
            );
        }

        List<RewardAllocation> allocations = new ArrayList<>();

        // 1. Asignación del pool de participación con redistribución proporcional
        for (RewardItemDefinition poolItem : config.participationPool()) {
            int totalUnits = poolItem.amount();
            if (totalUnits <= 0) {
                continue;
            }

            // Estructura auxiliar para calcular enteros y fracciones por participante
            List<ParticipantShare> shares = new ArrayList<>(eligible.size());
            int sumBase = 0;

            for (ParticipantSnapshot p : eligible) {
                double relativeWeight = p.totalDamage() / eligibleDamage;
                double exactUnits = relativeWeight * totalUnits;
                int baseUnits = (int) Math.floor(exactUnits);
                double fraction = exactUnits - baseUnits;

                shares.add(new ParticipantShare(p, baseUnits, fraction));
                sumBase += baseUnits;
            }

            int remainderUnits = totalUnits - sumBase;
            if (remainderUnits > 0) {
                // Ordenar para repartir el remanente:
                // 1. Mayor fracción decimal DESC
                // 2. Menor firstHitSequence ASC (golpeó primero)
                // 3. UUID lexicográfico ASC (estabilidad estricta)
                shares.sort(Comparator
                        .comparingDouble(ParticipantShare::fraction).reversed()
                        .thenComparingLong(s -> s.participant().firstHitSequence())
                        .thenComparing(s -> s.participant().playerId().toString())
                );

                for (int i = 0; i < remainderUnits && i < shares.size(); i++) {
                    shares.get(i).incrementBonus();
                }
            }

            // Generar las cuotas asignadas
            for (ParticipantShare share : shares) {
                int finalAmount = share.totalAmount();
                if (finalAmount > 0) {
                    ParticipantSnapshot p = share.participant();
                    double participantPct = (p.totalDamage() / totalBattleDamage) * 100.0;
                    RewardItem item = new RewardItem(poolItem.material(), finalAmount);
                    allocations.add(new RewardAllocation(
                            result.battleId(),
                            p.playerId(),
                            p.lastKnownName(),
                            RewardSource.PARTICIPATION,
                            poolItem.id(),
                            item,
                            participantPct,
                            now
                    ));
                }
            }
        }

        // 2. Asignación de la recompensa de Slayer (TOP_DAMAGE)
        SlayerRewardDefinition slayerDef = config.slayerReward();
        if (slayerDef.enabled() && !slayerDef.items().isEmpty()) {
            Optional<UUID> slayerIdOpt = result.getSlayerUniqueId();
            if (slayerIdOpt.isPresent()) {
                UUID slayerId = slayerIdOpt.get();
                boolean isEligible = eligibleUuids.contains(slayerId);

                if (!slayerDef.requiresEligibility() || isEligible) {
                    String slayerName = result.getSlayerLastKnownName().orElse("Slayer");
                    double slayerPct = 0.0;
                    for (ParticipantSnapshot p : participants) {
                        if (p.playerId().equals(slayerId)) {
                            slayerPct = (p.totalDamage() / totalBattleDamage) * 100.0;
                            break;
                        }
                    }

                    for (RewardItemDefinition sItem : slayerDef.items()) {
                        RewardItem item = new RewardItem(sItem.material(), sItem.amount());
                        allocations.add(new RewardAllocation(
                                result.battleId(),
                                slayerId,
                                slayerName,
                                RewardSource.SLAYER,
                                sItem.id(),
                                item,
                                slayerPct,
                                now
                        ));
                    }
                }
            }
        }

        return new RewardAllocationPlan(
                result.battleId(),
                allocations,
                eligibleUuids,
                ineligibleUuids,
                totalBattleDamage,
                eligibleDamage,
                now
        );
    }

    private static class ParticipantShare {
        private final ParticipantSnapshot participant;
        private final int baseUnits;
        private final double fraction;
        private int bonusUnits;

        public ParticipantShare(ParticipantSnapshot participant, int baseUnits, double fraction) {
            this.participant = participant;
            this.baseUnits = baseUnits;
            this.fraction = fraction;
            this.bonusUnits = 0;
        }

        public ParticipantSnapshot participant() {
            return participant;
        }

        public double fraction() {
            return fraction;
        }

        public void incrementBonus() {
            this.bonusUnits++;
        }

        public int totalAmount() {
            return baseUnits + bonusUnits;
        }
    }
}
