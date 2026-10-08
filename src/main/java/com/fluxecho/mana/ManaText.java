package com.fluxecho.mana;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;

import com.fluxecho.Config;
import com.fluxecho.client.Keys;
import com.fluxecho.core.CoreCircuits;
import com.fluxecho.core.EchoText;
import com.fluxecho.logic.Compact;
import com.fluxecho.logic.ManaSpring;

/** Tooltip, Waila, GUI and hologram texts of the Mana Echo Spring ({@code fluxecho.spring.*}). */
public final class ManaText {

    private ManaText() {}

    public static String t(String key, Object... args) {
        return EchoText.t("spring." + key, args);
    }

    // ---- tooltip

    public static String[] description() {
        List<String> l = new ArrayList<>();
        l.add(EchoText.machineType("mana_echo"));
        l.add(EnumChatFormatting.DARK_AQUA + t("lore"));
        l.add(t("core"));
        if (Keys.shift()) {
            for (int tier = ManaSpring.MIN_TIER; tier <= ManaSpring.MAX_TIER; tier++) l.add(tierRow(tier));
        } else {
            l.add(EnumChatFormatting.DARK_GRAY + t("shift"));
        }
        l.add(t("petal", perMinute()));
        l.add(t("pools"));
        l.add(t("charge"));
        l.add(t("holo"));
        l.add(EnumChatFormatting.RED + t("voltage"));
        return l.toArray(new String[0]);
    }

    /** "LV · 32 V · 16 mana/t · 32 EU/t · petal 1.25K · reach 4 / ±2". */
    private static String tierRow(int tier) {
        return chat(tier) + CoreCircuits.label(tier)
            + EnumChatFormatting.GRAY
            + " · "
            + t(
                "row",
                ManaSpring.voltage(tier),
                ManaSpring.manaPerTick(Config.manaPerTick, tier),
                ManaSpring.euPerTick(Config.manaPerTick, Config.euPerMana, tier),
                Compact.si(ManaSpring.manaPerPetal(Config.manaPerPetal, tier)),
                ManaSpring.range(Config.manaRange, tier),
                ManaSpring.height(Config.manaHeight, tier));
    }

    /** Petals a minute, "15.4". */
    public static String perMinute() {
        return String.format(Locale.ROOT, "%.1f", ManaSpring.petalsPerMinute(Config.manaPerTick, Config.manaPerPetal));
    }

    // ---- GUI and hologram lines

    /** "LuV core · 32768 V", or "No circuit in the core". */
    public static String coreLabel(int tier) {
        return tier > 0 ? t("gui.core", CoreCircuits.label(tier), ManaSpring.voltage(tier)) : t("gui.no_core");
    }

    public static String status(MTEManaSpring.State st) {
        return t(
            "status." + st.name()
                .toLowerCase(Locale.ROOT));
    }

    public static int statusColor(MTEManaSpring.State st) {
        return switch (st) {
            case WORKING -> 0x6CFF8A;
            case IDLE -> 0x86A6B8;
            case NO_POWER, DISABLED, NO_CORE -> 0xFF6050;
            default -> 0xFFB040;
        };
    }

    /** "64 mana/t · 128 EU/t". */
    public static String rate(int mana, long eu) {
        return t("gui.rate", Compact.si(mana), Compact.si(eu));
    }

    /** "A petal 5K mana · 1.20K tuned". */
    public static String petalLine(long worth, long credit) {
        return t("gui.petal", Compact.si(worth), Compact.si(credit));
    }

    /** "3 pools · 1.20M / 3.00M", or "No pool in reach". */
    public static String poolLine(int count, long mana, long cap) {
        return count > 0 ? t("gui.pools", count, Compact.si(mana), Compact.si(cap)) : t("gui.no_pool");
    }

    /** "Tablet 45%", or "Sent 45.0M" without one. */
    public static String chargeOrSent(int chargeFill, long delivered) {
        return chargeFill >= 0 ? t("gui.charge", chargeFill / 10) : t("gui.sent", Compact.si(delivered));
    }

    public static String reach(int range, int height) {
        return t("gui.reach", range, height);
    }

    public static String energyBar(long eu, long cap) {
        return t("gui.energy", Compact.si(eu), Compact.si(cap));
    }

    public static String bufferBar(long mana, long cap) {
        return t("gui.buffer", Compact.si(mana), Compact.si(cap));
    }

    private static EnumChatFormatting chat(int tier) {
        return switch (tier) {
            case 1 -> EnumChatFormatting.GRAY;
            case 2 -> EnumChatFormatting.AQUA;
            case 3 -> EnumChatFormatting.GOLD;
            case 4 -> EnumChatFormatting.DARK_PURPLE;
            case 5 -> EnumChatFormatting.BLUE;
            case 6 -> EnumChatFormatting.LIGHT_PURPLE;
            default -> EnumChatFormatting.DARK_GRAY;
        };
    }

    // ---- Waila, from MTEManaSpring#holoData

    public static void waila(NBTTagCompound d, List<String> tip) {
        int tier = d.getByte("t");
        tip.add(chat(tier) + coreLabel(tier));
        MTEManaSpring.State st = MTEManaSpring.State.of(d.getByte("s"));
        EnumChatFormatting c = st == MTEManaSpring.State.WORKING ? EnumChatFormatting.AQUA
            : st == MTEManaSpring.State.IDLE ? EnumChatFormatting.GRAY
                : statusColor(st) == 0xFF6050 ? EnumChatFormatting.RED : EnumChatFormatting.GOLD;
        tip.add(c + status(st));
        if (tier > 0) tip.add(rate(d.getInteger("mt"), d.getLong("et")));
        tip.add(poolLine(d.getInteger("n"), d.getLong("pm"), d.getLong("pc")));
        int cf = d.getInteger("cf");
        if (cf >= 0) tip.add(t("gui.charge", cf / 10));
        tip.add(t("waila.petals", d.getInteger("pe"), Compact.si(d.getLong("c"))));
        tip.add(t("gui.sent", Compact.si(d.getLong("d"))));
        tip.add(EnumChatFormatting.DARK_GRAY + t("waila.holo." + (d.getBoolean("h") ? "on" : "off")));
    }
}
