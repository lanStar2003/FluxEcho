package com.fluxecho.enchant;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.fluxecho.Config;

/** Enchanted books: what is stored on one, and the Codex's keys ({@code id:level}, one per enchantment). */
public final class EnchantBooks {

    private static Set<Integer> banned;

    private EnchantBooks() {}

    public static boolean isBook(ItemStack s) {
        return s != null && s.getItem() == Items.enchanted_book && stored(s).tagCount() > 0;
    }

    public static NBTTagList stored(ItemStack s) {
        NBTTagList l = Items.enchanted_book.func_92110_g(s);
        return l == null ? new NBTTagList() : l;
    }

    /** Levels on the book, summed (Fortune III and Unbreaking II make 5). */
    public static int levels(ItemStack s) {
        NBTTagList l = stored(s);
        int n = 0;
        for (int i = 0; i < l.tagCount(); i++) n += Math.max(
            1,
            l.getCompoundTagAt(i)
                .getShort("lvl"));
        return n;
    }

    private static synchronized Set<Integer> banned() {
        if (banned == null) {
            Set<Integer> b = new HashSet<>();
            for (String e : Config.enchantBlacklist) {
                try {
                    b.add(Integer.parseInt(e.trim()));
                } catch (NumberFormatException ignored) {
                    // not an id: skipped
                }
            }
            banned = b;
        }
        return banned;
    }

    public static boolean hasBanned(ItemStack s) {
        NBTTagList l = stored(s);
        for (int i = 0; i < l.tagCount(); i++) if (banned().contains(
            (int) l.getCompoundTagAt(i)
                .getShort("id")))
            return true;
        return false;
    }

    /** The Codex keys of a book: {@code id:level} for each enchantment on it. */
    public static Set<String> keys(ItemStack s) {
        Set<String> out = new HashSet<>();
        NBTTagList l = stored(s);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound t = l.getCompoundTagAt(i);
            out.add(t.getShort("id") + ":" + t.getShort("lvl"));
        }
        return out;
    }

    /** The key of a book with exactly one enchantment, for its tooltip line. */
    public static String keyOf(ItemStack s) {
        if (s == null || s.getItem() != Items.enchanted_book) return null;
        Set<String> k = keys(s);
        return k.size() == 1 ? k.iterator()
            .next() : null;
    }

    private static Enchantment enchantment(String key, int[] level) {
        int i = key.indexOf(':');
        if (i <= 0) return null;
        try {
            int id = Integer.parseInt(key.substring(0, i));
            level[0] = Integer.parseInt(key.substring(i + 1));
            return id >= 0 && id < Enchantment.enchantmentsList.length ? Enchantment.enchantmentsList[id] : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static ItemStack icon(String key) {
        int[] lvl = new int[1];
        Enchantment e = enchantment(key, lvl);
        return e == null ? null : Items.enchanted_book.getEnchantedItemStack(new EnchantmentData(e, lvl[0]));
    }

    public static String name(String key) {
        int[] lvl = new int[1];
        Enchantment e = enchantment(key, lvl);
        return e == null ? key : e.getTranslatedName(lvl[0]);
    }
}
