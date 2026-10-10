package com.fluxecho.campus;

import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.fluxecho.core.EchoRecipes;
import com.fluxecho.frame.BlockFrame;
import com.fluxecho.frame.FrameModule;
import com.fluxecho.library.LibraryModule;
import com.fluxecho.logic.GtPattern;
import com.fluxecho.logic.PartRecipes;
import com.fluxecho.logic.Parts;
import com.fluxecho.logic.ResearchTree;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * The one place a campus part code ({@link Parts}) becomes a block, a metadata, an item or a recipe ingredient, and a
 * block in the world becomes a part code again. The plans, costs and packets of the campus builder only ever carry
 * codes; everything that has to touch Minecraft goes through here.
 */
public final class PartBlocks {

    private PartBlocks() {}

    /**
     * The block of a part code: the flux frame, the campus deck or fittings, the library core, grass, dirt or air. Null
     * for the supply port (it has no block yet) and for codes that name nothing.
     */
    public static Block block(int code) {
        int meta = Parts.meta(code);
        if (Parts.isFrame(code)) return meta < BlockFrame.TYPES ? FrameModule.frame : null;
        if (Parts.isDeck(code)) return meta < Parts.DECK_TYPES ? CampusModule.deck : null;
        if (Parts.isFitting(code)) return meta < Parts.FITTING_TYPES ? CampusModule.fitting : null;
        switch (code) {
            case Parts.LIBRARY_CORE:
                return LibraryModule.core;
            case Parts.GRASS:
                return Blocks.grass;
            case Parts.DIRT:
                return Blocks.dirt;
            case Parts.AIR:
                return Blocks.air;
            default:
                return null;
        }
    }

    /** The metadata a part code is placed with: its meta within the frame, deck or fitting family, 0 for the rest. */
    public static int meta(int code) {
        return Parts.meta(code);
    }

    /**
     * The part code of one of our blocks: frame, deck and fitting blocks of a known meta, and the library core (any
     * meta). -1 for everything that is not ours, grass, dirt and air included: a cell holds the part a step wants when
     * {@link #block} and {@link #meta} of its code match, which callers test directly for those three.
     */
    public static int code(Block b, int meta) {
        if (b == null) return -1;
        if (b == FrameModule.frame) return meta >= 0 && meta < BlockFrame.TYPES ? Parts.frame(meta) : -1;
        if (b == CampusModule.deck) return meta >= 0 && meta < Parts.DECK_TYPES ? Parts.deck(meta) : -1;
        if (b == CampusModule.fitting) return meta >= 0 && meta < Parts.FITTING_TYPES ? Parts.fitting(meta) : -1;
        if (b == LibraryModule.core) return Parts.LIBRARY_CORE;
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
