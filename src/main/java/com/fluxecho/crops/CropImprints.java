package com.fluxecho.crops;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;

import com.fluxecho.logic.CropStats;

import ic2.api.crops.CropCard;
import ic2.api.crops.Crops;
import ic2.api.item.IC2Items;
import ic2.core.item.ItemCropSeed;

/**
 * Between IC2's crops (and those Crops++ adds through the same API) and FluxEcho's crop imprints: which crop a seed
 * bag or imprint holds, its stats, and fresh seed bags.
 */
public final class CropImprints {

    private static final String OWNER = "Owner", NAME = "Name", GROWTH = "Growth", GAIN = "Gain",
        RESISTANCE = "Resistance";
    /** IC2's scan level of a fully analysed seed bag: the stats show. */
    static final int FULL_SCAN = 4;

    private CropImprints() {}

    public static boolean isImprint(ItemStack s) {
        return s != null && CropModule.imprint != null && s.getItem() == CropModule.imprint;
    }

    public static boolean isSeedBag(ItemStack s) {
        ItemStack bag = IC2Items.getItem("cropSeed");
        return s != null && bag != null && s.getItem() == bag.getItem();
    }

    /** The crop of a seed bag or an imprint; null for a blank imprint, an unknown crop or anything else. */
    public static CropCard crop(ItemStack s) {
        if (isImprint(s)) {
            NBTTagCompound t = s.getTagCompound();
            return t == null || !t.hasKey(NAME) ? null
                : Crops.instance.getCropCard(t.getString(OWNER), t.getString(NAME));
        }
        return isSeedBag(s) ? Crops.instance.getCropCard(s) : null;
    }

    /** Whether the stats can be read: an imprint always, a seed bag once fully scanned. */
    public static boolean known(ItemStack s) {
        if (isImprint(s)) return crop(s) != null;
        return isSeedBag(s) && ItemCropSeed.getScannedFromStack(s) >= FULL_SCAN;
    }

    public static CropStats stats(ItemStack s) {
        if (isImprint(s)) {
            NBTTagCompound t = s.getTagCompound();
            return t == null ? new CropStats(0, 0, 0)
                : new CropStats(t.getInteger(GROWTH), t.getInteger(GAIN), t.getInteger(RESISTANCE));
        }
        return new CropStats(
            ItemCropSeed.getGrowthFromStack(s),
            ItemCropSeed.getGainFromStack(s),
            ItemCropSeed.getResistanceFromStack(s));
    }

    public static ItemStack imprint(CropCard crop, CropStats stats) {
        NBTTagCompound t = new NBTTagCompound();
        t.setString(OWNER, crop.owner());
        t.setString(NAME, crop.name());
        t.setInteger(GROWTH, stats.growth);
        t.setInteger(GAIN, stats.gain);
        t.setInteger(RESISTANCE, stats.resistance);
        ItemStack s = new ItemStack(CropModule.imprint);
        s.setTagCompound(t);
        return s;
    }

    /** A fully scanned seed bag of the crop with the stats. */
    public static ItemStack seeds(CropCard crop, CropStats stats) {
        return ItemCropSeed.generateItemStackFromValues(
            crop,
            (byte) stats.growth,
            (byte) stats.gain,
            (byte) stats.resistance,
            (byte) FULL_SCAN);
    }

    /** The ledger key of a crop: {@code owner:name}. */
    public static String key(CropCard crop) {
        return crop == null ? null : crop.owner() + ":" + crop.name();
    }

    public static CropCard byKey(String key) {
        int i = key == null ? -1 : key.indexOf(':');
        return i <= 0 ? null : Crops.instance.getCropCard(key.substring(0, i), key.substring(i + 1));
    }

    /**
     * The crop's name as IC2's seed bags show it: IC2 hands the display name to the language file, since its own
     * crops name a lang key there ({@code ic2.crop.redwheat}); other crops' plain names pass through unchanged.
     */
    public static String name(CropCard crop) {
        String raw;
        try {
            raw = crop.displayName();
        } catch (RuntimeException e) {
            raw = null;
        }
        if (raw == null || raw.isEmpty()) raw = crop.name();
        return StatCollector.translateToLocal(raw);
    }

    public static boolean isSticks(ItemStack s) {
        ItemStack sticks = IC2Items.getItem("crop");
        return s != null && sticks != null && s.isItemEqual(sticks);
    }
}
