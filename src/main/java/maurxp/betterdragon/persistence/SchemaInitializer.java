package maurxp.betterdragon.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Inicializador y verificador de esquema de base de datos para BetterDragon.
 * <p>
 * Responsabilidades:
 * <ul>
 *   <li><b>Idempotencia DDL:</b> Crea tablas e índices si no existen sin destruir datos existentes.</li>
 *   <li><b>Control de Versiones y Migraciones:</b> Administra {@code schema_version} y aplica migraciones seguras (v1 -> v2).</li>
 *   <li><b>Rechazo Fail-Safe:</b> Rechaza de forma inmediata bases de datos con versiones de esquema
 *       superiores a la soportada por el binario en ejecución, impidiendo corrupción de datos.</li>
 * </ul>
 *
 * @author maurxp
 */
public class SchemaInitializer {

    public static final int CURRENT_SCHEMA_VERSION = 2;
    public static final String TABLE_METADATA = "bd_schema_metadata";
    public static final String TABLE_REWARD_CLAIMS = "bd_reward_claims";
    public static final String TABLE_LEADERBOARD_BATTLES = "bd_leaderboard_battles";
    public static final String TABLE_LEADERBOARD_PARTICIPATION = "bd_leaderboard_participation";
    public static final String TABLE_LEADERBOARD_PLAYERS = "bd_leaderboard_players";

    private final Logger logger;

    public SchemaInitializer(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger no puede ser nulo");
    }

    /**
     * Inicializa o valida el esquema en la conexión SQLite proporcionada, aplicando migraciones si es necesario.
     *
     * @param connection conexión activa a la base de datos
     * @throws SQLException          si ocurre un error de base de datos
     * @throws IllegalStateException si se detecta una versión de esquema futura no compatible
     */
    public void initializeSchema(Connection connection) throws SQLException {
        Objects.requireNonNull(connection, "connection no puede ser nula");

        // 1. Crear tabla de metadatos de versión de esquema si no existe
        createMetadataTable(connection);

        // 2. Comprobar versión de esquema actual
        int existingVersion = fetchSchemaVersion(connection);

        if (existingVersion == 0) {
            // Base de datos nueva: inicializar tablas v1 y v2 directamente
            createRewardClaimsTable(connection);
            createLeaderboardTables(connection);
            recordSchemaVersion(connection, CURRENT_SCHEMA_VERSION);
            logger.info("[BetterDragon] Esquema de persistencia inicializado directamente en versión " + CURRENT_SCHEMA_VERSION + ".");
        } else if (existingVersion == 1) {
            // Migración v1 -> v2: añadir tablas de leaderboard conservando bd_reward_claims intacta
            logger.info("[BetterDragon] Migrando esquema de persistencia de v1 a v2 (Leaderboard)...");
            createLeaderboardTables(connection);
            recordSchemaVersion(connection, CURRENT_SCHEMA_VERSION);
            logger.info("[BetterDragon] Migración de persistencia a versión " + CURRENT_SCHEMA_VERSION + " completada exitosamente.");
        } else if (existingVersion == CURRENT_SCHEMA_VERSION) {
            // Versión actual v2 verificada
            createRewardClaimsTable(connection);
            createLeaderboardTables(connection);
            logger.info("[BetterDragon] Esquema de persistencia verificado correctamente (versión v" + existingVersion + ").");
        } else {
            // existingVersion > CURRENT_SCHEMA_VERSION: rechazo fail-safe
            String error = "[BetterDragon] Versión de esquema incompatible detectada: v" + existingVersion
                    + " (máxima soportada por este binario: v" + CURRENT_SCHEMA_VERSION + "). "
                    + "El plugin no iniciará para evitar corrupción o pérdida de datos.";
            logger.severe(error);
            throw new IllegalStateException(error);
        }
    }

