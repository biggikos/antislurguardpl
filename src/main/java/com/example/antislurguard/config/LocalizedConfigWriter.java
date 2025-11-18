package com.example.antislurguard.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.example.antislurguard.AntiSlurGuardPlugin;
import com.example.antislurguard.language.LanguageBundle;

public final class LocalizedConfigWriter {

    private final AntiSlurGuardPlugin plugin;
    private final Path configPath;

    public LocalizedConfigWriter(AntiSlurGuardPlugin plugin) {
        this.plugin = plugin;
        this.configPath = plugin.getDataFolder().toPath().resolve("config.yml");
    }

    public void write(Config config, LanguageBundle bundle) {
        if (bundle == null) {
            return;
        }
        try {
            if (configPath.getParent() != null) {
                Files.createDirectories(configPath.getParent());
            }
        } catch (IOException ex) {
            plugin.getLogger().severe("Не удалось создать директорию плагина: " + ex.getMessage());
            return;
        }
        StringBuilder sb = new StringBuilder();
        appendComment(sb, bundle.configComment("header"), 0);
        appendComment(sb, bundle.configComment("lang"), 0);
        appendKeyValue(sb, 0, "lang", quote(config.lang()));
        sb.append(System.lineSeparator());

        appendSectionHeader(sb, bundle.configComment("paths.header"));
        appendKeyValue(sb, 0, "paths:", null);
        appendComment(sb, bundle.configComment("paths.patternsFile"), 2);
        appendKeyValue(sb, 2, "patternsFile", quote(config.paths().patternsFile()));
        appendComment(sb, bundle.configComment("paths.exceptionsFile"), 2);
        appendKeyValue(sb, 2, "exceptionsFile", quote(config.paths().exceptionsFile()));
        appendComment(sb, bundle.configComment("paths.messagesFile"), 2);
        appendKeyValue(sb, 2, "messagesFile", quote(config.paths().messagesFile()));
        appendComment(sb, bundle.configComment("paths.adminNotifyFile"), 2);
        appendKeyValue(sb, 2, "adminNotifyFile", quote(config.paths().adminNotifyFile()));
        appendComment(sb, bundle.configComment("paths.playerStatsFile"), 2);
        appendKeyValue(sb, 2, "playerStatsFile", quote(config.paths().playerStatsFile()));
        appendComment(sb, bundle.configComment("paths.userDataDir"), 2);
        appendKeyValue(sb, 2, "userDataDir", quote(config.paths().userDataDir()));
        appendComment(sb, bundle.configComment("paths.runtimeSettingsFile"), 2);
        appendKeyValue(sb, 2, "runtimeSettingsFile", quote(config.paths().runtimeSettingsFile()));
        appendComment(sb, bundle.configComment("paths.announcementsFile"), 2);
        appendKeyValue(sb, 2, "announcementsFile", quote(config.paths().announcementsFile()));
        appendComment(sb, bundle.configComment("paths.languagesDir"), 2);
        appendKeyValue(sb, 2, "languagesDir", quote(config.paths().languagesDir()));
        sb.append(System.lineSeparator());

        appendSectionHeader(sb, bundle.configComment("patterns.header"));
        appendKeyValue(sb, 0, "patterns:", null);
        appendComment(sb, bundle.configComment("patterns.autoVariants"), 2);
        appendKeyValue(sb, 2, "autoVariants", Boolean.toString(config.patternOptions().autoVariants()));
        sb.append(System.lineSeparator());

        appendSectionHeader(sb, bundle.configComment("normalize.header"));
        appendKeyValue(sb, 0, "normalize:", null);
        appendComment(sb, bundle.configComment("normalize.examples"), 2);
        appendNormalize(sb, bundle, config.normalize());
        sb.append(System.lineSeparator());

        appendSectionHeader(sb, bundle.configComment("permissions.header"));
        appendKeyValue(sb, 0, "permissions:", null);
        appendKeyValue(sb, 2, "bypass", quote(config.permissions().bypass()));
        appendKeyValue(sb, 2, "admin", quote(config.permissions().admin()));
        sb.append(System.lineSeparator());

        appendSectionHeader(sb, bundle.configComment("console.header"));
        appendKeyValue(sb, 0, "console:", null);
        appendComment(sb, bundle.configComment("console.aggregate"), 2);
        appendKeyValue(sb, 2, "aggregateIntervalSeconds", Long.toString(config.console().aggregateIntervalSeconds()));
        appendComment(sb, bundle.configComment("console.threshold"), 2);
        appendKeyValue(sb, 2, "massAlertThreshold", Integer.toString(config.console().massAlertThreshold()));
        sb.append(System.lineSeparator());

        appendSectionHeader(sb, bundle.configComment("notifications.header"));
        appendKeyValue(sb, 0, "notifications:", null);
        appendKeyValue(sb, 2, "player:", null);
        appendKeyValue(sb, 4, "enabled", Boolean.toString(config.notifications().player().enabled()));
        appendKeyValue(sb, 4, "messageKey", quote(config.notifications().player().messageKey()));
        appendKeyValue(sb, 2, "admin:", null);
        appendKeyValue(sb, 4, "enabled", Boolean.toString(config.notifications().admin().enabled()));
        appendKeyValue(sb, 4, "chatMessageKey", quote(config.notifications().admin().chatMessageKey()));
        sb.append(System.lineSeparator());

        appendSectionHeader(sb, bundle.configComment("antiSpam.header"));
        appendKeyValue(sb, 0, "antiSpam:", null);
        appendKeyValue(sb, 2, "enabled", Boolean.toString(config.antiSpam().enabled()));
        appendKeyValue(sb, 2, "repeatWindowSeconds", Long.toString(config.antiSpam().repeatWindowSeconds()));
        appendKeyValue(sb, 2, "repeatThreshold", Integer.toString(config.antiSpam().repeatThreshold()));
        appendKeyValue(sb, 2, "slowmodeDurationSeconds", Long.toString(config.antiSpam().slowmodeDurationSeconds()));
        appendKeyValue(sb, 2, "slowmodeCooldownSeconds", Long.toString(config.antiSpam().slowmodeCooldownSeconds()));
        appendKeyValue(sb, 2, "messages:", null);
        appendKeyValue(sb, 4, "playerTriggeredKey", quote(config.antiSpam().messages().playerTriggeredKey()));
        appendKeyValue(sb, 4, "playerCooldownKey", quote(config.antiSpam().messages().playerCooldownKey()));
        appendKeyValue(sb, 4, "adminTriggeredKey", quote(config.antiSpam().messages().adminTriggeredKey()));
        sb.append(System.lineSeparator());

        appendSectionHeader(sb, bundle.configComment("placeholders.header"));

        appendSectionHeader(sb, bundle.configComment("punishments.header"));
        appendKeyValue(sb, 0, "punishments:", null);
        appendPunishment(sb, "nickname", config.punishments().nickname());
        appendPunishment(sb, "chat", config.punishments().chat());
        sb.append(System.lineSeparator());

        appendSectionHeader(sb, bundle.configComment("stats.header"));
        appendKeyValue(sb, 0, "stats:", null);
        appendKeyValue(sb, 2, "trackChatViolations", Boolean.toString(config.stats().trackChatViolations()));
        appendKeyValue(sb, 2, "autoPermaBanThreshold", Integer.toString(config.stats().autoPermaBanThreshold()));
        appendKeyValue(sb, 2, "autoPermaBanReasonKey", quote(config.stats().autoPermaBanReasonKey()));
        sb.append(System.lineSeparator());

        appendSectionHeader(sb, bundle.configComment("essentials.header"));
        appendKeyValue(sb, 0, "essentials:", null);
        appendKeyValue(sb, 2, "enabled", Boolean.toString(config.essentials().enabled()));
        appendKeyValue(sb, 2, "kickCommand", quote(config.essentials().kickCommand()));
        appendKeyValue(sb, 2, "banCommand", quote(config.essentials().banCommand()));
        appendKeyValue(sb, 2, "tempBanCommand", quote(config.essentials().tempBanCommand()));
        appendKeyValue(sb, 2, "muteCommand", quote(config.essentials().muteCommand()));
        sb.append(System.lineSeparator());

        appendSectionHeader(sb, bundle.configComment("announcements.header"));
        appendKeyValue(sb, 0, "announcements:", null);
        appendKeyValue(sb, 2, "enabled", Boolean.toString(config.announcements().enabled()));
        appendKeyValue(sb, 2, "defaultIntervalSeconds", Long.toString(config.announcements().defaultIntervalSeconds()));
        sb.append(System.lineSeparator());

        appendSectionHeader(sb, bundle.configComment("management.header"));
        appendKeyValue(sb, 0, "management:", null);
        appendComment(sb, bundle.configComment("management.unmuteCommand"), 2);
        appendKeyValue(sb, 2, "unmuteCommand", quote(config.management().unmuteCommand()));
        appendComment(sb, bundle.configComment("management.unbanCommand"), 2);
        appendKeyValue(sb, 2, "unbanCommand", quote(config.management().unbanCommand()));

        try {
            Files.writeString(configPath, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            plugin.getLogger().severe("Не удалось записать локализованный config.yml: " + ex.getMessage());
        }
    }

    private void appendNormalize(StringBuilder sb, LanguageBundle bundle, Config.NormalizeSettings settings) {
        appendKeyValue(sb, 2, "enabled", Boolean.toString(settings.enabled()));
        appendKeyValue(sb, 2, "caseFold", Boolean.toString(settings.caseFold()));
        appendKeyValue(sb, 2, "nfdStripDiacritics", Boolean.toString(settings.nfdStripDiacritics()));
        appendKeyValue(sb, 2, "leetMappings", Boolean.toString(settings.leetMappings()));
        appendKeyValue(sb, 2, "stripNonAlnum", Boolean.toString(settings.stripNonAlnum()));
        appendKeyValue(sb, 2, "collapseRepeats", Boolean.toString(settings.collapseRepeats()));
        appendKeyValue(sb, 2, "transliterateCyrillic", Boolean.toString(settings.transliterateCyrillic()));
    }

    private void appendPunishment(StringBuilder sb, String key, Config.Punishment punishment) {
        appendKeyValue(sb, 2, key + ":", null);
        appendKeyValue(sb, 4, "action", punishment.action().name());
        appendKeyValue(sb, 4, "durationSeconds", Long.toString(punishment.durationSeconds()));
        appendKeyValue(sb, 4, "reason", quote(punishment.reason()));
        appendKeyValue(sb, 4, "command", quote(punishment.command()));
    }

    private void appendSectionHeader(StringBuilder sb, String comment) {
        appendComment(sb, comment, 0);
    }

    private void appendComment(StringBuilder sb, String comment, int indent) {
        if (comment == null || comment.isBlank()) {
            return;
        }
        String[] lines = comment.split("\\r?\\n");
        for (String line : lines) {
            indent(sb, indent).append("# ").append(line).append(System.lineSeparator());
        }
    }

    private void appendKeyValue(StringBuilder sb, int indent, String key, String value) {
        indent(sb, indent).append(key);
        if (value != null) {
            sb.append(": ").append(value);
        }
        sb.append(System.lineSeparator());
    }

    private StringBuilder indent(StringBuilder sb, int spaces) {
        for (int i = 0; i < spaces; i++) {
            sb.append(' ');
        }
        return sb;
    }

    private String quote(String value) {
        if (value == null) {
            return "\"\"";
        }
        String escaped = value.replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }
}
