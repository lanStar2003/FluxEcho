package com.fluxecho.campus;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.fluxecho.FluxEcho;
import com.fluxecho.core.EchoText;
import com.fluxecho.frame.FrameEvents;
import com.fluxecho.nexus.TileNexus;
import com.gtnewhorizons.modularui.api.UIInfos;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The recycle bin ({@code fluxecho:recycle_bin}, 0.10.1, the user's 「把对应的方块回收存入最终的回收箱中」): where whatever
 * the nexus's builder took down comes out again. Everything the builder clears goes to the campus's recycle store
 * ({@link Campus#recycle}); the bin is its window, linked to a nexus as the supply port is. The establish and forum
 * jobs stand one beside the supply port for free; more can be crafted ({@code PartRecipes}, part-only for the builder)
 * and placed by hand within reach of the team's nexus.
 * <p>
 * Right-click opens its screen ({@link RecycleGui}: the store as a grid, taken out by click, shift-click or 全部取出),
 * for a member of the bin's team ({@link TileRecycleBin#mayUse}). Hoppers, pipes and AE buses take from it on every
 * side ({@link TileRecycleBin}). Its front, the violet-cyan intake, faces whoever placed it, or the walkway when the
 * builder placed it; the facing lives in the tile, so the block's meta stays 0 (the part's meta). Breaking it loses
 * nothing: the store belongs to the nexus.
 */
public class BlockRecycleBin extends BlockContainer {

    @SideOnly(Side.CLIENT)
    private IIcon top, side, front;

    public BlockRecycleBin() {
        super(Material.iron);
        setBlockName("fluxecho.recycle_bin");
        setHardness(5f);
        setResistance(10f);
        setStepSound(soundTypeMetal);
        setHarvestLevel("pickaxe", 1);
        setCreativeTab(FluxEcho.TAB);
    }

    @Override
    public TileEntity createNewTileEntity(World w, int meta) {
        return new TileRecycleBin();
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
        if (!(w.getTileEntity(x, y, z) instanceof TileRecycleBin bin)) return;
        int look = MathHelper.floor_double(placer.rotationYaw * 4f / 360f + 0.5) & 3;
        // the front faces the placer: looking south (0) -> north (2), west (1) -> east (5), ...
        int[] front = { 2, 5, 3, 4 };
        bin.setFacing(front[look]);
        if (w.isRemote || !(placer instanceof EntityPlayer p)) return;
        TileNexus n = bin.linkFor(p);
        if (n == null) p.addChatMessage(new ChatComponentTranslation("fluxecho.recycle_bin.unlinked"));
        else p.addChatMessage(new ChatComponentTranslation("fluxecho.recycle_bin.linked", where(n)));
    }

    @Override
    public void breakBlock(World w, int x, int y, int z, Block block, int meta) {
        super.breakBlock(w, x, y, z, block, meta);
        if (!Builder.QUIET) FrameEvents.changed(w, x, y, z);
    }

    // ---- using it

    /** Opens the screen for a member of its team while its nexus is loaded; otherwise says why not. */
    @Override
    public boolean onBlockActivated(World w, int x, int y, int z, EntityPlayer p, int s, float hx, float hy, float hz) {
        if (w.isRemote) return true;
        if (!(w.getTileEntity(x, y, z) instanceof TileRecycleBin bin)) return false;
        boolean mine = bin.mayUse(p);
        if (!mine) {
            p.addChatMessage(new ChatComponentTranslation("fluxecho.recycle_bin.not_yours"));
            return true;
        }
        TileNexus n = bin.nexus();
        if (n == null && !bin.linked()) n = bin.linkFor(p);
        if (n == null) {
            p.addChatMessage(
                new ChatComponentTranslation(
                    bin.linked() ? "fluxecho.recycle_bin.unloaded" : "fluxecho.recycle_bin.unlinked"));
            return true;
        }
        UIInfos.TILE_MODULAR_UI.open(p, w, x, y, z);
        return true;
    }

    private static String where(TileNexus n) {
        return n.xCoord + ", " + n.yCoord + ", " + n.zCoord;
    }

    // ---- looks

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister r) {
        String p = FluxEcho.MODID + ":recycle_bin/";
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
        if (w.getTileEntity(x, y, z) instanceof TileRecycleBin bin) return s == bin.facing() ? front : side;
        return getIcon(s, 0);
    }

    /** The bin as an item: what it does. */
    public static class ItemBin extends ItemBlock {

        public ItemBin(Block block) {
            super(block);
        }

        @Override
        @SideOnly(Side.CLIENT)
        @SuppressWarnings({ "rawtypes", "unchecked" })
        public void addInformation(ItemStack stack, EntityPlayer player, List tip, boolean advanced) {
            tip.addAll(EchoText.lines("recycle_bin.tip"));
        }
    }
}
