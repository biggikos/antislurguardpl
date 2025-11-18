package com.example.antislurguard.management;

import org.bukkit.Bukkit;

import com.example.antislurguard.AntiSlurGuardPlugin;

public final class PlayerManagementService {

    private final AntiSlurGuardPlugin plugin;

    public PlayerManagementService(AntiSlurGuardPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean unmute(String playerName) {
        return executeCommand(plugin.config().management().unmuteCommand(), playerName);
    }

    public boolean unban(String playerName) {
        return executeCommand(plugin.config().management().unbanCommand(), playerName);
    }

    private boolean executeCommand(String template, String playerName) {
        if (template == null || template.isBlank() || playerName == null || playerName.isBlank()) {
            return false;
        }
        String command = template.replace("{player}", playerName);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        return true;
    }
}
