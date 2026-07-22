package com.sulphate.chatcolor2.utils;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("deprecation")
public class InventoryUtils {

    private InventoryUtils() {

    }

    public static List<String> getLore(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasLore() ? meta.getLore() : new ArrayList<>();
    }

    public static void setDisplayName(ItemStack item, String displayName) {
        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return;
        }

        meta.setDisplayName(GeneralUtils.colourise(displayName));
        item.setItemMeta(meta);
    }

    public static void setLore(ItemStack item, List<String> lore) {
        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return;
        }

        meta.setLore(lore.stream().map(GeneralUtils::colourise).toList());
        item.setItemMeta(meta);
    }

    public static void addFakeEnchantment(ItemStack item) {
        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return;
        }

        meta.addEnchant(Enchantment.FIRE_ASPECT, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        item.setItemMeta(meta);
    }

}
