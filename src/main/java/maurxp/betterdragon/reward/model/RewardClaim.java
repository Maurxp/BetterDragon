package maurxp.betterdragon.reward.model;

import maurxp.betterdragon.battle.model.BattleId;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Representación inmutable de un reclamo de recompensa en el buzón.
 * <p>
 * Diseñado para garantizar entrega confiable sin pérdida de ítems ante jugadores
 * desconectados o inventarios saturados.
 *
 * @param claimId         identificador único del reclamo
 * @param idempotencyKey  clave compuesta de deduplicación (battleId:participantId:rewardId)
 * @param battleId        identificador de la batalla de origen
 * @param playerId        UUID del jugador beneficiario
 * @param playerName      nombre del jugador
 * @param source          origen de la recompensa
 * @param item            ítem asignado
 * @param originalAmount  cantidad total adjudicada inicialmente
 * @param deliveredAmount cantidad efectivamente depositada en el inventario
 * @param status          estado actual del reclamo
 * @param createdAt       fecha y hora de creación
 * @param claimedAt       fecha y hora de entrega completa (si aplica)
 * @param failureReason   motivo de error si está en FAILED_RETRYABLE
 * @author maurxp
 */
public record RewardClaim(
        UUID claimId,
        String idempotencyKey,
        BattleId battleId,
        UUID playerId,
        String playerName,
        RewardSource source,
        RewardItem item,
        int originalAmount,
        int deliveredAmount,
        ClaimStatus status,
        Instant createdAt,
        Instant claimedAt,
        String failureReason
) implements Serializable {

    public RewardClaim {
        Objects.requireNonNull(claimId, "claimId no puede ser nulo");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey no puede ser nulo");
        Objects.requireNonNull(battleId, "battleId no puede ser nulo");
        Objects.requireNonNull(playerId, "playerId no puede ser nulo");
        Objects.requireNonNull(playerName, "playerName no puede ser nulo");
        Objects.requireNonNull(source, "source no puede ser nulo");
        Objects.requireNonNull(item, "item no puede ser nulo");
        Objects.requireNonNull(status, "status no puede ser nulo");
        Objects.requireNonNull(createdAt, "createdAt no puede ser nulo");

        if (originalAmount <= 0) {
            throw new IllegalArgumentException("originalAmount debe ser positivo: " + originalAmount);
        }
        if (deliveredAmount < 0 || deliveredAmount > originalAmount) {
            throw new IllegalArgumentException("deliveredAmount (" + deliveredAmount + ") debe estar entre 0 y " + originalAmount);
        }
    }

    /**
     * Construye un reclamo inicial nuevo en estado PENDING.
     */
    public static RewardClaim createPending(
            String idempotencyKey,
            BattleId battleId,
            UUID playerId,
            String playerName,
            RewardSource source,
            RewardItem item
    ) {
        return new RewardClaim(
                UUID.randomUUID(),
                idempotencyKey,
                battleId,
                playerId,
                playerName,
                source,
                item,
                item.amount(),
                0,
                ClaimStatus.PENDING,
                Instant.now(),
                null,
                null
        );
    }

    /**
     * Cantidad de ítems pendientes por entregar.
     *
     * @return remanente pendiente
     */
    public int getRemainingAmount() {
        return originalAmount - deliveredAmount;
    }

    /**
     * Retorna una copia actualizada tras una entrega (parcial o total).
     *
     * @param addedAmount cantidad de ítems recién añadidos al inventario
     * @param when        instante de la entrega
     * @return reclamo actualizado
     */
    public RewardClaim withDelivery(int addedAmount, Instant when) {
        if (addedAmount <= 0) {
            return this;
        }
        int newDelivered = this.deliveredAmount + addedAmount;
        if (newDelivered > this.originalAmount) {
            throw new IllegalArgumentException("newDelivered (" + newDelivered + ") excede originalAmount (" + this.originalAmount + ")");
        }
        ClaimStatus newStatus = newDelivered == this.originalAmount ? ClaimStatus.CLAIMED : ClaimStatus.PENDING;
        Instant newClaimedAt = newStatus == ClaimStatus.CLAIMED ? when : this.claimedAt;
        return new RewardClaim(
                this.claimId,
                this.idempotencyKey,
                this.battleId,
                this.playerId,
                this.playerName,
                this.source,
                this.item,
                this.originalAmount,
                newDelivered,
                newStatus,
                this.createdAt,
                newClaimedAt,
                null
        );
    }

    /**
     * Retorna una copia con fallo transitorio reintentable.
     *
     * @param reason descripción del error
     * @return reclamo en estado FAILED_RETRYABLE
     */
    public RewardClaim withFailure(String reason) {
        return new RewardClaim(
                this.claimId,
                this.idempotencyKey,
                this.battleId,
                this.playerId,
                this.playerName,
                this.source,
                this.item,
                this.originalAmount,
                this.deliveredAmount,
                ClaimStatus.FAILED_RETRYABLE,
                this.createdAt,
                this.claimedAt,
                reason
        );
    }

    public Optional<Instant> getClaimedAt() {
        return Optional.ofNullable(claimedAt);
    }

    public Optional<String> getFailureReason() {
        return Optional.ofNullable(failureReason);
    }
}
