package com.example.antislurguard.pattern;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.antislurguard.AntiSlurGuardPlugin;
import com.example.antislurguard.config.Config;
import com.example.antislurguard.normalization.NormalizationService;

/**
 * Allow-list that takes precedence over all pattern matches.
 */
public final class ExceptionStore {

    private final AntiSlurGuardPlugin plugin;
    private final Path filePath;
    private final NormalizationService normalizationService;
    private final boolean autoVariants;
    private volatile List<RegexPattern> regexPatterns = List.of();
    private volatile List<LiteralPattern> literalPatterns = List.of();
    private volatile Set<String> rawEntries = Set.of();

    public ExceptionStore(AntiSlurGuardPlugin plugin, Path filePath, NormalizationService normalizationService,
            Config.PatternOptions options) {
        this.plugin = plugin;
        this.filePath = filePath;
        this.normalizationService = normalizationService;
        this.autoVariants = options.autoVariants();
    }

    public synchronized void reload() {
        Logger logger = plugin.getLogger();
        ensureFileExists();
        List<RegexPattern> loadedRegex = new ArrayList<>();
        List<LiteralPattern> literals = new ArrayList<>();
        Set<String> raw = new HashSet<>();
        try {
            List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                raw.add(line);
                boolean literal = autoVariants && !looksLikeRegex(line);
                if (literal) {
                    String normalized = normalizationService.normalize(line);
                    if (!normalized.isBlank()) {
                        Pattern normalizedPattern = Pattern.compile(Pattern.quote(normalized),
                                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                        literals.add(new LiteralPattern(line, normalizedPattern));
                    }
                }
                try {
                    Pattern pattern = Pattern.compile(line, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                    loadedRegex.add(new RegexPattern(line, pattern));
                } catch (Exception ex) {
                    int lineNumber = i + 1;
                    logger.warning(() -> "Не удалось скомпилировать regex исключения в строке " + lineNumber + ".");
                }
            }
        } catch (IOException ex) {
            logger.severe("Не удалось прочитать файл исключений: " + ex.getMessage());
        }
        this.regexPatterns = List.copyOf(loadedRegex);
        this.literalPatterns = List.copyOf(literals);
        this.rawEntries = Set.copyOf(raw);
    }

    public Optional<String> findMatch(String normalized, String original) {
        if ((normalized == null || normalized.isEmpty()) && (original == null || original.isEmpty())) {
            return Optional.empty();
        }
        String normalizedValue = normalized != null ? normalized : "";
        for (LiteralPattern literal : literalPatterns) {
            Matcher matcher = literal.normalizedPattern().matcher(normalizedValue);
            if (matcher.find()) {
                return Optional.of(literal.raw());
            }
        }
        String toCheck = original != null ? original : normalizedValue;
        for (RegexPattern pattern : regexPatterns) {
            Matcher matcher = pattern.pattern().matcher(toCheck);
            if (matcher.find()) {
                return Optional.of(pattern.raw());
            }
            if (!normalizedValue.isEmpty() && !normalizedValue.equals(toCheck)) {
                matcher = pattern.pattern().matcher(normalizedValue);
                if (matcher.find()) {
                    return Optional.of(pattern.raw());
                }
            }
        }
        return Optional.empty();
    }

    public boolean containsRaw(String value) {
        if (value == null) {
            return false;
        }
        return rawEntries.contains(value.trim());
    }

    public synchronized boolean append(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String trimmed = value.trim();
        if (rawEntries.contains(trimmed)) {
            return false;
        }
        try {
            Files.writeString(filePath, System.lineSeparator() + trimmed, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            reload();
            return true;
        } catch (IOException ex) {
            plugin.getLogger().severe("Не удалось сохранить исключение: " + ex.getMessage());
            return false;
        }
    }

    public synchronized boolean remove(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        ensureFileExists();
        String trimmed = value.trim();
        try {
            List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
            boolean removed = false;
            List<String> updated = new ArrayList<>(lines.size());
            for (String line : lines) {
                if (!removed && line.trim().equals(trimmed)) {
                    removed = true;
                    continue;
                }
                updated.add(line);
            }
            if (!removed) {
                return false;
            }
            Files.write(filePath, updated, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.CREATE);
            reload();
            return true;
        } catch (IOException ex) {
            plugin.getLogger().severe("Не удалось удалить исключение: " + ex.getMessage());
            return false;
        }
    }

    private void ensureFileExists() {
        if (Files.exists(filePath)) {
            return;
        }
        try {
            if (filePath.getParent() != null) {
                Files.createDirectories(filePath.getParent());
            }
            List<String> template = List.of(
                    "# AntiSlurGuard exceptions", 
                    "# Каждая строка — слово/фраза или regex, которые НЕЛЬЗЯ блокировать.",
                    "# Примеры:",
                    "# safeWord", 
                    "# ^specific\\sphrase$");
            Files.write(filePath, template, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            plugin.getLogger().severe("Не удалось создать файл исключений: " + ex.getMessage());
        }
    }

    private boolean looksLikeRegex(String value) {
        return value.chars().anyMatch(ch -> "^$.*+?{}[]\\|()".indexOf(ch) >= 0);
    }

    private record LiteralPattern(String raw, Pattern normalizedPattern) {
    }

    private record RegexPattern(String raw, Pattern pattern) {
    }
}
