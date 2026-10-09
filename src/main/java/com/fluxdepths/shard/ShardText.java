package com.fluxdepths.shard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.fluxdepths.client.Keys;

/** Tooltip, Waila, GUI and hologram texts of the Flux Shard Collector. */
public final class ShardText {

    private ShardText() {}

    public static String t(String key, Object... args) {
        return StatCollector.translateToLocalFormatted("fluxdepths.shard." + key, args);
    }

    // ---- tooltip

    public static String[] description() {
        List<String> l = new ArrayList<>();
        l.add(machineType("fluxdepths.shard.type"));
        l.add(EnumChatFormatting.DARK_AQUA + t("lore"));
        l.add(t("core"));
        if (Keys.shift()) {
            for (ShardTier tier : ShardTier.values()) l.add(tierRow(tier));
        } else {
            l.add(EnumChatFormatting.DARK_GRAY + t("shift"));
        }
        l.add(t("head"));
        l.add(t("fluid", ShardTier.MV.fluidPerOre));
        l.add(t("holo"));
        l.add(EnumChatFormatting.RED + t("voltage"));
        l.add(t("world"));
        l.add(
            EnumChatFormatting.DARK_GRAY
                + t("cap", Math.round(ShardTier.LUV.perSecond() / ShardTier.VOID_MINER_PER_SECOND * 100)));
        return l.toArray(new String[0]);
    }

    /** "LV · 32 V · 2.5 s per ore · 24 EU/t · 2 imprints". */
    private static String tierRow(ShardTier tier) {
        String power = tier.steam() ? t("row.steam", tier.steamLitres()) : t("row.eu", tier.voltage(), tier.energy);
        return color(tier) + tier.label()
            + EnumChatFormatting.GRAY
            + " · "
            + power
            + " · "
            + t("row.speed", seconds(tier.ticks), Math.round(tier.perMinute()))
            + " · "
            + t("row.imprints", tier.imprints);
    }

    /** GT's "Machine Type: X" line, as its own machines have it ({@code GT5U.MBTT.MachineType} is GT's lang key). */
    public static String machineType(String typeKey) {
        return StatCollector.translateToLocal("GT5U.MBTT.MachineType") + ": "
            + EnumChatFormatting.YELLOW
            + StatCollector.translateToLocal(typeKey)
            + EnumChatFormatting.RESET;
    }

    // ---- GUI and hologram lines

    /** "LuV core · 32768 V", or "No circuit · steam". */
    public static String coreLabel(ShardTier tier) {
        return tier.steam() ? t("gui.steam_core") : t("gui.core", tier.label(), tier.voltage());
    }

    public static String status(ShardState.Status st) {
        return StatCollector.translateToLocal(
            "fluxdepths.status." + st.name()
                .toLowerCase(Locale.ROOT));
    }

    public static int statusColor(ShardState.Status st) {
        return switch (st) {
            case WORKING -> 0x6CFF8A;
            case IDLE -> 0x86A6B8;
            case NO_POWER, DISABLED -> 0xFF6050;
            default -> 0xFFB040;
        };
    }

    public static String imprintCaption(int used, int max) {
        return t("gui.imprints", used, max);
    }

    public static String speedCaption(ShardTier tier) {
        return t("gui.per_minute", Math.round(tier.perMinute()));
    }

    public static String powerLine(ShardTier tier) {
        return tier.steam() ? t("gui.power_steam", tier.steamLitres(), seconds(tier.ticks))
            : t("gui.power", tier.energy, seconds(tier.ticks));
    }

    public static String wearLine(int headUses, long produced) {
        return headUses > 0 ? t("gui.wear", DrillHead.percent(headUses), headUses, produced)
            : t("gui.no_head", produced);
    }

    /** "24576 EU/t", "16 L/t". */
    public static String powerShort(ShardTier tier) {
        return tier.steam() ? t("holo.steam_use", tier.steamLitres()) : t("holo.eu_use", tier.energy);
    }

