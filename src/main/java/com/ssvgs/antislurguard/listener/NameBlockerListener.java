package com.biggiko.antislurguard.listener;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import com.biggiko.antislurguard.AntiSlurGuardPlugin;
import com.biggiko.antislurguard.config.Config;
import com.biggiko.antislurguard.pattern.ExceptionStore;
import com.biggiko.antislurguard.pattern.PatternStore;
import com.biggiko.antislurguard.pattern.PatternStore.PatternMatch;

public final class NameBlockerListener implements Listener {

    private final AntiSlurGuardPlugin plugin;

    public NameBlockerListener(AntiSlurGuardPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (plugin.hasBypass(event.getUniqueId(), event.getName())) {
            return;
        }
        String normalized = plugin.normalizationService().normalize(event.getName());
        ExceptionStore exceptions = plugin.exceptionStore();
        if (exceptions.findMatch(normalized, event.getName()).isPresent()) {
            return;
        }
        PatternStore patternStore = plugin.patternStore();
        PatternMatch match = patternStore.findMatch(normalized).orElse(null);
        if (match == null) {
            return;
        }
        Config.Punishment punishment = plugin.config().punishments().nickname();
        plugin.punishmentService().disallowJoin(event, punishment);
        plugin.statsService().incrementNameBlocks();

        Bukkit.getScheduler().runTask(plugin, () -> {
            plugin.punishmentService().applyPreLogin(punishment, event.getName(), match.match(), match.origin().type(),
                    com.biggiko.antislurguard.runtime.RuntimeSettingsService.Scope.NICKNAME);
        });
    }
}