    private void createMetadataTable(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS " + TABLE_METADATA + " ("
                    + "key TEXT PRIMARY KEY, "
                    + "value TEXT NOT NULL"
                    + ");");
        }
    }

    private void createRewardClaimsTable(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS " + TABLE_REWARD_CLAIMS + " ("
                    + "claim_id TEXT PRIMARY KEY, "
                    + "idempotency_key TEXT UNIQUE NOT NULL, "
                    + "battle_id TEXT NOT NULL, "
                    + "participant_uuid TEXT NOT NULL, "
                    + "player_name TEXT NOT NULL, "
                    + "source TEXT NOT NULL, "
                    + "material TEXT NOT NULL, "
                    + "display_name TEXT, "
                    + "lore TEXT, "
                    + "original_amount INTEGER NOT NULL, "
                    + "delivered_amount INTEGER NOT NULL, "
                    + "remaining_amount INTEGER NOT NULL, "
                    + "status TEXT NOT NULL, "
                    + "created_at INTEGER NOT NULL, "
                    + "claimed_at INTEGER, "
                    + "failure_reason TEXT, "
                    + "updated_at INTEGER NOT NULL"
                    + ");");

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_reward_claims_status ON " + TABLE_REWARD_CLAIMS + "(status);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_reward_claims_participant ON " + TABLE_REWARD_CLAIMS + "(participant_uuid);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_reward_claims_battle ON " + TABLE_REWARD_CLAIMS + "(battle_id);");
        }
    }

    private void createLeaderboardTables(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // 1. Historial de batallas completadas
            stmt.execute("CREATE TABLE IF NOT EXISTS " + TABLE_LEADERBOARD_BATTLES + " ("
                    + "battle_id TEXT PRIMARY KEY, "
                    + "completed_at INTEGER NOT NULL, "
                    + "world_name TEXT NOT NULL, "
                    + "slayer_uuid TEXT"
                    + ");");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_leaderboard_battles_completed ON "
                    + TABLE_LEADERBOARD_BATTLES + "(completed_at DESC);");

            // 2. Historial de participaciones por batalla
            stmt.execute("CREATE TABLE IF NOT EXISTS " + TABLE_LEADERBOARD_PARTICIPATION + " ("
                    + "battle_id TEXT NOT NULL, "
                    + "player_uuid TEXT NOT NULL, "
                    + "historical_name TEXT NOT NULL, "
                    + "damage REAL NOT NULL, "
                    + "first_hit_sequence INTEGER NOT NULL, "
                    + "was_slayer INTEGER NOT NULL, "
                    + "participated_at INTEGER NOT NULL, "
                    + "PRIMARY KEY (battle_id, player_uuid)"
                    + ");");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_leaderboard_part_player ON "
                    + TABLE_LEADERBOARD_PARTICIPATION + "(player_uuid);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_leaderboard_part_battle ON "
                    + TABLE_LEADERBOARD_PARTICIPATION + "(battle_id);");

            // 3. Estadísticas acumuladas por jugador
            stmt.execute("CREATE TABLE IF NOT EXISTS " + TABLE_LEADERBOARD_PLAYERS + " ("
                    + "player_uuid TEXT PRIMARY KEY, "
                    + "last_known_name TEXT NOT NULL, "
                    + "battles_participated INTEGER NOT NULL, "
                    + "total_damage REAL NOT NULL, "
                    + "highest_damage REAL NOT NULL, "
                    + "slayer_count INTEGER NOT NULL, "
                    + "first_participation_at INTEGER NOT NULL, "
                    + "last_participation_at INTEGER NOT NULL"
                    + ");");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_leaderboard_players_damage ON "
                    + TABLE_LEADERBOARD_PLAYERS + "(total_damage DESC, player_uuid ASC);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_leaderboard_players_slayer ON "
                    + TABLE_LEADERBOARD_PLAYERS + "(slayer_count DESC, player_uuid ASC);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_leaderboard_players_battles ON "
                    + TABLE_LEADERBOARD_PLAYERS + "(battles_participated DESC, player_uuid ASC);");
        }
    }

    private int fetchSchemaVersion(Connection connection) throws SQLException {
        String query = "SELECT value FROM " + TABLE_METADATA + " WHERE key = 'schema_version';";
        try (PreparedStatement pstmt = connection.prepareStatement(query);
             ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) {
                try {
                    return Integer.parseInt(rs.getString("value"));
                } catch (NumberFormatException nfe) {
                    throw new IllegalStateException("El valor de 'schema_version' en " + TABLE_METADATA + " no es un entero válido.", nfe);
                }
            }
        }
        return 0;
    }

    private void recordSchemaVersion(Connection connection, int version) throws SQLException {
        String sql = "INSERT OR REPLACE INTO " + TABLE_METADATA + " (key, value) VALUES ('schema_version', ?);";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, String.valueOf(version));
            pstmt.executeUpdate();
        }
    }
}
