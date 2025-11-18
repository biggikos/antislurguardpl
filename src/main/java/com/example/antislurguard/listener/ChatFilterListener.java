package com.example.antislurguard.listener;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import com.example.antislurguard.AntiSlurGuardPlugin;
import com.example.antislurguard.antispam.AntiSpamService;
import com.example.antislurguard.config.Config;
import com.example.antislurguard.pattern.ExceptionStore;
import com.example.antislurguard.pattern.PatternStore;
import com.example.antislurguard.pattern.PatternStore.PatternMatch;
import com.example.antislurguard.stats.PlayerStatsService;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class ChatFilterListener implements Listener {

    private final AntiSlurGuardPlugin plugin;
    private final PlainTextComponentSerializer plainSerializer = PlainTextComponentSerializer.plainText();

    public ChatFilterListener(AntiSlurGuardPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (plugin.hasBypass(player.getUniqueId(), player.getName())) {
            return;
        }
        String plain = plainSerializer.serialize(event.message());
        String normalized = plugin.normalizationService().normalize(plain);
        ExceptionStore exceptions = plugin.exceptionStore();
        if (exceptions.findMatch(normalized, plain).isPresent()) {
            return;
        }
        AntiSpamService.CheckResult spamResult = plugin.antiSpamService()
                .evaluate(player.getUniqueId(), normalized, System.currentTimeMillis());
        if (spamResult.blocked()) {
            event.setCancelled(true);
            handleAntiSpam(player, spamResult);
            return;
        }
        PatternMatch match = plugin.patternStore().findMatch(normalized).orElse(null);
        if (match == null) {
            return;
        }
        event.setCancelled(true);
        Config.Punishment punishment = plugin.config().punishments().chat();
        Config.Notifications notifications = plugin.config().notifications();
        plugin.statsService().incrementChatBlocks();
        PlayerStatsService.RecordResult statsRecord = plugin.playerStatsService()
                .recordChatViolation(player.getUniqueId(), player.getName());
        plugin.userViolationLogService().recordChat(player.getUniqueId(), player.getName(), plain, match);

        Map<String, String> placeholders = Map.of(
                "player", player.getName(),
                "type", match.origin().type(),
                "pattern", match.pattern(),
                "match", match.match() == null ? "" : match.match(),
                "original", plain
        );

        var messages = plugin.messages();
        var playerMessage = notifications.player().enabled()
                ? messages.component(notifications.player().messageKey(), "&7Сообщение не отправлено.", placeholders)
                : null;
        var adminMessage = notifications.admin().enabled()
                ? messages.component(notifications.admin().chatMessageKey(), "&c[ASG]", placeholders)
                : null;

        boolean triggerPerma = statsRecord.triggerPermaBan();
        String autoBanReason = triggerPerma
                ? messages.raw(plugin.config().stats().autoPermaBanReasonKey(), "Повторные нарушения правил чата")
                : "";

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (playerMessage != null) {
                player.sendMessage(playerMessage);
            }
            plugin.punishmentService().applyOnline(punishment, player, match.match(), match.origin().type(),
                    com.example.antislurguard.runtime.RuntimeSettingsService.Scope.CHAT);
            if (triggerPerma) {
                Config.Punishment auto = new Config.Punishment(Config.PunishmentAction.BAN, 0L, autoBanReason, "");
                plugin.punishmentService().applyOnline(auto, player, match.match(), "auto-ban", null);
            }
            plugin.adminNotifications().broadcast(adminMessage);
        });
    }

    private void handleAntiSpam(Player player, AntiSpamService.CheckResult result) {
        Config.AntiSpamSettings antiSpam = plugin.config().antiSpam();
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", player.getName());
        if (result.durationSeconds() > 0) {
            placeholders.put("duration", Long.toString(result.durationSeconds()));
        }
        if (result.cooldownSeconds() > 0) {
            placeholders.put("cooldown", Long.toString(result.cooldownSeconds()));
        }
        if (result.remainingSeconds() > 0) {
            placeholders.put("seconds", Long.toString(result.remainingSeconds()));
        }
        String playerKey = result.reason() == AntiSpamService.Reason.TRIGGERED
                ? antiSpam.messages().playerTriggeredKey()
                : antiSpam.messages().playerCooldownKey();
        String fallback = result.reason() == AntiSpamService.Reason.TRIGGERED
                ? "&cВключён медленный режим на {duration}s. Интервал {cooldown}s."
                : "&eПодождите {seconds}s перед отправкой следующего сообщения.";
        var playerComponent = plugin.messages().component(playerKey, fallback, placeholders);
        var adminComponent = result.reason() == AntiSpamService.Reason.TRIGGERED
                ? plugin.messages().component(antiSpam.messages().adminTriggeredKey(),
                        "&c[ASG] Включён slowmode для {player}.", placeholders)
                : null;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (playerComponent != null) {
                player.sendMessage(playerComponent);
            }
            if (adminComponent != null) {
                plugin.adminNotifications().broadcast(adminComponent);
            }
        });
    }
}
