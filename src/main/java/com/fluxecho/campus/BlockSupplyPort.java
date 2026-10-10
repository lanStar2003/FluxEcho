package com.fluxecho.campus;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.fluxecho.FluxEcho;
import com.fluxecho.core.EchoText;
import com.fluxecho.frame.FrameEvents;
import com.fluxecho.logic.BuildState;
import com.fluxecho.logic.PartRecipes;
import com.fluxecho.nexus.TileNexus;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The supply port ({@code fluxecho:supply_port}): where automation and passing players hand materials to the job a
 * nexus is building. The establish job stands one beside the campus's main axis for free; more can be crafted
 * ({@code PartRecipes}, part-only for the builder) and placed by hand anywhere within reach of the team's nexus.
 * <p>
 * Right-click with a stack: it takes what the job still needs of it. Sneak-right-click with an empty hand: it takes
 * everything the job needs from the main inventory. Right-click with an empty hand: what the job still lacks (the first
 * three bill lines), and anything the port is still holding back comes out (to a member of its team only). Anyone may
 * hand in materials; only the port's team links it ({@link TileSupplyPort#mayUse}). Its front faces whoever placed
 * it, or the walkway when the builder placed it; the facing lives in the tile, so the block's meta stays 0 (the
 * part's meta).
 */
public class BlockSupplyPort extends BlockContainer {

    @SideOnly(Side.CLIENT)
    private IIcon top, side, front;

    public BlockSupplyPort() {
        super(Material.iron);
        setBlockName("fluxecho.supply_port");
        setHardness(5f);
        setResistance(10f);
        setStepSound(soundTypeMetal);
        setHarvestLevel("pickaxe", 1);
        setCreativeTab(FluxEcho.TAB);
    }

    @Override
    public TileEntity createNewTileEntity(World w, int meta) {
        return new TileSupplyPort();
    }

    // ---- placing and breaking

    @Override
    public void onBlockAdded(World w, int x, int y, int z) {
        super.onBlockAdded(w, x, y, z);
        if (!Builder.QUIET) FrameEvents.changed(w, x, y, z);
    }

    /** Faces the placer and links to the nearest nexus of the placer's team, saying which one. */
    @Override
    public void onBlockPlacedBy(World w, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        if (!(w.getTileEntity(x, y, z) instanceof TileSupplyPort port)) return;
        int look = MathHelper.floor_double(placer.rotationYaw * 4f / 360f + 0.5) & 3;
        // the front faces the placer: looking south (0) -> north (2), west (1) -> east (5), ...
        int[] front = { 2, 5, 3, 4 };
        port.setFacing(front[look]);
        if (w.isRemote || !(placer instanceof EntityPlayer p)) return;
        TileNexus n = port.linkFor(p);
        if (n == null) p.addChatMessage(new ChatComponentTranslation("fluxecho.supply_port.unlinked"));
        else p.addChatMessage(new ChatComponentTranslation("fluxecho.supply_port.linked", where(n)));
    }

    /** Whatever the port still holds falls out (not when a refused placement is being undone). */
    @Override
    public void breakBlock(World w, int x, int y, int z, Block block, int meta) {
        if (!w.isRemote && !w.restoringBlockSnapshots && w.getTileEntity(x, y, z) instanceof TileSupplyPort port) {
            ItemStack s = port.getStackInSlot(0);
            if (s != null) {
                w.spawnEntityInWorld(new EntityItem(w, x + 0.5, y + 0.5, z + 0.5, s.copy()));
                port.setInventorySlotContents(0, null);
            }
        }
        super.breakBlock(w, x, y, z, block, meta);
        if (!Builder.QUIET) FrameEvents.changed(w, x, y, z);
    }

    // ---- using it

    @Override
    public boolean onBlockActivated(World w, int x, int y, int z, EntityPlayer p, int s, float hx, float hy, float hz) {
        if (w.isRemote) return true;
        if (!(w.getTileEntity(x, y, z) instanceof TileSupplyPort port)) return false;
        TileNexus n = port.nexus();
        boolean mine = port.mayUse(p);
        if (n == null && !port.linked() && mine) n = port.linkFor(p);
        if (n == null) {
            p.addChatMessage(
                new ChatComponentTranslation(
                    port.linked() ? "fluxecho.supply_port.unloaded"
                        : mine ? "fluxecho.supply_port.unlinked" : "fluxecho.supply_port.not_yours"));
            return true;
        }
        Campus c = n.campus();
        ItemStack held = p.getCurrentEquippedItem();
        if (held != null) {
            ItemStack left = port.feed(held, false);
            int took = held.stackSize - (left == null ? 0 : left.stackSize);
            if (took <= 0) {
                p.addChatMessage(new ChatComponentTranslation(refusal(c)));
                return true;
            }
            p.addChatMessage(new ChatComponentTranslation("fluxecho.supply_port.taken", took, held.func_151000_E()));
            p.inventory.setInventorySlotContents(p.inventory.currentItem, left);
            p.inventory.markDirty();
            return true;
        }
        if (p.isSneaking()) {
            int took = depositAll(port, p);
            p.addChatMessage(
                took > 0 ? new ChatComponentTranslation("fluxecho.supply_port.taken_all", took)
                    : new ChatComponentTranslation(refusal(c)));
            return true;
        }
        if (mine) giveBack(port, p);
        tellMissing(c, p);
        return true;
    }

