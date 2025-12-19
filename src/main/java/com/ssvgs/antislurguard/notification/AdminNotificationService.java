package com.biggiko.antislurguard.notification;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import com.biggiko.antislurguard.AntiSlurGuardPlugin;

import net.kyori.adventure.text.Component;

public final class AdminNotificationService {

    private final AntiSlurGuardPlugin plugin;
    private final Path filePath;
    private final Map<UUID, AdminSetting> preferences = new HashMap<>();
    private final Object lock = new Object();

    public AdminNotificationService(AntiSlurGuardPlugin plugin, Path filePath) {
        this.plugin = plugin;
        this.filePath = filePath;
        load();
    }

    public void load() {
        synchronized (lock) {
            preferences.clear();
        }
        try {
            if (filePath.getParent() != null) {
                Files.createDirectories(filePath.getParent());
            }
        } catch (IOException ex) {
            plugin.getLogger().severe("Не удалось создать директорию настроек админов: " + ex.getMessage());
        }
        File file = filePath.toFile();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String key : players.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                ConfigurationSection section = players.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                boolean enabled = section.getBoolean("enabled", true);
                String name = section.getString("name", "");
                synchronized (lock) {
                    preferences.put(uuid, new AdminSetting(enabled, name));
                }
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Пропускаю некорректный UUID в admin-notify.yml: " + key);
            }
        }
    }

    public ToggleResult update(UUID uuid, String name, ToggleAction action) {
        if (uuid == null) {
            return new ToggleResult(false, false);
        }
        boolean next;
        synchronized (lock) {
            AdminSetting existing = preferences.getOrDefault(uuid, new AdminSetting(true, name));
            boolean current = existing.enabled();
            next = switch (action) {
                case ON -> true;
                case OFF -> false;
                case TOGGLE -> !current;
            };
            preferences.put(uuid, new AdminSetting(next, name != null ? name : existing.lastKnownName()));
        }
        saveAsync();
        return new ToggleResult(true, next);
    }

    public boolean isEnabled(UUID uuid) {
        synchronized (lock) {
            return preferences.getOrDefault(uuid, new AdminSetting(true, "")).enabled();
        }
    }

    public void broadcast(Component component) {
        if (component == null || !plugin.config().notifications().admin().enabled()) {
            return;
        }
        String permission = plugin.config().permissions().admin();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission(permission)) {
                continue;
            }
            if (!isEnabled(player.getUniqueId())) {
                continue;
            }
            player.sendMessage(component);
        }
    }

    private void saveAsync() {
        Map<UUID, AdminSetting> snapshot;
        synchronized (lock) {
            snapshot = new HashMap<>(preferences);
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            File file = filePath.toFile();
            YamlConfiguration yaml = new YamlConfiguration();
            ConfigurationSection players = yaml.createSection("players");
            for (Map.Entry<UUID, AdminSetting> entry : snapshot.entrySet()) {
                ConfigurationSection section = players.createSection(entry.getKey().toString());
                AdminSetting setting = entry.getValue();
                section.set("enabled", setting.enabled());
                section.set("name", setting.lastKnownName());
            }
            try {
                yaml.save(file);
            } catch (IOException ex) {
                plugin.getLogger().severe("Не удалось сохранить admin-notify.yml: " + ex.getMessage());
            }
        });
    }

    public enum ToggleAction {
        ON,
        OFF,
        TOGGLE
    }

    public record ToggleResult(boolean success, boolean enabled) {
    }

    private record AdminSetting(boolean enabled, String lastKnownName) {
    }
}
