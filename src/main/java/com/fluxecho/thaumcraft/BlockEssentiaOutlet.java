package com.fluxecho.thaumcraft;

import java.util.List;
import java.util.UUID;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import com.fluxecho.FluxEcho;

import thaumcraft.api.aspects.Aspect;

/**
 * Essentia Outlet: placed next to an Essentia Echo. Right-click with a phial, jar or crystal to select the aspect
 * tubes get when they ask for none in particular (it is learned at the same time); right-click empty-handed to see
 * the link.
 */
public class BlockEssentiaOutlet extends BlockContainer {

    public BlockEssentiaOutlet() {
        super(Material.iron);
        setBlockName("fluxecho.essentia_outlet");
        setBlockTextureName(FluxEcho.MODID + ":essentia_outlet");
        setHardness(3.0F);
        setResistance(10.0F);
        setStepSound(soundTypeMetal);
        setHarvestLevel("pickaxe", 1);
        setCreativeTab(FluxEcho.TAB);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEssentiaOutlet();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hx, float hy,
        float hz) {
        if (world.isRemote) return true;
        if (!(world.getTileEntity(x, y, z) instanceof TileEssentiaOutlet outlet)) return false;
        MTEEssentiaEcho echo = outlet.echo();
        ItemStack held = player.getHeldItem();
        List<Aspect> taught = AspectSamples.taught(held);
        if (!taught.isEmpty()) {
            Aspect a = taught.get(0);
            outlet.select(a);
            UUID team = echo == null ? null : echo.team();
            if (team != null) AspectMemory.get()
                .learn(team, taught);
            player.addChatMessage(new ChatComponentTranslation("fluxecho.essentia_outlet.selected", a.getName()));
            return true;
        }
        if (echo == null) {
            player.addChatMessage(new ChatComponentTranslation("fluxecho.essentia_outlet.no_echo"));
            return true;
        }
        Aspect sel = outlet.selected();
        player.addChatMessage(echo.describe());
        player.addChatMessage(
            sel == null ? new ChatComponentTranslation("fluxecho.essentia_outlet.none")
                : new ChatComponentTranslation(
                    "fluxecho.essentia_outlet.selected_now",
                    sel.getName(),
                    echo.affordable(sel, Integer.MAX_VALUE)));
        return true;
    }
}
