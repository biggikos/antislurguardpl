package com.example.antislurguard.punishment;

import java.time.Instant;
import java.util.Date;
import java.util.Map;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.example.antislurguard.AntiSlurGuardPlugin;
import com.example.antislurguard.config.Config;
import com.example.antislurguard.runtime.RuntimeSettingsService.Scope;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class PunishmentService {

    private final AntiSlurGuardPlugin plugin;

    public PunishmentService(AntiSlurGuardPlugin plugin) {
        this.plugin = plugin;
    }

    public void applyPreLogin(Config.Punishment punishment, String playerName, String match, String type, Scope scope) {
        Config.PunishmentAction action = normalizePreLoginAction(punishment.action());
        long duration = effectiveDuration(scope, punishment.durationSeconds());
        String reason = defaultReason(punishment.reason());
        switch (action) {
            case NONE -> {
            }
            case DISALLOW -> {
                // handled by listener via disallow
            }
            case BAN -> banOffline(playerName, reason, 0L, match, type, false);
            case TEMPBAN -> banOffline(playerName, reason, duration, match, type, false);
            case MUTE -> mute(playerName, reason, duration, match, type);
            case COMMAND -> runCommand(punishment.command(), playerName, reason, duration, match, type);
            default -> {
            }
        }
    }

    public void disallowJoin(org.bukkit.event.player.AsyncPlayerPreLoginEvent event, Config.Punishment punishment) {
        String reason = defaultReason(punishment.reason());
        event.disallow(org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result.KICK_OTHER, reason);
    }

    public void applyOnline(Config.Punishment punishment, Player player, String match, String type, Scope scope) {
        Config.PunishmentAction action = punishment.action();
        long duration = effectiveDuration(scope, punishment.durationSeconds());
        String reason = defaultReason(punishment.reason());
        Component reasonComponent = parseReason(reason);
        switch (action) {
            case NONE -> {
            }
            case KICK -> kick(player, reasonComponent, reason, duration, match, type);
            case BAN -> {
                banOffline(player.getName(), reason, 0L, match, type, true);
                player.kick(reasonComponent);
            }
            case TEMPBAN -> {
                banOffline(player.getName(), reason, duration, match, type, true);
                player.kick(reasonComponent);
            }
            case COMMAND -> runCommand(punishment.command(), player.getName(), reason, duration, match, type);
            case MUTE -> mute(player.getName(), reason, duration, match, type);
            case DISALLOW -> player.kick(reasonComponent);
        }
    }

    private long effectiveDuration(Scope scope, long configured) {
        if (scope == null) {
            return configured;
        }
        return plugin.runtimeSettings().resolveDuration(scope, configured);
    }

    private Config.PunishmentAction normalizePreLoginAction(Config.PunishmentAction original) {
        if (original == Config.PunishmentAction.KICK) {
            return Config.PunishmentAction.DISALLOW;
        }
        return original;
    }

    private void kick(Player player, Component reasonComponent, String reason, long durationSeconds, String match,
            String type) {
        if (shouldUseEssentials()) {
            runCommand(plugin.config().essentials().kickCommand(), player.getName(), reason, durationSeconds, match,
                    type);
        } else {
            player.kick(reasonComponent);
        }
    }

    private void banOffline(String playerName, String reason, long durationSeconds, String match, String type,
            boolean allowIntegrations) {
        if (allowIntegrations && shouldUseEssentials()) {
            String template = durationSeconds > 0 ? plugin.config().essentials().tempBanCommand()
                    : plugin.config().essentials().banCommand();
            runCommand(template, playerName, reason, durationSeconds, match, type);
            return;
        }
        BanList banList = Bukkit.getBanList(BanList.Type.NAME);
        Date expires = durationSeconds > 0 ? Date.from(Instant.now().plusSeconds(durationSeconds)) : null;
        banList.addBan(playerName, reason, expires, "AntiSlurGuard");
    }

    private void runCommand(String commandTemplate, String playerName, String reason, long durationSeconds,
            String match, String type) {
        if (commandTemplate == null || commandTemplate.isBlank()) {
            return;
        }
        Map<String, String> replacements = Map.of(
                "{player}", playerName != null ? playerName : "",
                "{match}", match != null ? match : "",
                "{type}", type != null ? type : "",
                "{reason}", reason != null ? reason : "",
                "{durationSeconds}", Long.toString(durationSeconds)
        );
        String command = commandTemplate;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            command = command.replace(entry.getKey(), entry.getValue());
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    private String defaultReason(String input) {
        if (input == null || input.isBlank()) {
            return " ";
        }
        return input;
    }

    private Component parseReason(String reason) {
        if (reason == null) {
            return Component.text(" ");
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize(reason);
    }

    private boolean shouldUseEssentials() {
        if (!plugin.config().essentials().enabled()) {
            return false;
        }
        Plugin essentials = Bukkit.getPluginManager().getPlugin("Essentials");
        return essentials != null && essentials.isEnabled();
    }

    private void mute(String playerName, String reason, long durationSeconds, String match, String type) {
        if (shouldUseEssentials()) {
            runCommand(plugin.config().essentials().muteCommand(), playerName, reason, durationSeconds, match, type);
            return;
        }
        runCommand("mute {player} {durationSeconds}s {reason}", playerName, reason, durationSeconds, match, type);
    }
}
