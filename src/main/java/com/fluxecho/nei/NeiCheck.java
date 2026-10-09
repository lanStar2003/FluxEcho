package com.fluxecho.nei;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;

import org.lwjgl.opengl.GL11;

import com.fluxecho.FluxEcho;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiUsageRecipe;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.IUsageHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;

/**
 * {@code /fluxecho_nei}: looks up every FluxEcho NEI page the ways a player does (the machine itself, as with U on it;
 * the page; an example's ingredients and result) and draws a few of its pages, then says in chat which page failed.
 * Each failure's details go to the log under "NEI check".
 */
public class NeiCheck extends CommandBase {

    /** The registered pages, each with the machine NEI shows as its catalyst. */
    private static final Map<TemplateRecipeHandler, ItemStack> PAGES = new LinkedHashMap<>();

    public static void track(TemplateRecipeHandler handler, ItemStack machine) {
        PAGES.put(handler, machine);
    }

    @Override
    public String getCommandName() {
        return "fluxecho_nei";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/fluxecho_nei";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        runAll(text -> sender.addChatMessage(new ChatComponentText(text)));
    }

    /** Checks every page; one line per page and a total go to out (with colour codes). Returns how many failed. */
    public static int runAll(Consumer<String> out) {
        int failed = 0;
        for (Map.Entry<TemplateRecipeHandler, ItemStack> e : PAGES.entrySet()) {
            TemplateRecipeHandler h = e.getKey();
            List<String> problems = new ArrayList<>();
            int pages = check(h, e.getValue(), problems);
            String name = h.getRecipeName();
            if (problems.isEmpty()) {
                FluxEcho.LOG.info("NEI check OK: {} ({}), {} pages", name, h.getHandlerId(), pages);
                out.accept("§a✔ §f" + name + " §7(" + pages + ")");
            } else {
                failed++;
                FluxEcho.LOG.error("NEI check FAILED: {} ({}): {}", name, h.getHandlerId(), problems);
                out.accept("§c✘ §f" + name + " §c" + problems.get(0));
            }
        }
        FluxEcho.LOG.info("NEI check done: {} pages, {} failed", PAGES.size(), failed);
        out.accept(
            (failed == 0 ? "§a" : "§c") + "FluxEcho NEI: " + (PAGES.size() - failed) + "/" + PAGES.size() + " OK");
        return failed;
    }

    /** The registered pages' handler ids, in order. */
    public static List<String> ids() {
        List<String> l = new ArrayList<>();
        for (TemplateRecipeHandler h : PAGES.keySet()) l.add(h.getHandlerId());
        return l;
    }

    /** Runs the lookups on one page and returns how many examples it has; what went wrong goes into problems. */
    private static int check(TemplateRecipeHandler h, ItemStack machine, List<String> problems) {
        String id = h.getHandlerId();
        int pages = 0;
        try {
            ICraftingHandler all = h.getRecipeHandler(id);
            pages = all.numRecipes();
            if (pages > 0) draw((TemplateRecipeHandler) all, problems);
            if (machine != null) {
                // U on the machine: NEI asks each handler, and as the catalyst each of its transfer rects' lookups
                IUsageHandler usage = h.getUsageAndCatalystHandler("item", machine.copy());
                if (pages > 0 && usage.numRecipes() < pages)
                    problems.add("machine shows " + usage.numRecipes() + " of " + pages);
                if (!found(GuiUsageRecipe.getUsageHandlers("item", machine.copy()), id))
                    problems.add("not among the machine's uses");
            }
            if (pages > 0) {
                if (!found(GuiCraftingRecipe.getCraftingHandlers(id), id)) problems.add("page lookup empty");
                // the spring's "result" is the pool it fills, not something it makes
                PositionedStack result = h instanceof FluxRecipeHandler ? all.getResultStack(0) : null;
                if (result != null && h.getRecipeHandler("item", result.item.copy())
                    .numRecipes() == 0) problems.add("result " + result.item.getDisplayName() + " not found");
                for (PositionedStack in : all.getIngredientStacks(0))
                    if (h.getUsageAndCatalystHandler("item", in.item.copy())
                        .numRecipes() == 0) problems.add("ingredient " + in.item.getDisplayName() + " not found");
            }
        } catch (Throwable t) {
            FluxEcho.LOG.error("NEI check: {} threw", id, t);
            problems.add(t.toString());
        }
        return pages;
    }

    private static boolean found(List<?> handlers, String id) {
        for (Object o : handlers) if (o instanceof TemplateRecipeHandler t && id.equals(t.getHandlerId())) return true;
        return false;
    }

    /** Draws the first few examples as NEI's window would, so a drawing error shows up here and not in the GUI. */
    private static void draw(TemplateRecipeHandler h, List<String> problems) {
        int n = Math.min(h.numRecipes(), 4);
        for (int i = 0; i < n; i++) {
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            GL11.glPushMatrix();
            try {
                h.drawBackground(i);
                h.drawExtras(i);
                h.getOtherStacks(i);
            } catch (Throwable t) {
                FluxEcho.LOG.error("NEI check: drawing {} #{} threw", h.getHandlerId(), i, t);
                problems.add("draw #" + i + ": " + t);
                return;
            } finally {
                GL11.glPopMatrix();
                GL11.glPopAttrib();
            }
        }
    }
}
