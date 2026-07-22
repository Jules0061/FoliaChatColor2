package com.sulphate.chatcolor2.commands;

import com.sulphate.chatcolor2.data.PlayerDataStore;
import com.sulphate.chatcolor2.data.YamlStorageImpl;
import com.sulphate.chatcolor2.listeners.ChatListener;
import com.sulphate.chatcolor2.main.ChatColor;
import com.sulphate.chatcolor2.managers.*;
import com.sulphate.chatcolor2.gui.GuiManager;
import com.sulphate.chatcolor2.utils.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.*;

public final class ChatColorCommand implements CommandExecutor, Reloadable {

    private static final List<String> SETTING_NAMES = Arrays.asList(
            "auto-save",
            "save-interval",
            "color-override",
            "notify-others",
            "join-message",
            "confirm-timeout",
            "default-color",
            "command-name",
            "force-group-colors",
            "default-color-enabled",
            "command-opens-gui",
            "ignore-symbol-prefixes"
    );

    private final Messages M;
    private final GeneralUtils generalUtils;
    private final ConfirmationsManager confirmationsManager;
    private final ConfigsManager configsManager;
    private final HandlersManager handlersManager;
    private final GuiManager guiManager;
    private final CustomColoursManager customColoursManager;
    private final GroupColoursManager groupColoursManager;
    private final PlayerDataStore dataStore;
    private final ChatListener chatListener;

    private YamlConfiguration mainConfig;

    public ChatColorCommand(
            Messages M, GeneralUtils generalUtils, ConfirmationsManager confirmationsManager,
            ConfigsManager configsManager, HandlersManager handlersManager, GuiManager guiManager,
            CustomColoursManager customColoursManager, GroupColoursManager groupColoursManager,
            PlayerDataStore playerDataStore, ChatListener chatListener
    ) {
        this.M = M;
        this.generalUtils = generalUtils;
        this.confirmationsManager = confirmationsManager;
        this.configsManager = configsManager;
        this.handlersManager = handlersManager;
        this.guiManager = guiManager;
        this.customColoursManager = customColoursManager;
        this.groupColoursManager = groupColoursManager;
        this.dataStore = playerDataStore;
        this.chatListener = chatListener;

        reload();
    }

    public void reload() {
        mainConfig = configsManager.getConfig(Config.MAIN_CONFIG);
    }

