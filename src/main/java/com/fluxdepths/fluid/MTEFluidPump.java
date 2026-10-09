package com.fluxdepths.fluid;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.fluxdepths.Config;
import com.fluxdepths.item.ItemImprint;
import com.fluxdepths.shard.DrillHead;
import com.fluxdepths.shard.DrillHeads;
import com.fluxdepths.shard.ShardText;
import com.fluxdepths.shard.ShardTextures;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEBasicMachine;
import gregtech.api.recipe.RecipeMap;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

/**
 * Fluid pump, LV to HV: a GT single-block machine with a fluid imprint in its special slot. Every second it echoes a
 * share of the imprinted chunk's underground fluid into its output tank; the chunk itself is never drained. A drill
 * head in the input opens the pinhole; it stays there and wears out by chance, {@link PumpTier#seconds} seconds on
 * average.
 */
public class MTEFluidPump extends MTEBasicMachine {

    private final PumpTier tier;
    /** Average seconds the drill head in use lasts (0: none yet), for Waila; not saved. */
    private int headSeconds;
    private String status = "idle";

    public MTEFluidPump(int id, PumpTier tier) {
        super(
            id,
            "fluxdepths.pump." + tier.key(),
            "Fluid Pump (" + tier.name() + ")",
            tier.gtTier,
            1,
            new String[0],
            1,
            1,
            ShardTextures.pumpOverlays());
        this.tier = tier;
    }

    private MTEFluidPump(String name, PumpTier tier, String[] description, ITexture[][][] textures) {
        super(name, tier.gtTier, 1, description, textures, 1, 1);
        this.tier = tier;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity te) {
        return new MTEFluidPump(mName, tier, mDescriptionArray, mTextures);
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return Pumps.map();
    }

    static double share(PumpTier tier) {
        return Config.pumpShares[Math.min(tier.ordinal(), Config.pumpShares.length - 1)];
    }

    private int idle(String why) {
        status = why;
        return 0;
    }

    @Override
    public int checkRecipe() {
        if (!Config.pumpsEnabled) return idle("disabled");
        ItemStack imprint = mInventory[getSpecialSlotIndex()];
        Fluid fluid = ItemImprint.fluid(imprint);
        if (fluid == null) return idle("no_imprint");
        World world = getBaseMetaTileEntity().getWorld();
        if (!Config.crossDimension && ItemImprint.dim(imprint) != world.provider.dimensionId)
            return idle("wrong_world");
        int litres = PumpRates.perCycle(ItemImprint.amount(imprint), share(tier));
        if (litres <= 0) return idle("dry");

        int headSlot = -1;
        DrillHead head = null;
        int first = getInputSlot();
        for (int i = first; i < first + mInputSlotCount && head == null; i++) {
            DrillHead h = DrillHeads.of(mInventory[i]);
            if (tier.takes(h)) {
                head = h;
                headSlot = i;
            }
        }
        if (head == null) return idle("no_head");

        FluidStack out = new FluidStack(fluid, litres);
        if (!canOutput(out)) {
            mOutputBlocked++;
            status = "output_full";
            return 1;
        }
        headSeconds = PumpTier.seconds(head);
        if (DrillHead.wears(headSeconds, world.rand.nextDouble())) {
            mInventory[headSlot].stackSize--;
            if (mInventory[headSlot].stackSize <= 0) mInventory[headSlot] = null;
        }
        mOutputFluid = out;
        mEUt = tier.energy;
        mMaxProgresstime = PumpTier.CYCLE;
        status = "working";
        return 2;
    }

    @Override
    public int getCapacity() {
        return 64_000;
    }

    @Override
    public boolean isFluidInputAllowed(FluidStack f) {
        return false;
    }

    /** Empty: GT would store the first description it sees in its own lang file, in whatever language. */
    @Override
    public String[] getDescription() {
        return new String[0];
    }

    @Override
    public void addAdditionalTooltipInformation(ItemStack stack, List<String> tooltip) {
        List<String> l = new ArrayList<>();
        l.add(ShardText.machineType("fluxdepths.pump.type"));
        l.add(EnumChatFormatting.DARK_AQUA + StatCollector.translateToLocal("fluxdepths.pump.lore"));
        l.add(StatCollector.translateToLocalFormatted("fluxdepths.pump.speed", Math.round(share(tier) * 1000) / 10.0));
        l.add(StatCollector.translateToLocalFormatted("fluxdepths.shard.eu", tier.energy));
        l.add(
            StatCollector.translateToLocalFormatted(
                "fluxdepths.pump.head",
                StatCollector.translateToLocal(
                    "fluxdepths.head." + tier.minHead.name()
                        .toLowerCase()),
                PumpTier.seconds(tier.minHead) / 60));
        l.add(StatCollector.translateToLocal("fluxdepths.pump.world"));
        tooltip.addAll(l);
    }

    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y,
        int z) {
        super.getWailaNBTData(player, tile, tag, world, x, y, z);
        tag.setString("fdPump", status);
        tag.setInteger("fdHeadSeconds", headSeconds);
    }

    @Override
    public void getWailaBody(ItemStack stack, List<String> tip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        super.getWailaBody(stack, tip, accessor, config);
        NBTTagCompound tag = accessor.getNBTData();
        if (!tag.hasKey("fdPump")) return;
        String st = tag.getString("fdPump");
        EnumChatFormatting color = "working".equals(st) ? EnumChatFormatting.AQUA
            : "idle".equals(st) ? EnumChatFormatting.GRAY : EnumChatFormatting.GOLD;
        tip.add(color + StatCollector.translateToLocal("fluxdepths.pump.status." + st));
        int seconds = tag.getInteger("fdHeadSeconds");
        if (seconds > 0) tip.add(
            StatCollector
                .translateToLocalFormatted("fluxdepths.pump.drill_wear", Math.max(1, Math.round(seconds / 60.0))));
    }
}
