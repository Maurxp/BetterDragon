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
 *   <li><b>Control de Versiones:</b> Registra y valida {@code schema_version}.</li>
 *   <li><b>Rechazo Fail-Safe:</b> Rechaza de forma inmediata bases de datos con versiones de esquema
 *       superiores a la soportada por el binario en ejecución, impidiendo corrupción de datos.</li>
 * </ul>
 *
 * @author maurxp
 */
public class SchemaInitializer {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final String TABLE_METADATA = "bd_schema_metadata";
    public static final String TABLE_REWARD_CLAIMS = "bd_reward_claims";

    private final Logger logger;

    public SchemaInitializer(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger no puede ser nulo");
    }

    /**
     * Inicializa o valida el esquema en la conexión SQLite proporcionada.
     *
     * @param connection conexión activa a la base de datos
     * @throws SQLException          si ocurre un error de base de datos
     * @throws IllegalStateException si se detecta una versión de esquema futura no compatible
     */
    public void initializeSchema(Connection connection) throws SQLException {
        Objects.requireNonNull(connection, "connection no puede ser nula");

        // 1. Crear tabla de metadatos de versión de esquema
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS " + TABLE_METADATA + " ("
                    + "key TEXT PRIMARY KEY, "
                    + "value TEXT NOT NULL"
                    + ");");
        }

        // 2. Comprobar o registrar la versión de esquema actual
        int existingVersion = fetchSchemaVersion(connection);
        if (existingVersion == 0) {
            recordSchemaVersion(connection, CURRENT_SCHEMA_VERSION);
            logger.info("[BetterDragon] Esquema de persistencia inicializado en versión " + CURRENT_SCHEMA_VERSION + ".");
        } else if (existingVersion > CURRENT_SCHEMA_VERSION) {
            String error = "[BetterDragon] Versión de esquema incompatible detectada: v" + existingVersion
                    + " (máxima soportada por este binario: v" + CURRENT_SCHEMA_VERSION + "). "
                    + "El plugin no iniciará para evitar corrupción o pérdida de datos.";
            logger.severe(error);
            throw new IllegalStateException(error);
        } else {
            logger.info("[BetterDragon] Esquema de persistencia verificado correctamente (versión v" + existingVersion + ").");
        }

        // 3. Crear tabla principal de claims de recompensas
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

            // 4. Índices para consultas eficientes de runtime
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_reward_claims_status ON " + TABLE_REWARD_CLAIMS + "(status);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_reward_claims_participant ON " + TABLE_REWARD_CLAIMS + "(participant_uuid);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_reward_claims_battle ON " + TABLE_REWARD_CLAIMS + "(battle_id);");
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
