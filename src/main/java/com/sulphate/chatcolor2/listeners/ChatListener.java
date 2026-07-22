package com.sulphate.chatcolor2.listeners;

import com.sulphate.chatcolor2.data.PlayerData;
import com.sulphate.chatcolor2.event.ChatColorEvent;
import com.sulphate.chatcolor2.commands.Setting;
import com.sulphate.chatcolor2.data.PlayerDataStore;
import com.sulphate.chatcolor2.managers.ConfigsManager;
import com.sulphate.chatcolor2.managers.GroupColoursManager;
import com.sulphate.chatcolor2.utils.Config;
import com.sulphate.chatcolor2.utils.GeneralUtils;
import com.sulphate.chatcolor2.utils.Reloadable;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@SuppressWarnings("deprecation")
public final class ChatListener implements Listener, Reloadable {

    private static final Pattern SYMBOLS_REGEX = Pattern.compile("^[!^\"£$%*()\\[\\]{}'#@~;:,./<>?\\\\|\\-_=+]+[^!^\"£$%&*()\\[\\]{}'#@~;:,./<>?\\\\|\\-_=+]+");

    private final ConfigsManager configsManager;
    private final GeneralUtils generalUtils;
    private final GroupColoursManager groupColoursManager;
    private final PlayerDataStore dataStore;

    private YamlConfiguration mainConfig;
    private final Set<Player> pausedPlayers;

    public ChatListener(ConfigsManager configsManager, GeneralUtils generalUtils, GroupColoursManager groupColoursManager, PlayerDataStore dataStore) {
        this.configsManager = configsManager;
        this.generalUtils = generalUtils;
        this.groupColoursManager = groupColoursManager;
        this.dataStore = dataStore;

        pausedPlayers = ConcurrentHashMap.newKeySet();

        reload();
    }

    public void reload() {
        mainConfig = configsManager.getConfig(Config.MAIN_CONFIG);
    }

    public boolean togglePause(Player player) {
        if (pausedPlayers.contains(player)) {
            pausedPlayers.remove(player);
            return false;
        }
        else {
            pausedPlayers.add(player);
            return true;
        }
    }

    public void onEvent(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();
        UUID uuid = player.getUniqueId();

        if (event.isCancelled() || checkHasSymbolPrefix(message) || pausedPlayers.contains(player)) {
            return;
        }

        boolean defaultColourEnabled = mainConfig.getBoolean(Setting.DEFAULT_COLOR_ENABLED.getConfigPath());

        if (dataStore.getColour(uuid) == null) {
            if (defaultColourEnabled) {
                String defaultColor = mainConfig.getString("default.color");
                colourAndModify(player, message, defaultColor, event);
            }

            return;
        }

        if (defaultColourEnabled) {
            generalUtils.checkDefault(uuid);
        }

        if (mainConfig.getBoolean(Setting.REMOVE_INACCESSIBLE_COLORS.getConfigPath())) {
            String colour = dataStore.getColour(uuid);
            String colourName = dataStore.getPlayerData(uuid).getColourName();

            if (!hasDefaultOrGroupColour(player, colour)) {
                removeInaccessibleColour(player, colourName);
            }
        }

        message = checkColourCodes(message, player);

        String groupColour = groupColoursManager.getGroupColourForPlayer(player);
        String colour = dataStore.getColour(uuid);

        if (groupColour != null) {

            if (mainConfig.getBoolean(Setting.FORCE_GROUP_COLORS.getConfigPath())) {
                colour = groupColour;
            }
        }

        colourAndModify(player, message, colour, event);
    }

    private boolean checkHasSymbolPrefix(String message) {
        return mainConfig.getBoolean(Setting.IGNORE_SYMBOL_PREFIXES.getConfigPath())
                && SYMBOLS_REGEX.matcher(message).matches();
    }

    private void colourAndModify(Player player, String message, String colour, AsyncPlayerChatEvent event) {
        if (GeneralUtils.isDifferentWhenColourised(message)) {
            boolean override = mainConfig.getBoolean(Setting.COLOR_OVERRIDE.getConfigPath());

            if (override) {
                while (GeneralUtils.isDifferentWhenColourised(message)) {

                    message = org.bukkit.ChatColor.stripColor(GeneralUtils.colourise(message));
                }

                event.setMessage(message);
            }
            else {
                event.setMessage(GeneralUtils.colourise(message));
            }
        }
        else {
            boolean eventSucceeded;

            try {
                eventSucceeded = fireEvent(player, message, colour, event);
            }
            catch (Exception ex) {
                eventSucceeded = false;
            }

            if (eventSucceeded) {
                event.setMessage(generalUtils.colouriseMessage(colour, message, false));
            }
        }
    }

    private String checkColourCodes(String message, Player player) {

        if (!player.hasPermission("chatcolor.use-color-codes")) {

            while (GeneralUtils.isDifferentWhenColourised(message)) {

                message = org.bukkit.ChatColor.stripColor(GeneralUtils.colourise(message));
            }
        }

        if (!player.hasPermission("chatcolor.use-hex-codes") && GeneralUtils.containsHexColour(message, true)) {
            while (GeneralUtils.isDifferentWhenColourised(message)) {
                message = org.bukkit.ChatColor.stripColor(GeneralUtils.colourise(message));
            }
        }

        return message;
    }

    private boolean hasDefaultOrGroupColour(Player player, String colour) {

        if (mainConfig.getBoolean(Setting.DEFAULT_COLOR_ENABLED.getConfigPath())) {
            String defaultColour = mainConfig.getString("default.color");

            if (colour.equals(defaultColour)) {
                return true;
            }
        }

        String groupColour = groupColoursManager.getGroupColourForPlayer(player);

        if (groupColour != null) {
            return colour.equals(groupColour);
        }

        return false;
    }

    private void removeInaccessibleColour(Player player, String colourName) {
        PlayerData data = dataStore.getPlayerData(player.getUniqueId());
        UUID uuid = player.getUniqueId();

        String permission = "";
        boolean shouldRemove = false;

        if (colourName.startsWith("%")) {
            permission = "chatcolor.custom." + colourName.substring(1);
        }
        else if (colourName.startsWith("#")) {
            if (player.hasPermission("chatcolor.use-hex-codes")) {
                shouldRemove = true;
            }
            else {
                permission = "chatcolor.color." + colourName.substring(1);
            }
        }
        else if (colourName.startsWith("u") || colourName.startsWith("g")) {
            permission = "chatcolor.special";
        }
        else {
            permission = "chatcolor.color." + colourName;
        }

        Set<Character> mods = data.getModifiers();

        for (char mod : mods) {
            if (!player.hasPermission("chatcolor.modifier." + mod)) {
                mods.remove(mod);
            }
        }

        if (shouldRemove || !player.hasPermission(permission)) {
            if (mainConfig.getBoolean(Setting.DEFAULT_COLOR_ENABLED.getConfigPath())) {
                dataStore.setColour(uuid, mainConfig.getString("default.color"));
            }
            else {
                dataStore.setColour(uuid, "");
            }
        }

        for (char mod : mods) {
            data.addModifier(mod);
        }
    }

    private boolean fireEvent(Player player, String message, String colour, AsyncPlayerChatEvent chatEvent) {
        ChatColorEvent chatColorEvent = new ChatColorEvent(player, message, colour, chatEvent);
        Bukkit.getPluginManager().callEvent(chatColorEvent);

        return !chatColorEvent.isCancelled();
    }

}
