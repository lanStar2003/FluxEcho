package com.fluxecho.core;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

/** FluxEcho's translated texts: tooltips and Waila lines of the machines. */
public final class EchoText {

    private EchoText() {}

    /** {@code fluxecho.<key>}, translated and formatted. */
    public static String t(String key, Object... args) {
        return StatCollector.translateToLocalFormatted("fluxecho." + key, args);
    }

    /** A translated text split into lines; .lang files write a line break as a literal "\n". */
    public static List<String> lines(String key, Object... args) {
        List<String> out = new ArrayList<>();
        for (String l : t(key, args).split("\\\\n|\n")) out.add(l);
        return out;
    }

    /**
     * GT's "Machine Type: X" line, as its own machines have it ({@code GT5U.MBTT.MachineType} is GT's lang key), with
     * the type from {@code fluxecho.<key>.type}.
     */
    public static String machineType(String key) {
        return StatCollector.translateToLocal("GT5U.MBTT.MachineType") + ": "
            + EnumChatFormatting.YELLOW
            + t(key + ".type")
            + EnumChatFormatting.RESET;
    }

    /** Ticks as seconds: "5", "0.65". */
    public static String seconds(int ticks) {
        double s = ticks / 20.0;
        return s == Math.rint(s) ? String.valueOf((long) s) : String.format("%.2f", s);
    }

    /** A machine's status line: {@code fluxecho.status.<key>}. */
    public static String status(String key) {
        return t("status." + (key == null || key.isEmpty() ? "idle" : key));
    }

    /** Green while it works, grey when idle, red without power, amber when it waits for something. */
    public static int statusColor(String key) {
        if ("working".equals(key)) return 0x6CFF8A;
        if (key == null || "idle".equals(key) || "ready".equals(key)) return 0x86A6B8;
        if ("no_power".equals(key) || "disabled".equals(key)) return 0xFF6050;
        return 0xFFB040;
    }

    /** A machine's info line ({@code MTEEchoMachine#info}): a lang key and its arguments, translated here. */
    public static String decode(String info) {
        if (info == null || info.isEmpty()) return "";
        String[] parts = info.split("\u0001", -1);
        Object[] args = new Object[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);
        return t(parts[0], args);
    }

    /** GT's tier names. */
    public static String tier(int gtTier) {
        String[] names = { "ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV" };
        return gtTier >= 0 && gtTier < names.length ? names[gtTier] : "?";
    }

    /** Waila: the machine's status line. */
    public static void wailaBody(NBTTagCompound tag, List<String> tip) {
        if (!tag.hasKey("feStatus")) return;
        String status = tag.getString("feStatus");
        EnumChatFormatting color = "working".equals(status) ? EnumChatFormatting.AQUA
            : "idle".equals(status) ? EnumChatFormatting.GRAY : EnumChatFormatting.GOLD;
        tip.add(color + t("status." + status));
    }
}
