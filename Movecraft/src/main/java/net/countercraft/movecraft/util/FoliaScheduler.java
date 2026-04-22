package net.countercraft.movecraft.util;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Folia-only scheduler bridge.
 */
public final class FoliaScheduler {
    private FoliaScheduler() {
    }

    @FunctionalInterface
    public interface TaskHandle {
        void cancel();
    }

    public static TaskHandle runGlobalTimer(Plugin plugin, Runnable runnable, long initialDelayTicks, long periodTicks) {
        long safeInitialDelay = Math.max(1L, initialDelayTicks);
        long safePeriod = Math.max(1L, periodTicks);
        ScheduledTask task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                ignored -> runnable.run(),
                safeInitialDelay,
                safePeriod
        );
        return task::cancel;
    }

    public static TaskHandle runGlobalLater(Plugin plugin, Runnable runnable, long delayTicks) {
        if (delayTicks <= 0L) {
            ScheduledTask task = Bukkit.getGlobalRegionScheduler().run(
                    plugin,
                    ignored -> runnable.run()
            );
            return task::cancel;
        }
        ScheduledTask task = Bukkit.getGlobalRegionScheduler().runDelayed(
                plugin,
                ignored -> runnable.run(),
                delayTicks
        );
        return task::cancel;
    }

    public static void runGlobalNow(Plugin plugin, Runnable runnable) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, runnable);
    }

    public static TaskHandle runAsyncNow(Plugin plugin, Runnable runnable) {
        ScheduledTask task = Bukkit.getAsyncScheduler().runNow(
                plugin,
                ignored -> runnable.run()
        );
        return task::cancel;
    }

    public static TaskHandle runAsyncTimer(Plugin plugin, Runnable runnable, long initialDelayTicks, long periodTicks) {
        long safeInitialDelayMillis = ticksToMillis(Math.max(1L, initialDelayTicks));
        long safePeriodMillis = ticksToMillis(Math.max(1L, periodTicks));
        ScheduledTask task = Bukkit.getAsyncScheduler().runAtFixedRate(
                plugin,
                ignored -> runnable.run(),
                safeInitialDelayMillis,
                safePeriodMillis,
                TimeUnit.MILLISECONDS
        );
        return task::cancel;
    }

    public static TaskHandle runAsyncLater(Plugin plugin, Runnable runnable, long delayTicks) {
        if (delayTicks <= 0L) {
            return runAsyncNow(plugin, runnable);
        }
        ScheduledTask task = Bukkit.getAsyncScheduler().runDelayed(
                plugin,
                ignored -> runnable.run(),
                ticksToMillis(delayTicks),
                TimeUnit.MILLISECONDS
        );
        return task::cancel;
    }

    public static TaskHandle runRegionLater(Plugin plugin, World world, int chunkX, int chunkZ, Runnable runnable, long delayTicks) {
        if (delayTicks <= 0L) {
            ScheduledTask task = Bukkit.getRegionScheduler().run(
                    plugin,
                    world,
                    chunkX,
                    chunkZ,
                    ignored -> runnable.run()
            );
            return task::cancel;
        }
        ScheduledTask task = Bukkit.getRegionScheduler().runDelayed(
                plugin,
                world,
                chunkX,
                chunkZ,
                ignored -> runnable.run(),
                delayTicks
        );
        return task::cancel;
    }

    public static void runRegionNow(Plugin plugin, World world, int chunkX, int chunkZ, Runnable runnable) {
        Bukkit.getRegionScheduler().execute(plugin, world, chunkX, chunkZ, runnable);
    }

    public static <T> CompletableFuture<T> callGlobal(Plugin plugin, Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        runGlobalNow(plugin, () -> {
            try {
                future.complete(supplier.get());
            }
            catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    public static <T> CompletableFuture<T> callRegion(Plugin plugin, World world, int chunkX, int chunkZ, Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        runRegionNow(plugin, world, chunkX, chunkZ, () -> {
            try {
                future.complete(supplier.get());
            }
            catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    public static boolean isGlobalTickThread() {
        try {
            return (boolean) Bukkit.class.getMethod("isGlobalTickThread").invoke(null);
        }
        catch (ReflectiveOperationException ignored) {
            return Bukkit.isPrimaryThread();
        }
    }

    public static boolean isOwnedByCurrentRegion(World world, int chunkX, int chunkZ) {
        try {
            return (boolean) Bukkit.class
                    .getMethod("isOwnedByCurrentRegion", World.class, int.class, int.class)
                    .invoke(null, world, chunkX, chunkZ);
        }
        catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static long ticksToMillis(long ticks) {
        return Math.max(0L, ticks) * 50L;
    }
}
