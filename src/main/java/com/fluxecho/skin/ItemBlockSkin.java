package com.fluxecho.skin;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import com.fluxecho.core.EchoText;
import com.fluxecho.logic.shape.SkinBlocks;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The new families' blocks as items (0.17.0): one subtype a shape number, named after the family and the shape
 * ({@code tile.fluxecho.hull.round}, {@code tile.fluxecho.skin_script.paper_lantern}; {@link SkinBlocks#slug}), the
 * same name whichever of the family's two ids holds it. The tooltip says what the material is for.
 */
public class ItemBlockSkin extends ItemBlock {

    public ItemBlockSkin(Block block) {
        super(block);
        setHasSubtypes(true);
        setMaxDamage(0);
    }

    private BlockSkin skin() {
        return (BlockSkin) field_150939_a;
    }

    @Override
    public int getMetadata(int meta) {
        return meta;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        int m = stack.getItemDamage() & 15;
        return "tile.fluxecho." + skin().family.id + "." + SkinBlocks.slug(skin().family, m);
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List tip, boolean advanced) {
        tip.addAll(EchoText.lines("skin." + SkinBlocks.material(skin().family, stack.getItemDamage() & 15) + ".tip"));
    }
}
