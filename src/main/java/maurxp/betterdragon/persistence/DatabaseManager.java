package maurxp.betterdragon.persistence;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gestor central de infraestructura para la base de datos SQLite de BetterDragon.
 * <p>
 * Principios:
 * <ul>
 *   <li><b>Single Writer:</b> Posee un {@link ExecutorService} de un único hilo dedicado a persistencia
 *       ({@code "BetterDragon-Persistence"}), serializando todas las operaciones de I/O sobre una única conexión.</li>
 *   <li><b>Cero Bloqueo de Main Thread:</b> Ninguna llamada a métodos JDBC se ejecuta en el hilo del servidor.</li>
 *   <li><b>Configuración Segura:</b> Habilita claves foráneas y define un {@code busy_timeout} explícito.</li>
 *   <li><b>Apagado Limpio:</b> Espera el drenaje de tareas en curso y cierra recursos ordenadamente.</li>
 * </ul>
 *
 * @author maurxp
 */
public class DatabaseManager implements AutoCloseable {

    private final Path databasePath;
    private final Logger logger;
    private final ExecutorService persistenceExecutor;
    private Connection connection;
    private volatile boolean closed = false;

    private volatile boolean ready = false;
    private volatile Throwable initializationError = null;

    /**
     * Resuelve y construye el gestor de base de datos a partir del dataFolder del plugin.
     *
     * @param dataFolder directorio de datos del plugin
     * @param logger     logger para trazas informativas y de error
     * @return instancia de DatabaseManager
     */
    public static DatabaseManager forPluginDataFolder(File dataFolder, Logger logger) {
        Objects.requireNonNull(dataFolder, "dataFolder no puede ser nulo");
        Objects.requireNonNull(logger, "logger no puede ser nulo");
        Path dbPath = dataFolder.toPath().resolve("data").resolve("betterdragon.db");
        return new DatabaseManager(dbPath, logger);
    }