    /** Offers every stack of the player's main inventory; returns how many items the job took. */
    private static int depositAll(TileSupplyPort port, EntityPlayer p) {
        ItemStack[] inv = p.inventory.mainInventory;
        int took = 0;
        for (int i = 0; i < inv.length; i++) {
            ItemStack s = inv[i];
            if (s == null) continue;
            ItemStack left = port.feed(s, false);
            int after = left == null ? 0 : left.stackSize;
            if (after == s.stackSize) continue;
            took += s.stackSize - after;
            inv[i] = left;
        }
        if (took > 0) p.inventory.markDirty();
        return took;
    }

    /** Hands the player whatever the port still holds back (overflow at their feet). */
    private static void giveBack(TileSupplyPort port, EntityPlayer p) {
        ItemStack s = port.getStackInSlot(0);
        if (s == null) return;
        port.setInventorySlotContents(0, null);
        IChatComponent name = s.func_151000_E();
        int n = s.stackSize;
        if (!p.inventory.addItemStackToInventory(s) && s.stackSize > 0) p.dropPlayerItemWithRandomChoice(s, false);
        p.inventory.markDirty();
        p.addChatMessage(new ChatComponentTranslation("fluxecho.supply_port.returned", n, name));
    }

    /** The job's name and its first three bill lines (whole items), or that it lacks nothing. */
    private static void tellMissing(Campus c, EntityPlayer p) {
        BuildJob job = c.job();
        if (!c.active() || job == null || BuildState.ended(job.state())) {
            p.addChatMessage(new ChatComponentTranslation("fluxecho.build.no_job"));
            return;
        }
        Map<String, Long> bill = c.bill();
        if (bill.isEmpty()) {
            p.addChatMessage(new ChatComponentTranslation("fluxecho.supply_port.funded", job.name()));
            return;
        }
        p.addChatMessage(new ChatComponentTranslation("fluxecho.supply_port.missing", job.name()));
        Iterator<Map.Entry<String, Long>> it = bill.entrySet()
            .iterator();
        for (int i = 0; i < 3 && it.hasNext(); i++) {
            Map.Entry<String, Long> e = it.next();
            long items = (e.getValue() + PartRecipes.UNIT - 1) / PartRecipes.UNIT;
            ItemStack icon = Campus.stackOf(e.getKey(), 1);
            IChatComponent name = icon != null ? icon.func_151000_E() : new ChatComponentText(e.getKey());
            p.addChatMessage(new ChatComponentTranslation("fluxecho.supply_port.line", name, items));
        }
    }

    /** Why the job took nothing: there is none, it was not started yet, or it does not need that. */
    private static String refusal(Campus c) {
        BuildJob job = c.job();
        if (!c.active() || job == null || BuildState.ended(job.state())) return "fluxecho.build.no_job";
        if (!job.started() && !BuildState.consented(job.state())) return "fluxecho.supply_port.not_started";
        return "fluxecho.supply_port.not_wanted";
    }

    private static String where(TileNexus n) {
        return n.xCoord + ", " + n.yCoord + ", " + n.zCoord;
    }

    // ---- looks

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister r) {
        String p = FluxEcho.MODID + ":supply_port/";
        top = r.registerIcon(p + "top");
        side = r.registerIcon(p + "side");
        front = r.registerIcon(p + "front");
    }

    /** As an item: the front at side 3, like the other FluxEcho blocks with a front. */
    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int s, int meta) {
        return s == 1 ? top : s == 3 ? front : side;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(IBlockAccess w, int x, int y, int z, int s) {
        if (s == 1) return top;
        if (w.getTileEntity(x, y, z) instanceof TileSupplyPort port) return s == port.facing() ? front : side;
        return getIcon(s, 0);
    }

    /** The port as an item: what it does. */
    public static class ItemPort extends ItemBlock {

        public ItemPort(Block block) {
            super(block);
        }

        @Override
        @SideOnly(Side.CLIENT)
        @SuppressWarnings({ "rawtypes", "unchecked" })
        public void addInformation(ItemStack stack, EntityPlayer player, List tip, boolean advanced) {
            tip.addAll(EchoText.lines("supply_port.tip"));
        }
    }
}
