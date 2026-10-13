package com.fluxecho.logic;

/**
 * What a must-be-air cell ({@code -}: a doorway, a vestibule, an opening between storeys) of a FluxEcho multiblock lets
 * stand in it when its structure is checked (0.14.2). StructureLib's own {@code -} wants real air, so a single snow
 * layer that settled in an open doorway while a building went up kept the Resonance Gate and the Dual-foil Vault from
 * ever forming. The checked structure takes air, and also whatever weather and plants leave in an opening that a player
 * could place a block over and walk through: a snow layer below a full block, tall grass, a vine, a dead bush, fire
 * (blocks Minecraft calls replaceable). A fluid is not taken (water or lava standing in a doorway is a flood, not
 * weather), and neither is anything solid: a full snow layer (meta 7, not replaceable), a snow block, a flower, any
 * block a player built.
 * <p>
 * The campus builder judges a must-be-air cell it already cleared by the same rule ({@code Builder.recheck}): only a
 * cell this rule refuses is cleared again.
 */
public final class MustBeAir {

    private MustBeAir() {}

    /**
     * Whether the cell may stand in the structure as it is: it is air, or its block is replaceable ({@code
     * Block.isReplaceable} at the cell, which for a snow layer depends on its height) and no fluid.
     */
    public static boolean open(boolean air, boolean replaceable, boolean liquid) {
        return air || replaceable && !liquid;
    }
}
