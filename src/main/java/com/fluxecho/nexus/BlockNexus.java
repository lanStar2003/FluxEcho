package com.fluxecho.nexus;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.fluxecho.FluxEcho;
import com.fluxecho.frame.FrameEvents;
import com.gtnewhorizons.modularui.api.UIInfos;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * A multiblock controller block of FluxEcho's own: the Flux Nexus's core, a module's core. Its front faces whoever
 * placed it; right-click opens its GUI for the owner's team and the teams bound to it.
 */
public abstract class BlockNexus extends BlockContainer {

    private final String texture;
    @SideOnly(Side.CLIENT)
    private IIcon front, frontOn, side, top;

    protected BlockNexus(String name, String texture) {
        super(Material.iron);
        this.texture = texture;
        setBlockName("fluxecho." + name);
        setHardness(6f);
        setResistance(60f);
        setStepSound(soundTypeMetal);
        setHarvestLevel("pickaxe", 2);
        setCreativeTab(FluxEcho.TAB);
        setLightLevel(0.5f);
    }

    @Override
    public void onBlockPlacedBy(World w, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        int look = MathHelper.floor_double(placer.rotationYaw * 4f / 360f + 0.5) & 3;
        // the front faces the placer: looking south (0) -> north (2), west (1) -> east (5), ...
        int[] front = { 2, 5, 3, 4 };
        TileEntity te = w.getTileEntity(x, y, z);
        if (te instanceof TileMultiblock m) m.place(placer instanceof EntityPlayer p ? p : null, front[look]);
        FrameEvents.changed(w, x, y, z);
    }

    @Override
    public boolean onBlockActivated(World w, int x, int y, int z, EntityPlayer p, int side, float hx, float hy,
        float hz) {
        if (w.isRemote) return true;
        TileEntity te = w.getTileEntity(x, y, z);
        if (!(te instanceof TileMultiblock m)) return false;
        if (!mayUse(m, p)) {
            p.addChatMessage(new ChatComponentTranslation("fluxecho.nexus.not_yours", m.ownerName()));
            return true;
        }
        UIInfos.TILE_MODULAR_UI.open(p, w, x, y, z);
        return true;
    }

    /** Whether the player may open it. */
    protected boolean mayUse(TileMultiblock m, EntityPlayer p) {
        if (m.owner() == null) return true;
        if (p.capabilities.isCreativeMode && p.canCommandSenderUseCommand(2, "")) return true;
        return m instanceof TileNexus n ? n.member(p)
            : com.fluxecho.core.Owners.team(p.getUniqueID())
                .equals(m.team());
    }

    @Override
    public void breakBlock(World w, int x, int y, int z, Block block, int meta) {
        TileEntity te = w.getTileEntity(x, y, z);
        if (te instanceof net.minecraft.inventory.IInventory inv) {
            for (int i = 0; i < inv.getSizeInventory(); i++) {
                ItemStack s = inv.getStackInSlot(i);
                if (s == null) continue;
                EntityItem e = new EntityItem(w, x + 0.5, y + 0.5, z + 0.5, s.copy());
                w.spawnEntityInWorld(e);
                inv.setInventorySlotContents(i, null);
            }
        }
        dropsMore(te, w, x, y, z);
        super.breakBlock(w, x, y, z, block, meta);
        FrameEvents.changed(w, x, y, z);
    }

    /** What else the tile holds that should fall out (a library's samples). */
    protected void dropsMore(TileEntity te, World w, int x, int y, int z) {}

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister r) {
        String p = FluxEcho.MODID + ":" + texture + "/";
        front = r.registerIcon(p + "front");
        frontOn = r.registerIcon(p + "front_on");
        side = r.registerIcon(p + "side");
        top = r.registerIcon(p + "top");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int s, int meta) {
        return s == 3 ? front : s <= 1 ? top : side;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(IBlockAccess w, int x, int y, int z, int s) {
        TileEntity te = w.getTileEntity(x, y, z);
        if (te instanceof TileMultiblock m) {
            if (s == m.facing) return m.formed() ? frontOn : front;
            return s <= 1 ? top : side;
        }
        return getIcon(s, 0);
    }
}
