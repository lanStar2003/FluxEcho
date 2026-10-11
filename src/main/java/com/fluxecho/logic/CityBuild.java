package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A city module's shape as the generic module plan reads it ({@link BuildPlan.ModuleBuild}, 0.11.0): a
 * {@link ShapeKit.Shape} drawn in the city legend, its units ({@link UnitLayout}), the part its controller is placed as
 * (its module core), and its stage rule. Every cell's part comes from the legend ({@link ShapeKit#partOf}); the
 * controller is the module's core. The DECOR stage takes the shape's canopy and the seeded ornaments.
 */
public final class CityBuild implements BuildPlan.ModuleBuild {

    /** Which stage a cell that belongs to no unit is built in, and its order within FIT. */
    public interface Stages {

        int stage(int x, int y, int z, char ch);

        int fitRank(int x, int y, int z, char ch);
    }

    private final String key;
    private final ShapeKit.Shape shape;
    private final int height;
    private final Blueprint blueprint;
    private final int corePart;
    private final Stages stages;
    private final List<List<int[]>> units;
    private final List<int[]> decor;

    /**
     * @param height   the blueprint's height (the shape's box may hold empty layers above it)
     * @param corePart the {@link Parts} code its controller is placed as
     */
    public CityBuild(String key, ShapeKit.Shape shape, int height, Blueprint blueprint, UnitLayout layout, int corePart,
        Stages stages) {
        this.key = key;
        this.shape = shape;
        this.height = height;
        this.blueprint = blueprint;
        this.corePart = corePart;
        this.stages = stages;
        List<List<int[]>> u = new ArrayList<>();
        for (UnitLayout.Unit unit : layout.units()) u.add(unit.cells);
        units = Collections.unmodifiableList(u);
        List<int[]> d = new ArrayList<>();
        for (int[] c : shape.decor) d.add(new int[] { c[0], c[1], c[2], ShapeKit.partOf((char) c[3]) });
        decor = Collections.unmodifiableList(d);
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public int width() {
        return shape.W;
    }

    @Override
    public int depth() {
        return shape.D;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public Blueprint blueprint() {
        return blueprint;
    }

    @Override
    public int[] cellOf(int x, int y, int z) {
        return new int[] { x, height - 1 - y, z };
    }

    @Override
    public int[] controller() {
        return shape.controller.clone();
    }

    @Override
    public char cell(int x, int y, int z) {
        return y < height ? shape.get(x, y, z) : ShapeKit.NONE;
    }

    @Override
    public int part(int x, int y, int z, char ch) {
        return ch == ShapeKit.CONTROLLER ? corePart : ShapeKit.partOf(ch);
    }

    @Override
    public int stage(int x, int y, int z, char ch) {
        return stages.stage(x, y, z, ch);
    }

    @Override
    public int fitRank(int x, int y, int z, char ch) {
        return stages.fitRank(x, y, z, ch);
    }

    @Override
    public List<List<int[]>> units() {
        return units;
    }

    @Override
    public List<int[]> decor() {
        return new ArrayList<>(decor);
    }

    @Override
    public boolean ornaments() {
        return true;
    }

    /** The part its controller is placed as. */
    public int corePart() {
        return corePart;
    }
}
