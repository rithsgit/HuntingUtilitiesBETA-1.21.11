package com.example.addon.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Reads and writes Xaero's Minimap / World Map waypoint files.
 *
 * File layout (relative to .minecraft/):
 *   XaeroWorldMap/<server>/waypoints/<dim>.txt   ← World Map (preferred)
 *   XaerosMinimap/mw$default/<server>/<dim>/mw$default.txt  ← Minimap fallback
 *
 * Waypoint line format:
 *   waypoint:<name>:<initials>:<x>:<y>:<z>:<color>:<disabled>:<type>:<set>:<yaw>:<visibility>
 *
 * Example:
 *   waypoint:My Portal:MP:100:64:-200:6:false:0:gui.xaero_default:0.0:false
 */
public class XaerosWaypointHelper {

    // ── Xaero color indices ───────────────────────────────────────────────────
    public static final int COLOR_RED    = 0;
    public static final int COLOR_ORANGE = 1;
    public static final int COLOR_YELLOW = 2;
    public static final int COLOR_GREEN  = 3;
    public static final int COLOR_CYAN   = 4;
    public static final int COLOR_BLUE   = 5;
    public static final int COLOR_PURPLE = 6;   // Nether portals
    public static final int COLOR_PINK   = 7;
    public static final int COLOR_LIME   = 11;  // End portals
    public static final int COLOR_GRAY   = 14;  // Removed portals

    /** Tag embedded in waypoint names so we can identify our own entries. */
    public static final String PORTAL_TAG      = "[PT]";
    public static final String REMOVED_TAG     = "[PT-REMOVED]";
    private static final String WAYPOINT_SET   = "gui.xaero_default";

