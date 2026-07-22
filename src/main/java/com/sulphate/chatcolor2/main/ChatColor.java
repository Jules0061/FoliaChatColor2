package com.sulphate.chatcolor2.main;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

import com.sulphate.chatcolor2.commands.ChatColorCommand;
import com.sulphate.chatcolor2.commands.Setting;
import com.sulphate.chatcolor2.data.DatabaseConnectionSettings;
import com.sulphate.chatcolor2.data.PlayerDataStore;
import com.sulphate.chatcolor2.data.SqlStorageImpl;
import com.sulphate.chatcolor2.data.YamlStorageImpl;
import com.sulphate.chatcolor2.gui.item.ItemStackTemplate;
import com.sulphate.chatcolor2.listeners.*;
import com.sulphate.chatcolor2.managers.*;
import com.sulphate.chatcolor2.gui.GuiManager;
import com.sulphate.chatcolor2.utils.*;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import com.sulphate.chatcolor2.schedulers.ConfirmScheduler;
import com.sulphate.chatcolor2.schedulers.Schedulers;
import com.sulphate.chatcolor2.commands.ConfirmHandler;

@SuppressWarnings("deprecation")
public class ChatColor extends JavaPlugin {

    private static final String EXAMPLE_HEAD_DATA = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzMzYWU4ZGU3ZWQwNzllMzhkMmM4MmRkNDJiNzRjZmNiZDk0YjM0ODAzNDhkYmI1ZWNkOTNkYThiODEwMTVlMyJ9fX0=";

    private static ChatColor plugin;
    private static List<Reloadable> reloadables;

    private HandlersManager handlersManager;
    private ConfigsManager configsManager;
    private CustomColoursManager customColoursManager;
    private GroupColoursManager groupColoursManager;
    private GeneralUtils generalUtils;
    private GuiManager guiManager;
    private ConfirmationsManager confirmationsManager;
    private PlayerDataStore playerDataStore;
    private Messages M;

    private PlayerJoinListener joinListener;
    private ChatListener chatListener;
    private YamlConfiguration config;

    private final ConsoleCommandSender console = Bukkit.getConsoleSender();
    private PluginManager manager;

    public static ChatColor getPlugin() {
        return plugin;
    }

    public static List<Reloadable> getReloadables() {
        return reloadables;
    }

    public String getVersionString() {
        return getPluginMeta().getVersion();
    }

    public List<String> getAuthorList() {
        return getPluginMeta().getAuthors();
    }

    @Override
    public void onEnable() {
        plugin = this;
        reloadables = new ArrayList<>();
        manager = Bukkit.getPluginManager();

        Schedulers.init();

        setupObjects();

        if (!isEnabled()) {
            return;
        }

        setupListeners();
        setupCommands();

        boolean metrics = getConfig().getBoolean("stats");
        if (metrics) {
            new Metrics(this, 826);
        }

        for (String message : M.STARTUP_MESSAGES) {
            message = message.replace("[version]", getVersionString());
            message = message.replace("[version-description]", "SQL support tweaks & bug fixes.");
            console.sendMessage(M.PREFIX + GeneralUtils.colourise(message));
        }

        if (CompatabilityUtils.isHexLegacy()) {
            console.sendMessage(M.PREFIX + M.LEGACY_DETECTED);
        }

        ItemStackTemplate head = new ItemStackTemplate(Material.PLAYER_HEAD, null, null, EXAMPLE_HEAD_DATA);
        head.build(1);

        if (head.failedToApplyHeadData()) {
            console.sendMessage(M.PREFIX + Messages.PLAYER_HEADS_NOT_SUPPORTED);
        }

        if (manager.getPlugin("PlaceholderAPI") != null) {
            new PlaceholderAPIHook(
                    this, generalUtils, customColoursManager, groupColoursManager, playerDataStore, M
            ).register();

            console.sendMessage(M.PREFIX + M.PLACEHOLDERS_ENABLED);
        }
        else {
            console.sendMessage(M.PREFIX + M.PLACEHOLDERS_DISABLED);
        }

        if (!metrics) {
            console.sendMessage(M.PREFIX + M.METRICS_DISABLED);
        }
        else {
            console.sendMessage(M.PREFIX + M.METRICS_ENABLED);
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            joinListener.handleJoin(player);
        }
    }

    @Override
    public void onDisable() {

        Schedulers.markShuttingDown();

        if (guiManager != null) {
            guiManager.closeOpenGuis();
        }

        if (playerDataStore != null) {
            playerDataStore.shutdown();
        }

        plugin = null;

        if (M != null) {
            console.sendMessage(M.PREFIX + M.SHUTDOWN.replace("[version]", getVersionString()));
        }
    }

