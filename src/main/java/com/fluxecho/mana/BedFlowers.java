package com.fluxecho.mana;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

import vazkii.botania.api.BotaniaAPI;
import vazkii.botania.common.block.ModBlocks;
import vazkii.botania.common.item.block.ItemBlockSpecialFlower;

/**
 * Every kind of Botania flower a player can plant, for the operators' bed probe ({@code /fluxecho probe bed}, 0.17.0
 * refactor FINAL F06, S-P4): the sixteen mystical flowers, the sixteen glimmering ones, the sixteen tall ones (two
 * blocks of eight) and every functional and generating flower in the creative menu's order. The probe plants one of
 * each on a planting bed to show which take. Only called when Botania is loaded; its classes stay in this package
 * ({@code IsolationTest}).
 */
public final class BedFlowers {

    private BedFlowers() {}

    /** One stack of each kind, in a fixed order (the plain ones first, then the special ones as Botania lists them). */
    public static List<ItemStack> kinds() {
        List<ItemStack> out = new ArrayList<>();
        colours(out, ModBlocks.flower, 16);
        colours(out, ModBlocks.shinyFlower, 16);
        colours(out, ModBlocks.doubleFlower1, 8);
        colours(out, ModBlocks.doubleFlower2, 8);
        for (String type : BotaniaAPI.subtilesForCreativeMenu) {
            ItemStack s = ItemBlockSpecialFlower.ofType(type);
            if (s != null && s.getItem() != null) out.add(s);
        }
        return out;
    }

    private static void colours(List<ItemStack> out, Block b, int n) {
        if (b == null) return;
        for (int meta = 0; meta < n; meta++) out.add(new ItemStack(b, 1, meta));
    }
}
