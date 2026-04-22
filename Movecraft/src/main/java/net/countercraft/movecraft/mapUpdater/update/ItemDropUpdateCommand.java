/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.countercraft.movecraft.mapUpdater.update;

import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.util.FoliaScheduler;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/**
 * Class that stores the data about a item drops to the map in an unspecified world. The world is retrieved contextually from the submitting craft.
 */
public class ItemDropUpdateCommand extends UpdateCommand {
    private final Location location;
    private final ItemStack itemStack;

    public ItemDropUpdateCommand(Location location, ItemStack itemStack) {
        this.location = location;
        this.itemStack = itemStack;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public Location getLocation() {
        return location;
    }

    @Override
    public void doUpdate() {
        if (itemStack != null) {
            final World world = location.getWorld();
            if (world == null) {
                return;
            }
            int chunkX = location.getBlockX() >> 4;
            int chunkZ = location.getBlockZ() >> 4;
            FoliaScheduler.runRegionLater(
                    Movecraft.getInstance(),
                    world,
                    chunkX,
                    chunkZ,
                    () -> world.dropItemNaturally(ItemDropUpdateCommand.this.location, itemStack),
                    20L
            );
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(location, itemStack);
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof ItemDropUpdateCommand)){
            return false;
        }
        ItemDropUpdateCommand other = (ItemDropUpdateCommand) obj;
        return this.location.equals(other.location) &&
                this.itemStack.equals(other.itemStack);
    }

}
