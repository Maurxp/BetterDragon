package maurxp.betterdragon.command;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Emisor simulado de comandos para pruebas unitarias deterministas sin servidor Bukkit completo.
 *
 * @author maurxp
 */
public class TestCommandSender implements CommandSender {

    private final String name;
    private boolean op;
    private final Set<String> permissions = new HashSet<>();
    private final List<String> messages = new ArrayList<>();

    public TestCommandSender(String name, boolean op) {
        this.name = name;
        this.op = op;
    }

    public TestCommandSender(String name) {
        this(name, false);
    }

    public void addPermission(String permission) {
        permissions.add(permission.toLowerCase());
    }

    public void removePermission(String permission) {
        permissions.remove(permission.toLowerCase());
    }

    public List<String> getMessages() {
        return List.copyOf(messages);
    }

    public void clearMessages() {
        messages.clear();
    }

    @Override
    public void sendMessage(String message) {
        messages.add(message);
    }

    @Override
    public void sendMessage(String... msgs) {
        if (msgs != null) {
            for (String m : msgs) {
                messages.add(m);
            }
        }
    }

    @Override
    public void sendMessage(UUID sender, String message) {
        messages.add(message);
    }

    @Override
    public void sendMessage(UUID sender, String... msgs) {
        sendMessage(msgs);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isOp() {
        return op;
    }

    @Override
    public void setOp(boolean value) {
        this.op = value;
    }

    @Override
    public boolean isPermissionSet(String name) {
        return permissions.contains(name.toLowerCase());
    }

    @Override
    public boolean isPermissionSet(Permission perm) {
        return perm != null && isPermissionSet(perm.getName());
    }

    @Override
    public boolean hasPermission(String name) {
        if (op) return true;
        return permissions.contains(name.toLowerCase());
    }

    @Override
    public boolean hasPermission(Permission perm) {
        return perm != null && hasPermission(perm.getName());
    }

    // Métodos stub para la interfaz CommandSender
    @Override public Server getServer() { return null; }
    @Override public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) { return null; }
    @Override public PermissionAttachment addAttachment(Plugin plugin) { return null; }
    @Override public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) { return null; }
    @Override public PermissionAttachment addAttachment(Plugin plugin, int ticks) { return null; }
    @Override public void removeAttachment(PermissionAttachment attachment) {}
    @Override public void recalculatePermissions() {}
    @Override public Set<PermissionAttachmentInfo> getEffectivePermissions() { return Set.of(); }
    @Override public CommandSender.Spigot spigot() { return null; }
    @Override public Component name() { return Component.text(name); }
}
