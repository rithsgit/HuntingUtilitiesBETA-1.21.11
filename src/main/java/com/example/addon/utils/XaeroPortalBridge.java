package com.example.addon.utils;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Soft-dependency bridge between PortalTracker and Xaero's mods.
 *
 * Does NOT import any Xaero classes — safe to load whether or not Xaero
 * is installed. Uses polling via PortalTracker's existing tick event rather
 * than Fabric API's ClientChunkEvents, so no extra dependencies are needed.
 *
 * Supports two watch modes:
 *   - watchPosition(pos, callback)      — exact BlockPos, Y known (from [PT] waypoints)
 *   - watchColumn(x, z, dim, callback)  — X/Z only, Y unknown (from XaeroPlus DB)
 *     In column mode the bridge scans the full world height to find a portal block.
 *
 * Each tick, watched positions whose chunk is loaded are checked once and resolved.
 *
 * Usage:
 *   XaeroPortalBridge.tick();
 *   XaeroPortalBridge.watchPosition(pos, callback);
 *   XaeroPortalBridge.watchColumn(x, z, callback);
 *   XaeroPortalBridge.unwatch(pos);
 *   XaeroPortalBridge.clearWatched();
 */
public class XaeroPortalBridge {

    public static final boolean WORLD_MAP_PRESENT =
        FabricLoader.getInstance().isModLoaded("xaeroworldmap");
    public static final boolean MINIMAP_PRESENT =
        FabricLoader.getInstance().isModLoaded("xaerominimapfair")
        || FabricLoader.getInstance().isModLoaded("xaerominimap");
    public static final boolean XAERO_PRESENT    = WORLD_MAP_PRESENT || MINIMAP_PRESENT;
    public static final boolean XAERO_PLUS_PRESENT =
        FabricLoader.getInstance().isModLoaded("xaeroplus");

    // ── Exact-position watches ([PT] waypoints) ───────────────────────────────

    /**
     * Key   = exact BlockPos (Y known).
     * Value = callback(pos, stillExists).
     */
    private static final Map<BlockPos, BiConsumer<BlockPos, Boolean>> watched
        = new ConcurrentHashMap<>();

    // ── Column watches (XaeroPlus DB — Y unknown) ─────────────────────────────

    /**
     * Key   = column key "x,z" string.
     * Value = column entry holding the callback and coordinates.
     */
    private static final Map<String, ColumnWatch> watchedColumns
        = new ConcurrentHashMap<>();

    /** All positions/columns already resolved this session — never re-fired. */
    private static final Set<String> checked = ConcurrentHashMap.newKeySet();

    // ─────────────────────────────────────────────────────────────────────────
    // API — exact position
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Watch an exact BlockPos (Y known). Callback fires once when the chunk loads.
     * stillExists=true → portal present. stillExists=false → portal gone.
     */
    public static void watchPosition(BlockPos pos, BiConsumer<BlockPos, Boolean> onResolved) {
        String key = posKey(pos);
        if (checked.contains(key)) return;
        watched.put(pos.toImmutable(), onResolved);
    }

    /** Remove an exact-position watch without firing its callback. */
    public static void unwatch(BlockPos pos) {
        watched.remove(pos);
        checked.add(posKey(pos));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // API — column (Y unknown, XaeroPlus)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Watch an X/Z column when Y is unknown (XaeroPlus DB entries).
     * The bridge scans the full world height to find a portal block.
     * Callback: (foundPos, found) where foundPos has the actual Y if found,
     * or Y=64 placeholder if not found.
     */
    public static void watchColumn(int x, int z, BiConsumer<BlockPos, Boolean> onResolved) {
        String key = colKey(x, z);
        if (checked.contains(key)) return;
        watchedColumns.put(key, new ColumnWatch(x, z, onResolved));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // API — general
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Clear all watches and the checked set.
     * Call on dimension change or module deactivate.
     */
    public static void clearWatched() {
        watched.clear();
        watchedColumns.clear();
        checked.clear();
    }

    /** Total number of positions/columns currently being watched. */
    public static int watchedCount() {
        return watched.size() + watchedColumns.size();
    }

    /**
     * Call this every tick from PortalTracker's onTick().
     * Resolves any watched positions/columns whose chunk is now loaded.
     */
    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        // ── Exact positions ───────────────────────────────────────────────────
        if (!watched.isEmpty()) {
            watched.entrySet().removeIf(entry -> {
                BlockPos pos = entry.getKey();
                String   key = posKey(pos);
                if (checked.contains(key)) return true;
                if (!isChunkLoaded(mc, pos)) return false;

                checked.add(key);
                boolean present = isPortalBlock(mc, pos);
                // Also check a few blocks up in case Y drifted.
                if (!present) {
                    for (int dy = 1; dy <= 3; dy++) {
                        if (isPortalBlock(mc, pos.up(dy))) { present = true; break; }
                    }
                }
                entry.getValue().accept(pos, present);
                return true;
            });
        }

        // ── Columns (Y unknown) ───────────────────────────────────────────────
        if (!watchedColumns.isEmpty()) {
            watchedColumns.entrySet().removeIf(entry -> {
                ColumnWatch col = entry.getValue();
                String      key = entry.getKey();
                if (checked.contains(key)) return true;

                BlockPos probe = new BlockPos(col.x, 64, col.z);
                if (!isChunkLoaded(mc, probe)) return false;

                checked.add(key);

                // Scan the full world height for a portal block.
                int     worldBottom = mc.world.getBottomY();
                int     worldTop    = mc.world.getBottomY() + mc.world.getHeight();
                BlockPos found      = null;

                for (int y = worldBottom; y <= worldTop; y++) {
                    BlockPos candidate = new BlockPos(col.x, y, col.z);
                    if (isPortalBlock(mc, candidate)) {
                        found = candidate;
                        break;
                    }
                }

                if (found != null) {
                    col.callback.accept(found, true);
                } else {
                    col.callback.accept(probe, false); // Y=64 placeholder
                }
                return true;
            });
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal
    // ─────────────────────────────────────────────────────────────────────────

    private static boolean isChunkLoaded(MinecraftClient mc, BlockPos pos) {
        return mc.world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private static boolean isPortalBlock(MinecraftClient mc, BlockPos pos) {
        var block = mc.world.getBlockState(pos).getBlock();
        return block == Blocks.NETHER_PORTAL
            || block == Blocks.END_PORTAL
            || block == Blocks.END_GATEWAY;
    }

    private static String posKey(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static String colKey(int x, int z) {
        return x + ",?," + z;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inner types
    // ─────────────────────────────────────────────────────────────────────────

    private static class ColumnWatch {
        final int x, z;
        final BiConsumer<BlockPos, Boolean> callback;

        ColumnWatch(int x, int z, BiConsumer<BlockPos, Boolean> callback) {
            this.x = x; this.z = z; this.callback = callback;
        }
    }
}