package net.countercraft.movecraft.features.integration;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.PilotedCraft;
import net.countercraft.movecraft.events.CraftDetectEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * Prevents a player from detecting / piloting a craft that overlaps a Towny town
 * they don't belong to. Soft-dependency on Towny — this class is only registered
 * when the Towny plugin is present.
 */
public final class TownyIntegration implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraftDetect(@NotNull CraftDetectEvent e) {
        Craft craft = e.getCraft();
        if (!(craft instanceof PilotedCraft pc)) return;
        Player player = pc.getPilot();
        if (player == null) return;
        if (player.hasPermission("movecraft.bypass.towny")) return;

        Resident resident = TownyAPI.getInstance().getResidentOrNull(player);
        World world = craft.getWorld();

        // Sample by chunk (16x16) — Towny claims are chunk-granular, so per-chunk
        // resolution avoids O(N) per-block lookups on large hitboxes.
        Set<Long> seenChunks = new HashSet<>();
        for (MovecraftLocation ml : craft.getHitBox()) {
            long key = (((long) (ml.getX() >> 4)) << 32) | ((ml.getZ() >> 4) & 0xffffffffL);
            if (!seenChunks.add(key)) continue;

            Location loc = new Location(world, ml.getX(), ml.getY(), ml.getZ());
            Town town = TownyAPI.getInstance().getTown(loc);
            if (town == null) continue; // wilderness — always allowed
            if (resident != null && town.hasResident(resident)) continue;

            // Block the detection: craft overlaps a town the player isn't a resident of
            e.setCancelled(true);
            player.sendMessage(Component.text(
                    "You can't pilot a craft inside " + town.getName() + " — not a resident.",
                    NamedTextColor.RED));
            return;
        }
    }
}
