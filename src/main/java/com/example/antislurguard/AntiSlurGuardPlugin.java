package com.example.antislurguard;

import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import com.example.antislurguard.announcements.AnnouncementService;
import com.example.antislurguard.antispam.AntiSpamService;
import com.example.antislurguard.command.AsgCommand;
import com.example.antislurguard.config.Config;
import com.example.antislurguard.config.LocalizedConfigWriter;
import com.example.antislurguard.language.LanguageBundle;
import com.example.antislurguard.language.LanguageBundleProvider;
import com.example.antislurguard.listener.ChatFilterListener;
import com.example.antislurguard.listener.NameBlockerListener;
import com.example.antislurguard.management.PlayerManagementService;
import com.example.antislurguard.messages.Messages;
import com.example.antislurguard.normalization.NormalizationService;
import com.example.antislurguard.notification.AdminNotificationService;
import com.example.antislurguard.pattern.ExceptionStore;
import com.example.antislurguard.pattern.PatternStore;
import com.example.antislurguard.punishment.PunishmentService;
import com.example.antislurguard.runtime.RuntimeSettingsService;
import com.example.antislurguard.stats.PlayerStatsService;
import com.example.antislurguard.stats.StatsService;
import com.example.antislurguard.stats.UserViolationLogService;

public final class AntiSlurGuardPlugin extends JavaPlugin {

    private Config config;
    private PatternStore patternStore;
    private ExceptionStore exceptionStore;
    private Messages messages;
    private NormalizationService normalizationService;
    private StatsService statsService;
    private PunishmentService punishmentService;
    private AdminNotificationService adminNotificationService;
    private PlayerStatsService playerStatsService;
    private UserViolationLogService userViolationLogService;
    private AntiSpamService antiSpamService;
    private RuntimeSettingsService runtimeSettingsService;
    private PlayerManagementService playerManagementService;
    private AnnouncementService announcementService;
    private LanguageBundle languageBundle;
    private LanguageBundleProvider languageProvider;
    private BukkitTask aggregateTask;
    private static final String[] BIGGIKO_BANNER = {
            "__________.__              .__ __           ",
            "╲______   ╲__│ ____   ____ │__│  │ ______   ",
            " │    │  _╱  │╱ ___╲ ╱ ___╲│  │  │╱ ╱  _ ╲  ",
            " │    │   ╲  ╱ ╱_╱  > ╱_╱  >  │    <  <_> ) ",
            " │______  ╱__╲___  ╱╲___  ╱│__│__│_ ╲____╱  ",
            "        ╲╱  ╱_____╱╱_____╱         ╲╱       " };

    @Override
    public void onEnable() {
        saveDefaultConfig();
        logBiggikoBanner();
        this.statsService = new StatsService();
        reloadConfiguration();
        registerListeners();
        registerCommands();
        scheduleAggregation();
    }

    @Override
    public void onDisable() {
        if (aggregateTask != null) {
            aggregateTask.cancel();
            aggregateTask = null;
        }
        if (announcementService != null) {
            announcementService.cancelAll();
        }
        HandlerList.unregisterAll(this);
    }

    public void reloadConfiguration() {
        reloadConfig();
        if (announcementService != null) {
            announcementService.cancelAll();
        }
        FileConfiguration fileConfig = getConfig();
        this.config = Config.from(fileConfig);
        this.languageProvider = new LanguageBundleProvider(this, Path.of(config.paths().languagesDir()));
        this.languageBundle = languageProvider.load(config.lang());
        new LocalizedConfigWriter(this).write(config, languageBundle);
        this.messages = new Messages(this, Path.of(config.paths().messagesFile()));
        this.messages.reload(languageBundle);
        this.normalizationService = new NormalizationService(config.normalize());
        this.patternStore = new PatternStore(this, Path.of(config.paths().patternsFile()), normalizationService,
                config.patternOptions());
        this.exceptionStore = new ExceptionStore(this, Path.of(config.paths().exceptionsFile()), normalizationService,
                config.patternOptions());
        this.patternStore.reload();
        this.exceptionStore.reload();
        this.runtimeSettingsService = new RuntimeSettingsService(this, Path.of(config.paths().runtimeSettingsFile()));
        this.punishmentService = new PunishmentService(this);
        this.adminNotificationService = new AdminNotificationService(this, Path.of(config.paths().adminNotifyFile()));
        this.playerStatsService = new PlayerStatsService(this, Path.of(config.paths().playerStatsFile()),
                config.stats());
        this.userViolationLogService = new UserViolationLogService(this, Path.of(config.paths().userDataDir()));
        this.antiSpamService = new AntiSpamService(config.antiSpam());
        this.playerManagementService = new PlayerManagementService(this);
        this.announcementService = new AnnouncementService(this, Path.of(config.paths().announcementsFile()),
                config.announcements());
        this.announcementService.reload();
    }