    private void setupObjects() {

        CompatabilityUtils.init();

        ConfigUtils configUtils = new ConfigUtils(this, GeneralUtils::sendConsoleMessage);
        configsManager = new ConfigsManager(configUtils);
        config = configsManager.getConfig(Config.MAIN_CONFIG);

        if (!validateConfig()) {
            manager.disablePlugin(this);
            return;
        }

        handlersManager = new HandlersManager();
        customColoursManager = new CustomColoursManager(configsManager);
        groupColoursManager = new GroupColoursManager(configsManager);
        M = new Messages(configsManager);

        scanMessages();

        String pdcType = config.getString("storage.type");

        if (pdcType != null && pdcType.equals("sql")) {
            ConfigurationSection dbSection = config.getConfigurationSection("storage.database");

            if (dbSection == null) {
                GeneralUtils.sendConsoleMessage(M.PREFIX + M.MISSING_DB_CONFIG_SECTION);
                Bukkit.getPluginManager().disablePlugin(this);
                return;
            }

            else if (!dbSection.contains("type")) {
                dbSection.set("type", "mysql");
                configsManager.saveConfig(Config.MAIN_CONFIG);
            }

            playerDataStore = new SqlStorageImpl(new DatabaseConnectionSettings(dbSection), M);
        }
        else {
            int saveInterval = config.getInt(Setting.SAVE_INTERVAL.getConfigPath());
            playerDataStore = new YamlStorageImpl(configsManager, saveInterval, M);
        }

        generalUtils = new GeneralUtils(configsManager, customColoursManager, playerDataStore, groupColoursManager, M);
        guiManager = new GuiManager(configsManager, playerDataStore, generalUtils, customColoursManager, M);
        confirmationsManager = new ConfirmationsManager();

        reloadables.add(customColoursManager);
        reloadables.add(groupColoursManager);
        reloadables.add(M);
        reloadables.add(generalUtils);
        reloadables.add(guiManager);

        scanSettings();
        scanOther();
    }

    private void setupCommands() {
        ChatColorCommand command = new ChatColorCommand(
                M, generalUtils, confirmationsManager, configsManager, handlersManager, guiManager,
                customColoursManager, groupColoursManager, playerDataStore, chatListener
        );
        ConfirmHandler confirmHandler = new ConfirmHandler(
                M, confirmationsManager, configsManager, customColoursManager, guiManager, generalUtils,
                playerDataStore
        );

        PluginCommand chatColorCommand = getCommand("chatcolor");

        if (chatColorCommand == null) {
            console.sendMessage(GeneralUtils.colourise("&b[ChatColor] &cError: The chatcolor command is missing from plugin.yml!"));
            manager.disablePlugin(this);
            return;
        }

        chatColorCommand.setExecutor(command);
        reloadables.add(command);
        reloadables.add(confirmHandler);
        handlersManager.registerHandler(ConfirmHandler.class, confirmHandler);
    }

    private void setupListeners() {
        EventPriority chatPriority = EventPriority.valueOf(config.getString("settings.event-priority"));
        chatListener = new ChatListener(configsManager, generalUtils, groupColoursManager, playerDataStore);

        EventExecutor executor = (listener, event) -> {
            if (listener instanceof ChatListener && event instanceof AsyncPlayerChatEvent) {
                ((ChatListener) listener).onEvent((AsyncPlayerChatEvent) event);
            }
        };

        manager.registerEvent(AsyncPlayerChatEvent.class, chatListener, chatPriority, executor, this);

        joinListener = new PlayerJoinListener(
                M, configsManager, generalUtils, customColoursManager, groupColoursManager, playerDataStore
        );
        CustomCommandListener commandListener = new CustomCommandListener(configsManager);

        manager.registerEvents(joinListener, this);
        manager.registerEvents(commandListener, this);
        manager.registerEvents(guiManager, this);

        reloadables.add(joinListener);
        reloadables.add(chatListener);
        reloadables.add(commandListener);
    }

