package com.fluxecho.logic.probe;

/**
 * Where an operator's check-up rig stands (0.17.0, refactor FINAL F06: the swatch wall and the four probes): the block
 * the operator stood in when it was set out and the way they faced, snapped to the nearest of the four compass
 * directions. Every rig is laid out in this frame, so the server that places its blocks and the client that draws its
 * light work out the same cells from the same few numbers.
 * <p>
 * A cell of the frame is named by how far it lies ahead of the operator ({@code along}, in the facing), to their right
 * ({@code right}) and up ({@code up}); the world cell is {@link #at}. A rig carries its frame in its marker sign
 * ({@link ProbeMarker}): the sign stands at a fixed cell of the frame and faces back at the operator, so the frame is
 * found again from the sign alone, after a restart or on another client.
 */
public final class ProbeFrame {

    /** The block the operator stood in (their feet). */
    public final int x, y, z;
    /** The way they faced: a unit step along x or z. */
    public final int dx, dz;

    public ProbeFrame(int x, int y, int z, int dx, int dz) {
        if (Math.abs(dx) + Math.abs(dz) != 1)
            throw new IllegalArgumentException("not a compass step: " + dx + ", " + dz);
        this.x = x;
        this.y = y;
        this.z = z;
        this.dx = dx;
        this.dz = dz;
    }

    /**
     * The frame of an operator standing in {@code (x, y, z)} and looking along Minecraft's yaw (degrees: 0 south, 90
     * west, 180 north, 270 east), snapped to the nearest compass direction.
     */
    public static ProbeFrame facing(int x, int y, int z, double yaw) {
        int q = Math.floorMod((int) Math.round(yaw / 90), 4);
        int[][] steps = { { 0, 1 }, { -1, 0 }, { 0, -1 }, { 1, 0 } };
        return new ProbeFrame(x, y, z, steps[q][0], steps[q][1]);
    }

    /** The x step of the operator's right hand: facing south (+z) the right is west (-x). */
    public int rx() {
        return -dz;
    }

    /** The z step of the operator's right hand. */
    public int rz() {
        return dx;
    }

    /** The world cell {@code along} ahead, {@code right} to the right and {@code up} above the operator's block. */
    public int[] at(int along, int right, int up) {
        return new int[] { x + along * dx + right * rx(), y + up, z + along * dz + right * rz() };
    }

    /**
     * A world point in the frame: {@code along}, {@code right} and {@code up} measured from the middle of the
     * operator's block at its floor (so {@code at(0, 0, 0)} is the middle of the floor they stood on).
     */
    public double[] at(double along, double right, double up) {
        return new double[] { x + 0.5 + along * dx + right * rx(), y + up, z + 0.5 + along * dz + right * rz() };
    }

    /**
     * The Minecraft yaw (degrees) of looking along the frame turned by {@code turn} quarter turns to the right (0 the
     * facing, 2 back at the operator).
     */
    public float yaw(int turn) {
        int[] d = turned(turn);
        return (float) Math.toDegrees(Math.atan2(-d[0], d[1]));
    }

    /** The step of the facing turned by {@code turn} quarter turns to the right. */
    public int[] turned(int turn) {
        int ax = dx, az = dz;
        for (int i = 0; i < Math.floorMod(turn, 4); i++) {
            int nx = -az, nz = ax;
            ax = nx;
            az = nz;
        }
        return new int[] { ax, az };
    }

    /**
     * The meta of a standing sign whose writing faces along {@code (fx, fz)}: vanilla's rotation in sixteenths, 0
     * facing south, 4 west, 8 north, 12 east.
     */
    public static int signMeta(int fx, int fz) {
        if (fz > 0) return 0;
        if (fx < 0) return 4;
        if (fz < 0) return 8;
        return 12;
    }

    /** The compass step a standing sign of meta {@code meta} faces, rounded to the nearest of the four. */
    public static int[] signFacing(int meta) {
        int q = Math.floorMod(Math.round((meta & 15) / 4f), 4);
        int[][] steps = { { 0, 1 }, { -1, 0 }, { 0, -1 }, { 1, 0 } };
        return steps[q];
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ProbeFrame f && f.x == x && f.y == y && f.z == z && f.dx == dx && f.dz == dz;
    }

    @Override
    public int hashCode() {
        return ((x * 31 + y) * 31 + z) * 31 + dx * 3 + dz;
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ", " + z + ") facing " + dx + ", " + dz;
    }
}
