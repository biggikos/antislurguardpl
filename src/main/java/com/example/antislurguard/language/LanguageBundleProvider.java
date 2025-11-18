package com.example.antislurguard.language;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import com.example.antislurguard.AntiSlurGuardPlugin;

public final class LanguageBundleProvider {

    private final AntiSlurGuardPlugin plugin;
    private final Path languagesDir;

    public LanguageBundleProvider(AntiSlurGuardPlugin plugin, Path languagesDir) {
        this.plugin = plugin;
        this.languagesDir = languagesDir;
    }

    public LanguageBundle load(String requestedCode) {
        ensureBaseLanguages();
        String code = requestedCode == null || requestedCode.isBlank() ? "en_US"
                : requestedCode.replace('-', '_');
        LanguageBundle fallback = loadInternal("en_US", null);
        if (code.equalsIgnoreCase("en_US")) {
            return fallback;
        }
        LanguageBundle bundle = loadInternal(code, fallback);
        return bundle != null ? bundle : fallback;
    }

    private LanguageBundle loadInternal(String code, LanguageBundle fallback) {
        try {
            ensureLanguageFileExists(code);
            File file = languagesDir.resolve(code + ".yml").toFile();
            return LanguageBundle.fromFile(code, file, fallback);
        } catch (IOException ex) {
            plugin.getLogger().severe("Не удалось загрузить язык " + code + ": " + ex.getMessage());
            return fallback;
        }
    }

    private void ensureBaseLanguages() {
        try {
            ensureLanguageFileExists("en_US");
            ensureLanguageFileExists("ru_RU");
        } catch (IOException ex) {
            plugin.getLogger().severe("Не удалось подготовить языковые файлы: " + ex.getMessage());
        }
    }

    private void ensureLanguageFileExists(String code) throws IOException {
        if (languagesDir.getParent() == null) {
            Files.createDirectories(languagesDir);
        } else {
            Files.createDirectories(languagesDir);
        }
        Path target = languagesDir.resolve(code + ".yml");
        if (Files.exists(target)) {
            return;
        }
        String resourcePath = "lang/" + code + ".yml";
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in != null) {
                Files.copy(in, target);
                return;
            }
        }
        // fallback to english resource if specific file missing
        try (InputStream fallback = plugin.getResource("lang/en_US.yml")) {
            if (fallback != null) {
                Files.copy(fallback, target);
            } else {
                Files.writeString(target, "messages: {}\nconfig-comments: {}\n");
            }
        }
    }
}
