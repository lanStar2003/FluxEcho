package com.fluxecho.ae;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import com.fluxecho.FluxEcho;

/** The Echo ME Provider block; all the work is in {@link TileEchoProvider}. */
public class BlockEchoProvider extends BlockContainer {

    public BlockEchoProvider() {
        super(Material.iron);
        setBlockName("fluxecho.echo_provider");
        setBlockTextureName(FluxEcho.MODID + ":echo_provider");
        setHardness(2.2f);
        setResistance(10f);
        setStepSound(soundTypeMetal);
        setCreativeTab(FluxEcho.TAB);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEchoProvider();
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, x, y, z, placer, stack);
        if (!world.isRemote && placer instanceof EntityPlayer p
            && world.getTileEntity(x, y, z) instanceof TileEchoProvider t) t.placedBy(p);
    }

    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, Block neighbour) {
        if (world.getTileEntity(x, y, z) instanceof TileEchoProvider t) t.neighbourChanged();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hx, float hy,
        float hz) {
        if (player.isSneaking()) return false;
        if (!world.isRemote && world.getTileEntity(x, y, z) instanceof TileEchoProvider t) {
            player.addChatMessage(
                new ChatComponentTranslation(
                    t.online() ? "fluxecho.echo_provider.status" : "fluxecho.echo_provider.offline",
                    t.offeredCount()));
        }
        return true;
    }
}
