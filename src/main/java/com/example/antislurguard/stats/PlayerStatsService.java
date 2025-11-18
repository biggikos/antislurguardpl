package com.example.antislurguard.stats;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import com.example.antislurguard.AntiSlurGuardPlugin;
import com.example.antislurguard.config.Config;

public final class PlayerStatsService {

    private final AntiSlurGuardPlugin plugin;
    private final Path filePath;
    private final Config.StatsSettings settings;
    private final Map<UUID, PlayerStats> stats = new HashMap<>();
    private boolean saveScheduled;

    public PlayerStatsService(AntiSlurGuardPlugin plugin, Path filePath, Config.StatsSettings settings) {
        this.plugin = plugin;
        this.filePath = filePath;
        this.settings = settings;
        load();
    }

    public RecordResult recordChatViolation(UUID uuid, String playerName) {
        if (!settings.trackChatViolations()) {
            return RecordResult.disabled();
        }
        PlayerStats data = stats.computeIfAbsent(uuid, id -> new PlayerStats(playerName));
        data.lastKnownName = playerName;
        data.chatViolations++;
        data.violationsSinceLastBan++;
        boolean reached = settings.autoPermaBanThreshold() > 0
                && data.violationsSinceLastBan >= settings.autoPermaBanThreshold();
        boolean triggerBan = reached;
        if (reached) {
            data.permanentBans++;
            data.violationsSinceLastBan = 0;
            data.lastPermaBanAt = Instant.now().toEpochMilli();
        }
        scheduleSave();
        return new RecordResult(playerName, data.chatViolations, data.permanentBans, triggerBan);
    }

    public Optional<PlayerStatsView> findByQuery(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }
        String normalized = query.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        for (Map.Entry<UUID, PlayerStats> entry : stats.entrySet()) {
            PlayerStats value = entry.getValue();
            if (entry.getKey().toString().equalsIgnoreCase(lower)
                    || (value.lastKnownName != null && value.lastKnownName.equalsIgnoreCase(normalized))) {
                return Optional.of(new PlayerStatsView(entry.getKey(), value));
            }
        }
        return Optional.empty();
    }

    private void load() {
        try {
            if (filePath.getParent() != null) {
                Files.createDirectories(filePath.getParent());
            }
        } catch (IOException ex) {
            plugin.getLogger().severe("Не удалось создать папку статистики: " + ex.getMessage());
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
                PlayerStats data = new PlayerStats(section.getString("name", "unknown"));
                data.chatViolations = section.getInt("chatViolations", 0);
                data.violationsSinceLastBan = section.getInt("violationsSinceLastBan", 0);
                data.permanentBans = section.getInt("permanentBans", 0);
                data.lastPermaBanAt = section.getLong("lastPermaBanAt", 0L);
                stats.put(uuid, data);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Пропускаю некорректный UUID в player-stats.yml: " + key);
            }
        }
    }

    private void scheduleSave() {
        synchronized (this) {
            if (saveScheduled) {
                return;
            }
            saveScheduled = true;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            synchronized (this) {
                saveScheduled = false;
                saveNow();
            }
        });
    }

    private void saveNow() {
        File file = filePath.toFile();
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection players = yaml.createSection("players");
        for (Map.Entry<UUID, PlayerStats> entry : stats.entrySet()) {
            ConfigurationSection section = players.createSection(entry.getKey().toString());
            PlayerStats data = entry.getValue();
            section.set("name", data.lastKnownName);
            section.set("chatViolations", data.chatViolations);
            section.set("violationsSinceLastBan", data.violationsSinceLastBan);
            section.set("permanentBans", data.permanentBans);
            section.set("lastPermaBanAt", data.lastPermaBanAt);
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Не удалось сохранить player-stats.yml: " + ex.getMessage());
        }
    }

    public record RecordResult(String playerName, int chatViolations, int permanentBans, boolean triggerPermaBan) {
        public static RecordResult disabled() {
            return new RecordResult("", 0, 0, false);
        }
    }

    public record PlayerStatsView(UUID uuid, PlayerStats data) {
        public String playerName() {
            return data.lastKnownName;
        }

        public int chatViolations() {
            return data.chatViolations;
        }

        public int permanentBans() {
            return data.permanentBans;
        }
    }

    private static final class PlayerStats {
        private String lastKnownName;
        private int chatViolations;
        private int violationsSinceLastBan;
        private int permanentBans;
        private long lastPermaBanAt;

        private PlayerStats(String name) {
            this.lastKnownName = name;
        }
    }
}
