package com.example.antislurguard.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import com.example.antislurguard.AntiSlurGuardPlugin;
import com.example.antislurguard.notification.AdminNotificationService.ToggleAction;
import com.example.antislurguard.pattern.PatternStore.PatternMatch;
import com.example.antislurguard.stats.PlayerStatsService;
import com.example.antislurguard.stats.UserViolationLogService.ViolationEntry;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class AsgCommand implements CommandExecutor, TabCompleter {

    private final AntiSlurGuardPlugin plugin;

    public AsgCommand(AntiSlurGuardPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            handleHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload" -> handleReload(sender);
            case "stats" -> handleStats(sender, args);
            case "test" -> handleTest(sender, args);
            case "add" -> handleAdd(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "except" -> handleException(sender, args);
            case "notify" -> handleNotify(sender, args);
            case "logs" -> handleLogs(sender, args);
            case "unmute" -> handleUnmute(sender, args);
            case "unban" -> handleUnban(sender, args);
            case "slowmode" -> handleSlowmode(sender, args);
            case "help" -> handleHelp(sender);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleHelp(CommandSender sender) {
        var messages = plugin.messages();
        sender.sendMessage(messages.component("help-header", "&6AntiSlurGuard команды:", Map.of()));
        for (HelpEntry entry : HelpEntry.DEFAULTS) {
            Map<String, String> placeholders = Map.of("usage", entry.usage(), "description",
                    messages.raw(entry.messageKey(), entry.fallback()));
            sender.sendMessage(messages.component("help-line", "&e{usage}&7 — {description}", placeholders));
        }
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadAll();
        sender.sendMessage(Component.text("AntiSlurGuard configuration reloaded.", NamedTextColor.GREEN));
    }

    private void handleStats(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            String target = args[1];
            plugin.playerStatsService().findByQuery(target).ifPresentOrElse(view -> {
                sender.sendMessage(plugin.messages().component("player-stats-header", "&6Статистика:",
                        Map.of("player", view.playerName())));
                sender.sendMessage(plugin.messages().component("player-stats-line",
                        "&7Чат-нарушений: {violations}, пермабанов: {permaBans}.", Map.of(
                                "violations", Integer.toString(view.chatViolations()),
                                "permaBans", Integer.toString(view.permanentBans()),
                                "player", view.playerName())));
            }, () -> sender.sendMessage(plugin.messages().component("player-stats-missing",
                    "&cНет данных по игроку {player}.", Map.of("player", target))));
            return;
        }
        int nameBlocks = plugin.statsService().currentNameBlocks();
        int chatBlocks = plugin.statsService().currentChatBlocks();
        sender.sendMessage(plugin.messages().component("stats-global",
                String.format("&eБлокировки: ники=%d, чат=%d.", nameBlocks, chatBlocks),
                Map.of("nameBlocks", Integer.toString(nameBlocks), "chatBlocks", Integer.toString(chatBlocks))));
    }

    private void handleTest(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /asg test <text>", NamedTextColor.RED));
            return;
        }
        String input = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        String normalized = plugin.normalizationService().normalize(input);
        var exception = plugin.exceptionStore().findMatch(normalized, input);
        if (exception.isPresent()) {
            sender.sendMessage(plugin.messages().component("test-exception",
                    "&eИсключение: {exception} (проверка пропущена)",
                    Map.of("normalized", normalized, "exception", exception.get())));
            return;
        }
        PatternMatch match = plugin.patternStore().findMatch(normalized).orElse(null);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("normalized", normalized);
        placeholders.put("matched", match != null ? "true" : "false");
        placeholders.put("pattern", match != null ? match.pattern() : "-");
        placeholders.put("matchValue", match != null ? match.match() : "-");
        placeholders.put("origin", match != null ? match.origin().type() : "-");
        sender.sendMessage(plugin.messages().component("test-output",
                "&bНормализация: {normalized} | Совпадение: {matched} | Триггер: {pattern} → {matchValue} ({origin})",
                placeholders));
    }

    private void handleLogs(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.messages().component("logs-usage", "&c/asg logs <player>", Map.of()));
            return;
        }
        String player = args[1];
        var stats = plugin.playerStatsService().findByQuery(player);
        var entries = plugin.userViolationLogService().readEntries(player, 50);

        int violations = stats.map(view -> view.chatViolations()).orElse(entries.size());
        int perma = stats.map(PlayerStatsService.PlayerStatsView::permanentBans).orElse(0);
        Map<String, String> header = Map.of(
                "player", stats.map(view -> view.playerName()).orElse(player),
                "violations", Integer.toString(violations),
                "permaBans", Integer.toString(perma));

        sender.sendMessage(plugin.messages().component("logs-header", "&6Статистика для {player}", header));
        sender.sendMessage(plugin.messages().component("logs-stats", "&7Банворды: {violations} | Пермбанов: {permaBans}",
                header));

        if (entries.isEmpty()) {
            sender.sendMessage(plugin.messages().component("logs-empty", "&7История пуста", Map.of()));
            return;
        }
        sender.sendMessage(plugin.messages().component("logs-history-header",
                "&fИстория запрещённых сообщений:", Map.of()));
        for (ViolationEntry entry : entries) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("message", entry.message());
            placeholders.put("pattern", entry.pattern());
            placeholders.put("origin", entry.origin());
            placeholders.put("since", entry.relativeTime());
            sender.sendMessage(plugin.messages().component("logs-line",
                    "&7{message} &8[{since}] ({origin}:{pattern})", placeholders));
        }
    }

    private void handleAdd(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /asg add <pattern>", NamedTextColor.RED));
            return;
        }
        String pattern = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        if (plugin.patternStore().containsRaw(pattern)) {
            sender.sendMessage(plugin.messages().component("pattern-exists",
                    "&eТакой шаблон уже есть.", Map.of("pattern", pattern)));
            return;
        }
        boolean added = plugin.patternStore().appendPattern(pattern);
        if (added) {
            sender.sendMessage(plugin.messages().component("pattern-added",
                    "&aШаблон добавлен.", Map.of("pattern", pattern)));
        } else {
            sender.sendMessage(plugin.messages().component("pattern-add-failed",
                    "&cНе удалось сохранить шаблон.", Map.of("pattern", pattern)));
        }
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /asg remove <pattern>", NamedTextColor.RED));
            return;
        }
        String pattern = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        boolean removed = plugin.patternStore().removePattern(pattern);
        if (removed) {
            sender.sendMessage(plugin.messages().component("pattern-removed",
                    "&aШаблон удалён.", Map.of("pattern", pattern)));
        } else {
            boolean known = plugin.patternStore().containsRaw(pattern);
            String key = known ? "pattern-remove-failed" : "pattern-missing";
            String fallback = known ? "&cНе удалось удалить шаблон." : "&eТакого шаблона нет в списке.";
            sender.sendMessage(plugin.messages().component(key, fallback, Map.of("pattern", pattern)));
        }
    }

    private void handleException(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(plugin.messages().component("exception-usage",
                    "&c/asg except <add|remove> <text>", Map.of()));
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        String value = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        switch (action) {
            case "add" -> {
                if (plugin.exceptionStore().containsRaw(value)) {
                    sender.sendMessage(plugin.messages().component("exception-exists",
                            "&eТакое исключение уже есть.", Map.of("pattern", value)));
                    return;
                }
                boolean saved = plugin.exceptionStore().append(value);
                String key = saved ? "exception-added" : "exception-add-failed";
                String fallback = saved ? "&aИсключение сохранено." : "&cНе удалось сохранить исключение.";
                sender.sendMessage(plugin.messages().component(key, fallback, Map.of("pattern", value)));
            }
            case "remove" -> {
                boolean removed = plugin.exceptionStore().remove(value);
                if (removed) {
                    sender.sendMessage(plugin.messages().component("exception-removed",
                            "&aИсключение удалено.", Map.of("pattern", value)));
                } else {
                    String key = plugin.exceptionStore().containsRaw(value) ? "exception-remove-failed"
                            : "exception-missing";
                    String fallback = plugin.exceptionStore().containsRaw(value)
                            ? "&cНе удалось удалить исключение."
                            : "&eТакого исключения нет.";
                    sender.sendMessage(plugin.messages().component(key, fallback, Map.of("pattern", value)));
                }
            }
            default -> sender.sendMessage(plugin.messages().component("exception-usage",
                    "&c/asg except <add|remove> <text>", Map.of()));
        }
    }

    private void handleNotify(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender);
            return;
        }
        if (args.length == 2 && "toggle".equalsIgnoreCase(args[1])) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.messages().component("notify-invalid-target",
                        "&cТолько игрок может переключить свои уведомления.", Map.of()));
                return;
            }
            toggleNotifications(sender, player.getUniqueId(), player.getName(), ToggleAction.TOGGLE);
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.messages().component("notify-invalid-action",
                    "&cИспользуйте on/off/toggle.", Map.of()));
            return;
        }
        String targetName = args[1];
        ToggleAction action = parseAction(args[2]);
        if (action == null) {
            sender.sendMessage(plugin.messages().component("notify-invalid-action",
                    "&cИспользуйте on/off/toggle.", Map.of()));
            return;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(targetName);
        UUID uuid = offline != null ? offline.getUniqueId() : null;
        if (uuid == null || (!offline.hasPlayedBefore() && !offline.isOnline())) {
            sender.sendMessage(plugin.messages().component("notify-invalid-target",
                    "&cУкажите существующего игрока.", Map.of()));
            return;
        }
        toggleNotifications(sender, uuid, offline.getName() != null ? offline.getName() : targetName, action);
    }

    private ToggleAction parseAction(String value) {
        return switch (value.toLowerCase()) {
            case "on" -> ToggleAction.ON;
            case "off" -> ToggleAction.OFF;
            case "toggle" -> ToggleAction.TOGGLE;
            default -> null;
        };
    }

    private void toggleNotifications(CommandSender sender, UUID uuid, String targetName, ToggleAction action) {
        var result = plugin.adminNotifications().update(uuid, targetName, action);
        if (!result.success()) {
            sender.sendMessage(plugin.messages().component("notify-invalid-target",
                    "&cУкажите существующего игрока.", Map.of()));
            return;
        }
        String key = result.enabled() ? "notify-toggle-on" : "notify-toggle-off";
        sender.sendMessage(plugin.messages().component(key, "&aУведомления обновлены.", Map.of("player", targetName)));
    }

    private void sendUsage(CommandSender sender) {
        handleHelp(sender);
    }

    private void handleUnmute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.messages().component("management-command-usage",
                    "&c/asg unmute <player>", Map.of()));
            return;
        }
        String player = args[1];
        boolean success = plugin.playerManagement().unmute(player);
        String key = success ? "management-unmute-success" : "management-command-failed";
        sender.sendMessage(plugin.messages().component(key, success ? "&aИгрок размьючен" : "&cКоманда не выполнена",
                Map.of("player", player)));
    }

    private void handleUnban(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.messages().component("management-command-usage",
                    "&c/asg unban <player>", Map.of()));
            return;
        }
        String player = args[1];
        boolean success = plugin.playerManagement().unban(player);
        String key = success ? "management-unban-success" : "management-command-failed";
        sender.sendMessage(plugin.messages().component(key, success ? "&aИгрок разбанен" : "&cКоманда не выполнена",
                Map.of("player", player)));
    }

    private void handleSlowmode(CommandSender sender, String[] args) {
        if (args.length < 3 || !"clear".equalsIgnoreCase(args[2])) {
            sender.sendMessage(plugin.messages().component("management-slowmode-usage",
                    "&c/asg slowmode <player> clear", Map.of()));
            return;
        }
        String playerName = args[1];
        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerName);
        UUID uuid = offline.getUniqueId();
        if (!offline.hasPlayedBefore() && !offline.isOnline()) {
            sender.sendMessage(plugin.messages().component("management-invalid-target",
                    "&cУкажите игрока, который уже был на сервере.", Map.of("player", playerName)));
            return;
        }
        boolean success = plugin.antiSpamService().clear(uuid);
        String key = success ? "management-slowmode-cleared" : "management-slowmode-missing";
        sender.sendMessage(plugin.messages().component(key,
                success ? "&aСлоумод снят" : "&eУ игрока не активен slowmode",
                Map.of("player", offline.getName() != null ? offline.getName() : playerName)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(plugin.config().permissions().admin())) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(List.of("help", "reload", "stats", "test", "add", "remove", "except", "notify", "logs",
                    "unmute", "unban", "slowmode"), args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "stats" -> {
                return args.length == 2 ? List.of("<player>") : List.of();
            }
            case "test" -> {
                return args.length == 2 ? List.of("<text>") : List.of();
            }
            case "add", "remove" -> {
                return args.length == 2 ? List.of("<pattern>") : List.of();
            }
            case "logs" -> {
                if (args.length == 2) {
                    List<String> base = new ArrayList<>();
                    base.add("<player>");
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        base.add(online.getName());
                    }
                    return filter(base, args[1]);
                }
                return List.of();
            }
            case "except" -> {
                if (args.length == 2) {
                    return filter(List.of("add", "remove"), args[1]);
                }
                if (args.length == 3) {
                    return List.of("<text>");
                }
                return List.of();
            }
            case "notify" -> {
                if (args.length == 2) {
                    List<String> base = new ArrayList<>();
                    base.add("me");
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        base.add(online.getName());
                    }
                    return filter(base, args[1]);
                }
                if (args.length == 3) {
                    return filter(List.of("on", "off", "toggle"), args[2]);
                }
                return List.of();
            }
            case "unmute", "unban", "slowmode" -> {
                if (args.length == 2) {
                    return List.of("<player>");
                }
                if ("slowmode".equals(sub) && args.length == 3) {
                    return filter(List.of("clear"), args[2]);
                }
                return List.of();
            }
            default -> {
                return List.of();
            }
        }
    }

    private List<String> filter(List<String> options, String current) {
        if (current == null || current.isEmpty()) {
            return options;
        }
        String lower = current.toLowerCase(Locale.ROOT);
        List<String> results = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                results.add(option);
            }
        }
        return results;
    }

    private record HelpEntry(String usage, String messageKey, String fallback) {
        private static final List<HelpEntry> DEFAULTS = List.of(
                new HelpEntry("/asg help", "help.help", "Справка по AntiSlurGuard"),
                new HelpEntry("/asg reload", "help.reload", "Перезагрузить конфиг, сообщения и паттерны"),
                new HelpEntry("/asg stats [player]", "help.stats", "Показать глобальную или персональную статистику"),
                new HelpEntry("/asg test <text>", "help.test", "Нормализовать текст и показать, триггерится ли"),
                new HelpEntry("/asg add <pattern>", "help.add", "Добавить банворд/regex"),
                new HelpEntry("/asg remove <pattern>", "help.remove", "Удалить банворд/regex"),
                new HelpEntry("/asg except <add|remove> <text>", "help.except", "Управление исключениями"),
                new HelpEntry("/asg notify <player|me> <on|off|toggle>", "help.notify", "Вкл/выкл алерты для админов"),
                new HelpEntry("/asg logs <player>", "help.logs", "Показать историю нарушений и полные сообщения"),
                new HelpEntry("/asg unmute <player>", "help.unmute", "Снять мут через шаблон команды управления"),
                new HelpEntry("/asg unban <player>", "help.unban", "Снять бан через шаблон команды управления"),
                new HelpEntry("/asg slowmode <player> clear", "help.slowmode", "Сбросить персональный слоумод")
        );
    }
}
