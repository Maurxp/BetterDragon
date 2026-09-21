package maurxp.betterdragon.command;

import maurxp.betterdragon.application.permission.CommandPermission;

import java.util.List;

/**
 * Contrato formal para cada subcomando ejecutable de BetterDragon.
 *
 * @author maurxp
 */
public interface SubCommand {

    /**
     * Nombre canónico del subcomando (ej. "help", "leaderboard", "start").
     */
    String name();

    /**
     * Alias reconocidos para este subcomando (ej. "top", "cancel", "spawn").
     */
    default List<String> aliases() {
        return List.of();
    }

    /**
     * Descripción breve para la ayuda y documentación interna.
     */
    String description();

    /**
     * Sintaxis de uso del subcomando (ej. "/bd leaderboard [damage|slayers|battles] [limit]").
     */
    String usage();

    /**
     * Permiso de capacidad requerido para ejecutar el subcomando.
     */
    CommandPermission permission();

    /**
     * Tipo de emisor admitido (jugador, consola o ambos).
     */
    AllowedSender allowedSender();

    /**
     * Ejecuta el subcomando con el contexto provisto.
     *
     * @param context contexto con el emisor y los argumentos analizados
     */
    void execute(CommandContext context);

    /**
     * Proporciona sugerencias para autocompletado en el cliente (Tab Completion).
     *
     * @param context contexto de autocompletado
     * @return lista de sugerencias compatibles con el último argumento
     */
    default List<String> tabComplete(CommandContext context) {
        return List.of();
    }
}
