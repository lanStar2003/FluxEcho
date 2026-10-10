package com.fluxecho.campus;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.common.IShearable;
import net.minecraftforge.fluids.IFluidBlock;

import com.fluxecho.Config;
import com.fluxecho.logic.Parts;
import com.fluxecho.logic.TerrainRule;

/**
 * Fills a {@link TerrainRule.Probe} from the world for one cell, so the pure rules can decide what the campus builder
 * may do there. Natural terrain is judged by Forge hooks and materials (wood, leaves, plants, ore-generation stone,
 * soft ground, uncut rock), never by names except for the player-built words of {@link TerrainRule#craftedName} and the
 * two config lists. GT, Bartworks and GT++ ores are recognised by class name, looked up once; GT's own stones count as
 * natural only in their raw variants. The caller must make sure the cell's chunk exists before probing (reading a
 * missing chunk on the server would load or generate it).
 * <p>
 * Players build with natural materials too, and the builder must not take another player's cabin or farm for terrain.
 * So a few natural-looking blocks count as built ({@link #builtByHand}): a log that is part of a group of logs
 * touching something crafted (planks, glass, a door, a torch, a chest...), which a tree never does; leaves that never
 * decay (vanilla leaves placed by hand); farmland and what grows on it; hay bales and jack-o'-lanterns. Dirt, sand
 * and raw stone placed by hand cannot be told from the ground and are still taken for terrain.
 */
public final class Terrain {

    private static final Material[] SOFT = { Material.ground, Material.grass, Material.sand, Material.clay,
        Material.snow, Material.craftedSnow, Material.ice, Material.packedIce, Material.leaves, Material.plants,
        Material.vine, Material.gourd, Material.cactus, Material.coral };

    private static final String GT_ORE = "gregtech.common.blocks.BlockOresAbstract",
        GT_STONE = "gregtech.common.blocks.BlockStonesAbstract",
        BW_ORE_TILE = "bartworks.system.material.BWTileEntityMetaGeneratedOre",
        GTPP_ORE = "gtPlusPlus.core.block.base.BlockBaseOre";

    private static Class<?> gtOre, gtStone, bwOreTile, gtppOre;
    private static boolean looked;

    /** What a block is, worked out once per block. */
    private static final class Kind {

        String name = "";
        boolean ore, gtStone, craftedName, vanillaCrafted, plant;
    }

    private static final Map<Block, Kind> KINDS = new IdentityHashMap<>();

    private Terrain() {}

    /**
     * The facts about the cell for a step that wants {@code targetPart} there ({@link Parts} code; -1 for a cell of a
     * clearance volume, which wants nothing). {@code campus} supplies the protected areas (other structures, other
     * campuses); null leaves them out.
     */
    public static TerrainRule.Probe probe(World w, int x, int y, int z, int targetPart, Campus campus) {
        TerrainRule.Probe p = new TerrainRule.Probe();
        if (y < 0 || y > 255) {
            p.unbreakable = true;
            return p;
        }
        Block b = w.getBlock(x, y, z);
        int meta = w.getBlockMetadata(x, y, z);
        Material m = b.getMaterial();
        p.air = m == Material.air || b.isAir(w, x, y, z);
        p.target = targetPart >= 0 && isTarget(b, meta, targetPart, p.air);
        if (p.air) return p;
        Kind k = kind(b);
        p.replaceable = b.isReplaceable(w, x, y, z);
        p.fluid = m.isLiquid() || b instanceof BlockLiquid || b instanceof IFluidBlock;
        p.lava = m == Material.lava;
        p.hasTile = b.hasTileEntity(meta);
        p.unbreakable = b.getBlockHardness(w, x, y, z) < 0;
        p.gtOre = k.ore || (p.hasTile && bwOre(w.getTileEntity(x, y, z)));
        int code = PartBlocks.code(b, meta);
        p.ours = code >= 0 && (Parts.isFrame(code) || Parts.isDeck(code) || Parts.isFitting(code));
        if (campus != null) {
            p.oursInOtherStructure = p.ours && campus.protectedCell(x, y, z);
            p.otherNexusArea = campus.otherCampusAt(x, z);
        }

        boolean listedBlocked = TerrainRule.listed(k.name, Arrays.asList(Config.buildBlocked));
        boolean rawGtStone = k.gtStone && meta % 8 == 0;
        boolean wood = b.isWood(w, x, y, z);
        p.crafted = listedBlocked || k.vanillaCrafted || (k.craftedName && !rawGtStone) || (k.gtStone && !rawGtStone);
        if (!p.crafted && !p.hasTile && builtByHand(w, x, y, z, b, meta, k, wood)) p.crafted = true;

        TerrainRule.Nature n = new TerrainRule.Nature();
        n.hasTile = p.hasTile;
        n.wood = wood;
        n.leaves = b.isLeaves(w, x, y, z);
        n.replaceable = p.replaceable;
        n.plant = k.plant;
        n.oreGenStone = b.isReplaceableOreGen(w, x, y, z, Blocks.stone);
        n.softMaterial = soft(m);
        n.rock = m == Material.rock;
        n.shaped = !b.isOpaqueCube() || !b.renderAsNormalBlock();
        n.crafted = p.crafted;
        n.clearable = TerrainRule.listed(k.name, Arrays.asList(Config.buildClearable)) || rawGtStone;
        n.blocked = listedBlocked;
        p.natural = TerrainRule.natural(n);
        return p;
    }

