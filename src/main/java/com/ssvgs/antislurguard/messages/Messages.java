package com.biggiko.antislurguard.messages;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

import org.bukkit.configuration.file.YamlConfiguration;

import com.biggiko.antislurguard.AntiSlurGuardPlugin;
import com.biggiko.antislurguard.language.LanguageBundle;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class Messages {

    private final AntiSlurGuardPlugin plugin;
    private final Path filePath;
    private YamlConfiguration yaml;
    private LanguageBundle languageBundle;

    public Messages(AntiSlurGuardPlugin plugin, Path filePath) {
        this.plugin = plugin;
        this.filePath = filePath;
    }

    public void reload(LanguageBundle bundle) {
        this.languageBundle = bundle;
        ensureFileExists();
        syncLanguageFile();
        File file = filePath.toFile();
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    public Component component(String key, String fallback, Map<String, String> placeholders) {
        String raw = raw(key, fallback);
        String withPlaceholders = applyPlaceholders(raw, placeholders);
        return LegacyComponentSerializer.legacyAmpersand().deserialize(withPlaceholders);
    }

    public String raw(String key, String fallback) {
        if (yaml != null && yaml.contains(key)) {
            return yaml.getString(key);
        }
        if (languageBundle != null) {
            String localized = languageBundle.message(key);
            if (localized != null) {
                return localized;
            }
        }
        return fallback != null ? fallback : "";
    }

    private String applyPlaceholders(String input, Map<String, String> placeholders) {
        if (input == null || placeholders == null || placeholders.isEmpty()) {
            return Objects.requireNonNullElse(input, "");
        }
        String result = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String token = "{" + entry.getKey() + "}";
            result = result.replace(token, entry.getValue());
        }
        return result;
    }

    private void ensureFileExists() {
        try {
            if (filePath.getParent() != null) {
                Files.createDirectories(filePath.getParent());
            }
        } catch (IOException ex) {
            plugin.getLogger().severe("Не удалось создать директорию сообщений: " + ex.getMessage());
        }
        File file = filePath.toFile();
        if (file.exists()) {
            return;
        }
        try {
            if (languageBundle != null) {
                languageBundle.writeMessagesTemplate(filePath);
                return;
            }
        } catch (IOException ex) {
            plugin.getLogger().severe("Не удалось создать messages.yml: " + ex.getMessage());
            return;
        }
        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in != null) {
                Files.copy(in, filePath);
                return;
            }
        } catch (IOException ex) {
            plugin.getLogger().severe("Не удалось создать messages.yml: " + ex.getMessage());
        }
        try {
            Files.writeString(filePath, "player-chat-block: \"&7Message blocked\"", StandardCharsets.UTF_8);
        } catch (IOException ex) {
            plugin.getLogger().severe("Не удалось записать messages.yml: " + ex.getMessage());
        }
    }

    private void syncLanguageFile() {
        if (languageBundle == null) {
            return;
        }
        File file = filePath.toFile();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration current = YamlConfiguration.loadConfiguration(file);
        String currentLang = current.getString("_meta.language");
        if (currentLang != null && currentLang.equalsIgnoreCase(languageBundle.code())) {
            return;
        }
        try {
            languageBundle.writeMessagesTemplate(filePath);
        } catch (IOException ex) {
            plugin.getLogger()
                    .severe("Не удалось синхронизировать messages.yml с языком " + languageBundle.code() + ": "
                            + ex.getMessage());
        }
    }
}
