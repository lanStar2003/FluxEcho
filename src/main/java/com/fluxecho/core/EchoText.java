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

    /** Ticks as seconds: "5", "0.65". */
    public static String seconds(int ticks) {
        double s = ticks / 20.0;
        return s == Math.rint(s) ? String.valueOf((long) s) : String.format("%.2f", s);
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
