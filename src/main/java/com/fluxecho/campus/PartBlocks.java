package com.fluxecho.campus;

import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.fluxecho.city.CityModule;
import com.fluxecho.core.EchoRecipes;
import com.fluxecho.frame.BlockFrame;
import com.fluxecho.frame.FrameModule;
import com.fluxecho.library.LibraryModule;
import com.fluxecho.logic.GtPattern;
import com.fluxecho.logic.LiveParts;
import com.fluxecho.logic.PartRecipes;
import com.fluxecho.logic.Parts;
import com.fluxecho.logic.ResearchTree;
import com.fluxecho.logic.shape.Family;
import com.fluxecho.logic.shape.FormCodes;
import com.fluxecho.logic.shape.SkinBlocks;
import com.fluxecho.skin.BlockSkin;
import com.fluxecho.skin.SkinModule;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * The one place a campus part code ({@link Parts}) becomes a block, a metadata, an item or a recipe ingredient, and a
 * block in the world becomes a part code again. The plans, costs and packets of the campus builder only ever carry
 * codes; everything that has to touch Minecraft goes through here.
 * <p>
 * 0.17.0 adds the first batch of the new material families (style FINAL §3.8.1; {@link FormCodes}: the hull 96 to
 * 107, the footing 108 to 117, the script skin 118 to 125, the garden skin 126, 127 and 59 to 62), each code to its
 * family's whole-block or shape-block id ({@link SkinBlocks}) at the shape number's meta and back; the deck's
 * hull-number tile (meta 13, code 29) and the fittings' planting bed (meta 14, code 46); and the live materials (54 to
 * 58, {@link LiveParts}), vanilla blocks that, like grass, are a part only where a plan wants them and never ours.
 */
public final class PartBlocks {

    private PartBlocks() {}

    /**
     * The block of a part code: the flux frame, the campus deck or fittings, the library core, a module core
     * (0.11.0), the supply port, the recycle bin, grass, dirt, water or air. Null for codes that name nothing, and for
     * the recycle bin while {@link CampusModule#recycleBin} is not registered (the builder then skips its step).
     */
    public static Block block(int code) {
        int meta = Parts.meta(code);
        if (Parts.isFrame(code)) return meta < BlockFrame.TYPES ? FrameModule.frame : null;
        if (Parts.isDeck(code)) return BlockDeck.known(meta) ? CampusModule.deck : null;
        if (Parts.isFitting(code)) return BlockFitting.known(meta) ? CampusModule.fitting : null;
        if (Parts.isModuleCore(code)) return CityModule.core;
        Family f = FormCodes.family(code);
        if (f != null) return SkinModule.block(f, SkinBlocks.inWhole(f, FormCodes.number(code)));
        switch (code) {
            case Parts.LIBRARY_CORE:
                return LibraryModule.core;
            case Parts.SUPPLY_PORT:
                return CampusModule.supplyPort;
            case Parts.RECYCLE_BIN:
                return CampusModule.recycleBin;
            case Parts.GRASS:
                return Blocks.grass;
            case Parts.DIRT:
                return Blocks.dirt;
            case Parts.WATER:
                return Blocks.water;
            case Parts.AIR:
                return Blocks.air;
            case Parts.BIRCH:
                return Blocks.log;
            case Parts.OAK_LEAVES:
                return Blocks.leaves;
            case Parts.VINE:
                return Blocks.vine;
            case Parts.LILY:
                return Blocks.waterlily;
            case Parts.FLOWER:
                return Blocks.red_flower;
            default:
                return null;
        }
    }

    /**
     * The metadata a part code is placed with: its meta within the frame, deck, fitting or module core family, (0.17.0)
     * its shape number within a new material family, a live material's ({@link LiveParts#meta}: a birch log upright,
     * oak leaves that never decay), 0 for the rest (water is a source).
     */
    public static int meta(int code) {
        if (FormCodes.family(code) != null) return FormCodes.number(code);
        if (Parts.isLive(code)) return LiveParts.meta(code);
        return Parts.meta(code);
    }

