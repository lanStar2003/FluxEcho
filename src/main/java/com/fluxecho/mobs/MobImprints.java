package com.fluxecho.mobs;

import java.util.Map;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;

import com.kuba6000.mobsinfo.api.MobRecipe;

/**
 * Prey imprints: the name a mob is registered under ({@code EntityList}, the same name the vanilla kill statistic and
 * MobsInfo use), written on an imprint once its holder has killed one.
 */
public final class MobImprints {

    private static final String MOB = "Mob";

    private MobImprints() {}

    public static boolean isImprint(ItemStack s) {
        return s != null && MobModule.imprint != null && s.getItem() == MobModule.imprint;
    }

    public static boolean isBlank(ItemStack s) {
        return isImprint(s) && mob(s) == null;
    }

    /** The mob on an imprint; null for a blank one or anything else. */
    public static String mob(ItemStack s) {
        if (!isImprint(s)) return null;
        NBTTagCompound t = s.getTagCompound();
        return t != null && t.hasKey(MOB) ? t.getString(MOB) : null;
    }

    public static ItemStack imprint(String mob) {
        ItemStack s = new ItemStack(MobModule.imprint);
        NBTTagCompound t = new NBTTagCompound();
        t.setString(MOB, mob);
        s.setTagCompound(t);
        return s;
    }

    /** The mob's name in the player's language, as the game shows it ("Enderman"). */
    public static String displayName(String mob) {
        String key = "entity." + mob + ".name";
        return StatCollector.canTranslate(key) ? StatCollector.translateToLocal(key) : mob;
    }

    /** MobsInfo's drop table for the mob; null when it has none (the mob cannot be echoed). */
    public static MobRecipe recipe(String mob) {
        return mob == null ? null : MobRecipe.getRecipeByEntityName(mob);
    }

    public static String name(Entity e) {
        return e == null ? null : EntityList.getEntityString(e);
    }

    /** The mob's spawn egg, when it has one, for icons. */
    public static ItemStack egg(String mob) {
        Class<? extends Entity> c = EntityList.stringToClassMapping.get(mob);
        if (c == null) return null;
        for (Map.Entry<Integer, ?> e : EntityList.entityEggs.entrySet()) {
            if (EntityList.IDtoClassMapping.get(e.getKey()) == c) return new ItemStack(Items.spawn_egg, 1, e.getKey());
        }
        return null;
    }

    /** The mob a spawn egg or an imprint stands for. */
    public static String keyOf(ItemStack s) {
        if (s == null) return null;
        if (isImprint(s)) return mob(s);
        if (s.getItem() == Items.spawn_egg) return EntityList.getStringFromID(s.getItemDamage());
        return null;
    }
}