    // ---- natural materials a player built with

    /** The most logs looked at to tell a tree from a log building. */
    static final int MAX_LOGS = 256;
    /** Ticks a log's verdict is remembered (the builder asks for every log of a trunk in turn). */
    static final long LOG_MEMORY = 200;

    private static final int[][] FACES = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 },
        { 0, 0, -1 } };
    private static final Map<Long, Boolean> BUILT_LOGS = new HashMap<>();
    private static World logWorld;
    private static long logAt;

    /**
     * Whether a natural-looking block was most likely placed by a player: vanilla leaves placed by hand (they never
     * decay), a plant on farmland, or a log of a log building ({@link #builtLog}).
     */
    static boolean builtByHand(World w, int x, int y, int z, Block b, int meta, Kind k, boolean wood) {
        if (handLeaves(b, meta)) return true;
        if (k.plant && y > 0 && w.getBlock(x, y - 1, z) == Blocks.farmland) return true;
        return wood && builtLog(w, x, y, z);
    }

    private static boolean handLeaves(Block b, int meta) {
        return (b == Blocks.leaves || b == Blocks.leaves2) && (meta & 4) != 0;
    }

    /**
     * Whether the log belongs to something built: the logs connected to it (also corner to corner, as branches and
     * acacia trunks are) touch, face to face, a crafted block, which a tree's logs never do. Looks at no more than
     * {@link #MAX_LOGS} logs and never into a chunk that is not loaded; the verdict is remembered for every log looked
     * at, for a while.
     */
    static synchronized boolean builtLog(World w, int x, int y, int z) {
        long now = w.getTotalWorldTime();
        if (logWorld != w || now - logAt > LOG_MEMORY || now < logAt || BUILT_LOGS.size() > 16384) {
            BUILT_LOGS.clear();
            logWorld = w;
            logAt = now;
        }
        Boolean known = BUILT_LOGS.get(BuildJob.pos(x, y, z));
        if (known != null) return known;
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> open = new ArrayDeque<>();
        seen.add(BuildJob.pos(x, y, z));
        open.add(new int[] { x, y, z });
        boolean built = false;
        search: while (!open.isEmpty()) {
            int[] c = open.poll();
            for (int[] f : FACES) {
                int nx = c[0] + f[0], ny = c[1] + f[1], nz = c[2] + f[2];
                if (ny < 0 || ny > 255 || !w.blockExists(nx, ny, nz)) continue;
                if (craftedNeighbour(w, nx, ny, nz)) {
                    built = true;
                    break search;
                }
            }
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                int nx = c[0] + dx, ny = c[1] + dy, nz = c[2] + dz;
                if (ny < 0 || ny > 255 || seen.size() >= MAX_LOGS || !w.blockExists(nx, ny, nz)) continue;
                if (!w.getBlock(nx, ny, nz)
                    .isWood(w, nx, ny, nz)) continue;
                if (seen.add(BuildJob.pos(nx, ny, nz))) open.add(new int[] { nx, ny, nz });
            }
        }
        for (long p : seen) BUILT_LOGS.put(p, built);
        return built;
    }

    /**
     * Whether a block next to a log shows a player's work: a tile entity (a chest, a sign; not an ore), a crafted block
     * ({@link Kind#vanillaCrafted}, {@link TerrainRule#craftedName}), leaves placed by hand, or a block of a made
     * material (wood that is not a log, such as planks, doors and fences; glass, wool, carpet, torches and redstone,
     * iron, lamps, pistons). Our own campus blocks and huge mushrooms do not count.
     */
    private static boolean craftedNeighbour(World w, int x, int y, int z) {
        Block b = w.getBlock(x, y, z);
        if (b.isAir(w, x, y, z) || b.isWood(w, x, y, z)) return false;
        int meta = w.getBlockMetadata(x, y, z);
        if (b.isLeaves(w, x, y, z)) return handLeaves(b, meta);
        if (b == Blocks.red_mushroom_block || b == Blocks.brown_mushroom_block) return false;
        Kind k = kind(b);
        if (b.hasTileEntity(meta)) return !k.ore && !bwOre(w.getTileEntity(x, y, z));
        if (k.vanillaCrafted || k.craftedName) return true;
        if (PartBlocks.code(b, meta) >= 0) return false;
        Material m = b.getMaterial();
        return m == Material.wood || m == Material.glass
            || m == Material.cloth
            || m == Material.carpet
            || m == Material.circuits
            || m == Material.iron
            || m == Material.anvil
            || m == Material.redstoneLight
            || m == Material.piston;
    }

    /** Whether the block keeps its drops in the spoils when cleared (ores and logs). */
    public static boolean spoils(World w, int x, int y, int z, Block b) {
        if (kind(b).ore || b.isWood(w, x, y, z)) return true;
        return b.hasTileEntity(w.getBlockMetadata(x, y, z)) && bwOre(w.getTileEntity(x, y, z));
    }

    /** Whether the cell already holds the part (any meta for grass and the library core). */
    public static boolean isTarget(Block b, int meta, int part, boolean air) {
        if (part == Parts.AIR) return air;
        Block want = PartBlocks.block(part);
        if (want == null || want == Blocks.air || b != want) return false;
        if (part == Parts.GRASS || part == Parts.LIBRARY_CORE) return true;
        return meta == PartBlocks.meta(part);
    }

    /** Whether a placed part would stand in the way of an entity (so the builder waits for it to move). */
    public static boolean solid(int part) {
        Block b = PartBlocks.block(part);
        return b != null && b != Blocks.air
            && b.getMaterial()
                .blocksMovement();
    }

    private static boolean soft(Material m) {
        for (Material s : SOFT) if (s == m) return true;
        return false;
    }

    private static synchronized Kind kind(Block b) {
        Kind k = KINDS.get(b);
        if (k != null) return k;
        lookUp();
        k = new Kind();
        Object name = Block.blockRegistry.getNameForObject(b);
        k.name = name == null ? "" : name.toString();
        k.ore = isA(gtOre, b) || isA(gtppOre, b);
        k.gtStone = isA(gtStone, b);
        k.craftedName = TerrainRule.craftedName(k.name);
        k.vanillaCrafted = b == Blocks.cobblestone || b == Blocks.mossy_cobblestone
            || b == Blocks.stonebrick
            || b == Blocks.brick_block
            || b == Blocks.planks
            || b == Blocks.farmland
            || b == Blocks.hay_block
            || b == Blocks.lit_pumpkin;
        k.plant = b instanceof IPlantable || b instanceof IShearable;
        KINDS.put(b, k);
        return k;
    }

    private static boolean isA(Class<?> c, Object o) {
        return c != null && c.isInstance(o);
    }

    private static boolean bwOre(TileEntity te) {
        lookUp();
        return isA(bwOreTile, te);
    }

    private static synchronized void lookUp() {
        if (looked) return;
        looked = true;
        gtOre = find(GT_ORE);
        gtStone = find(GT_STONE);
        bwOreTile = find(BW_ORE_TILE);
        gtppOre = find(GTPP_ORE);
    }

    private static Class<?> find(String name) {
        try {
            return Class.forName(name, false, Terrain.class.getClassLoader());
        } catch (Throwable t) {
            return null;
        }
    }
}
