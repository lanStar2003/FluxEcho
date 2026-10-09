package com.fluxecho.thaumcraft;

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
import net.minecraft.world.World;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The Flux Vis Pedestal block: a low pedestal the wand floats above; the work is in {@link TileVisPedestal}.
 * <ul>
 * <li>Right-click with a wand, sceptre, staff or vis amulet: puts it on the pedestal.</li>
 * <li>Right-click with an empty hand: takes it back; with nothing on it, tells how the pedestal is doing.</li>
 * <li>Right-click with a module: installs it. Sneak-right-click with an empty hand: takes the last module out.</li>
 * </ul>
 */
public class BlockVisPedestal extends BlockContainer {

    private IIcon top, side, bottom;

    public BlockVisPedestal() {
        super(Material.iron);
        setBlockName("fluxecho.vis_pedestal");
        setHardness(2.5f);
        setResistance(10f);
        setStepSound(soundTypeMetal);
        setLightLevel(0.5f);
        setBlockBounds(0f, 0f, 0f, 1f, 0.75f, 1f);
        setCreativeTab(FluxEcho.TAB);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileVisPedestal();
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister r) {
        top = r.registerIcon(FluxEcho.MODID + ":vis_pedestal_top");
        side = r.registerIcon(FluxEcho.MODID + ":vis_pedestal_side");
        bottom = r.registerIcon(FluxEcho.MODID + ":vis_pedestal_bottom");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int s, int meta) {
        return s == 1 ? top : s == 0 ? bottom : side;
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, x, y, z, placer, stack);
        if (!world.isRemote && placer instanceof EntityPlayer p
            && world.getTileEntity(x, y, z) instanceof TileVisPedestal t) t.placedBy(p);
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int s, float hx, float hy,
        float hz) {
        if (!(world.getTileEntity(x, y, z) instanceof TileVisPedestal t)) return false;
        if (world.isRemote) return true;
        ItemStack held = player.getCurrentEquippedItem();
        if (held == null) {
            ItemStack out = player.isSneaking() ? t.uninstall() : t.takeWand();
            if (out != null) {
                give(player, out);
                world.playSoundEffect(x + 0.5, y + 0.75, z + 0.5, "random.pop", 0.4f, 1.2f);
            } else if (!player.isSneaking()) status(player, t);
            return true;
        }
        if (ItemVisModule.Kind.of(held) != null) {
            if (t.install(held)) {
                consume(player, held);
                world.playSoundEffect(x + 0.5, y + 0.75, z + 0.5, "random.click", 0.5f, 1.4f);
                player.addChatMessage(
                    new ChatComponentTranslation(
                        "fluxecho.vis_pedestal.installed",
                        // the module's name, translated by the player's game rather than the server's
                        new ChatComponentTranslation(held.getUnlocalizedName() + ".name"),
                        t.installed(),
                        TileVisPedestal.MODULE_SLOTS));
            } else {
                player.addChatMessage(new ChatComponentTranslation("fluxecho.vis_pedestal.no_slot"));
            }
            return true;
        }
        if (VisItems.chargeable(held) && t.putWand(held)) {
            consume(player, held);
            world.playSoundEffect(x + 0.5, y + 0.75, z + 0.5, "random.orb", 0.4f, 0.7f);
            return true;
        }
        return false;
    }

    private static void consume(EntityPlayer player, ItemStack held) {
        if (player.capabilities.isCreativeMode) return;
        if (--held.stackSize <= 0) player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
    }

    private static void give(EntityPlayer player, ItemStack s) {
        if (player.getCurrentEquippedItem() == null)
            player.inventory.setInventorySlotContents(player.inventory.currentItem, s);
        else if (!player.inventory.addItemStackToInventory(s)) player.dropPlayerItemWithRandomChoice(s, false);
        player.inventoryContainer.detectAndSendChanges();
    }

    private static void status(EntityPlayer player, TileVisPedestal t) {
        player.addChatMessage(
            new ChatComponentTranslation(
                "fluxecho.vis_pedestal.status",
                t.energy(),
                TileVisPedestal.capacity(),
                t.rate() * 20 / 100.0,
                (long) t.rate() * 20 * Config.visEuPerCentiVis * 6,
                t.installed(),
                TileVisPedestal.MODULE_SLOTS));
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        if (world.getTileEntity(x, y, z) instanceof TileVisPedestal t) {
            for (ItemStack s : t.contents()) if (s != null) {
                EntityItem e = new EntityItem(world, x + 0.5, y + 0.8, z + 0.5, s.copy());
                world.spawnEntityInWorld(e);
            }
        }
        super.breakBlock(world, x, y, z, block, meta);
    }
}
