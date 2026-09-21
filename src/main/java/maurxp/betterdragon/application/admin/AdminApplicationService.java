package maurxp.betterdragon.application.admin;

import maurxp.betterdragon.application.admin.model.ReloadResult;
import maurxp.betterdragon.config.ConfigurationService;

import java.io.File;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Servicio de aplicación para tareas administrativas globales y recarga de configuración.
 *
 * @author maurxp
 */
public class AdminApplicationService {

    private final ConfigurationService configurationService;
    private final Supplier<File> dataFolderSupplier;

    public AdminApplicationService(ConfigurationService configurationService, Supplier<File> dataFolderSupplier) {
        this.configurationService = Objects.requireNonNull(configurationService, "configurationService no puede ser nulo");
        this.dataFolderSupplier = Objects.requireNonNull(dataFolderSupplier, "dataFolderSupplier no puede ser nulo");
    }

    /**
     * Ejecuta una recarga atómica y fail-safe de la configuración.
     *
     * @return resultado tipado de la operación
     */
    public ReloadResult reloadConfiguration() {
        long start = System.currentTimeMillis();
        File dataFolder = dataFolderSupplier.get();
        File configFile = new File(dataFolder, "config.yml");
        File arenasFile = new File(dataFolder, "arenas.yml");

        boolean ok = configurationService.reload(configFile, arenasFile);
        long elapsed = System.currentTimeMillis() - start;

        if (ok) {
            return ReloadResult.success("Configuración y arenas recargadas exitosamente.", elapsed);
        } else {
            return ReloadResult.failure("Fallo al validar la nueva configuración. Se conserva la configuración anterior activa.", elapsed);
        }
    }
}
