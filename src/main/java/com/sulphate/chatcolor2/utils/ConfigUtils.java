package com.sulphate.chatcolor2.utils;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

@SuppressWarnings("deprecation")
public class ConfigUtils {

    private final JavaPlugin plugin;
    private final Consumer<String> errorLogger;

    private final Map<String, YamlConfiguration> configCache;

    public ConfigUtils(JavaPlugin plugin, Consumer<String> errorLogger) {
        this.plugin = plugin;
        this.errorLogger = errorLogger;

        configCache = new HashMap<>();
    }

    public void clearCache() {
        configCache.clear();
    }

    private void printError(String message) {
        errorLogger.accept(colourise(String.format("&6%s &7| &cError: %s", plugin.getName(), message)));
    }

    public void saveConfig(String configName) {
        YamlConfiguration config = configCache.get(configName);

        if (config == null) {
            printError(String.format("Tried to save an unloaded config: %s", configName));
            return;
        }

        File configFile = getFileOrCreateBlank(configName);

        if (configFile == null) {
            return;
        }

        try {
            config.save(configFile);
        }
        catch (IOException ex) {
            printError(String.format("Failed to save config %s: %s", configName, ex.getMessage()));
        }
    }

    private File getDataFolder() {
        File dataFolder = plugin.getDataFolder();

        if (!dataFolder.isDirectory() && !dataFolder.mkdirs()) {
            printError(String.format("Failed to create data folder %s", dataFolder.getPath()));
        }

        return dataFolder;
    }

    public boolean configExists(String configName) {
        return new File(getDataFolder(), configName).exists();
    }

    public YamlConfiguration getConfigOrCreateBlank(String configName) {
        File file = getFileOrCreateBlank(configName);
        return file == null ? null : loadAndCache(configName, file);
    }

    private File getFileOrCreateBlank(String fileName) {
        File target = new File(getDataFolder(), fileName);

        if (!target.exists()) {
            try {
                if (!target.createNewFile()) {
                    printError(String.format("Failed to create file %s", fileName));
                    return null;
                }
            }
            catch (IOException ex) {
                printError(String.format("Failed to create file %s: %s", fileName, ex.getMessage()));
                return null;
            }
        }

        return target;
    }

    public YamlConfiguration getConfigOrCopyDefault(String configName, String fileName) {
        File file = getFileOrCopyResource(fileName, configName);
        return file == null ? null : loadAndCache(configName, file);
    }

    public YamlConfiguration getConfigOrCopyDefault(String configName) {
        return getConfigOrCopyDefault(configName, configName);
    }

    private YamlConfiguration loadAndCache(String configName, File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        configCache.put(configName, config);

        return config;
    }

    private File getFileOrCopyResource(String resourceFileName, String destinationFileName) {
        File target = new File(getDataFolder(), destinationFileName);

        if (target.exists()) {
            return target;
        }

        target = getFileOrCreateBlank(destinationFileName);

        if (target == null) {
            return null;
        }

        try (InputStream in = plugin.getResource(resourceFileName)) {
            if (in == null) {
                printError(String.format("Failed to find resource %s", resourceFileName));
                return null;
            }

            try (OutputStream out = Files.newOutputStream(target.toPath())) {
                in.transferTo(out);
            }
        }
        catch (IOException ex) {
            printError(String.format("Failed to write resource %s: %s", destinationFileName, ex.getMessage()));
            return null;
        }

        return target;
    }

    private static String colourise(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

}
