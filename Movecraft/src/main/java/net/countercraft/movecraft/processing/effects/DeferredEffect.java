package net.countercraft.movecraft.processing.effects;

import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.processing.WorldManager;
import net.countercraft.movecraft.util.FoliaScheduler;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * A wrapper effect that allows delaying the execution of a provided effect by a number of ticks.
 */
public class DeferredEffect implements Effect {
    private final long delayTicks;
    private final @NotNull Effect effect;

    public DeferredEffect(long delayTicks, @NotNull Effect effect){
        this.delayTicks = delayTicks;
        this.effect = Objects.requireNonNull(effect);
    }
    
    @Override
    public void run() {
        FoliaScheduler.runAsyncLater(Movecraft.getInstance(), () -> WorldManager.INSTANCE.submit(() -> effect), delayTicks);
    }

    @Override
    public boolean isAsync() {
        return true;
    }
}
