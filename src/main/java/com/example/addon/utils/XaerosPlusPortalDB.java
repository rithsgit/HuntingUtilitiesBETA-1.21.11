package com.example.addon.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Lightweight pure-Java reader for XaeroPlus's SQLite portal database.
 * No external dependencies — reads the SQLite B-tree format directly.
 *
 * Only supports the specific schema used by XaerosPlusPortals.db:
 *   Tables: "minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"
 *   Columns: x INTEGER, z INTEGER, foundTime INTEGER
 *
 * Page types handled:
 *   0x0D = leaf table b-tree page
 *   0x05 = interior table b-tree page
 *
 * Root page numbers are read from sqlite_master (page 1).
 */
public class XaerosPlusPortalDB {

    private static final String DB_FILENAME = "XaerosPlusPortals.db";

    private static final int PAGE_INTERIOR_TABLE = 0x05;
    private static final int PAGE_LEAF_TABLE     = 0x0D;

    // ─────────────────────────────────────────────────────────────────────────
    // Public types
    // ─────────────────────────────────────────────────────────────────────────

    public static class PortalEntry {
        public final int    x;
        public final int    z;
        public final long   foundTime;
        public final String dimensionId;

        PortalEntry(int x, int z, long foundTime, String dimensionId) {
            this.x = x; this.z = z;
            this.foundTime = foundTime;
            this.dimensionId = dimensionId;
        }

        public BlockPos toBlockPos() { return new BlockPos(x, 64, z); }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    public static boolean isAvailable() { return resolveDbFile() != null; }

