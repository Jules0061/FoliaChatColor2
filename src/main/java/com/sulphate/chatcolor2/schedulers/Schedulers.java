package com.sulphate.chatcolor2.schedulers;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.function.Consumer;

public final class Schedulers {

    private static volatile boolean shuttingDown;

    private Schedulers() {

    }

    public static void init() {
        shuttingDown = false;
    }

    public static void markShuttingDown() {
        shuttingDown = true;
    }

    public static void global(Plugin plugin, Runnable task) {
        if (shuttingDown) {
            task.run();
            return;
        }

        Bukkit.getGlobalRegionScheduler().execute(plugin, task);
    }

    public static ScheduledTask globalRepeating(Plugin plugin, Consumer<ScheduledTask> task, long delayTicks, long periodTicks) {
        return Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task, Math.max(1L, delayTicks), Math.max(1L, periodTicks));
    }

    public static void async(Plugin plugin, Runnable task) {
        if (shuttingDown) {
            task.run();
            return;
        }

        Bukkit.getAsyncScheduler().runNow(plugin, ignored -> task.run());
    }

    public static void entity(Plugin plugin, Entity entity, Runnable task) {
        if (shuttingDown) {
            task.run();
            return;
        }

        entity.getScheduler().run(plugin, ignored -> task.run(), null);
    }

    public static ScheduledTask entityDelayed(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        return entity.getScheduler().runDelayed(plugin, ignored -> task.run(), null, Math.max(1L, delayTicks));
    }

}
