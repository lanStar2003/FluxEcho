package com.fluxecho.campus;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.fluxecho.FluxEcho;
import com.fluxecho.client.shape.SkinIcons;
import com.fluxecho.frame.FrameEvents;
import com.fluxecho.logic.Parts;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The campus deck: the one floor every building of a nexus campus stands on, and the wall panels its modules are built
 * of. One meta per look ({@link Parts} deck metas, names and light), all of them plain opaque stone cubes that only
 * differ in their texture and in how much light they give. Drawn into the chunk mesh by {@code client.DeckRender}, so
 * the bright lines work with shader packs; nothing spawns on it, so a paved campus stays quiet at night.
 * <p>
 * 0.14.0 adds the canvas (meta 12, CITY.md §1.4), the wall a module's unit paints its item on: units can be made of
 * deck cells now, so a use of a deck block is offered to the formed modules first ({@link #onUse}), as a frame's and a
 * fitting's is. 0.17.0 adds meta 13, the hull-number tile (style FINAL §3.7, code 29): a footing-dark floor tile with
 * an octagonal badge, laid in front of every building's door on the way home, its number and whether it is lit the box
 * table's to say; it gives no light of its own. Metas 14 and 15 stay unregistered (ids.md §1.2).
 */
public class BlockDeck extends Block {

    /** What a formed module does when one of its deck blocks is used (a canvas of a city module's unit, 0.14.0). */
    public interface Use {

        /** Whether it handled the click; called on both sides. */
        boolean use(World w, int x, int y, int z, EntityPlayer p, int side, int meta);
    }

    private static final List<Use> USES = new ArrayList<>();

    /** Registers a use (the city's units, {@code city.UnitUse}). */
    public static void onUse(Use u) {
        USES.add(u);
    }

    /** Set by the client proxy. */
    public static int renderId = -1;

    @SideOnly(Side.CLIENT)
    private IIcon[] icons, glows;
    /** The lit strip's upright faces: one level line instead of the floor's cross. */
    @SideOnly(Side.CLIENT)
    private IIcon litSide, litSideGlow;

    public BlockDeck() {
        super(Material.rock);
        setBlockName("fluxecho.deck");
        setHardness(3f);
        setResistance(15f);
        setStepSound(soundTypeStone);
        setHarvestLevel("pickaxe", 1);
        setCreativeTab(FluxEcho.TAB);
    }

    /** Whether the meta is a registered deck: the run of 0.16.0, and (0.17.0) the hull-number tile. */
    public static boolean known(int meta) {
        return meta >= 0 && meta < Parts.DECK_TYPES || meta == Parts.D_HULL_NUMBER;
    }

    /** The meta clamped to a known deck type; unknown metas look like the plain deck. */
    public static int type(int meta) {
        return known(meta) ? meta : Parts.D_DECK;
    }

    /** The light a deck of this meta gives. */
    public static int light(int meta) {
        return meta >= 0 && meta < Parts.DECK_TYPES ? Parts.DECK_LIGHT[meta] : 0;
    }

    @Override
    public int damageDropped(int meta) {
        return meta;
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        for (int i = 0; i < Parts.DECK_TYPES; i++) list.add(new ItemStack(item, 1, i));
        list.add(new ItemStack(item, 1, Parts.D_HULL_NUMBER));
    }

    @Override
    public int getRenderType() {
        return renderId;
    }

    @Override
    public int getLightValue(IBlockAccess w, int x, int y, int z) {
        return light(w.getBlockMetadata(x, y, z));
    }

    @Override
    public boolean canCreatureSpawn(EnumCreatureType type, IBlockAccess w, int x, int y, int z) {
        return false;
    }

    @Override
    public boolean onBlockActivated(World w, int x, int y, int z, EntityPlayer p, int side, float hx, float hy,
        float hz) {
        int meta = w.getBlockMetadata(x, y, z);
        for (Use u : USES) if (u.use(w, x, y, z, p, side, meta)) return true;
        return false;
    }

    @Override
    public void onBlockAdded(World w, int x, int y, int z) {
        super.onBlockAdded(w, x, y, z);
        if (!Builder.QUIET) FrameEvents.changed(w, x, y, z);
    }

    @Override
    public void breakBlock(World w, int x, int y, int z, Block block, int meta) {
        super.breakBlock(w, x, y, z, block, meta);
        if (!Builder.QUIET) FrameEvents.changed(w, x, y, z);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister r) {
        icons = new IIcon[Parts.DECK_TYPES];
        glows = new IIcon[Parts.DECK_TYPES];
        for (int i = 0; i < Parts.DECK_TYPES; i++) {
            String p = FluxEcho.MODID + ":deck/" + Parts.DECK_NAMES[i];
            icons[i] = r.registerIcon(p);
            if (Parts.DECK_LIGHT[i] > 0) glows[i] = r.registerIcon(p + "_glow");
        }
        String lit = FluxEcho.MODID + ":deck/" + Parts.DECK_NAMES[Parts.D_LIT] + "_side";
        litSide = r.registerIcon(lit);
        litSideGlow = r.registerIcon(lit + "_glow");
        // the hull-number tile's (0.17.0), drawn by tools/HullTextures.java
        SkinIcons.registerHullNumber(r);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return icon(meta, side);
    }

    /**
     * The texture of a face. A deck looks the same on every side, except the lit strip: its cross of light is for the
     * floor, so its upright faces (a gallery edge seen from the void) show one level line and its underside is a plain
     * plate.
     */
    @SideOnly(Side.CLIENT)
    public IIcon icon(int meta, int side) {
        int t = type(meta);
        if (t == Parts.D_HULL_NUMBER) return SkinIcons.hullNumber(side);
        if (t == Parts.D_LIT && side != 1) return side == 0 ? icons[Parts.D_DECK] : litSide;
        return icons[t];
    }

    /**
     * The overlay drawn at full brightness on a face, or null when this meta does not glow there (the lit strip glows
     * on its top and its upright faces, not underneath).
     */
    @SideOnly(Side.CLIENT)
    public IIcon glow(int meta, int side) {
        if (meta < 0 || meta >= Parts.DECK_TYPES) return null;
        if (meta == Parts.D_LIT && side != 1) return side == 0 ? null : litSideGlow;
        return glows[meta];
    }
}