    // ── Dimension id → Xaero folder/filename stem ────────────────────────────
    private static final Map<String, String> DIM_FOLDER = Map.of(
        "minecraft:overworld",  "dim%0",
        "minecraft:the_nether", "dim%-1",
        "minecraft:the_end",    "dim%1"
    );

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Writes a waypoint to the Xaero file for the given dimension.
     * Skips silently if the Xaero directory is not found.
     * Deduplicates: will not write if an existing waypoint is within {@code dedupRadius} blocks (XZ).
     *
     * @return true if the waypoint was actually written.
     */
    public static boolean addWaypoint(BlockPos pos, String name, String dimensionId,
                                      int colorIndex, int dedupRadius) {
        Path file = resolveWaypointFile(dimensionId);
        if (file == null) return false;

        try {
            if (dedupRadius > 0 && waypointExistsNear(file, pos, dedupRadius)) return false;

            String line = formatLine(name, pos, colorIndex);
            Files.createDirectories(file.getParent());
            try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                w.write(line);
                w.newLine();
            }
            return true;
        } catch (IOException e) {
            System.err.println("[XaerosWaypointHelper] Write failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Loads all waypoints from the file for the given dimension whose names
     * contain {@code PORTAL_TAG} (written by PortalTracker).
     * Returns an empty list if the file is absent or Xaero is not installed.
     */
    public static List<WaypointEntry> loadPortalWaypoints(String dimensionId) {
        Path file = resolveWaypointFile(dimensionId);
        if (file == null || !Files.exists(file)) return Collections.emptyList();

        List<WaypointEntry> result = new ArrayList<>();
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.startsWith("waypoint:")) continue;
                WaypointEntry e = WaypointEntry.parse(line);
                if (e != null && e.name.contains(PORTAL_TAG)) result.add(e);
            }
        } catch (IOException e) {
            System.err.println("[XaerosWaypointHelper] Read failed: " + e.getMessage());
        }
        return result;
    }

    /**
     * Replaces an existing waypoint line (matched by name + position) with a
     * new line. Used to convert a [PT] entry to a [PT-REMOVED] entry in-place.
     *
     * @return true if a replacement was made.
     */
    public static boolean replaceWaypoint(String dimensionId, WaypointEntry original,
                                          String newName, int newColor) {
        Path file = resolveWaypointFile(dimensionId);
        if (file == null || !Files.exists(file)) return false;

        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            String oldLine = original.rawLine;
            String newLine = formatLine(newName, new BlockPos(original.x, original.y, original.z), newColor);

            boolean found = false;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).equals(oldLine)) {
                    lines.set(i, newLine);
                    found = true;
                    break;
                }
            }
            if (!found) return false;

            Files.write(file, lines, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return true;
        } catch (IOException e) {
            System.err.println("[XaerosWaypointHelper] Replace failed: " + e.getMessage());
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    public static boolean waypointExistsNear(Path file, BlockPos pos, int radius) {
        if (!Files.exists(file)) return false;
        int rSq = radius * radius;
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.startsWith("waypoint:")) continue;
                WaypointEntry e = WaypointEntry.parse(line);
                if (e == null) continue;
                int dx = e.x - pos.getX(), dz = e.z - pos.getZ();
                if (dx * dx + dz * dz <= rSq) return true;
            }
        } catch (IOException ignored) {}
        return false;
    }

    private static String formatLine(String name, BlockPos pos, int color) {
        String initials = buildInitials(name);
        return String.format("waypoint:%s:%s:%d:%d:%d:%d:false:0:%s:0.0:false",
            name, initials, pos.getX(), pos.getY(), pos.getZ(), color, WAYPOINT_SET);
    }

    private static String buildInitials(String name) {
        StringBuilder sb = new StringBuilder();
        for (String word : name.split("\\s+")) {
            if (!word.isEmpty() && Character.isLetter(word.charAt(0)))
                sb.append(Character.toUpperCase(word.charAt(0)));
            if (sb.length() >= 3) break;
        }
        return sb.length() > 0 ? sb.toString() : "P";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Path resolution
    // ─────────────────────────────────────────────────────────────────────────

    /** Returns the waypoint .txt path for the given dimension, or null if not found. */
    public static Path resolveWaypointFile(String dimensionId) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return null;

        String server  = getServerFolderName(mc);
        String dimFile = DIM_FOLDER.getOrDefault(dimensionId, "dim%0");
        Path   base    = mc.runDirectory.toPath();

        // 1. Xaero's World Map
        Path worldMap = base.resolve("XaeroWorldMap").resolve(server)
                           .resolve("waypoints").resolve(dimFile + ".txt");
        if (Files.exists(worldMap.getParent())) return worldMap;

        // 2. Xaero's Minimap
        Path minimap = base.resolve("XaerosMinimap").resolve("mw$default")
                          .resolve(server).resolve(dimFile).resolve("mw$default.txt");
        if (Files.exists(minimap.getParent())) return minimap;

        return null; // Xaero not present / not yet initialised
    }

    private static String getServerFolderName(MinecraftClient mc) {
        if (mc.isIntegratedServerRunning() && mc.getServer() != null)
            return "Singleplayer_" + mc.getServer().getSaveProperties().getLevelName();
        if (mc.getCurrentServerEntry() != null)
            return mc.getCurrentServerEntry().address.replace(":", "_");
        return "Unknown";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WaypointEntry
    // ─────────────────────────────────────────────────────────────────────────

    public static class WaypointEntry {
        public final String rawLine;
        public final String name;
        public final int    x, y, z;
        public final int    color;

        public WaypointEntry(String rawLine, String name, int x, int y, int z, int color) {
            this.rawLine = rawLine;
            this.name    = name;
            this.x = x; this.y = y; this.z = z;
            this.color   = color;
        }

        public BlockPos toBlockPos() { return new BlockPos(x, y, z); }

        /** Returns null on malformed input. */
        public static WaypointEntry parse(String line) {
            try {
                // waypoint:<name>:<init>:<x>:<y>:<z>:<color>:<disabled>:<type>:<set>:<yaw>:<vis>
                String[] p = line.split(":", -1);
                if (p.length < 7) return null;
                return new WaypointEntry(line, p[1],
                    Integer.parseInt(p[3]), Integer.parseInt(p[4]), Integer.parseInt(p[5]),
                    Integer.parseInt(p[6]));
            } catch (Exception e) {
                return null;
            }
        }
    }
}