package com.biggiko.antislurguard.runtime;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.bukkit.configuration.file.YamlConfiguration;

import com.biggiko.antislurguard.AntiSlurGuardPlugin;

public final class RuntimeSettingsService {

    public enum Scope {
        CHAT,
        NICKNAME
    }

    private final AntiSlurGuardPlugin plugin;
    private final Path filePath;
    private long chatDurationOverride;
    private long nicknameDurationOverride;

    public RuntimeSettingsService(AntiSlurGuardPlugin plugin, Path filePath) {
        this.plugin = plugin;
        this.filePath = filePath;
        load();
    }

    public void load() {
        ensureFile();
        File file = filePath.toFile();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        chatDurationOverride = yaml.getLong("durations.chatSeconds", -1L);
        nicknameDurationOverride = yaml.getLong("durations.nicknameSeconds", -1L);
    }

    public long resolveDuration(Scope scope, long configDuration) {
        return switch (scope) {
            case CHAT -> chatDurationOverride >= 0 ? chatDurationOverride : configDuration;
            case NICKNAME -> nicknameDurationOverride >= 0 ? nicknameDurationOverride : configDuration;
        };
    }

    public long chatDurationOverride() {
        return chatDurationOverride;
    }

    public long nicknameDurationOverride() {
        return nicknameDurationOverride;
    }

    public void adjustChatDuration(long delta, long fallback) {
        chatDurationOverride = Math.max(0L, resolveOrFallback(chatDurationOverride, fallback) + delta);
        saveAsync();
    }

    public void adjustNicknameDuration(long delta, long fallback) {
        nicknameDurationOverride = Math.max(0L, resolveOrFallback(nicknameDurationOverride, fallback) + delta);
        saveAsync();
    }

    public void setChatDuration(long value) {
        chatDurationOverride = Math.max(0L, value);
        saveAsync();
    }

    public void setNicknameDuration(long value) {
        nicknameDurationOverride = Math.max(0L, value);
        saveAsync();
    }

    public void resetChatDuration() {
        chatDurationOverride = -1L;
        saveAsync();
    }

    public void resetNicknameDuration() {
        nicknameDurationOverride = -1L;
        saveAsync();
    }

    private long resolveOrFallback(long value, long fallback) {
        return value >= 0 ? value : fallback;
    }

    private void saveAsync() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("durations.chatSeconds", chatDurationOverride);
        yaml.set("durations.nicknameSeconds", nicknameDurationOverride);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                yaml.save(filePath.toFile());
            } catch (IOException ex) {
                plugin.getLogger().severe("Не удалось сохранить runtime-settings.yml: " + ex.getMessage());
            }
        });
    }

    private void ensureFile() {
        try {
            if (filePath.getParent() != null) {
                Files.createDirectories(filePath.getParent());
            }
        } catch (IOException ex) {
            plugin.getLogger().severe("Не удалось создать директорию runtime настроек: " + ex.getMessage());
        }
        File file = filePath.toFile();
        if (!file.exists()) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("durations.chatSeconds", -1L);
            yaml.set("durations.nicknameSeconds", -1L);
            try {
                yaml.save(file);
            } catch (IOException ex) {
                plugin.getLogger().severe("Не удалось создать runtime-settings.yml: " + ex.getMessage());
            }
        }
    }
}
