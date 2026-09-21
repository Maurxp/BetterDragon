package maurxp.betterdragon.application.admin.model;

import java.util.Objects;

/**
 * Resultado estructurado de una recarga de configuración.
 *
 * @author maurxp
 */
public record ReloadResult(
        boolean success,
        String message,
        long elapsedMillis
) {
    public ReloadResult {
        Objects.requireNonNull(message, "message no puede ser nulo");
    }

    public static ReloadResult success(String message, long elapsedMillis) {
        return new ReloadResult(true, message, elapsedMillis);
    }

    public static ReloadResult failure(String message, long elapsedMillis) {
        return new ReloadResult(false, message, elapsedMillis);
    }
}
