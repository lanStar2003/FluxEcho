package com.fluxecho.thaumcraft;

import java.util.List;

import net.minecraft.item.ItemStack;

import com.fluxecho.logic.VisFlow;

import thaumcraft.api.aspects.Aspect;
import thaumcraft.common.items.baubles.ItemAmuletVis;
import thaumcraft.common.items.wands.ItemWandCasting;

/** What holds vis and how to fill it: wands, sceptres, staves and vis amulets. Vis is counted in centivis. */
public final class VisItems {

    private VisItems() {}

    public static boolean chargeable(ItemStack s) {
        return s != null && (s.getItem() instanceof ItemWandCasting || s.getItem() instanceof ItemAmuletVis);
    }

    public static int room(ItemStack s, Aspect a) {
        if (s.getItem() instanceof ItemWandCasting w) return Math.max(0, w.getMaxVis(s) - w.getVis(s, a));
        if (s.getItem() instanceof ItemAmuletVis v) return Math.max(0, v.getMaxVis(s) - v.getVis(s, a));
        return 0;
    }

    public static void add(ItemStack s, Aspect a, int cv) {
        if (cv <= 0) return;
        if (s.getItem() instanceof ItemWandCasting w) w.addRealVis(s, a, cv, true);
        else if (s.getItem() instanceof ItemAmuletVis v) v.addRealVis(s, a, cv, true);
    }

    /** Centivis each primal still takes, in {@link Aspect#getPrimalAspects()} order. */
    public static int[] room(ItemStack s) {
        List<Aspect> primals = Aspect.getPrimalAspects();
        int[] room = new int[primals.size()];
        for (int i = 0; i < room.length; i++) room[i] = room(s, primals.get(i));
        return room;
    }

    /**
     * Fills the item from {@code euHave} EU at up to {@code rate} centivis per primal; returns the flow, whose
     * {@code eu} the caller pays.
     */
    public static VisFlow charge(ItemStack s, int rate, int euPerCv, long euHave) {
        VisFlow f = VisFlow.step(room(s), rate, euPerCv, euHave);
        List<Aspect> primals = Aspect.getPrimalAspects();
        for (int i = 0; i < f.add.length && i < primals.size(); i++) add(s, primals.get(i), f.add[i]);
        return f;
    }
}
