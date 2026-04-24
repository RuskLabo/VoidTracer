package net.countercraft.movecraft.features.status;

import net.countercraft.movecraft.Movecraft;
import org.bukkit.scoreboard.Scoreboard;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Folia 1.21.8 では CraftScoreboardManager.getNewScoreboard() が
 * UnsupportedOperationException で無効化されている。
 * NMS の ServerScoreboard を直接生成して CraftScoreboard にラップすることで回避する。
 */
final class ScoreboardFactory {
    private ScoreboardFactory() {}

    static Scoreboard create() {
        try {
            // MinecraftServer singleton
            Class<?> mcServerClass = Class.forName("net.minecraft.server.MinecraftServer");
            Method getServer = mcServerClass.getMethod("getServer");
            Object mcServer = getServer.invoke(null);

            // ServerScoreboard(MinecraftServer) — NMS scoreboard (extends net.minecraft.world.scores.Scoreboard)
            Class<?> serverScoreboardClass = Class.forName("net.minecraft.server.ServerScoreboard");
            Constructor<?> nmsCtor = serverScoreboardClass.getDeclaredConstructor(mcServerClass);
            nmsCtor.setAccessible(true);
            Object nmsScoreboard = nmsCtor.newInstance(mcServer);

            // CraftScoreboard(net.minecraft.world.scores.Scoreboard) — constructor takes BASE class type
            Class<?> nmsBaseClass = Class.forName("net.minecraft.world.scores.Scoreboard");
            Class<?> craftScoreboardClass = Class.forName("org.bukkit.craftbukkit.scoreboard.CraftScoreboard");
            Constructor<?> craftCtor = craftScoreboardClass.getDeclaredConstructor(nmsBaseClass);
            craftCtor.setAccessible(true);
            return (Scoreboard) craftCtor.newInstance(nmsScoreboard);

        } catch (Throwable t) {
            Movecraft.getInstance().getLogger().warning(
                    "[ScoreboardFactory] Failed to create scoreboard via reflection: " + t);
            return null;
        }
    }
}