    public DatabaseManager(Path databasePath, Logger logger) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath no puede ser nulo");
        this.logger = Objects.requireNonNull(logger, "logger no puede ser nulo");
        this.persistenceExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "BetterDragon-Persistence");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Inicializa la conexión SQLite y aplica la configuración de pragmas de forma asíncrona en el hilo de persistencia.
     * <p>
     * Garantiza que ninguna llamada JDBC bloquee el hilo principal en {@code onEnable()}.
     * Al utilizar un {@link ExecutorService} de hilo único, todas las tareas posteriores
     * enviadas a {@code persistenceExecutor} se encolan ordenadamente detrás de esta inicialización.
     *
     * @return CompletableFuture que se completa cuando la base de datos está lista
     */
    public CompletableFuture<Void> initializeAsync() {
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("DatabaseManager ya se encuentra cerrado."));
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        persistenceExecutor.submit(() -> {
            try {
                performInitialization();
                ready = true;
                future.complete(null);
            } catch (Throwable t) {
                initializationError = t;
                ready = false;
                logger.log(Level.SEVERE, "[BetterDragon] Error fatal durante la inicialización asíncrona de SQLite: " + t.getMessage(), t);
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    /**
     * Inicializa la base de datos de forma síncrona esperando hasta 30 segundos.
     * <p>
     * <b>Uso exclusivo para suites de pruebas unitarias/de integración</b> donde se requiere setup bloqueante.
     *
     * @throws SQLException si ocurre un error de base de datos
     * @throws IOException  si no se pueden crear los directorios padres
     */
    public void initializeSync() throws SQLException, IOException {
        try {
            initializeAsync().get(30, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof SQLException sqle) throw sqle;
            if (cause instanceof IOException ioe) throw ioe;
            if (cause instanceof RuntimeException re) throw re;
            throw new SQLException("Error durante la inicialización de base de datos", cause);
        } catch (Exception e) {
            throw new SQLException("Timeout o interrupción al inicializar base de datos", e);
        }
    }

    /**
     * Inicializa la conexión SQLite. Conservado por compatibilidad hacia atrás delegando en {@link #initializeSync()}.
     */
    public void initialize() throws SQLException, IOException {
        initializeSync();
    }

    private void performInitialization() throws SQLException, IOException {
        Path parentDir = databasePath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            logger.log(Level.SEVERE, "[BetterDragon] Driver org.sqlite.JDBC no encontrado en el classpath.", e);
            throw new SQLException("Driver org.sqlite.JDBC no encontrado", e);
        }

        String jdbcUrl = "jdbc:sqlite:" + databasePath.toAbsolutePath();
        logger.info("[BetterDragon] Abriendo base de datos SQLite en: " + databasePath.toAbsolutePath());
        this.connection = DriverManager.getConnection(jdbcUrl);

        // Configuración de pragmas segura
        try (Statement stmt = this.connection.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
            stmt.execute("PRAGMA busy_timeout = 5000;");
        }

        // Inicialización y verificación de esquema
        SchemaInitializer schemaInitializer = new SchemaInitializer(logger);
        schemaInitializer.initializeSchema(this.connection);

        logger.info("[BetterDragon] Conexión SQLite establecida y esquema verificado exitosamente.");
    }

    /**
     * Ejecuta una operación de consulta o lectura de forma asíncrona en el hilo de persistencia.
     *
     * @param <T>      tipo de retorno
     * @param supplier función con acceso a la conexión JDBC
     * @return CompletableFuture con el resultado
     */
    public <T> CompletableFuture<T> supplyAsync(SqlSupplier<T> supplier) {
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("DatabaseManager ya está cerrado."));
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        persistenceExecutor.submit(() -> {
            try {
                if (initializationError != null) {
                    throw new IllegalStateException("DatabaseManager falló en su inicialización previa: " + initializationError.getMessage(), initializationError);
                }
                if (closed || connection == null || connection.isClosed()) {
                    throw new IllegalStateException("Conexión a base de datos cerrada o no disponible.");
                }
                T result = supplier.get(connection);
                future.complete(result);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    /**
     * Ejecuta una operación de mutación o escritura de forma asíncrona en el hilo de persistencia.
     *
     * @param consumer acción que recibe la conexión JDBC
     * @return CompletableFuture completado al finalizar la tarea
     */
    public CompletableFuture<Void> runAsync(SqlConsumer consumer) {
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("DatabaseManager ya está cerrado."));
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        persistenceExecutor.submit(() -> {
            try {
                if (initializationError != null) {
                    throw new IllegalStateException("DatabaseManager falló en su inicialización previa: " + initializationError.getMessage(), initializationError);
                }
                if (closed || connection == null || connection.isClosed()) {
                    throw new IllegalStateException("Conexión a base de datos cerrada o no disponible.");
                }
                consumer.accept(connection);
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    /**
     * Retorna la conexión activa en el hilo de persistencia (uso exclusivo interno).
     */
    public Connection getConnection() {
        return connection;
    }

    public Path getDatabasePath() {
        return databasePath;
    }

    public boolean isClosed() {
        return closed;
    }

    public boolean isReady() {
        return ready && !closed && connection != null;
    }

    public Throwable getInitializationError() {
        return initializationError;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        logger.info("[BetterDragon] Cerrando DatabaseManager y liberando recursos de persistencia...");

        persistenceExecutor.shutdown();
        try {
            if (!persistenceExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warning("[BetterDragon] PersistenceExecutor no terminó en 5 segundos. Forzando detención...");
                persistenceExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            persistenceExecutor.shutdownNow();
        }

        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                    logger.info("[BetterDragon] Conexión SQLite cerrada limpiamente.");
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[BetterDragon] Error al cerrar conexión SQLite: " + e.getMessage(), e);
            }
        }
    }

    @FunctionalInterface
    public interface SqlSupplier<T> {
        T get(Connection connection) throws Exception;
    }

    @FunctionalInterface
    public interface SqlConsumer {
        void accept(Connection connection) throws Exception;
    }
}
