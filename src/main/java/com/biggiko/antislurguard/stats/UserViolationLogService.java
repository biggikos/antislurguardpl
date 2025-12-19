package com.biggiko.antislurguard.stats;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.biggiko.antislurguard.AntiSlurGuardPlugin;
import com.biggiko.antislurguard.pattern.PatternStore.PatternMatch;

/**
 * Persists full violation messages per user for audit purposes.
 */
public final class UserViolationLogService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private static final Pattern LINE_PATTERN = Pattern
            .compile("^\\[(?<ts>[^\\]]+)\\] .*?origin=(?<origin>[^ ]+) pattern=(?<pattern>[^ ]+) message=(?<message>.*)$");

    private final AntiSlurGuardPlugin plugin;
    private final Path directory;

    public UserViolationLogService(AntiSlurGuardPlugin plugin, Path directory) {
        this.plugin = plugin;
        this.directory = directory;
        try {
            Files.createDirectories(directory);
        } catch (IOException ex) {
            plugin.getLogger().severe("Не удалось создать каталог userdata: " + ex.getMessage());
        }
    }

    public void recordChat(UUID uuid, String playerName, String originalMessage, PatternMatch match) {
        if (originalMessage == null) {
            return;
        }
        String timestamp = FORMATTER.format(Instant.now());
        String sanitizedName = sanitizeName(playerName);
        String pattern = match != null ? match.pattern() : "unknown";
        String origin = match != null ? match.origin().type() : "unknown";
        String entry = String.format("[%s] player=%s origin=%s pattern=%s message=%s%s",
                timestamp, sanitizedName, origin, pattern, originalMessage, System.lineSeparator());
        Path file = resolveFileForWrite(uuid, sanitizedName);
        if (file == null) {
            return;
        }
        try {
            Files.writeString(file, entry, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            plugin.getLogger().warning("Не удалось записать userdata/" + file.getFileName() + ": " + ex.getMessage());
        }
    }

    public List<ViolationEntry> readEntries(String playerName, int limit) {
        Path file = resolveFileForRead(playerName);
        if (file == null || !Files.exists(file)) {
            return List.of();
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            List<ViolationEntry> entries = new ArrayList<>(lines.size());
            for (String line : lines) {
                parseLine(line).ifPresent(entries::add);
            }
            Collections.reverse(entries);
            if (limit > 0 && entries.size() > limit) {
                return entries.subList(0, limit);
            }
            return entries;
        } catch (IOException ex) {
            plugin.getLogger().warning("Не удалось прочитать userdata/" + file.getFileName() + ": " + ex.getMessage());
            return List.of();
        }
    }

    private Optional<ViolationEntry> parseLine(String line) {
        Matcher matcher = LINE_PATTERN.matcher(line);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String ts = matcher.group("ts");
        String origin = matcher.group("origin");
        String pattern = matcher.group("pattern");
        String message = matcher.group("message");
        try {
            Instant instant = Instant.from(FORMATTER.parse(ts));
            return Optional.of(new ViolationEntry(instant, origin, pattern, message));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private Path resolveFileForWrite(UUID uuid, String sanitizedName) {
        if (sanitizedName.isEmpty() && uuid == null) {
            return null;
        }
        String fileName = !sanitizedName.isEmpty() ? sanitizedName : uuid.toString();
        return directory.resolve(fileName + ".log");
    }

    private Path resolveFileForRead(String playerName) {
        String sanitized = sanitizeName(playerName);
        if (!sanitized.isEmpty()) {
            Path file = directory.resolve(sanitized + ".log");
            if (Files.exists(file)) {
                return file;
            }
        }
        if (playerName != null) {
            try {
                UUID uuid = UUID.fromString(playerName);
                Path legacy = directory.resolve(uuid.toString() + ".log");
                if (Files.exists(legacy)) {
                    return legacy;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    private String sanitizeName(String playerName) {
        if (playerName == null) {
            return "";
        }
        String cleaned = playerName.replaceAll("[^A-Za-z0-9_.-]", "_");
        return cleaned.trim();
    }

    public record ViolationEntry(Instant timestamp, String origin, String pattern, String message) {
        public String relativeTime() {
            Duration duration = Duration.between(timestamp, Instant.now()).abs();
            if (duration.compareTo(Duration.ofDays(2)) > 0) {
                return FORMATTER.format(timestamp);
            }
            if (duration.toDays() >= 1) {
                return duration.toDays() + "d";
            }
            long hours = duration.toHours();
            if (hours >= 1) {
                long minutes = duration.minus(hours, ChronoUnit.HOURS).toMinutes();
                return hours + "h" + (minutes > 0 ? (" " + minutes + "m") : "");
            }
            long minutes = duration.toMinutes();
            if (minutes >= 1) {
                return minutes + "m";
            }
            long seconds = Math.max(1, duration.getSeconds());
            return seconds + "s";
        }
    }
}