    private void registerListeners() {
        HandlerList.unregisterAll(this);
        Bukkit.getPluginManager().registerEvents(new NameBlockerListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ChatFilterListener(this), this);
    }

    private void registerCommands() {
        AsgCommand executor = new AsgCommand(this);
        var command = Objects.requireNonNull(getCommand("asg"), "asg command not defined");
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void scheduleAggregation() {
        if (aggregateTask != null) {
            aggregateTask.cancel();
        }
        long intervalTicks = Math.max(1L, config.console().aggregateIntervalSeconds() * 20L);
        aggregateTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            StatsService.Snapshot snapshot = statsService.drain();
            if (!snapshot.shouldAlert(config.console().massAlertThreshold())) {
                return;
            }
            Logger logger = getLogger();
            logger.info(() -> String.format("[AntiSlurGuard] Заблокировано %d ник(ов) и %d сообщ.(ий) по regex",
                    snapshot.nameBlocks(), snapshot.chatBlocks()));
        }, intervalTicks, intervalTicks);
    }

    private void logBiggikoBanner() {
        Logger logger = getLogger();
        logger.info(" ");
        for (String line : BIGGIKO_BANNER) {
            logger.info(line);
        }
        logger.info("║ AntiSlurGuard lovingly authored by Biggiko ║");
        logger.info("╚══════════════════════════════════════════════════════════════╝");
    }

    public Config config() {
        return config;
    }

    public PatternStore patternStore() {
        return patternStore;
    }

    public ExceptionStore exceptionStore() {
        return exceptionStore;
    }

    public Messages messages() {
        return messages;
    }

    public NormalizationService normalizationService() {
        return normalizationService;
    }

    public StatsService statsService() {
        return statsService;
    }

    public PunishmentService punishmentService() {
        return punishmentService;
    }

    public AdminNotificationService adminNotifications() {
        return adminNotificationService;
    }

    public PlayerStatsService playerStatsService() {
        return playerStatsService;
    }

    public UserViolationLogService userViolationLogService() {
        return userViolationLogService;
    }

    public AntiSpamService antiSpamService() {
        return antiSpamService;
    }

    public RuntimeSettingsService runtimeSettings() {
        return runtimeSettingsService;
    }

    public PlayerManagementService playerManagement() {
        return playerManagementService;
    }

    public void reloadAll() {
        reloadConfiguration();
        registerListeners();
        scheduleAggregation();
        getLogger().info("AntiSlurGuard configuration reloaded.");
    }

    public boolean hasBypass(UUID uniqueId, String name) {
        String permission = config.permissions().bypass();
        if (permission == null || permission.isBlank()) {
            return false;
        }
        if (uniqueId != null) {
            Player online = Bukkit.getPlayer(uniqueId);
            if (online != null && online.hasPermission(permission)) {
                return true;
            }
            OfflinePlayer offline = Bukkit.getOfflinePlayer(uniqueId);
            if (offline != null && offline.isOp()) {
                return true;
            }
        }
        if (name != null && !name.isBlank()) {
            Player onlineByName = Bukkit.getPlayerExact(name);
            if (onlineByName != null && onlineByName.hasPermission(permission)) {
                return true;
            }
            OfflinePlayer offlineByName = Bukkit.getOfflinePlayer(name);
            if (offlineByName != null && offlineByName.isOp()) {
                return true;
            }
        }
        return false;
    }
}
