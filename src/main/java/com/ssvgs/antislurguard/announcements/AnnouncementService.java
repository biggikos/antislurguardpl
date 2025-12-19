package com.biggiko.antislurguard.announcements;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import com.biggiko.antislurguard.AntiSlurGuardPlugin;
import com.biggiko.antislurguard.config.Config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class AnnouncementService {

    private final AntiSlurGuardPlugin plugin;
    private final Path filePath;
    private final Config.AnnouncementsSettings settings;
    private final List<Integer> taskIds = new ArrayList<>();

    public AnnouncementService(AntiSlurGuardPlugin plugin, Path filePath, Config.AnnouncementsSettings settings) {
        this.plugin = plugin;
        this.filePath = filePath;
        this.settings = settings;
    }

    public void reload() {
        cancelAll();
        if (!settings.enabled()) {
            return;
        }
        ensureFile();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(filePath.toFile());
        List<Map<?, ?>> nodes = yaml.getMapList("announcements");
        if (nodes.isEmpty()) {
            return;
        }
        for (Map<?, ?> node : nodes) {
            String text = string(node.get("text"));
            if (text == null || text.isBlank()) {
                continue;
            }
            long interval = longValue(node.get("intervalSeconds"), settings.defaultIntervalSeconds());
            if (interval <= 0) {
                interval = settings.defaultIntervalSeconds();
            }
            String hover = string(node.get("hover"));
            String clickUrl = string(node.get("clickUrl"));
            String permission = string(node.get("permission"));
            schedule(text, hover, clickUrl, permission, interval);
        }
    }

    public void cancelAll() {
        for (Integer id : taskIds) {
            if (id != null) {
                Bukkit.getScheduler().cancelTask(id);
            }
        }
        taskIds.clear();
    }

    private void schedule(String text, String hover, String clickUrl, String permission, long intervalSeconds) {
        long ticks = Math.max(20L, intervalSeconds * 20L);
        Component component = buildComponent(text, hover, clickUrl);
        int id = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> broadcast(component, permission), ticks, ticks);
        taskIds.add(id);
    }

    private Component buildComponent(String text, String hover, String clickUrl) {
        Component base = LegacyComponentSerializer.legacyAmpersand().deserialize(text);
        if (hover != null && !hover.isBlank()) {
            base = base.hoverEvent(HoverEvent.showText(LegacyComponentSerializer.legacyAmpersand().deserialize(hover)));
        }
        if (clickUrl != null && !clickUrl.isBlank()) {
            base = base.clickEvent(ClickEvent.openUrl(clickUrl));
        }
        return base;
    }

    private void broadcast(Component component, String permission) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (permission != null && !permission.isBlank() && !player.hasPermission(permission)) {
                continue;
            }
            player.sendMessage(component);
        }
    }

    private long longValue(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return fallback;
    }

    private String string(Object value) {
        return value instanceof String str ? str : null;
    }

    private void ensureFile() {
        try {
            if (filePath.getParent() != null) {
                Files.createDirectories(filePath.getParent());
            }
        } catch (IOException ex) {
            plugin.getLogger().severe("Не удалось создать директорию для announcements: " + ex.getMessage());
        }
        File file = filePath.toFile();
        if (file.exists()) {
            return;
        }
        try (InputStream in = plugin.getResource("announcements.yml")) {
            if (in != null) {
                Files.copy(in, filePath);
                return;
            }
        } catch (IOException ex) {
            plugin.getLogger().severe("Не удалось записать announcements.yml: " + ex.getMessage());
            return;
        }
        try {
            Files.writeString(filePath,
                    "announcements:\n  - text: \"&aExample announcement\"\n    intervalSeconds: 300\n",
                    StandardCharsets.UTF_8);
        } catch (IOException ex) {
            plugin.getLogger().severe("Не удалось создать announcements.yml: " + ex.getMessage());
        }
    }
}
