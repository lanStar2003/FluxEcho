package com.fluxdepths.shard;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

import com.fluxdepths.Config;
import com.fluxdepths.item.ItemImprint;

import gregtech.api.enums.ItemList;

/**
 * One cycle of the Flux Shard Collector, in the place of GT's recipe lookup: take the next imprint's turn, make sure
 * there is a drill head and, from MV on, drilling fluid, and condense one ore of that vein. The core circuit sets the
 * tier; the drill head stays in its slot and wears out by chance ({@link DrillHead#wears}), the better the head the
 * rarer.
 */
public final class ShardWork {

    /** GT's results of {@code checkRecipe}. */
    static final int NOTHING = 0, BLOCKED = 1, STARTED = 2;

    private ShardWork() {}

    public static int check(MTEFluxCollector m) {
        ShardTier tier = m.tier();
        ShardState s = m.state();
        if (!Config.shardsEnabled) {
            s.status = ShardState.Status.DISABLED;
            return NOTHING;
        }
        World world = m.getBaseMetaTileEntity()
            .getWorld();
        int dim = world.provider.dimensionId;

        List<Veins.Vein> veins = new ArrayList<>(tier.imprints);
        boolean foreign = false;
        for (int i = 0; i < ShardTier.MAX_IMPRINTS && veins.size() < tier.imprints; i++) {
            ItemStack st = m.mInventory[m.imprintSlot(i)];
            Veins.Vein v = Veins.get(ItemImprint.vein(st));
            if (v == null) continue;
            if (!Config.crossDimension && ItemImprint.dim(st) != dim) {
                foreign = true;
                continue;
            }
            veins.add(v);
        }
        s.imprints = veins.size();
        if (veins.isEmpty()) {
            s.status = foreign ? ShardState.Status.WRONG_WORLD : ShardState.Status.NO_IMPRINT;
            return NOTHING;
        }

        int headSlot = m.headSlot();
        int uses = DrillHeads.uses(m.mInventory[headSlot]);
        s.headUses = uses;
        if (uses <= 0) {
            s.status = ShardState.Status.NO_HEAD;
            return NOTHING;
        }

        FluidStack tank = null;
        if (tier.fluidPerOre > 0) {
            tank = m.tank();
            if (tank == null || tank.getFluid() != ItemList.sDrillingFluid || tank.amount < tier.fluidPerOre) {
                s.status = ShardState.Status.NO_FLUID;
                return NOTHING;
            }
        }

        int turn = Math.floorMod(s.next, veins.size());
        Veins.Vein vein = veins.get(turn);
        ItemStack ore = vein.mix.pick(world.rand.nextDouble());
        if (ore == null) return NOTHING;
        ore = ore.copy();
        ore.stackSize = 1;
        if (!m.fits(ore)) {
            s.status = ShardState.Status.OUTPUT_FULL;
            m.mOutputBlocked++;
            return BLOCKED;
        }

        s.next = (turn + 1) % veins.size();
        if (DrillHead.wears(uses, world.rand.nextDouble())) {
            m.mInventory[headSlot].stackSize--;
            if (m.mInventory[headSlot].stackSize <= 0) m.mInventory[headSlot] = null;
        }
        if (tank != null) tank.amount -= tier.fluidPerOre;
        m.mOutputItems[0] = ore;
        m.mEUt = tier.energy;
        m.mMaxProgresstime = tier.ticks;
        s.status = ShardState.Status.WORKING;
        s.lastVein = vein.name;
        return STARTED;
    }
}
