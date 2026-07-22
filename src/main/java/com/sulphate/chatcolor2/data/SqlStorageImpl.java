package com.sulphate.chatcolor2.data;

import com.sulphate.chatcolor2.main.ChatColor;
import com.sulphate.chatcolor2.schedulers.Schedulers;
import com.sulphate.chatcolor2.utils.GeneralUtils;
import com.sulphate.chatcolor2.utils.Messages;

import javax.sql.DataSource;
import java.sql.*;
import java.util.UUID;

import static com.sulphate.chatcolor2.data.DatabaseConnectionSettings.TABLE_NAME;

@SuppressWarnings({ "SqlNoDataSourceInspection", "SqlDialectInspection" })
public class SqlStorageImpl extends PlayerDataStore {

    private static final String SELECT_QUERY = "SELECT Colour, DefaultCode FROM " + TABLE_NAME + " WHERE UUID=?;";
    private static final String INSERT_QUERY = "INSERT INTO " + TABLE_NAME + " VALUES(?, '', -1);";
    private static final String UPDATE_QUERY = "UPDATE " + TABLE_NAME + " SET Colour=?, DefaultCode=? WHERE UUID=?;";

    private final DatabaseConnectionSettings settings;
    private final Messages M;

    private final DataSource dataSource;

    public SqlStorageImpl(DatabaseConnectionSettings settings, Messages M) {
        super();

        this.settings = settings;
        this.M = M;
        dataSource = settings.createDataSource();

        if (!initialiseDatabase()) {
            GeneralUtils.sendConsoleMessage(M.PREFIX + M.FAILED_TO_INITIALISE_DB);
        }
    }

    private void runAsync(Runnable runnable) {
        Schedulers.async(ChatColor.getPlugin(), runnable);
    }

    private boolean initialiseDatabase() {
        String databaseName = settings.getDatabaseName();

        try (Connection con = dataSource.getConnection()) {
            if (!databaseExists(con)) {
                try (PreparedStatement statement = con.prepareStatement(String.format("CREATE DATABASE %s;", databaseName))) {
                    statement.executeUpdate();
                }
                catch (SQLException ex) {
                    GeneralUtils.sendConsoleMessage(M.PREFIX + M.FAILED_TO_CREATE_DB.replace("[error]", ex.getMessage()));
                    return false;
                }
            }

            con.setCatalog(databaseName);

            if (!tableExists(con)) {
                try (PreparedStatement statement = con.prepareStatement(
                    "CREATE TABLE " + TABLE_NAME + " (" +
                            "UUID VARCHAR(255) PRIMARY KEY," +
                            "Colour VARCHAR(255)," +
                            "DefaultCode BIGINT" +
                        ");")
                ) {
                    statement.executeUpdate();
                }
                catch (SQLException ex) {
                    GeneralUtils.sendConsoleMessage(M.PREFIX + M.FAILED_TO_CREATE_TABLE.replace("[error]", ex.getMessage()));
                    return false;
                }
            }
        }
        catch (SQLException ex) {
            GeneralUtils.sendConsoleMessage(M.PREFIX + M.FAILED_TO_CONNECT_TO_DB.replace("[error]", ex.getMessage()));
            return false;
        }

        return true;
    }

    private boolean databaseExists(Connection con) throws SQLException {
        DatabaseMetaData metaData = con.getMetaData();
        ResultSet results = metaData.getCatalogs();

        while (results.next()) {
            if (results.getString(1).equals(settings.getDatabaseName())) {
                return true;
            }
        }

        return false;
    }

    private boolean tableExists(Connection con) throws SQLException {
        DatabaseMetaData metaData = con.getMetaData();
        ResultSet results = metaData.getTables(con.getCatalog(), null, "%", new String[] { "TABLE" });

        while (results.next()) {
            String name = results.getString(3);

            if (name.equals(TABLE_NAME)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void loadPlayerData(UUID uuid, Callback<Boolean> callback) {
        runAsync(() -> {
            try (
                    Connection con = dataSource.getConnection();
                    PreparedStatement statement = con.prepareStatement(SELECT_QUERY)
            ) {
                statement.setString(1, uuid.toString());
                ResultSet results = statement.executeQuery();

                if (results == null || !results.next()) {
                    dataMap.put(uuid, new PlayerData(uuid, null, -1));
                    insertNewPlayer(uuid, con);
                }
                else {

                    String colour = results.getString("Colour");
                    long defaultCode = results.getLong("DefaultCode");

                    dataMap.put(uuid, new PlayerData(uuid, colour, defaultCode));
                }

                callback.callback(true);
            }
            catch (SQLException ex) {
                GeneralUtils.sendConsoleMessage(M.PREFIX + M.FAILED_TO_LOAD_PLAYER_DATA.replace("[error]", ex.getMessage()));
                dataMap.put(uuid, PlayerData.createTemporaryData(uuid));

                callback.callback(true);
            }
        });
    }

    private void insertNewPlayer(UUID uuid, Connection con) throws SQLException {
        try (PreparedStatement statement = con.prepareStatement(INSERT_QUERY)) {
            statement.setString(1, uuid.toString());
            int rows = statement.executeUpdate();

            if (rows == 0) {
                GeneralUtils.sendConsoleMessage(M.PREFIX + M.FAILED_TO_CREATE_NEW_PLAYER.replace("[player]", uuid.toString()));
            }
        }
    }

    @Override
    public void savePlayerData(UUID uuid) {
        PlayerData data = dataMap.get(uuid);

        if (data.isTemporary() || !data.isDirty()) {
            return;
        }

        runAsync(() -> {
            try (
                    Connection con = dataSource.getConnection();
                    PreparedStatement statement = con.prepareStatement(UPDATE_QUERY)
            ) {
                statement.setString(1, data.getColour());
                statement.setLong(2, data.getDefaultCode());
                statement.setString(3, uuid.toString());

                int count = statement.executeUpdate();

                if (count == 0) {
                    GeneralUtils.sendConsoleMessage(M.PREFIX + M.FAILED_TO_SAVE_PLAYER_DATA.replace("[error]", "No player data found to update."));
                }
                else {
                    data.markClean();
                }
            }
            catch (SQLException ex) {
                GeneralUtils.sendConsoleMessage(M.PREFIX + M.FAILED_TO_SAVE_PLAYER_DATA.replace("[error]", ex.getMessage()));
            }
        });
    }

    @Override
    public void shutdown() {
        dataMap.values().stream().filter(PlayerData::isDirty).forEach(it -> savePlayerData(it.getUuid()));

        if (dataSource instanceof AutoCloseable) {
            try {
                ((AutoCloseable) dataSource).close();
            }
            catch (Exception ex) {
                GeneralUtils.sendConsoleMessage(M.PREFIX + "&cError: Failed to close the database connection pool: " + ex.getMessage());
            }
        }
    }

}
