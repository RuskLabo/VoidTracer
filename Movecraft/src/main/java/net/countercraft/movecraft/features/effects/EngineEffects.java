package net.countercraft.movecraft.features.effects;

import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.SinkingCraft;
import net.countercraft.movecraft.events.CraftTranslateEvent;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class EngineEffects implements Listener {

    // Minimum interval between effect bursts per craft (ms)
    private static final long EFFECT_INTERVAL_MS = 150;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftTranslate(@NotNull CraftTranslateEvent e) {
        Craft craft = e.getCraft();
        if (craft instanceof SinkingCraft) return;

        long now = System.currentTimeMillis();
        if (now - craft.getDataTag(Craft.LAST_ENGINE_EFFECT_MS) < EFFECT_INTERVAL_MS) return;
        craft.setDataTag(Craft.LAST_ENGINE_EFFECT_MS, now);

        List<MovecraftLocation> engines = craft.getDataTag(Craft.ENGINE_POSITIONS);
        if (engines.isEmpty()) return;

        World world = e.getWorld();

        // Smoke particles at each engine block
        for (MovecraftLocation el : engines) {
            Location loc = el.toBukkit(world).add(0.5, 1.2, 0.5);
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc, 5, 0.15, 0.4, 0.15, 0.005);
        }

        // Engine sound at craft midpoint — pitch scales inversely with tick cooldown (faster = higher)
        int cooldown = Math.max(1, craft.getTickCooldown());
        float pitch = (float) Math.min(2.0, Math.max(0.5, 10.0 / cooldown));
        MovecraftLocation mid = e.getNewHitBox().getMidPoint();
        Location midLoc = mid.toBukkit(world).add(0.5, 0.5, 0.5);
        world.playSound(midLoc, Sound.BLOCK_FIRE_AMBIENT, SoundCategory.AMBIENT, 1.8f, pitch);
    }
}
