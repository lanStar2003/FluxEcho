package com.fluxecho.codex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import net.minecraft.item.ItemStack;

/**
 * The kinds of things the ledger keeps, in the Codex's order. A module registers how to show its category (an icon
 * and a name for each key) and which items stand for a key, for the "done once" line in their tooltips. The functions
 * are only called on the client, but registering them is harmless on a server.
 */
public final class Categories {

    public static final String BEE = "bee", TREE = "tree", BUTTERFLY = "butterfly", ASPECT = "aspect", MOB = "mob",
        CROP = "crop", INFUSION = "infusion", ENCHANT = "enchant", ORB = "orb";

    public static final class Category {

        public final String id;
        /** The key's icon in the Codex; null shows a blank slot. */
        public final Function<String, ItemStack> icon;
        /** The key's name in the Codex. */
        public final Function<String, String> name;
        /** The key an item stands for (a bee's species, ...), or null when it is none of this category's. */
        public final Function<ItemStack, String> keyOf;

        public Category(String id, Function<String, ItemStack> icon, Function<String, String> name,
            Function<ItemStack, String> keyOf) {
            this.id = id;
            this.icon = icon;
            this.name = name;
            this.keyOf = keyOf;
        }
    }

    private static final List<Category> ALL = new ArrayList<>();

    private Categories() {}

    public static synchronized void register(Category c) {
        ALL.removeIf(x -> x.id.equals(c.id));
        ALL.add(c);
    }

    public static synchronized List<Category> all() {
        return Collections.unmodifiableList(new ArrayList<>(ALL));
    }
}
