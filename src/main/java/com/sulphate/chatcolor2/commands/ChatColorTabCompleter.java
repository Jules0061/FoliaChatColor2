package com.sulphate.chatcolor2.commands;

import com.sulphate.chatcolor2.managers.CustomColoursManager;
import com.sulphate.chatcolor2.managers.GroupColoursManager;
import com.sulphate.chatcolor2.utils.StaticMaps;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class ChatColorTabCompleter implements TabCompleter {

    private static final List<String> COLOUR_CODES = Arrays.asList(
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b", "c", "d", "e", "f"
    );

    private static final List<String> MODIFIER_CODES = Arrays.asList("k", "l", "m", "n", "o");

    private static final List<String> ADMIN_SUB_COMMANDS = Arrays.asList(
            "confirm", "custom", "group", "pause", "reload", "reset", "set"
    );

    private static final List<String> ACTIONS = Arrays.asList("add", "list", "remove");
    private static final List<String> BOOLEANS = Arrays.asList("false", "true");

    private final CustomColoursManager customColoursManager;
    private final GroupColoursManager groupColoursManager;

    public ChatColorTabCompleter(CustomColoursManager customColoursManager, GroupColoursManager groupColoursManager) {
        this.customColoursManager = customColoursManager;
        this.groupColoursManager = groupColoursManager;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }

        if (!(sender instanceof Player player)) {
            return filter(consoleSuggestions(args), args[args.length - 1]);
        }

        return filter(playerSuggestions(player, args), args[args.length - 1]);
    }

    private List<String> consoleSuggestions(String[] args) {
        if (args.length == 1) {
            return playerNames();
        }

        if (args.length == 2) {
            return allColourNames(null);
        }

        return allModifierNames(null);
    }

    private List<String> playerSuggestions(Player player, String[] args) {
        String subCommand = args[0].toLowerCase(Locale.ENGLISH);

        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>(availableSubCommands(player));

            if (hasPermission(player, "chatcolor.change.self")) {
                suggestions.addAll(allColourNames(player));
            }

            if (hasPermission(player, "chatcolor.change.others")) {
                suggestions.addAll(playerNames());
            }

            return suggestions;
        }

        switch (subCommand) {
            case "set":
                return settingSuggestions(player, args);

            case "add":
            case "remove":
                return args.length == 2 ? allModifierNames(player) : Collections.emptyList();

            case "group":
                return groupSuggestions(player, args);

            case "custom":
                return customSuggestions(player, args);

            default:
                return colourAndModifierSuggestions(player, args);
        }
    }

    private List<String> availableSubCommands(Player player) {
        List<String> suggestions = new ArrayList<>(Arrays.asList("available", "help"));

        if (hasPermission(player, "chatcolor.clear")) {
            suggestions.add("clear");
        }

        if (hasPermission(player, "chatcolor.gui")) {
            suggestions.add("gui");
        }

        if (hasPermission(player, "chatcolor.change.self")) {
            suggestions.add("add");
            suggestions.add("remove");
        }

        if (hasPermission(player, "chatcolor.admin")) {
            suggestions.addAll(ADMIN_SUB_COMMANDS);
        }

        return suggestions;
    }

    private List<String> settingSuggestions(Player player, String[] args) {
        if (!hasPermission(player, "chatcolor.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 2) {
            return ChatColorCommand.getSettingNames();
        }

        if (args.length != 3) {
            return Collections.emptyList();
        }

        Setting setting;

        try {
            setting = Setting.getSetting(args[1]);
        }
        catch (IllegalArgumentException ex) {
            return Collections.emptyList();
        }

        switch (setting.getDataType()) {
            case BOOLEAN:
                return BOOLEANS;

            case COLOUR_STRING:
                return allColourNames(null);

            default:
                return Collections.emptyList();
        }
    }

    private List<String> groupSuggestions(Player player, String[] args) {
        if (!hasPermission(player, "chatcolor.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 2) {
            return ACTIONS;
        }

        String action = args[1].toLowerCase(Locale.ENGLISH);

        if (args.length == 3 && action.equals("remove")) {
            return new ArrayList<>(groupColoursManager.getOrderedGroupNames());
        }

        if (args.length == 4 && action.equals("add")) {
            return allColourNames(null);
        }

        return Collections.emptyList();
    }

    private List<String> customSuggestions(Player player, String[] args) {
        if (!hasPermission(player, "chatcolor.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 2) {
            return ACTIONS;
        }

        String action = args[1].toLowerCase(Locale.ENGLISH);

        if (args.length == 3 && action.equals("remove")) {
            return new ArrayList<>(customColoursManager.getCustomColours().keySet());
        }

        if (args.length == 4 && action.equals("add")) {
            return allColourNames(null);
        }

        return Collections.emptyList();
    }

    private List<String> colourAndModifierSuggestions(Player player, String[] args) {
        boolean targetsAnotherPlayer = Bukkit.getPlayerExact(args[0]) != null;

        if (targetsAnotherPlayer) {
            if (!hasPermission(player, "chatcolor.change.others")) {
                return Collections.emptyList();
            }

            return args.length == 2 ? allColourNames(player) : allModifierNames(player);
        }

        if (!hasPermission(player, "chatcolor.change.self")) {
            return Collections.emptyList();
        }

        return allModifierNames(player);
    }

    private List<String> allColourNames(Player player) {
        List<String> suggestions = new ArrayList<>();
        suggestions.add("default");

        for (String code : COLOUR_CODES) {
            if (player != null && lacksColourPermission(player, code)) {
                continue;
            }

            suggestions.add(code);
            suggestions.add(StaticMaps.getColourName(code));
        }

        for (String customColour : customColoursManager.getCustomColours().keySet()) {
            if (player != null && !hasPermission(player, "chatcolor.custom." + customColour.replace("%", ""))) {
                continue;
            }

            suggestions.add(customColour);
        }

        return suggestions;
    }

    private List<String> allModifierNames(Player player) {
        List<String> suggestions = new ArrayList<>();

        for (String code : MODIFIER_CODES) {
            if (player != null && lacksModifierPermission(player, code)) {
                continue;
            }

            suggestions.add(code);
            suggestions.add(StaticMaps.getModifierName(code));
        }

        return suggestions;
    }

    private List<String> playerNames() {
        List<String> names = new ArrayList<>();

        for (Player online : Bukkit.getOnlinePlayers()) {
            names.add(online.getName());
        }

        return names;
    }

    private boolean hasPermission(Player player, String permission) {
        return player.isOp() || player.hasPermission(permission);
    }

    private boolean lacksColourPermission(Player player, String code) {
        return !hasPermission(player, "chatcolor.color." + code)
                && !player.hasPermission("chatcolor.color." + StaticMaps.getColourName(code));
    }

    private boolean lacksModifierPermission(Player player, String code) {
        return !hasPermission(player, "chatcolor.modifier." + code)
                && !player.hasPermission("chatcolor.modifier." + StaticMaps.getModifierName(code));
    }

    private List<String> filter(List<String> suggestions, String argument) {
        if (suggestions.isEmpty()) {
            return suggestions;
        }

        String prefix = argument.toLowerCase(Locale.ENGLISH);
        List<String> matches = new ArrayList<>();

        for (String suggestion : suggestions) {
            if (suggestion.toLowerCase(Locale.ENGLISH).startsWith(prefix)) {
                matches.add(suggestion);
            }
        }

        Collections.sort(matches);
        return matches;
    }

}
