package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * The units of a city module (0.11.0): the whole-unit furniture a player uses as one thing, the Echo Archive's
 * bookcase rule ({@link LibraryUnits}) made general. A unit is a kind ({@code planter}, {@code canvas} …), its cells
 * (always checked parts of the shape), its slots and an optional face. Clicking any of its cells means the unit; an
 * item goes into its first empty slot. Units come in the order their generator declares them, and that order is the
 * slot order: unit {@code i}'s slots follow unit {@code i - 1}'s, so it is never changed once a module shipped.
 * <p>
 * A unit's sockets (fitting SOCKET, {@code o}) are the cells its slots are shown in, one slot a socket in the order the
 * generator lists its cells; a unit with fewer sockets than slots shows the rest nowhere in particular (a canvas
 * shows its one slot over its whole field). The storeys are the distinct floors the units stand on (the y of each
 * unit's lowest cell, or for a unit hung on a wall over the floor it is used from, that floor: 0.14.0, the Dual-foil
 * Vault's foil), which the module window's plan draws one at a time.
 * <p>
 * Pure data in the module's local frame ({@link ShapeKit}): x across, y up, z from the front.
 */
public final class UnitLayout {

    /** One unit. */
    public static final class Unit {

        /** Its place in the layout. */
        public final int index;
        /** The generator's name (a label such as {@code G-RB}), and its kind (the lang key {@code unit.<kind>}). */
        public final String name, kind;
        /** Its cells {x, y, z}, in the generator's order. */
        public final List<int[]> cells;
        /** Its socket cells {x, y, z}: slot {@code firstSlot + k} shows in socket {@code k}. */
        public final List<int[]> sockets;
        /** How many slots it has, and the first of them. */
        public final int slots, firstSlot;
        /** The direction {fx, fz} its face looks (local frame), or null for a unit used from any side. */
        public final int[] face;
        /**
         * The floor it stands on, the storey of the window's plan it shows on: the y of its lowest cell, {@link #base},
         * or (0.14.0) for a unit hung on a wall over the floor it is used from, that floor.
         */
        public final int floor;
        /** The y of its lowest cell (0.14.0; its {@link #floor} but for a unit hung over the floor it is used from). */
        public final int base;

        Unit(int index, ShapeKit.Unit u, List<int[]> sockets, int firstSlot, Integer usedFrom) {
            this.index = index;
            name = u.name;
            kind = u.kind;
            cells = u.cells;
            this.sockets = Collections.unmodifiableList(sockets);
            slots = u.slots;
            this.firstSlot = firstSlot;
            face = u.face;
            int lo = Integer.MAX_VALUE;
            for (int[] c : cells) lo = Math.min(lo, c[1]);
            base = lo;
            floor = usedFrom == null ? lo : usedFrom;
        }

        /** The local footprint {x0, z0, x1, z1} (inclusive) of its cells. */
        public int[] footprint() {
            int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
            for (int[] c : cells) {
                x0 = Math.min(x0, c[0]);
                z0 = Math.min(z0, c[2]);
                x1 = Math.max(x1, c[0]);
                z1 = Math.max(z1, c[2]);
            }
            return new int[] { x0, z0, x1, z1 };
        }
    }

    private final List<Unit> units;
    private final Map<Long, Integer> cellUnit = new HashMap<>();
    private final Map<Long, Integer> socketSlot = new HashMap<>();
    private final int slots;
    private final int[] storeys;

    /**
     * The units of a shape, in the order it declared them.
     *
     * @throws IllegalStateException when a cell belongs to two units or a unit has more sockets than slots
     */
    public UnitLayout(ShapeKit.Shape shape) {
        this(shape, Collections.<String, Integer>emptyMap());
    }

    /**
     * The units of a shape whose units named in {@code usedFrom} hang on a wall over the floor they are used from
     * (0.14.0: the Dual-foil Vault's foil, its lowest cells over its frame two above the nave's floor): each of those
     * stands on that floor's storey, not on a storey of its own at its lowest cell.
     *
     * @throws IllegalStateException    when a cell belongs to two units or a unit has more sockets than slots
     * @throws IllegalArgumentException when a named unit is not the shape's or its floor lies above its lowest cell
     */
    public UnitLayout(ShapeKit.Shape shape, Map<String, Integer> usedFrom) {
        List<Unit> out = new ArrayList<>();
        int slot = 0;
        TreeSet<Integer> floors = new TreeSet<>();
        for (ShapeKit.Unit u : shape.units()) {
            int index = out.size();
            List<int[]> sockets = new ArrayList<>();
            for (int[] c : u.cells) {
                long k = ShapeKit.Shape.pos(c[0], c[1], c[2]);
                if (cellUnit.put(k, index) != null) throw new IllegalStateException(
                    shape.name + ": a cell of two units at " + c[0] + "," + c[1] + "," + c[2]);
                if (shape.get(c[0], c[1], c[2]) == 'o') sockets.add(new int[] { c[0], c[1], c[2] });
            }
            if (sockets.size() > u.slots)
                throw new IllegalStateException(shape.name + " unit " + u.name + ": more sockets than slots");
            for (int i = 0; i < sockets.size(); i++) {
                int[] c = sockets.get(i);
                socketSlot.put(ShapeKit.Shape.pos(c[0], c[1], c[2]), slot + i);
            }
            Unit unit = new Unit(index, u, sockets, slot, usedFrom.get(u.name));
            if (unit.floor > unit.base) throw new IllegalArgumentException(
                shape.name + " unit " + u.name + ": used from " + unit.floor + ", over its lowest cell");
            out.add(unit);
            floors.add(unit.floor);
            slot += u.slots;
        }
        for (String name : usedFrom.keySet()) {
            boolean known = false;
            for (Unit u : out) known |= u.name.equals(name);
            if (!known) throw new IllegalArgumentException(shape.name + " has no unit " + name);
        }
        units = Collections.unmodifiableList(out);
        slots = slot;
        storeys = new int[floors.size()];
        int i = 0;
        for (int f : floors) storeys[i++] = f;
    }

    /** Every unit, in slot order. */
    public List<Unit> units() {
        return units;
    }

    public Unit unit(int index) {
        return units.get(index);
    }

    /** How many units there are. */
    public int size() {
        return units.size();
    }

    /** How many slots all the units have together. */
    public int slots() {
        return slots;
    }

    /** The unit a local cell belongs to, or -1. */
    public int unitOfCell(int x, int y, int z) {
        Integer u = cellUnit.get(ShapeKit.Shape.pos(x, y, z));
        return u == null ? -1 : u;
    }

    /** The slot a socket cell shows, or -1 for any other cell. */
    public int slotOfSocket(int x, int y, int z) {
        Integer s = socketSlot.get(ShapeKit.Shape.pos(x, y, z));
        return s == null ? -1 : s;
    }

    /** The unit a slot belongs to, or -1 outside the layout. */
    public int unitOfSlot(int slot) {
        if (slot < 0 || slot >= slots) return -1;
        for (Unit u : units) if (slot < u.firstSlot + u.slots) return u.index;
        return -1;
    }

    /** The socket cell {x, y, z} a slot shows in, or null when the slot has none. */
    public int[] socketOf(int slot) {
        int u = unitOfSlot(slot);
        if (u < 0) return null;
        Unit unit = units.get(u);
        int k = slot - unit.firstSlot;
        return k < unit.sockets.size() ? unit.sockets.get(k)
            .clone() : null;
    }

    /** The floors the units stand on ({@link Unit#floor}), lowest first: the plan's storeys. */
    public int[] storeys() {
        return storeys.clone();
    }

    /** The storey (an index into {@link #storeys()}) a unit stands on. */
    public int storey(int unit) {
        int f = units.get(unit).floor;
        for (int i = 0; i < storeys.length; i++) if (storeys[i] == f) return i;
        return -1;
    }
}