    /**
     * Reads up to {@code limit} portal entries for the given dimension.
     * Returns an empty list if the file is absent or the table doesn't exist.
     * Pass limit=0 for no cap.
     */
    public static List<PortalEntry> loadForDimension(String dimensionId, int limit) {
        List<PortalEntry> result = new ArrayList<>();
        File dbFile = resolveDbFile();
        if (dbFile == null) return result;

        try (RandomAccessFile raf = new RandomAccessFile(dbFile, "r")) {
            SQLiteReader reader = new SQLiteReader(raf);
            if (!reader.readHeader()) return result;

            int rootPage = reader.findTableRootPage(dimensionId);
            if (rootPage < 1) return result;

            reader.traverseTable(rootPage, limit,
                (x, z, foundTime) -> result.add(new PortalEntry(x, z, foundTime, dimensionId)));
        } catch (IOException e) {
            System.err.println("[XaerosPlusPortalDB] Read error: " + e.getMessage());
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal
    // ─────────────────────────────────────────────────────────────────────────

    private static File resolveDbFile() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return null;
        Path path = mc.runDirectory.toPath().resolve(DB_FILENAME);
        File file = path.toFile();
        return file.exists() ? file : null;
    }

    @FunctionalInterface
    private interface RowConsumer { void accept(int x, int z, long foundTime); }

    // ─────────────────────────────────────────────────────────────────────────
    // SQLiteReader
    // ─────────────────────────────────────────────────────────────────────────

    private static class SQLiteReader {

        private final RandomAccessFile raf;
        private int pageSize;

        SQLiteReader(RandomAccessFile raf) { this.raf = raf; }

        boolean readHeader() throws IOException {
            if (raf.length() < 100) return false;
            raf.seek(0);
            byte[] magic = new byte[16];
            raf.readFully(magic);
            if (!new String(magic, "UTF-8").startsWith("SQLite format 3")) return false;
            byte[] hdr = new byte[84];
            raf.readFully(hdr);
            int raw = u16(hdr, 0); // offset 16 in file
            pageSize = (raw == 1) ? 65536 : raw;
            return pageSize >= 512;
        }

        private byte[] readPage(int pageNum) throws IOException {
            long offset = (long)(pageNum - 1) * pageSize;
            if (offset + pageSize > raf.length()) return null;
            raf.seek(offset);
            byte[] page = new byte[pageSize];
            raf.readFully(page);
            return page;
        }

        // ── sqlite_master scan ────────────────────────────────────────────────

        int findTableRootPage(String tableName) throws IOException {
            byte[] page = readPage(1);
            if (page == null || page[100] != PAGE_LEAF_TABLE) return -1;

            int ncells = u16(page, 103);
            for (int i = 0; i < ncells; i++) {
                int cellOff = u16(page, 108 + i * 2);
                int[] pos   = {cellOff};

                long payloadSize = varint(page, pos);
                varint(page, pos); // rowid

                byte[] payload = inlinePayload(page, pos[0], (int) payloadSize);
                pos[0] = 0;

                long hdrLen = varint(payload, pos);
                List<Long> types = new ArrayList<>();
                while (pos[0] < hdrLen) types.add(varint(payload, pos));
                if (types.size() < 4) continue;

                int vpos = (int) hdrLen;
                // Read type (text), name (text), tbl_name (text)
                String[] texts = new String[3];
                for (int col = 0; col < 3; col++) {
                    long st = types.get(col);
                    if (st >= 13 && st % 2 == 1) {
                        int len = (int)((st - 13) / 2);
                        texts[col] = new String(payload, vpos, len, java.nio.charset.StandardCharsets.UTF_8);
                        vpos += len;
                    } else {
                        vpos += stLen(st);
                    }
                }
                // rootpage integer
                long rootpage = readInt(payload, vpos, types.get(3));

                if ("table".equals(texts[0]) && tableName.equals(texts[1])) {
                    return (int) rootpage;
                }
            }
            return -1;
        }

        // ── B-tree traversal ──────────────────────────────────────────────────

        void traverseTable(int rootPageNum, int limit, RowConsumer consumer) throws IOException {
            int[] count = {0};
            Deque<Integer> stack = new ArrayDeque<>();
            stack.push(rootPageNum);

            outer:
            while (!stack.isEmpty()) {
                int pnum = stack.pop();
                byte[] page = readPage(pnum);
                if (page == null) continue;
                int ptype = page[0] & 0xFF;

                if (ptype == PAGE_LEAF_TABLE) {
                    int ncells = u16(page, 3);
                    for (int i = 0; i < ncells; i++) {
                        if (limit > 0 && count[0] >= limit) break outer;
                        int cellOff = u16(page, 8 + i * 2);
                        int[] pos   = {cellOff};
                        long pSize  = varint(page, pos);
                        varint(page, pos); // rowid
                        byte[] payload = inlinePayload(page, pos[0], (int) pSize);
                        parseRow(payload, consumer);
                        count[0]++;
                    }

                } else if (ptype == PAGE_INTERIOR_TABLE) {
                    int ncells    = u16(page, 3);
                    int rightMost = u32(page, 8);
                    stack.push(rightMost);
                    for (int i = ncells - 1; i >= 0; i--) {
                        int cellOff   = u16(page, 12 + i * 2);
                        int leftChild = u32(page, cellOff);
                        stack.push(leftChild);
                    }
                }
            }
        }

        // ── Row parsing ───────────────────────────────────────────────────────

        private void parseRow(byte[] payload, RowConsumer consumer) {
            int[] pos    = {0};
            long hdrLen  = varint(payload, pos);
            List<Long> types = new ArrayList<>();
            while (pos[0] < hdrLen) types.add(varint(payload, pos));
            if (types.size() < 3) return;

            int vpos       = (int) hdrLen;
            long x         = readInt(payload, vpos, types.get(0)); vpos += stLen(types.get(0));
            long z         = readInt(payload, vpos, types.get(1)); vpos += stLen(types.get(1));
            long foundTime = readInt(payload, vpos, types.get(2));
            consumer.accept((int) x, (int) z, foundTime);
        }

        // ── Payload (inline only — XaeroPlus rows are always tiny) ───────────

        private byte[] inlinePayload(byte[] page, int offset, int size) throws IOException {
            int available = page.length - offset;
            if (size <= available) {
                byte[] p = new byte[size];
                System.arraycopy(page, offset, p, 0, size);
                return p;
            }
            // Overflow chain (shouldn't happen for 3-int rows, but handle it)
            byte[] result   = new byte[size];
            int inlineSize  = available - 4;
            System.arraycopy(page, offset, result, 0, inlineSize);
            int nextPage = u32(page, offset + inlineSize);
            int written  = inlineSize;
            while (nextPage != 0 && written < size) {
                byte[] op   = readPage(nextPage);
                if (op == null) break;
                nextPage    = u32(op, 0);
                int toCopy  = Math.min(size - written, pageSize - 4);
                System.arraycopy(op, 4, result, written, toCopy);
                written    += toCopy;
            }
            return result;
        }

        // ── Varint ────────────────────────────────────────────────────────────

        private static long varint(byte[] data, int[] pos) {
            long result = 0;
            for (int i = 0; i < 9; i++) {
                int b = data[pos[0] + i] & 0xFF;
                if (i < 8) {
                    result = (result << 7) | (b & 0x7F);
                    if ((b & 0x80) == 0) { pos[0] += i + 1; return result; }
                } else {
                    result = (result << 8) | b;
                    pos[0] += 9; return result;
                }
            }
            pos[0] += 9;
            return result;
        }

        // ── Serial type helpers ───────────────────────────────────────────────

        private static long readInt(byte[] d, int o, long st) {
            switch ((int) st) {
                case 0: return 0;
                case 1: return (byte)(d[o] & 0xFF);
                case 2: return (short)(((d[o]&0xFF)<<8)|(d[o+1]&0xFF));
                case 3: { int v=((d[o]&0xFF)<<16)|((d[o+1]&0xFF)<<8)|(d[o+2]&0xFF);
                          return v>=0x800000?v-0x1000000:v; }
                case 4: return (int)(((d[o]&0xFF)<<24)|((d[o+1]&0xFF)<<16)|((d[o+2]&0xFF)<<8)|(d[o+3]&0xFF));
                case 5: { long v=0; for(int i=0;i<6;i++) v=(v<<8)|(d[o+i]&0xFF);
                          return (v&0x800000000000L)!=0?v|0xFFFF000000000000L:v; }
                case 6: { long v=0; for(int i=0;i<8;i++) v=(v<<8)|(d[o+i]&0xFF); return v; }
                case 8: return 0;
                case 9: return 1;
                default: return 0;
            }
        }

        private static int stLen(long st) {
            if (st==0||st==8||st==9) return 0;
            if (st==1) return 1; if (st==2) return 2; if (st==3) return 3;
            if (st==4) return 4; if (st==5) return 6; if (st==6||st==7) return 8;
            if (st>=12&&st%2==0) return (int)((st-12)/2);
            if (st>=13&&st%2==1) return (int)((st-13)/2);
            return 0;
        }

        private static int u16(byte[] d, int o) { return ((d[o]&0xFF)<<8)|(d[o+1]&0xFF); }
        private static int u32(byte[] d, int o) {
            return ((d[o]&0xFF)<<24)|((d[o+1]&0xFF)<<16)|((d[o+2]&0xFF)<<8)|(d[o+3]&0xFF);
        }
    }
}