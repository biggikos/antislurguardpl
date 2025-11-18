package com.example.antislurguard.config;

import java.util.Locale;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public record Config(
        String lang,
        Paths paths,
        PatternOptions patternOptions,
        NormalizeSettings normalize,
        Permissions permissions,
        ConsoleSettings console,
        Punishments punishments,
        Notifications notifications,
        AntiSpamSettings antiSpam,
        StatsSettings stats,
        EssentialsSettings essentials,
        AnnouncementsSettings announcements,
        ManagementSettings management) {

    public static Config from(FileConfiguration config) {
        String lang = config.getString("lang", "en_US");
        Paths paths = Paths.from(config.getConfigurationSection("paths"));
        PatternOptions patternOptions = PatternOptions.from(config.getConfigurationSection("patterns"));
        NormalizeSettings normalize = NormalizeSettings.from(config.getConfigurationSection("normalize"));
        Permissions permissions = Permissions.from(config.getConfigurationSection("permissions"));
        ConsoleSettings console = ConsoleSettings.from(config.getConfigurationSection("console"));
        Punishments punishments = Punishments.from(config.getConfigurationSection("punishments"));
        Notifications notifications = Notifications.from(config.getConfigurationSection("notifications"));
        AntiSpamSettings antiSpam = AntiSpamSettings.from(config.getConfigurationSection("antiSpam"));
        StatsSettings stats = StatsSettings.from(config.getConfigurationSection("stats"));
        EssentialsSettings essentials = EssentialsSettings.from(config.getConfigurationSection("essentials"));
        AnnouncementsSettings announcements = AnnouncementsSettings
                .from(config.getConfigurationSection("announcements"));
        ManagementSettings management = ManagementSettings.from(config.getConfigurationSection("management"));
        return new Config(lang, paths, patternOptions, normalize, permissions, console, punishments, notifications, antiSpam,
                stats, essentials, announcements, management);
    }

    public String patternsFile() {
        return paths.patternsFile();
    }

    public record Paths(String patternsFile,
            String exceptionsFile,
            String messagesFile,
            String adminNotifyFile,
            String playerStatsFile,
            String userDataDir,
            String runtimeSettingsFile,
            String announcementsFile,
            String languagesDir) {
        public static Paths from(ConfigurationSection section) {
            String base = "plugins/AntiSlurGuard";
            if (section == null) {
                return new Paths(base + "/banned-patterns.txt", base + "/exceptions.txt", base + "/messages.yml",
                        base + "/admin-notify.yml", base + "/player-stats.yml", base + "/userdata",
                        base + "/runtime-settings.yml", base + "/announcements.yml", base + "/lang");
            }
            String patterns = section.getString("patternsFile", base + "/banned-patterns.txt");
            String exceptions = section.getString("exceptionsFile", base + "/exceptions.txt");
            String messages = section.getString("messagesFile", base + "/messages.yml");
            String admin = section.getString("adminNotifyFile", base + "/admin-notify.yml");
            String stats = section.getString("playerStatsFile", base + "/player-stats.yml");
            String userDataDir = section.getString("userDataDir", base + "/userdata");
            String runtime = section.getString("runtimeSettingsFile", base + "/runtime-settings.yml");
            String announce = section.getString("announcementsFile", base + "/announcements.yml");
            String languages = section.getString("languagesDir", base + "/lang");
            return new Paths(patterns, exceptions, messages, admin, stats, userDataDir, runtime, announce, languages);
        }
    }

    public record PatternOptions(boolean autoVariants) {
        public static PatternOptions from(ConfigurationSection section) {
            if (section == null) {
                return new PatternOptions(true);
            }
            return new PatternOptions(section.getBoolean("autoVariants", true));
        }
    }

    public record NormalizeSettings(
            boolean enabled,
            boolean caseFold,
            boolean nfdStripDiacritics,
            boolean leetMappings,
            boolean stripNonAlnum,
            boolean collapseRepeats,
            boolean transliterateCyrillic) {

        public static NormalizeSettings from(ConfigurationSection section) {
            if (section == null) {
                return new NormalizeSettings(false, true, true, true, true, true, true);
            }
            return new NormalizeSettings(
                    section.getBoolean("enabled", false),
                    section.getBoolean("caseFold", true),
                    section.getBoolean("nfdStripDiacritics", true),
                    section.getBoolean("leetMappings", true),
                    section.getBoolean("stripNonAlnum", true),
                    section.getBoolean("collapseRepeats", true),
                    section.getBoolean("transliterateCyrillic", true));
        }
    }

    public record Permissions(String bypass, String admin) {
        public static Permissions from(ConfigurationSection section) {
            String bypass = "antislurguard.bypass";
            String admin = "antislurguard.admin";
            if (section != null) {
                bypass = section.getString("bypass", bypass);
                admin = section.getString("admin", admin);
            }
            return new Permissions(bypass, admin);
        }
    }

    public record ConsoleSettings(long aggregateIntervalSeconds, int massAlertThreshold) {
        public static ConsoleSettings from(ConfigurationSection section) {
            if (section == null) {
                return new ConsoleSettings(60L, 10);
            }
            long interval = section.getLong("aggregateIntervalSeconds", 60L);
            int threshold = section.getInt("massAlertThreshold", 10);
            return new ConsoleSettings(interval, threshold);
        }
    }

    public record Punishments(Punishment nickname, Punishment chat) {
        public static Punishments from(ConfigurationSection section) {
            if (section == null) {
                return new Punishments(Punishment.defaultNickname(), Punishment.defaultChat());
            }
            Punishment nickname = Punishment.from(section.getConfigurationSection("nickname"),
                    Punishment.defaultNickname());
            Punishment chat = Punishment.from(section.getConfigurationSection("chat"), Punishment.defaultChat());
            return new Punishments(nickname, chat);
        }
    }

    public record Punishment(PunishmentAction action, long durationSeconds, String reason, String command) {

        public static Punishment from(ConfigurationSection section, Punishment fallback) {
            if (section == null) {
                return fallback;
            }
            PunishmentAction action = PunishmentAction.from(section.getString("action", fallback.action.name()));
            long duration = section.getLong("durationSeconds", fallback.durationSeconds);
            String reason = section.getString("reason", fallback.reason);
            String command = section.getString("command", fallback.command);
            return new Punishment(action, duration, reason, command);
        }

        public static Punishment defaultNickname() {
            return new Punishment(PunishmentAction.DISALLOW, 0L, " ", "");
        }

        public static Punishment defaultChat() {
            return new Punishment(PunishmentAction.NONE, 0L, " ", "");
        }
    }

    public record Notifications(PlayerNotification player, AdminNotification admin) {
        public static Notifications from(ConfigurationSection section) {
            if (section == null) {
                return new Notifications(PlayerNotification.defaults(), AdminNotification.defaults());
            }
            PlayerNotification player = PlayerNotification.from(section.getConfigurationSection("player"));
            AdminNotification admin = AdminNotification.from(section.getConfigurationSection("admin"));
            return new Notifications(player, admin);
        }
    }

    public record AntiSpamSettings(
            boolean enabled,
            long repeatWindowSeconds,
            int repeatThreshold,
            long slowmodeDurationSeconds,
            long slowmodeCooldownSeconds,
            AntiSpamMessages messages) {

        public static AntiSpamSettings from(ConfigurationSection section) {
            if (section == null) {
                return defaults();
            }
            boolean enabled = section.getBoolean("enabled", true);
            long repeatWindowSeconds = section.getLong("repeatWindowSeconds", 15L);
            int repeatThreshold = section.getInt("repeatThreshold", 2);
            long slowmodeDurationSeconds = section.getLong("slowmodeDurationSeconds", 60L);
            long slowmodeCooldownSeconds = section.getLong("slowmodeCooldownSeconds", 10L);
            AntiSpamMessages messages = AntiSpamMessages
                    .from(section.getConfigurationSection("messages"));
            return new AntiSpamSettings(enabled, repeatWindowSeconds, repeatThreshold, slowmodeDurationSeconds,
                    slowmodeCooldownSeconds, messages);
        }

        public static AntiSpamSettings defaults() {
            return new AntiSpamSettings(true, 15L, 2, 60L, 10L, AntiSpamMessages.defaults());
        }
    }

    public record AntiSpamMessages(String playerTriggeredKey, String playerCooldownKey, String adminTriggeredKey) {
        public static AntiSpamMessages from(ConfigurationSection section) {
            if (section == null) {
                return defaults();
            }
            String triggered = section.getString("playerTriggeredKey", "player-slowmode-start");
            String cooldown = section.getString("playerCooldownKey", "player-slowmode-wait");
            String admin = section.getString("adminTriggeredKey", "admin-slowmode-alert");
            return new AntiSpamMessages(triggered, cooldown, admin);
        }

        public static AntiSpamMessages defaults() {
            return new AntiSpamMessages("player-slowmode-start", "player-slowmode-wait", "admin-slowmode-alert");
        }
    }

    public record PlayerNotification(boolean enabled, String messageKey) {
        public static PlayerNotification from(ConfigurationSection section) {
            if (section == null) {
                return defaults();
            }
            boolean enabled = section.getBoolean("enabled", false);
            String messageKey = section.getString("messageKey", "player-chat-block");
            return new PlayerNotification(enabled, messageKey);
        }

        public static PlayerNotification defaults() {
            return new PlayerNotification(false, "player-chat-block");
        }
    }

    public record AdminNotification(boolean enabled, String chatMessageKey) {
        public static AdminNotification from(ConfigurationSection section) {
            if (section == null) {
                return defaults();
            }
            boolean enabled = section.getBoolean("enabled", true);
            String chat = section.getString("chatMessageKey", "admin-chat-alert");
            return new AdminNotification(enabled, chat);
        }

        public static AdminNotification defaults() {
            return new AdminNotification(true, "admin-chat-alert");
        }
    }

    public record StatsSettings(boolean trackChatViolations, int autoPermaBanThreshold, String autoPermaBanReasonKey) {
        public static StatsSettings from(ConfigurationSection section) {
            if (section == null) {
                return new StatsSettings(true, 5, "auto-permaban-reason");
            }
            boolean track = section.getBoolean("trackChatViolations", true);
            int threshold = section.getInt("autoPermaBanThreshold", 5);
            String reasonKey = section.getString("autoPermaBanReasonKey", "auto-permaban-reason");
            return new StatsSettings(track, threshold, reasonKey);
        }
    }

    public record EssentialsSettings(boolean enabled, String kickCommand, String banCommand, String tempBanCommand,
            String muteCommand) {
        public static EssentialsSettings from(ConfigurationSection section) {
            if (section == null) {
                return new EssentialsSettings(true, "essentials:kick {player} {reason}",
                        "essentials:ban {player} {reason}",
                        "essentials:tempban {player} {durationSeconds}s {reason}",
                        "essentials:mute {player} {durationSeconds}s {reason}");
            }
            boolean enabled = section.getBoolean("enabled", true);
            String kick = section.getString("kickCommand", "essentials:kick {player} {reason}");
            String ban = section.getString("banCommand", "essentials:ban {player} {reason}");
            String tempBan = section.getString("tempBanCommand", "essentials:tempban {player} {durationSeconds}s {reason}");
            String mute = section.getString("muteCommand", "essentials:mute {player} {durationSeconds}s {reason}");
            return new EssentialsSettings(enabled, kick, ban, tempBan, mute);
        }
    }

    public enum PunishmentAction {
        NONE,
        DISALLOW,
        KICK,
        BAN,
        TEMPBAN,
        COMMAND,
        MUTE;

        public static PunishmentAction from(String input) {
            if (input == null) {
                return NONE;
            }
            try {
                return PunishmentAction.valueOf(input.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return NONE;
            }
        }
    }

    public record AnnouncementsSettings(boolean enabled, long defaultIntervalSeconds) {
        public static AnnouncementsSettings from(ConfigurationSection section) {
            if (section == null) {
                return new AnnouncementsSettings(true, 300L);
            }
            boolean enabled = section.getBoolean("enabled", true);
            long defaultInterval = section.getLong("defaultIntervalSeconds", 300L);
            return new AnnouncementsSettings(enabled, defaultInterval);
        }
    }

    public record ManagementSettings(String unmuteCommand, String unbanCommand) {
        public static ManagementSettings from(ConfigurationSection section) {
            String unmute = "essentials:unmute {player}";
            String unban = "essentials:pardon {player}";
            if (section != null) {
                unmute = section.getString("unmuteCommand", unmute);
                unban = section.getString("unbanCommand", unban);
            }
            return new ManagementSettings(unmute, unban);
        }
    }
}
