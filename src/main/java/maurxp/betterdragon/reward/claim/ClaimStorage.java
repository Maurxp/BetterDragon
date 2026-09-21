package maurxp.betterdragon.reward.claim;

import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.reward.model.ClaimStatus;
import maurxp.betterdragon.reward.model.RewardClaim;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Abstracción de almacenamiento para reclamos de recompensas.
 * <p>
 * Proporciona un límite arquitectónico desacoplado para persistir y consultar
 * el estado de recompensas adjudicadas, permitiendo implementaciones
 * en memoria (Fase 3.8) o en base de datos SQLite persistente (Fase 3.9).
 *
 * @author maurxp
 */
public interface ClaimStorage {

    /**
     * Guarda o actualiza un reclamo en el almacenamiento.
     *
     * @param claim reclamo a almacenar
     */
    void save(RewardClaim claim);

    /**
     * Guarda o actualiza múltiples reclamos en una operación en lote.
     *
     * @param claims colección de reclamos
     */
    void saveAll(Collection<RewardClaim> claims);

    /**
     * Busca un reclamo por su identificador único.
     *
     * @param claimId UUID del reclamo
     * @return Optional con el reclamo si existe
     */
    Optional<RewardClaim> findById(UUID claimId);

    /**
     * Busca un reclamo por su clave de idempotencia única.
     *
     * @param idempotencyKey clave única (battleId:participantId:rewardId)
     * @return Optional con el reclamo si existe
     */
    Optional<RewardClaim> findByIdempotencyKey(String idempotencyKey);

    /**
     * Obtiene todos los reclamos asociados a un jugador.
     *
     * @param playerId UUID del jugador
     * @return lista inmutable de reclamos del jugador
     */
    List<RewardClaim> findByPlayer(UUID playerId);

    /**
     * Obtiene los reclamos pendientes de entrega o reintentables para un jugador.
     *
     * @param playerId UUID del jugador
     * @return lista inmutable de reclamos en estado PENDING o FAILED_RETRYABLE
     */
    List<RewardClaim> findPendingByPlayer(UUID playerId);

    /**
     * Obtiene todos los reclamos asociados a una batalla.
     *
     * @param battleId ID de la batalla
     * @return lista inmutable de reclamos de la batalla
     */
    List<RewardClaim> findByBattleId(BattleId battleId);

    /**
     * Obtiene todos los reclamos que coincidan con el estado solicitado.
     *
     * @param status estado de reclamo
     * @return lista inmutable de reclamos con dicho estado
     */
    List<RewardClaim> findByStatus(ClaimStatus status);

    /**
     * Retorna la cantidad total de reclamos almacenados.
     *
     * @return conteo total
     */
    int count();

    /**
     * Limpia todos los reclamos almacenados.
     */
    void clear();
}