    static List<String> getSettingNames() {
        return SETTING_NAMES;
    }

    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String @NonNull [] args) {
        boolean notifyOthers = mainConfig.getBoolean(Setting.NOTIFY_OTHERS.getConfigPath());
        boolean forceGroupColours = mainConfig.getBoolean(Setting.FORCE_GROUP_COLORS.getConfigPath());

        if (sender instanceof Player s) {

            if (!checkCommand(args, s)) {
                return true;
            }

            if (args.length == 0) {
                args = new String[] { "gui" };
            }

            List<String> cmds = Arrays.asList("clear", "confirm", "help", "set", "reset", "reload", "available", "gui", "add", "remove", "group", "custom", "pause");
            if (cmds.contains(args[0].toLowerCase())) {
                switch (args[0].toLowerCase()) {
                    case "clear": {
                        dataStore.setColour(s.getUniqueId(), "");
                        s.sendMessage(M.PREFIX + M.COLOR_CLEARED);
                        return true;
                    }

                    case "confirm": {
                        return handlersManager.callHandler(ConfirmHandler.class, s);
                    }

                    case "help": {
                        s.sendMessage(M.PREFIX + M.HELP_INFO_MESSAGE);
                        s.sendMessage(M.HELP_PLUGIN_PAGE);
                        s.sendMessage(M.HELP_WIKI_LINK);
                        return true;
                    }

                    case "set": {
                        handleSet(args, s);
                        return true;
                    }

                    case "reset": {
                        s.sendMessage("");
                        s.sendMessage(M.PREFIX + M.RESET_WARNING);
                        s.sendMessage(M.PREFIX + M.CONFIRM_ARE_YOU_SURE);
                        s.sendMessage(M.PREFIX + M.CONFIRM_FINALISE);

                        ChatColor.getPlugin().createConfirmScheduler(s, Setting.RESET, null);
                        return true;
                    }

                    case "reload": {
                        configsManager.reload();

                        for (Reloadable reloadable : ChatColor.getReloadables()) {
                            reloadable.reload();
                        }

                        if (dataStore instanceof YamlStorageImpl) {
                            for (Player player : Bukkit.getOnlinePlayers()) {
                                configsManager.loadPlayerConfig(player.getUniqueId());
                            }
                        }

                        s.sendMessage(M.PREFIX + M.RELOADED_MESSAGES);
                        return true;
                    }

                    case "pause": {
                        boolean paused = chatListener.togglePause(s);

                        if (paused) {
                            s.sendMessage(M.PREFIX + M.PLAYER_PAUSED);
                        }
                        else {
                            s.sendMessage(M.PREFIX + M.PLAYER_UNPAUSED);
                        }

                        return true;
                    }

                    case "available": {
                        char[] availableColours = GeneralUtils.getAvailableColours(s);
                        char[] availableModifiers = GeneralUtils.getAvailableModifiers(s);

                        String colourString = buildCharacterColourString(availableColours);
                        String modifierString = buildCharacterColourString(availableModifiers);

                        s.sendMessage(M.PREFIX + M.AVAILABLE_COLORS);
                        s.sendMessage(GeneralUtils.colourise(" &7- &e" + M.COLORS + ": " + colourString));
                        s.sendMessage(GeneralUtils.colourise(" &7- &e" + M.MODIFIERS + ": " + modifierString));
                        s.sendMessage(M.PREFIX + (checkPermission(s, "chatcolor.use-hex-codes") ? M.HEX_ACCESS : M.NO_HEX_PERMISSIONS));

                        return true;
                    }

                    case "gui": {
                        guiManager.openMainGui(s);
                        return true;
                    }

                    case "add": {

                        String modifierToAdd = getModifier(args[1]);
                        String colour = dataStore.getColour(s.getUniqueId());
                        String newColour = colour + modifierToAdd;

                        dataStore.setColour(s.getUniqueId(), newColour);
                        s.sendMessage(M.PREFIX + generalUtils.colourSetMessage(M.SET_OWN_COLOR, newColour));
                        return true;
                    }

                    case "remove": {

                        String modifierToRemove = getModifier(args[1]);
                        String colour = dataStore.getColour(s.getUniqueId());
                        String newColour = modifierToRemove == null ? colour : colour.replace(modifierToRemove, "");

                        dataStore.setColour(s.getUniqueId(), newColour);
                        s.sendMessage(M.PREFIX + generalUtils.colourSetMessage(M.SET_OWN_COLOR, newColour));
                        return true;
                    }

                    case "group": {
                        if (args[1].equals("list")) {
                            s.sendMessage(M.PREFIX + M.GROUP_COLOR_LIST);

                            Set<String> groupColourNames = groupColoursManager.getOrderedGroupNames();
                            Map<String, String> groupColours = groupColoursManager.getGroupColours();

                            for (String colourName : groupColourNames) {
                                s.sendMessage(generalUtils.colourSetMessage(M.GROUP_COLOR_FORMAT.replace("[color-name]", colourName), groupColours.get(colourName)));
                            }

                            return true;
                        }

                        String action = args[1];
                        String name = args[2];

                        if (action.equals("add")) {
                            String colour = args[3];
                            groupColoursManager.addGroupColour(name, colour);
                            s.sendMessage(M.PREFIX + generalUtils.colourSetMessage(M.ADDED_GROUP_COLOR.replace("[color-name]", name), colour));
                        }
                        else if (action.equals("remove")) {
                            groupColoursManager.removeGroupColour(name);
                            s.sendMessage(M.PREFIX + M.REMOVED_GROUP_COLOR.replace("[color-name]", name));
                        }

                        return true;
                    }

                    case "custom": {
                        String subCommand = args[1];

                        switch (subCommand) {
                            case "list":
                                s.sendMessage(M.PREFIX + M.CUSTOM_COLORS_LIST);

                                Map<String, String> customColours = customColoursManager.getCustomColours();
                                for (String colourName : customColours.keySet()) {
                                    s.sendMessage(generalUtils.colourSetMessage(M.CUSTOM_COLOR_FORMAT.replace("[color-name]", colourName), customColours.get(colourName)));
                                }

                                return true;

                            case "add":
                                String actualName = customColoursManager.addCustomColour(args[2], args[3]);

                                if (actualName == null) {
                                    s.sendMessage(M.PREFIX + M.CUSTOM_COLOR_EXISTS);
                                    return true;
                                }

                                s.sendMessage(generalUtils.colourSetMessage(M.PREFIX + M.CUSTOM_COLOR_ADDED.replace("[color-name]", actualName), args[3]));

                                return true;

                            case "remove":
                                String colourName = args[2].startsWith("%") ? args[2] : '%' + args[2];
                                String removedColour = customColoursManager.removeCustomColour(colourName);

                                s.sendMessage(generalUtils.colourSetMessage(M.PREFIX + M.CUSTOM_COLOR_REMOVED.replace("[color-name]", colourName), removedColour));
                                return true;
                        }
                    }
                }
            }

            UUID uuid = generalUtils.getUUIDFromName(args[0]);
            if (uuid != null) {
                String result = parseAndSetColour(uuid, Arrays.copyOfRange(args, 1, args.length));

                Player target = Bukkit.getPlayer(uuid);
                if (notifyOthers && target != null) {
                    target.sendMessage(M.PREFIX + generalUtils.colourSetMessage(M.PLAYER_SET_YOUR_COLOR.replace("[player]", s.getName()), result));
                }

                s.sendMessage(M.PREFIX + generalUtils.colourSetMessage(M.SET_OTHERS_COLOR.replace("[player]", args[0]), result));
            }

            else {

                if (groupColoursManager.getGroupColourForPlayer(s) != null) {
                    if (forceGroupColours) {
                        s.sendMessage(M.PREFIX + M.USING_GROUP_COLOR);
                        return true;
                    }
                }

                String result = parseAndSetColour(s.getUniqueId(), args);
                s.sendMessage(M.PREFIX + generalUtils.colourSetMessage(M.SET_OWN_COLOR, result));
            }

            return true;
        }

        else {
            if (args.length < 2) {
                sender.sendMessage(M.PREFIX + M.NOT_ENOUGH_ARGS);
                return true;
            }

            if (args.length > 7) {
                sender.sendMessage(M.PREFIX + M.TOO_MANY_ARGS);
                return true;
            }

            UUID uuid = generalUtils.getUUIDFromName(args[0]);

            if (uuid == null && sender instanceof BlockCommandSender blockSender && args[0].equals("@p")) {
                Location location = blockSender.getBlock().getLocation();

                Collection<Player> players = location.getWorld().getEntitiesByClass(Player.class);
                Player closestPlayer = null;
                double closestDistance = Integer.MAX_VALUE;

                for (Player player : players) {
                    Location playerLoc = player.getLocation();
                    double distance = playerLoc.distance(location);

                    if (distance < closestDistance) {
                        closestDistance = distance;
                        closestPlayer = player;
                    }
                }

                if (closestPlayer != null) {
                    uuid = closestPlayer.getUniqueId();
                }
            }

            if (uuid != null) {
                doConsoleSetColour(sender, uuid, args, notifyOthers);
            }
            else {
                sender.sendMessage(M.PREFIX + M.PLAYER_NOT_JOINED);
            }
        }

        return true;
    }

    private void doConsoleSetColour(CommandSender sender, UUID uuid, String[] args, boolean notifyOthers) {
        String colour = args[1];

        if (colour.startsWith("&u") || colour.startsWith("&g")) {
            if (isInvalidColourString(colour)) {
                sender.sendMessage(M.PREFIX + M.INVALID_COLOR.replace("[color]", colour));
                return;
            }
        }
        else {
            colour = getColour(colour);

            if (colour == null) {
                sender.sendMessage(M.PREFIX + M.INVALID_COLOR.replace("[color]", args[1]));
                return;
            }

            if (GeneralUtils.isValidHexColour(colour) && CompatabilityUtils.isHexLegacy()) {
                sender.sendMessage(M.PREFIX + M.NO_HEX_SUPPORT);
                return;
            }
            else if (GeneralUtils.isCustomColour(colour)) {
                if (customColoursManager.getCustomColour(colour) == null) {
                    sender.sendMessage(M.PREFIX + M.INVALID_CUSTOM_COLOR);
                    return;
                }
                else if (args.length > 2) {
                    sender.sendMessage(M.PREFIX + M.CANNOT_MODIFY_CUSTOM_COLOR);
                    return;
                }
            }

            for (int i = 2; i < args.length; i++) {
                if (getModifier(args[i]) == null) {
                    sender.sendMessage(M.PREFIX + M.INVALID_MODIFIER.replace("[modifier]", args[i]));
                    return;
                }
            }
        }

        String result = parseAndSetColour(uuid, Arrays.copyOfRange(args, 1, args.length));
        Player target = Bukkit.getPlayer(args[0]);

        if (notifyOthers && target != null) {
            target.sendMessage(M.PREFIX + generalUtils.colourSetMessage(M.PLAYER_SET_YOUR_COLOR.replace("[player]", "CONSOLE"), result));
        }

        sender.sendMessage(M.PREFIX + generalUtils.colourSetMessage(M.SET_OTHERS_COLOR.replace("[player]", args[0]), result));
    }

    private String parseAndSetColour(UUID uuid, String[] args) {
        String colour = args[0];
        String result;

        if (colour.equals("default")) {
            colour = generalUtils.getDefaultColourForPlayer(uuid);
            dataStore.setColour(uuid, colour);

            result = colour;
        }
        else if (colour.startsWith("&u") || colour.startsWith("&g")) {
            dataStore.setColour(uuid, colour);
            result = colour;
        }
        else {
            result = colourFromArgs(args, 0);
            dataStore.setColour(uuid, result);
        }

        return result;
    }

    private String buildCharacterColourString(char[] characters) {
        String comma = GeneralUtils.colourise("&7, ");
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < characters.length; i++) {
            char mod = characters[i];
            builder.append('&').append(mod).append(mod);

            if (i != characters.length - 1) {
                builder.append(comma);
            }
        }

        return builder.toString();
    }

    public String colourFromArgs(String[] args, int startIndex) {
        StringBuilder builder = new StringBuilder();

        for (int i = startIndex; i < args.length; i++) {
            if (i == startIndex) {
                builder.append(getColour(args[i]));
            }
            else {
                builder.append(getModifier(args[i]));
            }
        }

        return builder.toString();
    }

    private boolean checkPermission(Player player, String permission) {
        return (player.isOp() || player.hasPermission(permission));
    }

    private boolean lacksColourPermission(Player player, char colour) {
        return !player.hasPermission("chatcolor.color." + colour) && !player.hasPermission("chatcolor.color." + StaticMaps.getColourName("" + colour));
    }

    private boolean lacksModifierPermission(Player player, char modifier) {
        return !player.hasPermission("chatcolor.modifier." + modifier) && !player.hasPermission("chatcolor.modifier." + StaticMaps.getModifierName("" + modifier));
    }

    private boolean checkCommand(String[] args, Player player) {
        UUID uuid = player.getUniqueId();

        if (args.length == 0) {

            if (mainConfig.getBoolean(Setting.COMMAND_OPENS_GUI.getConfigPath())) {
                args = new String[] { "gui" };
            }
            else {

                String groupColour = groupColoursManager.getGroupColourForPlayer(player);
                String colour = dataStore.getColour(uuid);

                if (groupColour != null) {

                    if (mainConfig.getBoolean(Setting.FORCE_GROUP_COLORS.getConfigPath())) {
                        colour = groupColour;
                    }
                }

                player.sendMessage(M.PREFIX + generalUtils.colourSetMessage(M.CURRENT_COLOR, colour));
                return false;
            }
        }

        if (!player.isOp() && !player.hasPermission("chatcolor.use")) {
            player.sendMessage(M.PREFIX + M.NO_PERMISSIONS);
            return false;
        }

        List<String> cmds = Arrays.asList("confirm", "reload", "reset", "help", "available", "pause");
        if (cmds.contains(args[0])) {
            if (args[0].equalsIgnoreCase("reset") && confirmationsManager.isConfirming(player)) {
                player.sendMessage(M.PREFIX + M.ALREADY_CONFIRMING);
                return false;
            }

            if (!player.isOp() && !player.hasPermission("chatcolor.admin") && !(args[0].equals("available") || args[0].equals("help"))) {
                player.sendMessage(M.PREFIX + M.NO_PERMISSIONS);
                return false;
            }

            return true;
        }

        switch (args[0]) {
            case "clear" -> {
                if (!player.hasPermission("chatcolor.clear")) {
                    player.sendMessage(M.PREFIX + M.NO_PERMISSIONS);
                    return false;
                }

                return true;
            }
            case "set" -> {
                if (args.length < 3) {
                    player.sendMessage(M.PREFIX + M.NOT_ENOUGH_ARGS);
                    return false;
                }

                if (!SETTING_NAMES.contains(args[1])) {
                    player.sendMessage(M.PREFIX + M.INVALID_SETTING.replace("[setting]", args[1]));
                    return false;
                }

                if (confirmationsManager.isConfirming(player)) {
                    player.sendMessage(M.PREFIX + M.ALREADY_CONFIRMING);
                    return false;
                }

                if (!player.isOp() && !player.hasPermission("chatcolor.admin")) {
                    player.sendMessage(M.PREFIX + M.NO_PERMISSIONS);
                    return false;
                }

                return true;
            }
            case "gui" -> {
                if (!player.isOp() && !player.hasPermission("chatcolor.gui")) {
                    player.sendMessage(M.PREFIX + M.NO_PERMISSIONS);
                    return false;
                }

                return true;
            }
            case "add", "remove" -> {
                if (args.length < 2) {
                    player.sendMessage(M.PREFIX + M.NOT_ENOUGH_ARGS);
                    return false;
                }

                String colour = dataStore.getColour(player.getUniqueId());

                if (GeneralUtils.isCustomColour(colour)) {
                    player.sendMessage(M.PREFIX + M.CANNOT_MODIFY_CUSTOM_COLOR);
                    return false;
                }

                String modifierToCheck = args[1];
                String modifier = getModifier(modifierToCheck);

                if (modifier == null) {
                    player.sendMessage(M.PREFIX + M.INVALID_MODIFIER.replace("[modifier]", modifierToCheck));
                    return false;
                } else if (lacksModifierPermission(player, modifier.charAt(1))) {
                    player.sendMessage(M.PREFIX + M.NO_MOD_PERMS.replace("[modifier]", generalUtils.getModifierName(modifier)));
                }

                if (args[0].equals("remove") && !colour.contains(modifier)) {
                    player.sendMessage(M.PREFIX + M.MODIFIER_NOT_IN_COLOR);
                    return false;
                }

                if (args[0].equals("add") && colour.contains(modifier)) {
                    player.sendMessage(M.PREFIX + M.MODIFIER_ALREADY_IN_COLOR);
                    return false;
                }

                return true;
            }
            case "group" -> {
                if (args.length < 2) {
                    player.sendMessage(M.PREFIX + M.NOT_ENOUGH_ARGS);
                    return false;
                }

                if (!player.isOp() && !player.hasPermission("chatcolor.admin")) {
                    player.sendMessage(M.PREFIX + M.NO_PERMISSIONS);
                    return false;
                }

                if (args[1].equals("list")) {
                    return true;
                } else if (args.length < 3) {
                    player.sendMessage(M.PREFIX + M.NOT_ENOUGH_ARGS);
                    return false;
                }

                String action = args[1];
                String name = args[2];

                switch (action) {
                    case "add": {
                        if (args.length < 4) {
                            player.sendMessage(M.PREFIX + M.NOT_ENOUGH_ARGS);
                            return false;
                        }

                        if (groupColoursManager.groupColourExists(name)) {
                            player.sendMessage(M.PREFIX + M.GROUP_COLOR_EXISTS);
                            return false;
                        }

                        String colour = args[3];

                        if (GeneralUtils.isCustomColour(colour) && customColoursManager.getCustomColour(colour) == null) {
                            player.sendMessage(M.INVALID_CUSTOM_COLOR);
                            return false;
                        } else if (isInvalidColourString(colour)) {
                            player.sendMessage(M.PREFIX + M.INVALID_COLOR.replace("[color]", args[3]));
                            return false;
                        } else if (CompatabilityUtils.isHexLegacy() && GeneralUtils.isValidHexColour(colour)) {
                            player.sendMessage(M.PREFIX + M.NO_HEX_SUPPORT);
                            return false;
                        }

                        return true;
                    }

                    case "remove": {
                        if (!groupColoursManager.groupColourExists(name)) {
                            player.sendMessage(M.PREFIX + M.GROUP_COLOR_NOT_EXISTS);
                            return false;
                        }

                        return true;
                    }

                    default: {
                        player.sendMessage(M.PREFIX + M.INVALID_COMMAND);
                        return false;
                    }
                }
            }
        }

        if (args[0].equalsIgnoreCase("custom")) {
            if (!player.isOp() && !player.hasPermission("chatcolor.admin")) {
                player.sendMessage(M.PREFIX + M.NO_PERMISSIONS);
                return false;
            }

            if (args.length >= 2 && args[1].equalsIgnoreCase("list")) {
                return true;
            }

            if (args.length < 3) {
                player.sendMessage(M.PREFIX + M.NOT_ENOUGH_ARGS);
                return false;
            }

            String subCommand = args[1];
            String colourName = args[2];
            switch (subCommand) {
                case "add": {
                    if (args.length < 4) {
                        player.sendMessage(M.PREFIX + M.NOT_ENOUGH_ARGS);
                        return false;
                    }

                    if (customColoursManager.getCustomColour(colourName) != null) {
                        player.sendMessage(M.PREFIX + M.CUSTOM_COLOR_EXISTS);
                        return false;
                    }
                    else if (isInvalidColourString(args[3])) {
                        player.sendMessage(M.PREFIX + M.INVALID_COLOR.replace("[color]", args[3]));
                        return false;
                    }

                    return true;
                }
                case "remove": {
                    if (customColoursManager.getCustomColour(colourName) == null) {
                        player.sendMessage(M.PREFIX + M.INVALID_CUSTOM_COLOR);
                        return false;
                    }

                    return true;
                }
            }
        }

        UUID targetUUID = generalUtils.getUUIDFromName(args[0]);
        if (targetUUID != null) {
            if (!player.isOp() && !player.hasPermission("chatcolor.change.others")) {
                player.sendMessage(M.PREFIX + M.NO_PERMISSIONS);
                return false;
            }

            if (args.length > 7) {
                player.sendMessage(M.PREFIX + M.TOO_MANY_ARGS);
                return false;
            }

            if (args.length < 2) {
                player.sendMessage(M.PREFIX + M.NOT_ENOUGH_ARGS);
                return false;
            }

            String colour = args[1];
            SpecialColourCheck specialCheck = checkSpecialColour(player, colour);

            if (specialCheck != SpecialColourCheck.NOT_SPECIAL) {
                return specialCheck == SpecialColourCheck.VALID;
            }

            colour = getColour(colour);

            if (colour != null) {

                if (GeneralUtils.isValidHexColour(colour) && CompatabilityUtils.isHexLegacy()) {
                    player.sendMessage(M.PREFIX + M.NO_HEX_SUPPORT);
                    return false;
                }
                else if (GeneralUtils.isCustomColour(colour)) {
                    if (customColoursManager.getCustomColour(colour) == null) {
                        player.sendMessage(M.PREFIX + M.INVALID_CUSTOM_COLOR);
                        return false;
                    }
                    else if (args.length > 2) {
                        player.sendMessage(M.PREFIX + M.CANNOT_MODIFY_CUSTOM_COLOR);
                        return false;
                    }
                }

                if (colour.equals("default")) {
                    return true;
                }

                for (int i = 1; i < args.length; i++) {
                    if (i == 1) {

                        if (args[1].startsWith("#")) {
                            String permission = "chatcolor.color." + args[i].substring(1).toLowerCase();

                            if (!checkPermission(player, permission) && !checkPermission(player, "chatcolor.use-hex-codes")) {
                                player.sendMessage(M.PREFIX + M.NO_COLOR_PERMS.replace("[color]", generalUtils.colouriseMessage(colour, args[1].toUpperCase(), false)));
                                return false;
                            }
                        }
                        else if (lacksColourPermission(player, colour.charAt(1))) {
                            player.sendMessage(M.PREFIX + M.NO_COLOR_PERMS.replace("[color]", generalUtils.colouriseMessage(colour, args[1], false)));
                            return false;
                        }

                        continue;
                    }

                    String modifier = getModifier(args[i]);

                    if (modifier == null) {
                        player.sendMessage(M.PREFIX + M.INVALID_MODIFIER.replace("[modifier]", args[i]));
                        return false;
                    }

                    if (!player.isOp() && lacksModifierPermission(player, modifier.charAt(1))) {
                        player.sendMessage(M.PREFIX + M.NO_MOD_PERMS.replace("[modifier]", generalUtils.getModifierName(args[i])));
                        return false;
                    }
                }
                return true;
            }

            if (args[1].length() == 1) {
                player.sendMessage(M.PREFIX + M.INVALID_COLOR.replace("[color]", args[1]));
                return false;
            }

            player.sendMessage(M.PREFIX + M.INVALID_COMMAND);
            return false;
        }

        String colour = args[0];
        SpecialColourCheck specialCheck = checkSpecialColour(player, colour);

        if (specialCheck != SpecialColourCheck.NOT_SPECIAL) {
            return specialCheck == SpecialColourCheck.VALID;
        }

        colour = getColour(colour);

        if (colour != null) {
            if (!checkPermission(player, "chatcolor.change.self")) {
                player.sendMessage(M.PREFIX + M.NO_PERMISSIONS);
                return false;
            }

            if (args.length > 6) {
                player.sendMessage(M.PREFIX + M.TOO_MANY_ARGS);
                return false;
            }

            if (GeneralUtils.isValidHexColour(colour) && CompatabilityUtils.isHexLegacy()) {
                player.sendMessage(M.PREFIX + M.NO_HEX_SUPPORT);
                return false;
            }
            else if (GeneralUtils.isCustomColour(colour)) {
                if (customColoursManager.getCustomColour(colour) == null) {
                    player.sendMessage(M.PREFIX + M.INVALID_CUSTOM_COLOR);
                    return false;
                }
                else if (!player.isOp() && !player.hasPermission("chatcolor.custom." + colour.replace("%", ""))) {
                    player.sendMessage(M.PREFIX + M.NO_CUSTOM_COLOR_PERMISSIONS);
                    return false;
                }
                else if (args.length > 1) {
                    player.sendMessage(M.PREFIX + M.CANNOT_MODIFY_CUSTOM_COLOR);
                    return false;
                }
            }

            if (colour.equals("default")) {
                return true;
            }

            for (int i = 0; i < args.length; i++) {
                if (i == 0) {
                    if (!GeneralUtils.isCustomColour(colour)) {

                        if (colour.startsWith("&#")) {
                            String permission = "chatcolor.color." + colour.substring(2).toLowerCase();

                            if (!checkPermission(player, permission) && !checkPermission(player, "chatcolor.use-hex-codes")) {
                                player.sendMessage(M.PREFIX + M.NO_COLOR_PERMS.replace("[color]", generalUtils.colouriseMessage(colour, args[0].toUpperCase(), false)));
                                return false;
                            }
                        }
                        else if (lacksColourPermission(player, colour.charAt(1))) {
                            player.sendMessage(M.PREFIX + M.NO_COLOR_PERMS.replace("[color]", generalUtils.colouriseMessage(colour, args[0], false)));
                            return false;
                        }
                    }

                    continue;
                }

                String modifier = getModifier(args[i]);

                if (modifier == null) {
                    player.sendMessage(M.PREFIX + M.INVALID_MODIFIER.replace("[modifier]", args[i]));
                    return false;
                }

                if (lacksModifierPermission(player, modifier.charAt(1))) {
                    player.sendMessage(M.PREFIX + M.NO_MOD_PERMS.replace("[modifier]", generalUtils.getModifierName(args[i])));
                    return false;
                }
            }
            return true;
        }

        if (args[0].length() == 1) {
            player.sendMessage(M.PREFIX + M.INVALID_COLOR.replace("[color]", args[0]));
            return false;
        }

        player.sendMessage(M.PREFIX + M.INVALID_COMMAND);
        return false;
    }

    @SuppressWarnings({ "deprecation", "fallthrough" })
    private void handleSet(String[] args, Player player) {
        String trueVal = GeneralUtils.colourise("&aTRUE");
        String falseVal = GeneralUtils.colourise("&cFALSE");

        String currentValueString = "";
        String newValueString = "";
        Object value = null;

        Setting setting = Setting.getSetting(args[1]);
        String rawValue = args[2];

        switch (setting.getDataType()) {
            case BOOLEAN: {
                boolean val;

                try {
                    val = Boolean.parseBoolean(rawValue);
                }
                catch (Exception e) {
                    player.sendMessage(M.PREFIX + M.NEEDS_BOOLEAN);
                    return;
                }

                boolean current = mainConfig.getBoolean(setting.getConfigPath());

                if (val == current) {
                    player.sendMessage(M.PREFIX + M.ALREADY_SET);
                    return;
                }

                currentValueString = current ? trueVal : falseVal;
                newValueString = val ? trueVal : falseVal;
                value = val;
                break;
            }

            case INTEGER: {
                int val;

                try {
                    val = Integer.parseInt(rawValue);
                }
                catch (Exception ex) {
                    player.sendMessage(M.PREFIX + M.NEEDS_NUMBER);
                    return;
                }

                int current = mainConfig.getInt(setting.getConfigPath());

                if (val == current) {
                    player.sendMessage(M.PREFIX + M.ALREADY_SET);
                    return;
                }

                currentValueString = String.valueOf(current);
                newValueString = String.valueOf(val);
                value = val;
                break;
            }

            case COMMAND_NAME: {

                for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
                    if (plugin.getDescription().getCommands().containsKey(rawValue)) {
                        player.sendMessage(M.PREFIX + M.COMMAND_EXISTS);
                        return;
                    }
                }

            }
            case STRING: {
                String current = mainConfig.getString(setting.getConfigPath(), "");

                if (rawValue.equals(current)) {
                    player.sendMessage(M.PREFIX + M.ALREADY_SET);
                    return;
                }

                currentValueString = current;
                newValueString = rawValue;
                value = rawValue;
                break;
            }

            case COLOUR_STRING: {
                if (isInvalidColourString(rawValue)) {
                    player.sendMessage(M.PREFIX + M.INVALID_COLOR.replace("[color]", rawValue));
                    return;
                }

                String current = mainConfig.getString("default.color", "");

                if (rawValue.equals(current)) {
                    player.sendMessage(M.PREFIX + M.ALREADY_SET);
                    return;
                }

                currentValueString = generalUtils.colouriseMessage(current, "this", false);
                newValueString = generalUtils.colouriseMessage(rawValue, "this", false);
                value = rawValue;
                break;
            }

        }

        player.sendMessage("");
        player.sendMessage(M.PREFIX + M.IS_CURRENTLY.replace("[setting]", setting.getName()).replace("[value]", currentValueString));
        player.sendMessage(M.PREFIX + M.TO_CHANGE.replace("[value]", newValueString));

        player.sendMessage("");
        player.sendMessage(M.PREFIX + M.CONFIRM_ARE_YOU_SURE);
        player.sendMessage(M.PREFIX + M.CONFIRM_FINALISE);

        ChatColor.getPlugin().createConfirmScheduler(player, setting, value);
    }

    public static String getColour(String str) {
        String colour = str.startsWith("%") ? str : str.toLowerCase();

        if (colour.equals("default") || GeneralUtils.isCustomColour(colour)) {
            return colour;
        }
        else if (GeneralUtils.isValidHexColour(str)) {
            if (str.startsWith("&")) {
                return str.toUpperCase();
            }
            else {
                return '&' + colour.toUpperCase();
            }
        }

        List<String> words = Arrays.asList("black", "dark.blue", "dark.green", "dark.aqua", "dark.red", "dark.purple", "gold", "gray", "dark.gray", "blue", "green", "aqua", "red", "light.purple", "yellow", "white");

        if (words.contains(colour)) {
            int wordIndex = words.indexOf(colour);

            if (wordIndex < 10) {
                return "&" + words.indexOf(colour);
            }
            else {
                return "&" + (char) ('a' + (wordIndex - 10));
            }
        }

        List<String> other = Arrays.asList("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b", "c", "d", "e", "f");

        if (other.contains(colour)) {
            return "&" + colour;
        }
        else {
            return null;
        }
    }

    public static String getModifier(String str) {
        List<String> mods = Arrays.asList("obfuscated", "bold", "strikethrough", "underlined", "italic");
        String s = str.toLowerCase();

        if (mods.contains(s)) {
            for (int i = 0; i < mods.size(); i++) {
                char modChar = 'k';
                modChar += (char) i;

                String currentMod = mods.get(i);
                if (currentMod.equals(s)) {
                    return "&" + modChar;
                }
            }
        }

        List<String> other = Arrays.asList("k", "l", "m", "n", "o");

        if (other.contains(s)) {
            return "&" + s;
        }

        return null;
    }

    private boolean isInvalidColourString(String customColourString) {
        return !GeneralUtils.COLOUR_PATTERN.matcher(customColourString).matches();
    }

    private enum SpecialColourCheck {
        NOT_SPECIAL,
        VALID,
        INVALID
    }

    private SpecialColourCheck checkSpecialColour(Player player, String colour) {
        if (!colour.startsWith("&u") && !colour.startsWith("&g")) {
            return SpecialColourCheck.NOT_SPECIAL;
        }

        if (colour.startsWith("&g") && CompatabilityUtils.isHexLegacy()) {
            player.sendMessage(M.PREFIX + M.NO_HEX_SUPPORT);
            return SpecialColourCheck.INVALID;
        }
        else if (!player.hasPermission("chatcolor.special")) {
            player.sendMessage(M.PREFIX + M.NO_PERMISSIONS);
            return SpecialColourCheck.INVALID;
        }
        else if (isInvalidColourString(colour)) {
            player.sendMessage(M.PREFIX + M.INVALID_COLOR.replace("[color]", colour));
            return SpecialColourCheck.INVALID;
        }

        return SpecialColourCheck.VALID;
    }

}