    /** "Drill 0.39%/ore", or "No drill head". */
    public static String drillShort(int headUses) {
        return headUses > 0 ? t("holo.drill", DrillHead.percent(headUses)) : t("holo.no_head");
    }

    public static String veinLine(String vein) {
        return t("gui.vein", vein);
    }

    public static String noVein() {
        return t("gui.no_vein");
    }

    public static String energyBar(long eu, long cap) {
        return t("gui.energy", compact(eu), compact(cap));
    }

    public static String steamBar(long litres, long cap) {
        return t("gui.steam", compact(litres), compact(cap));
    }

    /** 950, 1.20K, 45.0M. */
    public static String compact(long v) {
        if (v < 1000) return Long.toString(Math.max(0, v));
        double d = v;
        String units = "KMGTPE";
        int unit = -1;
        while (d >= 999.5 && unit < units.length() - 1) {
            d /= 1000;
            unit++;
        }
        String f = d >= 99.95 ? "%.0f%c" : d >= 9.995 ? "%.1f%c" : "%.2f%c";
        return String.format(Locale.ROOT, f, d, units.charAt(unit));
    }

    static String seconds(int ticks) {
        double s = ticks / 20.0;
        return s == Math.rint(s) ? String.valueOf((long) s) : String.format(Locale.ROOT, "%.2f", s);
    }

    private static EnumChatFormatting color(ShardTier tier) {
        return switch (tier) {
            case STEAM -> EnumChatFormatting.GOLD;
            case LV -> EnumChatFormatting.GRAY;
            case MV -> EnumChatFormatting.AQUA;
            case HV -> EnumChatFormatting.GOLD;
            case EV -> EnumChatFormatting.DARK_PURPLE;
            case IV -> EnumChatFormatting.BLUE;
            case LUV -> EnumChatFormatting.LIGHT_PURPLE;
        };
    }

    // ---- Waila

    public static void wailaData(MTEFluxCollector m, NBTTagCompound tag) {
        ShardState s = m.state();
        tag.setInteger(
            "fdTier",
            m.tier()
                .ordinal());
        tag.setInteger("fdStatus", s.status.ordinal());
        tag.setInteger("fdHeadUses", s.headUses);
        tag.setInteger("fdImprints", s.imprints);
        tag.setLong("fdProduced", s.produced);
        tag.setString("fdVein", m.veinName());
        tag.setBoolean("fdHolo", m.hologram());
    }

    public static void wailaBody(NBTTagCompound tag, List<String> tip) {
        if (!tag.hasKey("fdStatus")) return;
        ShardTier[] tiers = ShardTier.values();
        ShardTier tier = tiers[Math.max(0, Math.min(tiers.length - 1, tag.getInteger("fdTier")))];
        tip.add(color(tier) + coreLabel(tier));
        ShardState.Status st = ShardState.status(tag.getInteger("fdStatus"));
        EnumChatFormatting c = st == ShardState.Status.WORKING ? EnumChatFormatting.AQUA
            : st == ShardState.Status.IDLE ? EnumChatFormatting.GRAY
                : st == ShardState.Status.NO_POWER ? EnumChatFormatting.RED : EnumChatFormatting.GOLD;
        tip.add(c + status(st));
        String vein = tag.getString("fdVein");
        if (!vein.isEmpty() && st == ShardState.Status.WORKING) tip.add(t("echoing", vein));
        tip.add(t("waila.imprints", tag.getInteger("fdImprints"), tier.imprints));
        int uses = tag.getInteger("fdHeadUses");
        tip.add(uses > 0 ? t("drill_wear", DrillHead.percent(uses), uses) : t("waila.no_head"));
        tip.add(t("waila.produced", tag.getLong("fdProduced")));
        tip.add(EnumChatFormatting.DARK_GRAY + t("waila.holo." + (tag.getBoolean("fdHolo") ? "on" : "off")));
    }
}
