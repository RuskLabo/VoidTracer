package net.countercraft.movecraft.processing;

import net.countercraft.movecraft.processing.effects.Effect;
import net.countercraft.movecraft.util.CompletableFutureTask;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 *
 */
public final class WorldManager implements Executor {

    public static final WorldManager INSTANCE = new WorldManager();
    private static final Runnable POISON = new Runnable() {
        @Override
        public void run() {/* No-op */}
        @Override
        public String toString(){
            return "POISON TASK";
        }
    };

    private final ConcurrentLinkedQueue<Effect> worldChanges = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Supplier<@Nullable Effect>> tasks = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Runnable> currentTasks = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pendingSuppliers = new AtomicInteger();
    private volatile Plugin plugin;
    private volatile boolean running = false;

    private WorldManager(){}

    public void setPlugin(@NotNull Plugin plugin) {
        this.plugin = plugin;
    }

    public void run() {
        if(!isGlobalTickThread()){
            throw new RuntimeException("WorldManager must be executed on the global tick thread.");
        }
        if(tasks.isEmpty() && currentTasks.isEmpty() && worldChanges.isEmpty() && pendingSuppliers.get() == 0) {
            return;
        }
        running = true;

        Supplier<@Nullable Effect> nextTask;
        while((nextTask = tasks.poll()) != null){
            pendingSuppliers.incrementAndGet();
            CompletableFuture.supplyAsync(nextTask).whenComplete((effect, exception) -> {
                poison();
                if(exception != null){
                    exception.printStackTrace();
                } else if(effect != null) {
                    worldChanges.add(effect);
                }
            });
        }

        // Process queued sync tasks without blocking this tick.
        Runnable runnable;
        while((runnable = currentTasks.poll()) != null){
            if(runnable == POISON){
                pendingSuppliers.updateAndGet(v -> Math.max(0, v - 1));
                continue;
            }
            runnable.run();
        }

        // Process world updates on the global thread.
        Effect sideEffect;
        while((sideEffect = worldChanges.poll()) != null){
            sideEffect.run();
        }

        if(pendingSuppliers.get() == 0 && tasks.isEmpty() && currentTasks.isEmpty()) {
            CachedMovecraftWorld.purge();
            running = false;
        }
    }

    public <T> T executeMain(@NotNull Supplier<T> callable){
        if(!this.isRunning()){
            throw new RejectedExecutionException("WorldManager must be running to execute on the global tick thread");
        }
        if(isGlobalTickThread()){
            throw new RejectedExecutionException("Cannot schedule on global tick thread from the global tick thread");
        }
        var task = new CompletableFutureTask<>(callable);
        currentTasks.add(task);
        try {
            return task.join();
        }
        catch (RuntimeException e) {
            throw e;
        }
    }

    public void executeMain(@NotNull Runnable runnable){
        this.executeMain(() -> {
            runnable.run();
            return null;
        });
    }

    private void poison(){
        currentTasks.add(POISON);
    }

    public void submit(Runnable task){
        tasks.add(() -> {
            task.run();
            return null;
        });
    }

    public void submit(Supplier<@Nullable Effect> task){
        tasks.add(task);
    }

    public <T> T executeRegion(@NotNull World world, int chunkX, int chunkZ, @NotNull Supplier<T> callable) {
        if (isOwnedByCurrentRegion(world, chunkX, chunkZ)) {
            return callable.get();
        }
        if (isGlobalTickThread()) {
            throw new RejectedExecutionException(
                    "Cannot synchronously wait for region task from the global tick thread."
            );
        }
        if (plugin == null) {
            throw new IllegalStateException("WorldManager plugin is not initialized.");
        }
        var task = new CompletableFutureTask<>(callable);
        Bukkit.getRegionScheduler().execute(plugin, world, chunkX, chunkZ, task);
        return task.join();
    }

    public boolean isRunning() {
        return running;
    }

    @Override
    public void execute(@NotNull Runnable command) {
        this.executeMain(command);
    }

    private boolean isGlobalTickThread() {
        try {
            return (boolean) Bukkit.class.getMethod("isGlobalTickThread").invoke(null);
        }
        catch (ReflectiveOperationException ignored) {
            return Bukkit.isPrimaryThread();
        }
    }

    private boolean isOwnedByCurrentRegion(@NotNull World world, int chunkX, int chunkZ) {
        try {
            return (boolean) Bukkit.class
                    .getMethod("isOwnedByCurrentRegion", World.class, int.class, int.class)
                    .invoke(null, world, chunkX, chunkZ);
        }
        catch (ReflectiveOperationException ignored) {
            return false;
        }
    }
}
