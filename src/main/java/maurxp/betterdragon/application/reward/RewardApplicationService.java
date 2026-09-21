package maurxp.betterdragon.application.reward;

import maurxp.betterdragon.reward.claim.ClaimStorage;
import maurxp.betterdragon.reward.model.RewardClaim;
import maurxp.betterdragon.reward.service.RewardService;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Servicio de aplicación para la consulta y reclamo de recompensas pendientes.
 * <p>
 * Sirve como punto de acceso único para que comandos (`/bd claim`) y futuras GUIs
 * consulten o reclamen ítems resguardados en el buzón duradero de SQLite.
 *
 * @author maurxp
 */
public class RewardApplicationService {

    private final RewardService rewardService;
    private final ClaimStorage claimStorage;

    public RewardApplicationService(RewardService rewardService, ClaimStorage claimStorage) {
        this.rewardService = Objects.requireNonNull(rewardService, "rewardService no puede ser nulo");
        this.claimStorage = Objects.requireNonNull(claimStorage, "claimStorage no puede ser nulo");
    }

    /**
     * Consulta de forma asíncrona todos los reclamos pendientes o reintentables de un jugador.
     *
     * @param playerId UUID del jugador
     * @return futuro con la lista inmutable de reclamos pendientes
     */
    public CompletableFuture<List<RewardClaim>> getPendingClaims(UUID playerId) {
        if (playerId == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        return claimStorage.findPendingByPlayer(playerId);
    }

    /**
     * Solicita la entrega asíncrona de los reclamos pendientes al inventario físico del jugador.
     * <p>
     * La entrega física al inventario ocurre en el hilo principal de Bukkit coordinada por
     * {@link RewardService}, garantizando idempotencia y cero pérdidas.
     *
     * @param playerId UUID del jugador
     * @return futuro con la cantidad total de ítems efectivamente entregados
     */
    public CompletableFuture<Integer> claimPendingRewards(UUID playerId) {
        if (playerId == null) {
            return CompletableFuture.completedFuture(0);
        }
        return rewardService.retryPendingClaims(playerId);
    }
}
