package maurxp.betterdragon.config;

import java.util.List;
import java.util.Objects;

/**
 * Excepción lanzada cuando uno o más valores de configuración no superan las validaciones
 * de tipo, rango o consistencia del dominio de BetterDragon.
 *
 * @author maurxp
 */
public class ConfigValidationException extends RuntimeException {

    private final List<String> errors;

    public ConfigValidationException(String message) {
        super(message);
        this.errors = List.of(message);
    }

    public ConfigValidationException(String message, List<String> errors) {
        super(message);
        this.errors = List.copyOf(Objects.requireNonNull(errors, "La lista de errores no puede ser nula"));
    }

    public List<String> getErrors() {
        return errors;
    }

    @Override
    public String getMessage() {
        if (errors == null || errors.isEmpty()) {
            return super.getMessage();
        }
        // Si el mensaje base es idéntico al único error contenido, devolver el error directo
        if (errors.size() == 1 && errors.getFirst().equals(super.getMessage())) {
            return super.getMessage();
        }
        return super.getMessage() + ":\n - " + String.join("\n - ", errors);
    }
}
