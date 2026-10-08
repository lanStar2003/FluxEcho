package com.fluxecho.mobs;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.StatBase;
import net.minecraft.stats.StatList;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import com.fluxecho.Config;
import com.fluxecho.codex.Categories;
import com.fluxecho.codex.EchoLedger;
import com.fluxecho.core.Owners;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Where prey imprints get written, each time by a real player and never by a machine: killing a mob while carrying a
 * blank imprint, or right-clicking a mob of a kind the vanilla statistics show the player has killed.
 */
public final class MobEvents {

    @SubscribeEvent
    public void onDeath(LivingDeathEvent e) {
        if (!Config.mobsEnabled || e.entityLiving == null || e.entityLiving.worldObj.isRemote) return;
        if (!(e.source.getEntity() instanceof EntityPlayerMP p) || p instanceof FakePlayer) return;
        String mob = MobImprints.name(e.entityLiving);
        if (mob == null || MobImprints.recipe(mob) == null) return;
        ItemStack blank = null;
        for (ItemStack s : p.inventory.mainInventory) {
            if (blank == null && MobImprints.isBlank(s)) blank = s;
            if (mob.equals(MobImprints.mob(s))) return; // already carries one
        }
        if (blank != null) write(p, blank, mob);
    }

    /** Right-click on a living mob with a blank imprint: written when the statistics show a kill of its kind. */
    static void writeFromStats(EntityPlayerMP p, ItemStack blank, EntityLivingBase target) {
        if (!Config.mobsEnabled) return;
        String mob = MobImprints.name(target);
        if (mob == null) return;
        if (MobImprints.recipe(mob) == null) {
            p.addChatMessage(
                new ChatComponentTranslation("fluxecho.mob_imprint.no_drops", MobImprints.displayName(mob)));
            return;
        }
        StatBase kills = StatList.func_151177_a("stat.killEntity." + mob);
        if (kills == null || p.func_147099_x()
            .writeStat(kills) <= 0) {
            p.addChatMessage(
                new ChatComponentTranslation("fluxecho.mob_imprint.not_killed", MobImprints.displayName(mob)));
            return;
        }
        write(p, blank, mob);
    }

    private static void write(EntityPlayerMP p, ItemStack blank, String mob) {
        if (!p.capabilities.isCreativeMode) blank.stackSize--;
        if (blank.stackSize <= 0) {
            for (int i = 0; i < p.inventory.mainInventory.length; i++)
                if (p.inventory.mainInventory[i] == blank) p.inventory.mainInventory[i] = null;
        }
        ItemStack imprint = MobImprints.imprint(mob);
        if (!p.inventory.addItemStackToInventory(imprint)) p.dropPlayerItemWithRandomChoice(imprint, false);
        p.inventoryContainer.detectAndSendChanges();
        EchoLedger.get()
            .record(Owners.team(p.getUniqueID()), Categories.MOB, mob);
        p.addChatMessage(new ChatComponentTranslation("fluxecho.mob_imprint.written", MobImprints.displayName(mob)));
        p.worldObj.playSoundAtEntity(p, "random.orb", 0.4f, 0.6f);
    }
}
