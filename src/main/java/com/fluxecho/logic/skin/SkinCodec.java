package com.fluxecho.logic.skin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The box table's bytes, the same at both ends: what {@code SKIN_TABLE} (S→C 7) and {@code SKIN_DELTA} (S→C 8) carry
 * (ui §2.9.1), and what a campus saves as {@code Skn}. Every message starts with a version byte, so a later version can
 * add fields after the ones here and still read these.
 * <p>
 * An entry is about 75 bytes (style §3.9.2 counts 45 without the lamp box and the shape's name; the boxes go as shorts
 * from the controller); a city of twenty-one buildings sends about 1.6 KB once.
 */
public final class SkinCodec {

    /** The bytes' version. */
    public static final int VERSION = 1;
    private static final int TABLE = 1, DELTA = 2, PUT = 0, REMOVE = 1;

    private SkinCodec() {}

    /**
     * A whole table, or the whole tables of some campuses: {@code reset} drops everything the client had (a login, a
     * change of dimension), else each listed campus's entries replace what the client had of it.
     */
    public static final class Table {

        public final int dim;
        public final boolean reset;
        public final long[] campuses;
        public final List<SkinEntry> entries;

        public Table(int dim, boolean reset, long[] campuses, List<SkinEntry> entries) {
            this.dim = dim;
            this.reset = reset;
            this.campuses = campuses.clone();
            this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
        }
    }

    /** One changed entry: put in place of its building's, or (entry null) the building of {@code campus, id} gone. */
    public static final class Delta {

        public final int dim;
        public final SkinEntry entry;
        public final long campus, id;

        private Delta(int dim, SkinEntry entry, long campus, long id) {
            this.dim = dim;
            this.entry = entry;
            this.campus = campus;
            this.id = id;
        }

        public static Delta put(int dim, SkinEntry e) {
            return new Delta(dim, e, e.campus, e.id());
        }

        public static Delta remove(int dim, long campus, long id) {
            return new Delta(dim, null, campus, id);
        }
    }

    public static byte[] write(Table t) {
        return bytes(out -> {
            out.writeByte(VERSION);
            out.writeByte(TABLE);
            out.writeInt(t.dim);
            out.writeBoolean(t.reset);
            out.writeShort(t.campuses.length);
            for (long c : t.campuses) out.writeLong(c);
            writeEntries(out, t.entries);
        });
    }

    public static byte[] write(Delta d) {
        return bytes(out -> {
            out.writeByte(VERSION);
            out.writeByte(DELTA);
            out.writeInt(d.dim);
            if (d.entry != null) {
                out.writeByte(PUT);
                writeEntry(out, d.entry);
            } else {
                out.writeByte(REMOVE);
                out.writeLong(d.campus);
                out.writeLong(d.id);
            }
        });
    }

    public static Table readTable(byte[] b) throws IOException {
        DataInput in = start(b, TABLE);
        int dim = in.readInt();
        boolean reset = in.readBoolean();
        long[] campuses = new long[in.readUnsignedShort()];
        for (int i = 0; i < campuses.length; i++) campuses[i] = in.readLong();
        return new Table(dim, reset, campuses, readEntries(in));
    }

    public static Delta readDelta(byte[] b) throws IOException {
        DataInput in = start(b, DELTA);
        int dim = in.readInt();
        int op = in.readUnsignedByte();
        if (op == PUT) return Delta.put(dim, readEntry(in));
        if (op == REMOVE) return Delta.remove(dim, in.readLong(), in.readLong());
        throw new IOException("unknown delta " + op);
    }

    /** A list of entries on its own (the campus's {@code Skn}). */
    public static byte[] writeList(List<SkinEntry> entries) {
        return bytes(out -> {
            out.writeByte(VERSION);
            writeEntries(out, entries);
        });
    }

    public static List<SkinEntry> readList(byte[] b) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(b));
        int v = in.readUnsignedByte();
        if (v != VERSION) throw new IOException("box table version " + v + " is not " + VERSION);
        return readEntries(in);
    }

    // ---- entries

    static void writeEntries(DataOutput out, List<SkinEntry> entries) throws IOException {
        out.writeShort(entries.size());
        for (SkinEntry e : entries) writeEntry(out, e);
    }

    static List<SkinEntry> readEntries(DataInput in) throws IOException {
        int n = in.readUnsignedShort();
        List<SkinEntry> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(readEntry(in));
        return out;
    }

    public static void writeEntry(DataOutput out, SkinEntry e) throws IOException {
        out.writeLong(e.campus);
        for (int v : e.ctrl) out.writeInt(v);
        // the boxes from the controller, a short each: a building lies within 127 blocks of its nexus (FxCodec)
        writeBox(out, e.box, e.ctrl);
        out.writeBoolean(e.lamp != null);
        if (e.lamp != null) writeBox(out, e.lamp, e.ctrl);
        out.writeShort(e.row);
        out.writeUTF(e.shape);
        out.writeByte(e.facing);
        out.writeByte(e.state.code);
        out.writeBoolean(e.powered);
        out.writeLong(e.formedAt);
        out.writeInt(e.accent);
        out.writeByte(e.variant);
        out.writeByte(e.extra.length);
        out.write(e.extra);
    }

    public static SkinEntry readEntry(DataInput in) throws IOException {
        SkinEntry.Builder b = SkinEntry.builder();
        b.campus(in.readLong());
        int[] c = ints(in, 3);
        b.ctrl(c[0], c[1], c[2]);
        b.box(readBox(in, c));
        if (in.readBoolean()) b.lamp(readBox(in, c));
        int row = in.readUnsignedShort();
        if (SkinKeys.key(row) == null) throw new IOException("no building row " + row);
        b.row(row);
        b.shape(in.readUTF());
        b.facing(in.readByte());
        b.state(SkinState.of(in.readUnsignedByte()));
        b.powered(in.readBoolean());
        b.formedAt(in.readLong());
        b.accent(in.readInt());
        b.variant(in.readUnsignedByte());
        int n = in.readUnsignedByte();
        if (n > SkinEntry.EXTRA_MAX) throw new IOException("extra of " + n + " bytes");
        byte[] extra = new byte[n];
        in.readFully(extra);
        return b.extra(extra)
            .build();
    }

    private static void writeBox(DataOutput out, int[] box, int[] ctrl) throws IOException {
        for (int i = 0; i < 6; i++) {
            int d = box[i] - ctrl[i % 3];
            if (d != (short) d)
                throw new IllegalArgumentException("a box reaches " + d + " blocks from its controller");
            out.writeShort(d);
        }
    }

    private static int[] readBox(DataInput in, int[] ctrl) throws IOException {
        int[] out = new int[6];
        for (int i = 0; i < 6; i++) out[i] = ctrl[i % 3] + in.readShort();
        return out;
    }

    private static int[] ints(DataInput in, int n) throws IOException {
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = in.readInt();
        return out;
    }

    private static DataInput start(byte[] b, int kind) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(b));
        int v = in.readUnsignedByte();
        if (v != VERSION) throw new IOException("box table version " + v + " is not " + VERSION);
        int k = in.readUnsignedByte();
        if (k != kind) throw new IOException("box table message " + k + " where " + kind + " was due");
        return in;
    }

    private interface Writes {

        void to(DataOutput out) throws IOException;
    }

    private static byte[] bytes(Writes w) {
        ByteArrayOutputStream b = new ByteArrayOutputStream(128);
        try (DataOutputStream out = new DataOutputStream(b)) {
            w.to(out);
        } catch (IOException e) {
            // a byte array does not fail
            throw new UncheckedIOException(e);
        }
        return b.toByteArray();
    }
}
