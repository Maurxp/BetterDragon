package maurxp.betterdragon.reward.claim;

import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.reward.model.ClaimStatus;
import maurxp.betterdragon.reward.model.RewardClaim;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Abstracción de almacenamiento asíncrono para reclamos de recompensas.
 * <p>
 * Proporciona un límite arquitectónico desacoplado para persistir y consultar
 * el estado de recompensas adjudicadas sin bloquear el hilo principal de Bukkit,
 * permitiendo implementaciones en memoria (pruebas unitarias) o en SQLite duradero (producción).
 *
 * @author maurxp
 */
public interface ClaimStorage extends AutoCloseable {

    /**
     * Crea un reclamo de forma atómica únicamente si no existe previamente por su clave de idempotencia.
     * <p>
     * Si el reclamo ya existía (por ejemplo, en estado {@code CLAIMED}, {@code PENDING} o {@code FAILED_RETRYABLE}),
     * la operación NO lo sobrescribe y retorna la instancia actualmente persistida intacta.
     *
     * @param claim reclamo a insertar si está ausente
     * @return CompletableFuture con el reclamo almacenado (nuevo o preexistente)
     */
    CompletableFuture<RewardClaim> createIfAbsent(RewardClaim claim);

    /**
     * Actualiza un reclamo existente de forma explícita y protegida.
     * <p>
     * <b>Garantía de Integridad:</b> Un reclamo que ya se encuentra en estado {@code CLAIMED}
     * nunca puede ser degradado a {@code PENDING} ni sobrescrito por un estado anterior.
     *
     * @param claim reclamo con los datos a actualizar
     * @return CompletableFuture con {@code true} si se actualizó una fila existente, o {@code false} si no existía o fue protegido
     */
    CompletableFuture<Boolean> updateExisting(RewardClaim claim);

    /**
     * Guarda o actualiza un reclamo en el almacenamiento de forma asíncrona.
     *
     * @param claim reclamo a almacenar
     * @return CompletableFuture completado al persistir
     */
    CompletableFuture<Void> save(RewardClaim claim);

    /**
     * Guarda o actualiza múltiples reclamos en una operación en lote de forma asíncrona.
     *
     * @param claims colección de reclamos
     * @return CompletableFuture completado al persistir el lote
     */
    CompletableFuture<Void> saveAll(Collection<RewardClaim> claims);

    /**
     * Busca un reclamo por su identificador único de forma asíncrona.
     *
     * @param claimId UUID del reclamo
     * @return CompletableFuture con Optional del reclamo si existe
     */
    CompletableFuture<Optional<RewardClaim>> findById(UUID claimId);

    /**
     * Busca un reclamo por su clave de idempotencia única (battleId:participantId:rewardId) de forma asíncrona.
     *
     * @param idempotencyKey clave única de deduplicación
     * @return CompletableFuture con Optional del reclamo si existe
     */
    CompletableFuture<Optional<RewardClaim>> findByIdempotencyKey(String idempotencyKey);

    /**
     * Obtiene todos los reclamos asociados a un jugador de forma asíncrona.
     *
     * @param playerId UUID del jugador
     * @return CompletableFuture con la lista inmutable de reclamos del jugador
     */
    CompletableFuture<List<RewardClaim>> findByPlayer(UUID playerId);

    /**
     * Obtiene los reclamos pendientes de entrega o reintentables para un jugador de forma asíncrona.
     *
     * @param playerId UUID del jugador
     * @return CompletableFuture con la lista inmutable de reclamos en estado PENDING o FAILED_RETRYABLE
     */
    CompletableFuture<List<RewardClaim>> findPendingByPlayer(UUID playerId);

    /**
     * Obtiene todos los reclamos asociados a una batalla de forma asíncrona.
     *
     * @param battleId ID de la batalla
     * @return CompletableFuture con la lista inmutable de reclamos de la batalla
     */
    CompletableFuture<List<RewardClaim>> findByBattleId(BattleId battleId);

    /**
     * Obtiene todos los reclamos que coincidan con el estado solicitado de forma asíncrona.
     *
     * @param status estado de reclamo
     * @return CompletableFuture con la lista inmutable de reclamos con dicho estado
     */
    CompletableFuture<List<RewardClaim>> findByStatus(ClaimStatus status);

    /**
     * Retorna la cantidad total de reclamos almacenados de forma asíncrona.
     *
     * @return CompletableFuture con el conteo total
     */
    CompletableFuture<Integer> count();

    /**
     * Limpia todos los reclamos almacenados de forma asíncrona.
     *
     * @return CompletableFuture completado al limpiar
     */
    CompletableFuture<Void> clear();

    @Override
    default void close() {}
}