    private boolean validateConfig() {
        File dataFolder = getDataFolder();
        File configFile = new File(dataFolder, "config.yml");

        if (configFile.exists()) {

            String version = config.getString("version");
            String latest = getVersionString();

            if (isOlderThan(version, "1.15")) {
                if (backupOldConfigFailed("gui.yml")) return false;
                saveResource("gui.yml", true);
                configsManager.reloadSingle(Config.GUI);

                console.sendMessage(GeneralUtils.colourise("&b[ChatColor] &cWarning: &eAn old GUI config was found. It has been copied to &aold-gui.yml&e."));
            }

            if (isOlderThan(version, "1.14")) {
                if (backupOldConfigFailed("config.yml")) return false;
                saveResource("config.yml", true);
                configsManager.reloadSingle(Config.MAIN_CONFIG);

                console.sendMessage(GeneralUtils.colourise("&b[ChatColor] &cWarning: &eAn old version of the config was found. It has been copied to &aold-config.yml&e."));
            }
            else if (isOlderThan(version, "1.12")) {
                File legacyGroupConfigFile = new File(dataFolder, "groups.yml");

                if (legacyGroupConfigFile.exists()) {
                    YamlConfiguration legacyGroupConfig = YamlConfiguration.loadConfiguration(legacyGroupConfigFile);
                    File newGroupConfigFile = new File(dataFolder, "groups.yml");

                    try {
                        if (!newGroupConfigFile.exists() && !newGroupConfigFile.createNewFile()) {
                            throw new IOException("could not create groups.yml");
                        }

                        legacyGroupConfig.save(newGroupConfigFile);

                        if (!legacyGroupConfigFile.equals(newGroupConfigFile) && !legacyGroupConfigFile.delete()) {
                            GeneralUtils.sendConsoleMessage("&b[ChatColor] &cWarning: &eFailed to delete the legacy groups config.");
                        }

                        GeneralUtils.sendConsoleMessage("&b[ChatColor] &bInfo: &eCopied legacy groups config to a new file, groups.yml.");
                    }
                    catch (IOException ex) {
                        GeneralUtils.sendConsoleMessage("&b[ChatColor] &cWarning: &eFailed to copy legacy groups config to new file: " + ex.getMessage());
                    }
                }
            }

            else if (!version.equals(latest)) {
                config.set("version", latest);
                configsManager.saveConfig(Config.MAIN_CONFIG);
            }
        }

        return true;
    }

    private boolean isOlderThan(String version, String target) {

        if (version == null) {
            return true;
        }

        String[] versionParts = version.split("\\.");
        String[] targetParts = target.split("\\.");

        for (int i = 0; i < versionParts.length; i++) {
            if (i == targetParts.length) {
                return false;
            }

            int versionPart = Integer.parseInt(versionParts[i]);
            int targetPart = Integer.parseInt(targetParts[i]);

            if (versionPart > targetPart) return false;

            if (versionPart < targetPart) return true;

        }

        return false;
    }

    private boolean backupOldConfigFailed(String configName) {
        File oldConfig = new File(getDataFolder(), configName);
        File backupFile = new File(getDataFolder(), "old-" + configName);

        try {

            if (!backupFile.exists() && !backupFile.createNewFile()) {
                return true;
            }

            YamlConfiguration.loadConfiguration(oldConfig).save(backupFile);
        }
        catch (IOException ex) {
            console.sendMessage(GeneralUtils.colourise("&b[ChatColor] &cError: Failed to create backup file."));
            return true;
        }

        return false;
    }

    private void scanMessages() {
        InputStream defaultStream = getResource("messages.yml");

        if (defaultStream == null) {
            console.sendMessage(M.PREFIX + GeneralUtils.colourise("&cError: Failed to load default messages resource. Messages will not be scanned."));
            return;
        }

        YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream));
        YamlConfiguration currentConfig = configsManager.getConfig(Config.MESSAGES);

        Set<String> keys = defaultConfig.getKeys(false);
        boolean needsReload = false;
        for (String key : keys) {

            if (!currentConfig.contains(key)) {

                currentConfig.set(key, defaultConfig.getString(key));
                configsManager.saveConfig(Config.MESSAGES);

                console.sendMessage(M.PREFIX + GeneralUtils.colourise("&eAdded new message: &a" + key));
                needsReload = true;
            }
        }

        if (needsReload) {
            M.reloadMessages();
        }
    }

    private void scanSettings() {
        InputStream defaultStream = getResource("config.yml");

        if (defaultStream == null) {
            console.sendMessage(M.PREFIX + GeneralUtils.colourise("&cError: Failed to load default config resource. Settings will not be scanned."));
            return;
        }

        YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream));
        ConfigurationSection defaultSettings = defaultConfig.getConfigurationSection("settings");

        if (defaultSettings == null) {
            console.sendMessage(M.PREFIX + GeneralUtils.colourise("&cError: The default config resource has no settings section. Settings will not be scanned."));
            return;
        }

        for (String key : defaultSettings.getKeys(false)) {

            if (!config.contains("settings." + key)) {

                config.set("settings." + key, defaultConfig.get("settings." + key));
                configsManager.saveConfig(Config.MAIN_CONFIG);
            }
        }
    }

    private void scanOther() {
        if (!config.contains("placeholders")) {
            config.set("placeholders", Collections.singletonList("[item]"));
            configsManager.saveConfig(Config.MAIN_CONFIG);
        }
    }

    public void createConfirmScheduler(Player player, Setting setting, Object value) {
        ConfirmScheduler scheduler = new ConfirmScheduler(M, confirmationsManager, configsManager, player, setting, value);
        confirmationsManager.addConfirmingPlayer(player, scheduler);
    }

}
