package com.biggiko.antislurguard.pattern;

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

import com.biggiko.antislurguard.AntiSlurGuardPlugin;
import com.biggiko.antislurguard.config.Config;
import com.biggiko.antislurguard.normalization.NormalizationService;

public final class PatternStore {

    private final AntiSlurGuardPlugin plugin;
    private final Path filePath;
    private final NormalizationService normalizationService;
    private final boolean autoVariants;
    private volatile List<RegexPattern> regexPatterns = List.of();
    private volatile List<LiteralPattern> literalPatterns = List.of();
    private volatile Set<String> rawEntries = Set.of();

    public PatternStore(AntiSlurGuardPlugin plugin, Path filePath, NormalizationService normalizationService,
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
                        literals.add(new LiteralPattern(line, normalized, normalizedPattern));
                    }
                }
                try {
                    Pattern pattern = Pattern.compile(line, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                    loadedRegex.add(new RegexPattern(line, pattern));
                } catch (Exception ex) {
                    int lineNumber = i + 1;
                    logger.warning(() -> "Не удалось скомпилировать regex в строке " + lineNumber + ".");
                }
            }
        } catch (IOException ex) {
            logger.severe("Не удалось прочитать файл с шаблонами: " + ex.getMessage());
        }
        this.regexPatterns = List.copyOf(loadedRegex);
        this.literalPatterns = List.copyOf(literals);
        this.rawEntries = Set.copyOf(raw);
        if (this.regexPatterns.isEmpty() && this.literalPatterns.isEmpty()) {
            logger.warning("AntiSlurGuard запущен в безопасном режиме: список шаблонов пуст.");
        } else {
            logger.info("AntiSlurGuard загрузил " + (this.regexPatterns.size() + this.literalPatterns.size())
                    + " шаблон(ов).");
        }
    }

    public Optional<PatternMatch> findMatch(String normalized) {
        if (normalized == null || normalized.isEmpty()) {
            return Optional.empty();
        }
        for (LiteralPattern literal : literalPatterns) {
            Matcher matcher = literal.normalizedPattern().matcher(normalized);
            if (matcher.find()) {
                return Optional.of(new PatternMatch(literal.raw(), literal.raw(), MatchOrigin.LITERAL));
            }
        }
        for (RegexPattern pattern : regexPatterns) {
            Matcher matcher = pattern.pattern().matcher(normalized);
            if (matcher.find()) {
                String match = matcher.group();
                return Optional.of(new PatternMatch(pattern.raw(), match, MatchOrigin.REGEX));
            }
        }
        return Optional.empty();
    }

    public boolean isEmpty() {
        return regexPatterns.isEmpty() && literalPatterns.isEmpty();
    }

    public synchronized boolean appendPattern(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return false;
        }
        String trimmed = pattern.trim();
        if (rawEntries.contains(trimmed)) {
            return false;
        }
        try {
            Files.writeString(filePath, System.lineSeparator() + trimmed, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            reload();
            return true;
        } catch (IOException ex) {
            plugin.getLogger().severe("Не удалось записать шаблон: " + ex.getMessage());
            return false;
        }
    }

    public boolean containsRaw(String value) {
        if (value == null) {
            return false;
        }
        return rawEntries.contains(value.trim());
    }

    public synchronized boolean removePattern(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return false;
        }
        ensureFileExists();
        String trimmed = pattern.trim();
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
            plugin.getLogger().severe("Не удалось удалить шаблон: " + ex.getMessage());
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
                    "# AntiSlurGuard banned patterns",
                    "# Добавьте сюда запрещённые выражения. Примеры:",
                    "# placeholder",
                    "# ^regex$",
                    "# Простые слова автоматически получат десятки вариаций.");
            Files.write(filePath, template, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            plugin.getLogger().severe("Не удалось создать файл паттернов: " + ex.getMessage());
        }
    }

    private boolean looksLikeRegex(String value) {
        return value.chars().anyMatch(ch -> "^$.*+?{}[]\\|()".indexOf(ch) >= 0);
    }

    public record PatternMatch(String pattern, String match, MatchOrigin origin) {
    }

    private record LiteralPattern(String raw, String normalized, Pattern normalizedPattern) {
    }

    private record RegexPattern(String raw, Pattern pattern) {
    }

    public enum MatchOrigin {
        REGEX("regex"),
        LITERAL("literal");

        private final String type;

        MatchOrigin(String type) {
            this.type = type;
        }

        public String type() {
            return type;
        }
    }
}
