package com.fluxecho.skin;

import com.fluxecho.logic.shape.Family;
import com.fluxecho.logic.shape.FormCodes;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * The new material families of the redesign (0.17.0, style FINAL §3.1, §3.5; ids.md §3.1): eight block ids, two for
 * each family of the first batch, the hull ({@code fluxecho:hull}, {@code hull_form}), the footing ({@code footing},
 * {@code footing_form}), the garden skin of moss bronze and white marble ({@code skin_garden},
 * {@code skin_garden_form}) and the script skin of ink and paper ({@code skin_script}, {@code skin_script_form}).
 * They are registered whatever is enabled, as every block of the city is, so a placed block never vanishes from a
 * save. 0.17.0 builds nothing of them yet: the swatch wall shows them and the new buildings take them up from 0.19.0
 * on; their part codes ({@link FormCodes}) let the builder place them and credit them back.
 */
public final class SkinModule {

    public static BlockSkin hull, hullForm, footing, footingForm, garden, gardenForm, script, scriptForm;

    private SkinModule() {}

    /** Makes the blocks without registering them (the tests' worlds take them as they are). */
    public static void create() {
        hull = new BlockSkin(Family.HULL, true);
        hullForm = new BlockSkin(Family.HULL, false);
        footing = new BlockSkin(Family.FOOTING, true);
        footingForm = new BlockSkin(Family.FOOTING, false);
        garden = new BlockSkin(Family.SKIN_GARDEN, true);
        gardenForm = new BlockSkin(Family.SKIN_GARDEN, false);
        script = new BlockSkin(Family.SKIN_SCRIPT, true);
        scriptForm = new BlockSkin(Family.SKIN_SCRIPT, false);
    }

    /** Makes and registers the eight blocks with their items, under the names of ids.md §3.1. */
    public static void preInit() {
        create();
        for (BlockSkin b : all()) GameRegistry.registerBlock(b, ItemBlockSkin.class, b.registryName());
    }

    /** The eight blocks, whole and shape id of each family in family order; empty before they are made. */
    public static BlockSkin[] all() {
        if (hull == null) return new BlockSkin[0];
        return new BlockSkin[] { hull, hullForm, footing, footingForm, garden, gardenForm, script, scriptForm };
    }

    /** The whole-block ({@code whole}) or shape-block id of a family; null before the blocks are made. */
    public static BlockSkin block(Family f, boolean whole) {
        switch (f) {
            case HULL:
                return whole ? hull : hullForm;
            case FOOTING:
                return whole ? footing : footingForm;
            case SKIN_GARDEN:
                return whole ? garden : gardenForm;
            default:
                return whole ? script : scriptForm;
        }
    }
}
