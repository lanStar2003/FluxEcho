package com.fluxecho.logic.shape;

/**
 * What the renderer lays over a quad besides the material (style FINAL §3.2, §3.6): the glow layers (a white core
 * and a tinted edge, drawn by {@code ShapeRender} over the same quad at full brightness) and the footing's marked
 * faces. {@link ShapeGeo} only says which faces carry which overlay; whether a glow is lit (lamp windows by the box
 * table, G6; dormant buildings) is the renderer's business.
 */
public enum Overlay {

    /** Nothing over the material. */
    NONE,
    /** A light seam's line on a side face; it runs on along the row of light seams. */
    LINE,
    /** A keel's cyan strip on a side face, running on up the keel. */
    KEEL,
    /** The keel's top cell: the strip ends in a point (no keel above). */
    KEEL_TIP,
    /** A lamp window's glowing panel behind the pane; lit or not by the box table (G6). */
    LAMP,
    /** A step's warm nose line on its open front. */
    NOSE,
    /** A pool lip's warm lip: on the top face (its {@code conn} marks the water sides) and on faces toward water. */
    LIP,
    /** A spout's outlet face (toward open air or water). */
    SPOUT
}
