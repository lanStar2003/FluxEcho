package com.fluxecho.logic.shape;

/**
 * One quad of a shape's mesh as pure data: four vertices in local cell coordinates with a normal and a texture
 * coordinate each, plus what the renderer needs to light, texture and overlay it.
 * <p>
 * <b>Coordinates.</b> Positions are in blocks inside the cell, {@code 0..1} on each axis (x east, y up, z south),
 * relative to the block's own corner; only a rib's 45-degree brace reaches up to 0.09 past its cell, into the corner
 * it shares with the next rib. Vertices run counter-clockwise seen from outside the shape (the front face, as the
 * Tessellator and Angelica expect). A triangle is a quad whose last vertex repeats the third ({@link #triangle()};
 * Angelica takes only quads, style §8.4 #10).
 * <p>
 * <b>Normals.</b> Every vertex has a unit normal pointing out of the solid: the face normal on flat faces, the
 * analytic normal on curved ones (radial on cylinders and the sphere corner, toward the arc's centre on the soffit's
 * concave face). Give them to {@code Tessellator.setNormal} vertex by vertex.
 * <p>
 * <b>Texture coordinates.</b> {@code u, v} are in blocks, with {@code u} to the right and {@code v} downward as seen
 * from outside. Flat faces are projected along their dominant axis (up and down faces: u = x, v = z; side faces: u
 * runs to the viewer's right, v = 1 - y), so a pattern several cells wide continues across cells: add
 * {@code sign(uAxis) * blockPos[|uAxis| - 1]} to {@code u} (axes 1 x, 2 y, 3 z; 0 means no world offset) and likewise
 * {@code vAxis} to {@code v}, then take the result modulo the pattern's span. Curved faces carry the arc length in
 * {@code u} (no world offset) and the length along their axis in {@code v}.
 */
public final class Quad {

    /** What kind of surface the quad lies on: the tests check each kind's normals against its own formula. */
    public enum Surface {
        /** A flat face. */
        PLANE,
        /** A cylinder of radius 0.5: a rounded vertical edge or a rounded top edge. */
        CYLINDER,
        /** The eighth of a sphere of radius 0.5 at a round's top corner. */
        SPHERE,
        /** The soffit's concave quarter cylinder of radius 1. */
        CONCAVE
    }

    /** Four vertices: x, y, z each. */
    public final double[] pos;
    /** Four unit normals: x, y, z each. */
    public final double[] normal;
    /** Four texture coordinates: u, v each, in blocks. */
    public final double[] uv;
    /**
     * The cell face the quad lies on ({@code ForgeDirection} order) for light from that side, or -1 inside the cell.
     */
    public final int face;
    /** The world axes the texture coordinates continue along: ±1 x, ±2 y, ±3 z, 0 none. */
    public final int uAxis, vAxis;
    /** What the renderer lays over the quad. */
    public final Overlay overlay;
    /**
     * Which in-plane neighbours continue the face (same shape, same family; for a pool lip's top, the water sides):
     * 1 toward -u, 2 toward +u, 4 toward -v (up the texture), 8 toward +v.
     */
    public final int conn;
    /** The kind of surface. */
    public final Surface surface;

    Quad(double[][] v, int face, int uAxis, int vAxis, Overlay overlay, int conn, Surface surface) {
        pos = new double[12];
        normal = new double[12];
        uv = new double[8];
        for (int i = 0; i < 4; i++) {
            System.arraycopy(v[i], 0, pos, 3 * i, 3);
            System.arraycopy(v[i], 3, normal, 3 * i, 3);
            System.arraycopy(v[i], 6, uv, 2 * i, 2);
        }
        this.face = face;
        this.uAxis = uAxis;
        this.vAxis = vAxis;
        this.overlay = overlay;
        this.conn = conn;
        this.surface = surface;
    }

    /** Vertex {@code i}'s x. */
    public double x(int i) {
        return pos[3 * i];
    }

    /** Vertex {@code i}'s y. */
    public double y(int i) {
        return pos[3 * i + 1];
    }

    /** Vertex {@code i}'s z. */
    public double z(int i) {
        return pos[3 * i + 2];
    }

    /** Whether the quad is a triangle written as a quad (its last vertex repeats the third). */
    public boolean triangle() {
        return pos[6] == pos[9] && pos[7] == pos[10] && pos[8] == pos[11];
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder(surface.name()).append(" f")
            .append(face);
        for (int i = 0; i < 4; i++) b.append(
            String.format(java.util.Locale.ROOT, " (%.4f %.4f %.4f)", pos[3 * i], pos[3 * i + 1], pos[3 * i + 2]));
        return b.toString();
    }
}
