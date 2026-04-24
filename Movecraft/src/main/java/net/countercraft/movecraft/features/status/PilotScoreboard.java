package net.countercraft.movecraft.features.status;

import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.PilotedCraft;
import net.countercraft.movecraft.craft.type.CraftType;
import net.countercraft.movecraft.events.CraftPilotEvent;
import net.countercraft.movecraft.events.CraftReleaseEvent;
import net.countercraft.movecraft.features.status.events.CraftStatusUpdateEvent;
import net.countercraft.movecraft.util.Counter;
import net.countercraft.movecraft.util.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PilotScoreboard implements Listener {

    private static final String OBJECTIVE_NAME = "ship_hud";

    // origCounts[0] = original redstone_block count, origCounts[1] = original wool count
    private final ConcurrentMap<Craft, int[]> origCounts = new ConcurrentHashMap<>();

    private volatile Set<Material> woolMaterials;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftPilot(CraftPilotEvent e) {
        if (!(e.getCraft() instanceof PilotedCraft pc)) return;
        Player player = pc.getPilot();
        if (player == null) return;

        Counter<Material> mats = e.getCraft().getDataTag(Craft.MATERIALS);
        int origEngine = mats.get(Material.REDSTONE_BLOCK);
        int origWool = woolCount(mats);
        origCounts.put(e.getCraft(), new int[]{origEngine, origWool});

        updateScoreboard(player, e.getCraft(), 100, 100, 100);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCraftRelease(CraftReleaseEvent e) {
        if (!(e.getCraft() instanceof PilotedCraft pc)) return;
        Player player = pc.getPilot();
        origCounts.remove(e.getCraft());
        if (player == null) return;
        clearScoreboard(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftStatusUpdate(CraftStatusUpdateEvent e) {
        if (!(e.getCraft() instanceof PilotedCraft pc)) return;
        Player player = pc.getPilot();
        if (player == null) return;

        int[] orig = origCounts.get(e.getCraft());
        if (orig == null) return;

        Counter<Material> mats = e.getCraft().getDataTag(Craft.MATERIALS);
        int nonNeg = e.getCraft().getDataTag(Craft.NON_NEGLIGIBLE_BLOCKS);
        int origTotal = e.getCraft().getOrigBlockCount();

        int overallPct = clamp(origTotal > 0 ? 100 * nonNeg / origTotal : 100);
        int enginePct  = clamp(orig[0] > 0 ? 100 * mats.get(Material.REDSTONE_BLOCK) / orig[0] : 100);
        int woolPct    = clamp(orig[1] > 0 ? 100 * woolCount(mats) / orig[1] : 100);

        updateScoreboard(player, e.getCraft(), overallPct, enginePct, woolPct);
    }

    private void updateScoreboard(Player player, Craft craft, int overall, int engine, int wool) {
        FoliaScheduler.runGlobalNow(Movecraft.getInstance(), () -> {
            if (!player.isOnline()) return;
            // CraftScoreboardManager.getNewScoreboard() is disabled in Folia 1.21.8;
            // bypass via ScoreboardFactory which constructs CraftScoreboard via reflection.
            Scoreboard board = ScoreboardFactory.create();
            if (board == null) return;
            Objective obj = board.registerNewObjective(
                    OBJECTIVE_NAME,
                    Criteria.DUMMY,
                    ChatColor.GOLD + "⚓ " + craft.getType().getStringProperty(CraftType.NAME)
            );
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);

            setLine(obj, ChatColor.WHITE + "全体   " + pctColor(overall) + overall + "%", 3);
            setLine(obj, ChatColor.RED   + "Engine " + pctColor(engine)  + engine  + "%", 2);
            setLine(obj, ChatColor.AQUA  + "羊毛   " + pctColor(wool)    + wool    + "%", 1);

            player.setScoreboard(board);
        });
    }

    private void clearScoreboard(Player player) {
        FoliaScheduler.runGlobalNow(Movecraft.getInstance(), () -> {
            if (!player.isOnline()) return;
            Scoreboard current = player.getScoreboard();
            if (current.getObjective(OBJECTIVE_NAME) != null) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        });
    }

    private String pctColor(int pct) {
        if (pct > 60) return ChatColor.GREEN.toString();
        if (pct > 30) return ChatColor.YELLOW.toString();
        return ChatColor.RED.toString();
    }

    private int clamp(int val) {
        return Math.max(0, Math.min(100, val));
    }

    private int woolCount(Counter<Material> mats) {
        if (woolMaterials == null) {
            Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_BLOCKS, NamespacedKey.minecraft("wool"), Material.class);
            woolMaterials = tag != null ? tag.getValues() : Set.of();
        }
        int total = 0;
        for (Material m : woolMaterials) {
            total += mats.get(m);
        }
        return total;
    }

    private static void setLine(Objective obj, String text, int score) {
        Score s = obj.getScore(text);
        s.setScore(score);
    }
}
