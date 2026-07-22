package com.sulphate.chatcolor2.utils;

import com.sulphate.chatcolor2.data.PlayerDataStore;
import com.sulphate.chatcolor2.main.ChatColor;
import com.sulphate.chatcolor2.managers.CustomColoursManager;
import com.sulphate.chatcolor2.managers.GroupColoursManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class PlaceholderAPIHook extends PlaceholderExpansion {

    private final ChatColor plugin;
    private final GeneralUtils generalUtils;
    private final CustomColoursManager customColoursManager;
    private final GroupColoursManager groupColoursManager;
    private final PlayerDataStore dataStore;
    private final Messages M;

    public PlaceholderAPIHook(
            ChatColor plugin, GeneralUtils generalUtils, CustomColoursManager customColoursManager,
            GroupColoursManager groupColoursManager, PlayerDataStore dataStore, Messages M
    ) {
        this.plugin = plugin;
        this.generalUtils = generalUtils;
        this.customColoursManager = customColoursManager;
        this.groupColoursManager = groupColoursManager;
        this.dataStore = dataStore;
        this.M = M;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getAuthorList().toString();
    }

    @Override
    public @NotNull String getIdentifier() {
        return "cc";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getVersionString();
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String identifier) {

        if (player == null) {
            return "";
        }

        UUID uuid = player.getUniqueId();
        String colour = dataStore.getColour(uuid);
        boolean isCustomColour = GeneralUtils.isCustomColour(colour);

        switch (identifier.toLowerCase()) {
            case "full_color": {

                if (isCustomColour) {
                    colour = customColoursManager.getCustomColour(colour);
                }

                return GeneralUtils.colourise(colour);
            }

            case "modifiers": {
                if (isCustomColour) {
                    colour = customColoursManager.getCustomColour(colour);
                }

                int modifiersStartIndex = (colour.substring(1).indexOf("&"));
                String modPart = colour.substring(modifiersStartIndex + 1);

                return GeneralUtils.colourise(modPart);
            }

            case "color": {
                if (isCustomColour) {
                    colour = customColoursManager.getCustomColour(colour);
                }

                int modifiersStartIndex = (colour.substring(1).indexOf("&"));

                if (modifiersStartIndex != -1) {
                    colour = colour.substring(0, modifiersStartIndex + 1);
                }

                return GeneralUtils.colourise(colour);
            }

            case "color_name": {
                return generalUtils.getColorName(colour, false);
            }

            case "colored_color_name": {
                return generalUtils.colouriseMessage(colour, generalUtils.getColorName(colour, false), false);
            }

            case "full_color_name": {
                return generalUtils.getColorName(colour, true);
            }

            case "colored_full_color_name": {
                return generalUtils.colouriseMessage(colour, generalUtils.getColorName(colour, true), false);
            }

            case "modifier_names": {
                if (isCustomColour) {
                    colour = customColoursManager.getCustomColour(colour);
                }

                return generalUtils.getModifierNames(colour, false).collect(Collectors.joining());
            }

            case "modifiers_spaced": {
                if (isCustomColour) {
                    colour = customColoursManager.getCustomColour(colour);
                }

                return generalUtils.getModifierNames(colour, false).collect(Collectors.joining(" "));
            }

            case "modified_modifier_names": {
                if (isCustomColour) {
                    colour = customColoursManager.getCustomColour(colour);
                }

                return GeneralUtils.colourise(generalUtils.getModifierNames(colour, false).map(m -> "&f&" + m + m).collect(Collectors.joining()));
            }

            case "full_modifier_names": {
                if (isCustomColour) {
                    colour = customColoursManager.getCustomColour(colour);
                }

                return generalUtils.getModifierNames(colour, true).collect(Collectors.joining(", "));
            }

            case "modified_full_modifier_names": {
                if (isCustomColour) {
                    colour = customColoursManager.getCustomColour(colour);
                }

                List<String> modifierChars = generalUtils.getModifierNames(colour, false).toList();
                List<String> modifierNames = generalUtils.getModifierNames(colour, true).toList();

                StringBuilder builder = new StringBuilder();

                for (int i = 0; i < modifierNames.size(); i++) {
                    if (i > 0) {
                        builder.append("&r&f, ");
                    }

                    builder.append("&f&").append(modifierChars.get(i)).append(modifierNames.get(i));
                }

                return GeneralUtils.colourise(builder.toString());
            }

            case "group": {
                String groupName = groupColoursManager.getGroupColourForPlayer(player, true);
                return groupName == null ? "None" : groupName;
            }

            default: {

                if (identifier.matches("^[0-9abcdef]_available$")) {
                    String codeToCheck = identifier.split("_")[0];

                    if (player.hasPermission("chatcolor.color." + codeToCheck)) {
                        return M.GUI_AVAILABLE;
                    }
                    else {
                        return M.GUI_UNAVAILABLE;
                    }
                }

                else if (identifier.matches("^[klmno]_available$")) {
                    String codeToCheck = identifier.split("_")[0];

                    if (player.hasPermission("chatcolor.modifier." + codeToCheck)) {
                        return M.GUI_AVAILABLE;
                    }
                    else {
                        return M.GUI_UNAVAILABLE;
                    }
                }
            }
        }

        return null;
    }

}
