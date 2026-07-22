package com.sulphate.chatcolor2.managers;

import com.sulphate.chatcolor2.utils.Config;
import com.sulphate.chatcolor2.utils.Reloadable;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class GroupColoursManager implements Reloadable {

    private final ConfigsManager configsManager;

    private final Map<String, String> groupColours;
    private volatile Set<String> orderedGroupNames;
    private YamlConfiguration config;

    public GroupColoursManager(ConfigsManager configsManager) {
        this.configsManager = configsManager;
        groupColours = new ConcurrentHashMap<>();

        reload();
    }

    public void reload() {
        groupColours.clear();
        config = configsManager.getConfig(Config.GROUPS);
        orderedGroupNames = config.getKeys(false);

        for (String groupName : orderedGroupNames) {
            String colour = config.getString(groupName);

            if (colour != null) {
                groupColours.put(groupName, colour);
            }
        }
    }

    public boolean groupColourExists(String name) {
        return groupColours.containsKey(name);
    }

    public void addGroupColour(String name, String colour) {
        if (groupColours.containsKey(name)) {
            return;
        }

        groupColours.put(name, colour);
        config.set(name, colour);
        configsManager.saveConfig(Config.GROUPS);
    }

    public void removeGroupColour(String name) {
        if (!groupColours.containsKey(name)) {
            return;
        }

        groupColours.remove(name);
        config.set(name, null);
        configsManager.saveConfig(Config.GROUPS);
    }

    public Set<String> getOrderedGroupNames() {
        return orderedGroupNames;
    }

    public String getGroupColourForPlayer(Player player, boolean returnName) {
        if (player.hasPermission("*") || player.hasPermission("chatcolor.*") || player.isOp()) {
            return null;
        }

        for (String groupName : orderedGroupNames) {
            if (player.hasPermission("chatcolor.group." + groupName)) {
                return returnName ? groupName : groupColours.get(groupName);
            }
        }

        return null;
    }

    public String getGroupColourForPlayer(Player player) {
        return getGroupColourForPlayer(player, false);
    }

    public Map<String, String> getGroupColours() {
        return groupColours;
    }

}
