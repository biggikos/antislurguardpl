package com.biggiko.antislurguard.language;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class LanguageBundle {

    private final String code;
    private final YamlConfiguration yaml;
    private final LanguageBundle fallback;

    public LanguageBundle(String code, YamlConfiguration yaml, LanguageBundle fallback) {
        this.code = code;
        this.yaml = yaml;
        this.fallback = fallback;
    }

    public String code() {
        return code;
    }

    public String message(String key) {
        if (key == null) {
            return null;
        }
        String value = yaml != null ? yaml.getString("messages." + key) : null;
        if (value != null) {
            return value;
        }
        return fallback != null ? fallback.message(key) : null;
    }

    public String configComment(String key) {
        if (key == null) {
            return null;
        }
        String value = yaml != null ? yaml.getString("config-comments." + key) : null;
        if (value != null) {
            return value;
        }
        return fallback != null ? fallback.configComment(key) : null;
    }

    public Map<String, String> messagesSnapshot() {
        ConfigurationSection section = yaml != null ? yaml.getConfigurationSection("messages") : null;
        if (section == null || section.getKeys(false).isEmpty()) {
            return fallback != null ? fallback.messagesSnapshot() : Collections.emptyMap();
        }
        Map<String, String> values = new HashMap<>();
        for (String key : section.getKeys(false)) {
            String value = section.getString(key);
            if (value != null) {
                values.put(key, value);
            }
        }
        return values;
    }

    public void writeMessagesTemplate(Path target) throws IOException {
        Map<String, String> values = messagesSnapshot();
        YamlConfiguration out = new YamlConfiguration();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            out.set(entry.getKey(), entry.getValue());
        }
        out.set("_meta.language", code);
        File file = target.toFile();
        out.save(file);
    }

    public static LanguageBundle fromFile(String code, File file, LanguageBundle fallback) throws IOException {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        return new LanguageBundle(code, yaml, fallback);
    }

    public static LanguageBundle fromResource(String code, YamlConfiguration yaml, LanguageBundle fallback) {
        return new LanguageBundle(code, Objects.requireNonNull(yaml, "yaml"), fallback);
    }
}
