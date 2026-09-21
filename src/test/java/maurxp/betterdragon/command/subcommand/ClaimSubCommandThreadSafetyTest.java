package maurxp.betterdragon.command.subcommand;

import maurxp.betterdragon.application.permission.CommandPermission;
import maurxp.betterdragon.application.reward.RewardApplicationService;
import maurxp.betterdragon.battle.BattleSessionManager;
import maurxp.betterdragon.command.AllowedSender;
import maurxp.betterdragon.command.CommandContext;
import maurxp.betterdragon.config.ArenaConfigurationSnapshot;
import maurxp.betterdragon.config.BetterDragonConfig;
import maurxp.betterdragon.config.ConfigurationService;
import maurxp.betterdragon.reward.allocation.RewardAllocationEngine;
import maurxp.betterdragon.reward.claim.InMemoryClaimStorage;
import maurxp.betterdragon.reward.delivery.PlayerInventoryAdapter;
import maurxp.betterdragon.reward.delivery.RewardDeliveryService;
import maurxp.betterdragon.reward.model.RewardItem;
import maurxp.betterdragon.reward.service.RewardService;
import maurxp.betterdragon.util.MainThreadDispatcher;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Pruebas Unitarias de Seguridad de Hilos para ClaimSubCommand (Fase 3.11-R1)")
class ClaimSubCommandThreadSafetyTest {

    private ExecutorService persistenceExecutor;
    private ExecutorService mainThreadSimulator;
    private Thread mainThread;
    private Thread persistenceThread;
    private Logger testLogger;

