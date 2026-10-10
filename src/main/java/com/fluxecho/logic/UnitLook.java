package com.fluxecho.logic;

/**
 * The look of an echo shelf in the chunk mesh, decided from its neighbours alone. The Echo Archive builds its shelves
 * as units (a plinth, three shelf bodies, a crown), and a body inside such a stack is drawn as a niche with boards
 * and a recessed back. The block renderer runs on the chunk-building thread and must not read {@code Formed} or any
 * tile entity, so the rule only looks at what kind of block sits next to the shelf. The client maps each neighbouring
 * block to a {@link Kind}; this class never sees a {@code Block}. The mapping tests the shelf and the three fittings
 * first and only then opacity, so a shelf neighbour is {@link Kind#SHELF} even though the frame block is an opaque
 * cube.
 */
public final class UnitLook {

    /** What a neighbouring block is, as far as the shelf look cares. */
    public enum Kind {
        /** A frame SHELF block (an echo shelf). */
        SHELF,
        /** A fitting POST: the upright between two units. */
        POST,
        /** A fitting PLINTH: the foot of a unit. */
        PLINTH,
        /** A fitting CROWN: the top of a unit. */
        CROWN,
        /** Any other opaque full cube (walls, floor, the other frame and deck blocks). */
        OPAQUE,
        /** Anything else: air, glass, rails, plants and other see-through blocks. */
        OTHER
    }

    private UnitLook() {}

    /**
     * Whether a shelf renders as a unit niche: the block below is a shelf or a plinth and the block above is a shelf or
     * a crown. A loose shelf standing on the floor or under a ceiling keeps the plain block look.
     */
    public static boolean niche(Kind below, Kind above) {
        boolean foot = below == Kind.SHELF || below == Kind.PLINTH;
        boolean head = above == Kind.SHELF || above == Kind.CROWN;
        return foot && head;
    }

    /**
     * Whether the horizontal side towards this neighbour shows books: every side whose neighbour is not opaque, not
     * another shelf (the inside of a run) and not a post (the upright between units).
     */
    public static boolean bookFace(Kind neighbour) {
        return neighbour != Kind.OPAQUE && neighbour != Kind.SHELF && neighbour != Kind.POST;
    }
}
