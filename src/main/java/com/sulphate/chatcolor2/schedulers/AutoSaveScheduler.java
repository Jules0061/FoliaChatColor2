package com.sulphate.chatcolor2.schedulers;

import com.sulphate.chatcolor2.main.ChatColor;
import com.sulphate.chatcolor2.utils.GeneralUtils;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AutoSaveScheduler {

    private final ChatColor plugin;

    private ScheduledTask task;
    private final ConcurrentHashMap<String, YamlConfiguration> configsToSave;
    private int saveInterval;

    public AutoSaveScheduler(int saveInterval) {
        this.plugin = ChatColor.getPlugin();
        this.configsToSave = new ConcurrentHashMap<>();
        this.saveInterval = saveInterval;

        run();
    }

    private void run() {
        long ticks = (long) saveInterval * 20 * 60;
        task = Schedulers.globalRepeating(plugin, ignored -> saveAllConfigs(), ticks, ticks);
    }

    public void setSaveInterval(int saveInterval) {
        this.saveInterval = saveInterval;
        restart();
    }

    private void restart() {
        cancelTask();
        run();
    }

    public void stop() {
        cancelTask();
        saveAllConfigs();
    }

    private void cancelTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void saveConfigWithDelay(String configName, YamlConfiguration config) {
        configsToSave.put(configName, config);
    }

    private void saveAllConfigs() {
        File dataFolder = plugin.getDataFolder();

        for (Iterator<Map.Entry<String, YamlConfiguration>> it = configsToSave.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, YamlConfiguration> entry = it.next();

            try {
                entry.getValue().save(new File(dataFolder, entry.getKey()));
            }
            catch (IOException ex) {
                GeneralUtils.sendConsoleMessage("&cError: Failed to save a config (" + entry.getKey() + ")!");
            }

            it.remove();
        }
    }

}