    @BeforeEach
    void setUp() throws Exception {
        testLogger = Logger.getLogger("ClaimSubCommandThreadSafetyTest");

        CountDownLatch persistenceLatch = new CountDownLatch(1);
        persistenceExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "sqlite-persistence-worker");
            return t;
        });
        persistenceExecutor.submit(() -> {
            persistenceThread = Thread.currentThread();
            persistenceLatch.countDown();
        });
        assertTrue(persistenceLatch.await(2, TimeUnit.SECONDS), "El hilo de persistencia debe inicializarse");

        CountDownLatch mainLatch = new CountDownLatch(1);
        mainThreadSimulator = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "paper-server-main-thread");
            return t;
        });
        mainThreadSimulator.submit(() -> {
            mainThread = Thread.currentThread();
            mainLatch.countDown();
        });
        assertTrue(mainLatch.await(2, TimeUnit.SECONDS), "El hilo principal simulado debe inicializarse");
    }

    @AfterEach
    void tearDown() {
        if (persistenceExecutor != null) persistenceExecutor.shutdownNow();
        if (mainThreadSimulator != null) mainThreadSimulator.shutdownNow();
    }

    private RewardService createRealRewardService() {
        InMemoryClaimStorage storage = new InMemoryClaimStorage();
        PlayerInventoryAdapter adapter = new PlayerInventoryAdapter() {
            @Override public int deliverItem(UUID playerId, RewardItem item) { return item != null ? item.amount() : 0; }
            @Override public boolean isPlayerOnline(UUID playerId) { return true; }
        };
        RewardDeliveryService delivery = new RewardDeliveryService(adapter, storage, Runnable::run, testLogger);
        ConfigurationService configService = new ConfigurationService(testLogger, BetterDragonConfig.defaults(), ArenaConfigurationSnapshot.defaults());
        return new RewardService(
                new BattleSessionManager(),
                configService,
                new RewardAllocationEngine(),
                delivery,
                storage,
                e -> {},
                testLogger
        );
    }

    private Player createFakePlayer(UUID uuid, List<String> messageLog, List<Thread> messageThreads) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("getUniqueId")) return uuid;
                    if (name.equals("getName")) return "DragonWarrior";
                    if (name.equals("sendMessage")) {
                        if (args != null && args.length > 0 && args[0] != null) {
                            messageLog.add(args[0].toString());
                            messageThreads.add(Thread.currentThread());
                        }
                        return null;
                    }
                    if (name.equals("hasPermission")) return true;
                    if (name.equals("isOp")) return true;
                    if (name.equals("equals")) return proxy == args[0];
                    if (name.equals("hashCode")) return uuid.hashCode();
                    return null;
                }
        );
    }

    @Test
    @DisplayName("G1: Claim exitoso despacha la respuesta final estrictamente en el hilo principal")
    void testClaimSuccessDispatchedOnMainThread() throws Exception {
        UUID playerId = UUID.randomUUID();
        List<String> messages = new CopyOnWriteArrayList<>();
        List<Thread> messageThreads = new CopyOnWriteArrayList<>();
        Player player = createFakePlayer(playerId, messages, messageThreads);

        CountDownLatch completionLatch = new CountDownLatch(1);
        AtomicReference<Thread> dispatcherCalledOnThread = new AtomicReference<>();

        MainThreadDispatcher dispatcher = runnable -> {
            dispatcherCalledOnThread.set(Thread.currentThread());
            mainThreadSimulator.submit(() -> {
                try {
                    runnable.run();
                } finally {
                    completionLatch.countDown();
                }
            });
        };

        RewardApplicationService mockRewardService = new RewardApplicationService(
                createRealRewardService(),
                new InMemoryClaimStorage()
        ) {
            @Override
            public CompletableFuture<Integer> claimPendingRewards(UUID targetId) {
                CompletableFuture<Integer> future = new CompletableFuture<>();
                persistenceExecutor.submit(() -> {
                    try {
                        Thread.sleep(20);
                        future.complete(3); // 3 ítems entregados
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                });
                return future;
            }
        };

        ClaimSubCommand claimSubCommand = new ClaimSubCommand(mockRewardService, dispatcher);
        CommandContext context = new CommandContext(player, "bd", new String[]{"claim"}, "claim", new String[0]);

        claimSubCommand.execute(context);

        assertTrue(completionLatch.await(3, TimeUnit.SECONDS), "El callback en main thread debe ejecutarse dentro del tiempo límite");

        // 1. Verificar mensaje inicial (enviado en el hilo de invocación del comando)
        assertTrue(messages.size() >= 2, "Debe haber al menos mensaje inicial y mensaje final");
        assertTrue(messages.getFirst().contains("Comprobando buzón de recompensas pendientes"));

        // 2. Verificar que el callback fue disparado desde el hilo de persistencia al dispatcher
        assertSame(persistenceThread, dispatcherCalledOnThread.get(),
                "El dispatcher debe ser invocado desde el hilo asíncrono donde completó el CompletableFuture");

        // 3. Verificar que el mensaje final se ejecutó en el hilo principal y NUNCA en el de persistencia
        String finalMessage = messages.getLast();
        Thread finalThread = messageThreads.getLast();

        assertTrue(finalMessage.contains("¡Se han entregado §e3§a ítems pendientes"));
        assertSame(mainThread, finalThread, "El mensaje final debe haberse ejecutado estrictamente en el hilo principal");
        assertFalse(finalThread.getName().contains("sqlite-persistence-worker"),
                "El mensaje final NUNCA debe ejecutarse en el worker de persistencia");
    }

    @Test
    @DisplayName("G2: Error en persistencia despacha el mensaje de error estrictamente en el hilo principal")
    void testClaimErrorDispatchedOnMainThread() throws Exception {
        UUID playerId = UUID.randomUUID();
        List<String> messages = new CopyOnWriteArrayList<>();
        List<Thread> messageThreads = new CopyOnWriteArrayList<>();
        Player player = createFakePlayer(playerId, messages, messageThreads);

        CountDownLatch completionLatch = new CountDownLatch(1);
        AtomicReference<Thread> dispatcherCalledOnThread = new AtomicReference<>();

        MainThreadDispatcher dispatcher = runnable -> {
            dispatcherCalledOnThread.set(Thread.currentThread());
            mainThreadSimulator.submit(() -> {
                try {
                    runnable.run();
                } finally {
                    completionLatch.countDown();
                }
            });
        };

        RewardApplicationService mockRewardService = new RewardApplicationService(
                createRealRewardService(),
                new InMemoryClaimStorage()
        ) {
            @Override
            public CompletableFuture<Integer> claimPendingRewards(UUID targetId) {
                CompletableFuture<Integer> future = new CompletableFuture<>();
                persistenceExecutor.submit(() -> {
                    try {
                        Thread.sleep(20);
                        future.completeExceptionally(new IllegalStateException("SQLite lock timeout simulation"));
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                });
                return future;
            }
        };

        ClaimSubCommand claimSubCommand = new ClaimSubCommand(mockRewardService, dispatcher);
        CommandContext context = new CommandContext(player, "bd", new String[]{"claim"}, "claim", new String[0]);

        claimSubCommand.execute(context);

        assertTrue(completionLatch.await(3, TimeUnit.SECONDS), "El callback de error debe ejecutarse");

        assertSame(persistenceThread, dispatcherCalledOnThread.get());

        String finalMessage = messages.getLast();
        Thread finalThread = messageThreads.getLast();

        assertTrue(finalMessage.contains("Error al reclamar recompensas: SQLite lock timeout simulation"));
        assertSame(mainThread, finalThread, "El mensaje de error debe ejecutarse estrictamente en el hilo principal");
    }

    @Test
    @DisplayName("G3: Sin recompensas pendientes despacha mensaje informativo estrictamente en el hilo principal")
    void testClaimEmptyDispatchedOnMainThread() throws Exception {
        UUID playerId = UUID.randomUUID();
        List<String> messages = new CopyOnWriteArrayList<>();
        List<Thread> messageThreads = new CopyOnWriteArrayList<>();
        Player player = createFakePlayer(playerId, messages, messageThreads);

        CountDownLatch completionLatch = new CountDownLatch(1);

        MainThreadDispatcher dispatcher = runnable -> mainThreadSimulator.submit(() -> {
            try {
                runnable.run();
            } finally {
                completionLatch.countDown();
            }
        });

        RewardApplicationService mockRewardService = new RewardApplicationService(
                createRealRewardService(),
                new InMemoryClaimStorage()
        ) {
            @Override
            public CompletableFuture<Integer> claimPendingRewards(UUID targetId) {
                CompletableFuture<Integer> future = new CompletableFuture<>();
                persistenceExecutor.submit(() -> future.complete(0));
                return future;
            }
        };

        ClaimSubCommand claimSubCommand = new ClaimSubCommand(mockRewardService, dispatcher);
        CommandContext context = new CommandContext(player, "bd", new String[]{"claim"}, "claim", new String[0]);

        claimSubCommand.execute(context);

        assertTrue(completionLatch.await(3, TimeUnit.SECONDS));

        String finalMessage = messages.getLast();
        Thread finalThread = messageThreads.getLast();

        assertTrue(finalMessage.contains("No tienes ítems de recompensa pendientes de entrega"));
        assertSame(mainThread, finalThread, "El mensaje de ausencia de claims debe ejecutarse en el hilo principal");
    }

    @Test
    @DisplayName("G4: Manejo defensivo ante deliveredCount nulo despacha mensaje informativo sin NullPointerException")
    void testClaimNullDeliveredCountHandledGracefully() throws Exception {
        UUID playerId = UUID.randomUUID();
        List<String> messages = new CopyOnWriteArrayList<>();
        List<Thread> messageThreads = new CopyOnWriteArrayList<>();
        Player player = createFakePlayer(playerId, messages, messageThreads);

        CountDownLatch completionLatch = new CountDownLatch(1);

        MainThreadDispatcher dispatcher = runnable -> mainThreadSimulator.submit(() -> {
            try {
                runnable.run();
            } finally {
                completionLatch.countDown();
            }
        });

        RewardApplicationService mockRewardService = new RewardApplicationService(
                createRealRewardService(),
                new InMemoryClaimStorage()
        ) {
            @Override
            public CompletableFuture<Integer> claimPendingRewards(UUID targetId) {
                CompletableFuture<Integer> future = new CompletableFuture<>();
                persistenceExecutor.submit(() -> future.complete(null));
                return future;
            }
        };

        ClaimSubCommand claimSubCommand = new ClaimSubCommand(mockRewardService, dispatcher);
        CommandContext context = new CommandContext(player, "bd", new String[]{"claim"}, "claim", new String[0]);

        claimSubCommand.execute(context);

        assertTrue(completionLatch.await(3, TimeUnit.SECONDS));

        String finalMessage = messages.getLast();
        assertTrue(finalMessage.contains("No tienes ítems de recompensa pendientes"));
        assertSame(mainThread, messageThreads.getLast());
    }

    @Test
    @DisplayName("G5: Valida metadatos del subcomando, AllowedSender y constructor por defecto")
    void testSubCommandMetadataAndDefaultConstructor() {
        RewardApplicationService mockRewardService = new RewardApplicationService(
                createRealRewardService(),
                new InMemoryClaimStorage()
        );

        ClaimSubCommand defaultCmd = new ClaimSubCommand(mockRewardService);
        assertEquals("claim", defaultCmd.name());
        assertTrue(defaultCmd.aliases().contains("reclamar"));
        assertTrue(defaultCmd.aliases().contains("recompensas"));
        assertEquals(CommandPermission.CLAIM, defaultCmd.permission());
        assertEquals(AllowedSender.PLAYER_ONLY, defaultCmd.allowedSender());
        assertEquals("/bd claim", defaultCmd.usage());
        assertNotNull(defaultCmd.description());

        // Verificar que consola lanza IllegalStateException al no ser jugador
        CommandSender fakeConsole = (CommandSender) Proxy.newProxyInstance(
                CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class},
                (p, m, a) -> null
        );
        CommandContext consoleCtx = new CommandContext(fakeConsole, "bd", new String[]{"claim"}, "claim", new String[0]);
        assertThrows(IllegalStateException.class, () -> defaultCmd.execute(consoleCtx));
    }
}
