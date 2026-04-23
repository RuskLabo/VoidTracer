/*
 * This file is part of Movecraft.
 *
 *     Movecraft is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Movecraft is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Movecraft.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.countercraft.movecraft.async;

import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.SinkingCraft;
import net.countercraft.movecraft.craft.type.CraftType;
import net.countercraft.movecraft.events.FuelBurnEvent;
import net.countercraft.movecraft.localisation.I18nSupport;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

public abstract class AsyncTask implements Runnable {
    protected final Craft craft;

    protected AsyncTask(Craft c) {
        craft = c;
    }

    public void run() {
        try {
            execute();
            Movecraft.getInstance().getAsyncManager().submitCompletedTask(this);
        } catch (Exception e) {
            Movecraft.getInstance().getLogger().log(Level.SEVERE, I18nSupport.getInternationalisedString("Internal - Error - Processor thread encountered an error"));
            e.printStackTrace();
        }
    }

    protected abstract void execute() throws InterruptedException, ExecutionException;

    protected Craft getCraft() {
        return craft;
    }

    protected boolean checkFuel() {
        // Consume hull-placed fuel blocks (e.g. coal_block). When a fuel block is
        // burned it is replaced with AIR in-world, making fuel storage a design
        // concern for the builder. Furnace-inventory fuel is no longer supported.
        double fuelBurnRate = (double) craft.getType().getPerWorldProperty(CraftType.PER_WORLD_FUEL_BURN_RATE, craft.getWorld());
        if (fuelBurnRate == 0.0 || craft instanceof SinkingCraft)
            return true;

        if (craft.getBurningFuel() >= fuelBurnRate) {
            double burningFuel = craft.getBurningFuel();
            final FuelBurnEvent event = new FuelBurnEvent(craft, burningFuel, fuelBurnRate);
            Bukkit.getPluginManager().callEvent(event);
            if (event.getBurningFuel() != burningFuel)
                burningFuel = event.getBurningFuel();
            if (event.getFuelBurnRate() != fuelBurnRate)
                fuelBurnRate = event.getFuelBurnRate();
            craft.setBurningFuel(burningFuel - fuelBurnRate);
            return true;
        }

        var v = craft.getType().getObjectProperty(CraftType.FUEL_TYPES);
        if (!(v instanceof Map<?, ?>))
            throw new IllegalStateException("FUEL_TYPES must be of type Map");
        var fuelTypes = (Map<?, ?>) v;

        for (MovecraftLocation loc : craft.getHitBox()) {
            Block b = craft.getWorld().getBlockAt(loc.getX(), loc.getY(), loc.getZ());
            Material mat = b.getType();
            Object fuelVal = fuelTypes.get(mat);
            if (!(fuelVal instanceof Double))
                continue;

            double burningFuel = (double) fuelVal;
            final FuelBurnEvent event = new FuelBurnEvent(craft, burningFuel, fuelBurnRate);
            Bukkit.getPluginManager().callEvent(event);
            if (event.getBurningFuel() != burningFuel)
                burningFuel = event.getBurningFuel();
            if (event.getFuelBurnRate() != fuelBurnRate)
                fuelBurnRate = event.getFuelBurnRate();
            if (burningFuel == 0.0)
                continue;

            b.setType(Material.AIR);
            // Keep count consistent for downstream speed/weight calculations.
            craft.getDataTag(net.countercraft.movecraft.craft.Craft.MATERIALS).add(mat, -1);
            craft.setBurningFuel(craft.getBurningFuel() + burningFuel - fuelBurnRate);
            return true;
        }
        return false;
    }
}