    /**
     * The part code of one of our blocks: frame, deck and fitting blocks of a known meta (0.17.0: the hull-number tile
     * and the planting bed too), the library core, a module core (by its meta), the supply port and the recycle bin
     * (any meta), and (0.17.0) a block of the new material families at a meta the first batch gives a code
     * ({@link SkinBlocks#code}; any other shape of theirs is a foreign block, style FINAL §3.8.1). -1 for everything
     * that is not ours, grass, dirt, water, air and the live materials included: a cell holds the part a step wants
     * when {@link #block} and {@link #meta} of its code match, which callers test directly for those
     * ({@code Terrain.isTarget}).
     */
    public static int code(Block b, int meta) {
        if (b == null) return -1;
        if (b == FrameModule.frame) return meta >= 0 && meta < BlockFrame.TYPES ? Parts.frame(meta) : -1;
        if (b == CampusModule.deck) return BlockDeck.known(meta) ? Parts.deck(meta) : -1;
        if (b == CampusModule.fitting) return BlockFitting.known(meta) ? Parts.fitting(meta) : -1;
        if (b instanceof BlockSkin s) return s.code(meta);
        if (b == LibraryModule.core) return Parts.LIBRARY_CORE;
        if (b == CityModule.core) return meta >= 0 && meta < Parts.MODULE_CORES ? Parts.moduleCore(meta) : -1;
        if (b == CampusModule.supplyPort) return Parts.SUPPLY_PORT;
        if (b == CampusModule.recycleBin) return Parts.RECYCLE_BIN;
        return -1;
    }

    /** A stack of a part, or null when the code has no block or the block has no item (air). */
    public static ItemStack stack(int code, int count) {
        Block b = block(code);
        Item item = b == null ? null : Item.getItemFromBlock(b);
        return item == null ? null : new ItemStack(item, count, meta(code));
    }

    /**
     * A {@link PartRecipes} ingredient spec as a recipe input: {@code ore:x} is the ore name {@code x},
     * {@code item:mod:name@meta} the item (meta 32767 matches any), {@code part:<code>} one of that part. Null when the
     * item or part does not exist in this pack ({@link EchoRecipes} then logs the recipe as missing an ingredient).
     *
     * @throws IllegalArgumentException when the spec is none of these
     */
    public static Object ingredient(String spec) {
        if (spec.startsWith("ore:")) return spec.substring(4);
        int part = PartRecipes.partCode(spec);
        if (part >= 0) return stack(part, 1);
        if (spec.startsWith("item:")) {
            ResearchTree.Cost c = ResearchTree.Cost.parse(spec);
            int colon = c.name.indexOf(':');
            Item item = GameRegistry.findItem(c.name.substring(0, colon), c.name.substring(colon + 1));
            if (item == null) return null;
            return new ItemStack(item, 1, c.meta == 32767 ? OreDictionary.WILDCARD_VALUE : c.meta);
        }
        throw new IllegalArgumentException("not an ingredient spec: " + spec);
    }

    /**
     * Registers a part's shaped recipe through {@link EchoRecipes} (GT first, a plain Forge recipe if GT does not take
     * it): its pattern rows, then every key with its ingredient. Lowercase keys are renamed first ({@link GtPattern}),
     * as GT would read some of them as crafting tools and put a tool in place of the ingredient.
     */
    public static void register(PartRecipes.Recipe r) {
        GtPattern.Safe safe = GtPattern.safe(r.pattern, r.keys);
        Object[] in = new Object[safe.pattern.length + 2 * safe.keys.size()];
        int i = 0;
        for (String row : safe.pattern) in[i++] = row;
        for (Map.Entry<Character, String> e : safe.keys.entrySet()) {
            in[i++] = e.getKey();
            in[i++] = ingredient(e.getValue());
        }
        EchoRecipes.shaped(r.name, stack(r.part, r.yield), in);
    }
}
