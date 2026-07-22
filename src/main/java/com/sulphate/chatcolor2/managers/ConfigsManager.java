package com.sulphate.chatcolor2.managers;

import com.sulphate.chatcolor2.utils.CompatabilityUtils;
import com.sulphate.chatcolor2.utils.Config;
import com.sulphate.chatcolor2.utils.ConfigUtils;
import com.sulphate.chatcolor2.utils.Reloadable;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.UUID;

public final class ConfigsManager implements Reloadable {

    private final ConfigUtils configUtils;

    private final HashMap<String, YamlConfiguration> configs;

    public ConfigsManager(ConfigUtils configUtils) {
        this.configUtils = configUtils;
        configs = new HashMap<>();

        reload();
    }

    public void reload() {
        configUtils.clearCache();
        configs.clear();

        for (Config config : Config.values()) {
            String fileName = config.getFilename();

            if (config.equals(Config.GUI)) {
                if (CompatabilityUtils.isHexLegacy() && !configUtils.configExists(fileName)) {
                    configs.put(fileName, configUtils.getConfigOrCopyDefault(fileName, "legacy-gui.yml"));
                    continue;
                }
            }

            configs.put(fileName, configUtils.getConfigOrCopyDefault(fileName));
        }
    }

    public void reloadSingle(Config config) {
        String fileName = config.getFilename();
        configs.put(fileName, configUtils.getConfigOrCopyDefault(fileName));
    }

    public YamlConfiguration getConfig(Config config) {
        return configs.get(config.getFilename());
    }

    public void saveConfig(Config config) {
        configUtils.saveConfig(config.getFilename());
    }

    public void loadPlayerConfig(UUID uuid) {
        String filePath = "players" + File.separator + uuid + ".yml";
        configs.put(uuid + ".yml", configUtils.getConfigOrCreateBlank(filePath));
    }

    public YamlConfiguration getPlayerConfig(UUID uuid) {
        return configs.get(uuid + ".yml");
    }

}
