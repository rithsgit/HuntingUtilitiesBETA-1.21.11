package com.example.addon.hud;

import com.example.addon.HuntingUtilities;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class PositionHud extends HudElement {

    public static final HudElementInfo<PositionHud> INFO = new HudElementInfo<>(
        HuntingUtilities.HUD_GROUP,
        "position",
        "Displays your current coordinates and their Nether equivalents.",
        PositionHud::new
    );

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<SettingColor> labelColor = sgGeneral.add(new ColorSetting.Builder()
        .name("label-color")
        .defaultValue(new SettingColor(170, 170, 170, 255))
        .build()
    );

    private final Setting<SettingColor> valueColor = sgGeneral.add(new ColorSetting.Builder()
        .name("value-color")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );

    private final Setting<SettingColor> separatorColor = sgGeneral.add(new ColorSetting.Builder()
        .name("separator-color")
        .description("Color of the | separators.")
        .defaultValue(new SettingColor(100, 100, 100, 255))
        .build()
    );

    private final Setting<SettingColor> netherLabelColor = sgGeneral.add(new ColorSetting.Builder()
        .name("nether-label-color")
        .description("Color of the Nether coordinate labels.")
        .defaultValue(new SettingColor(200, 80, 80, 255))
        .build()
    );

    private final Setting<Boolean> showNether = sgGeneral.add(new BoolSetting.Builder()
        .name("show-nether-coords")
        .description("Show the Nether equivalent on a second line.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showBackground = sgGeneral.add(new BoolSetting.Builder()
        .name("background")
        .description("Show a per-line background highlight.")
        .defaultValue(false)
        .build()
    );

    private final Setting<SettingColor> backgroundColor = sgGeneral.add(new ColorSetting.Builder()
        .name("background-color")
        .defaultValue(new SettingColor(0, 0, 0, 80))
        .visible(showBackground::get)
        .build()
    );

    public PositionHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        if (mc.player == null) { setSize(0, 0); return; }

        double padH       = 4;
        double padV       = 2;
        double rowGap     = 2;
        double lineHeight = renderer.textHeight(false, 1);
        double sepW       = renderer.textWidth(" | ", false, 1);

        BlockPos pos = mc.player.getBlockPos();
        int bx = pos.getX(), by = pos.getY(), bz = pos.getZ();

        // ── Determine Nether coords ───────────────────────────────────────────
        // If in Overworld → divide by 8 for Nether equivalent
        // If in Nether    → multiply by 8 for Overworld equivalent
        boolean inNether = mc.world != null
            && mc.world.getRegistryKey() == World.NETHER;
        boolean inEnd = mc.world != null
            && mc.world.getRegistryKey() == World.END;

        int nx, nz;
        String netherLineLabel;
        if (inNether) {
            nx = bx * 8; nz = bz * 8;
            netherLineLabel = "OW: ";
        } else {
            nx = bx / 8; nz = bz / 8;
            netherLineLabel = "Nether: ";
        }

        // ── Build line 1: current coords ──────────────────────────────────────
        // Format: X: 100 | Y: 64 | Z: -200
        String xLabel = "X: ", xVal = String.valueOf(bx);
        String yLabel = "Y: ", yVal = String.valueOf(by);
        String zLabel = "Z: ", zVal = String.valueOf(bz);

        double line1W = renderer.textWidth(xLabel, false, 1) + renderer.textWidth(xVal, false, 1)
                      + sepW
                      + renderer.textWidth(yLabel, false, 1) + renderer.textWidth(yVal, false, 1)
                      + sepW
                      + renderer.textWidth(zLabel, false, 1) + renderer.textWidth(zVal, false, 1);

        // ── Build line 2: nether/overworld coords ─────────────────────────────
        // Format: Nether: X: 12 | Z: -25   (Y not shown — same level)
        String nxLabel = "X: ", nxVal = String.valueOf(nx);
        String nzLabel = "Z: ", nzVal = String.valueOf(nz);

        double line2TextW = renderer.textWidth(netherLineLabel, false, 1)
                          + renderer.textWidth(nxLabel, false, 1) + renderer.textWidth(nxVal, false, 1)
                          + sepW
                          + renderer.textWidth(nzLabel, false, 1) + renderer.textWidth(nzVal, false, 1);

        boolean hasLine2 = showNether.get() && !inEnd;

        double maxW  = hasLine2 ? Math.max(line1W, line2TextW) : line1W;
        double totalW = maxW + padH * 2;
        int lineCount = hasLine2 ? 2 : 1;
        double totalH = lineCount * lineHeight + (lineCount - 1) * rowGap + padV * 2;

        // ── Draw line 1 ───────────────────────────────────────────────────────
        double line1BoxW = line1W + padH * 2;
        double rowY1 = y + padV;
        if (showBackground.get())
            renderer.quad(x, rowY1 - 1, line1BoxW, lineHeight + 2, backgroundColor.get());

        double cx = x + padH;
        renderer.text(xLabel, cx, rowY1, labelColor.get(),     false, 1); cx += renderer.textWidth(xLabel, false, 1);
        renderer.text(xVal,   cx, rowY1, valueColor.get(),     false, 1); cx += renderer.textWidth(xVal,   false, 1);
        renderer.text(" | ",  cx, rowY1, separatorColor.get(), false, 1); cx += sepW;
        renderer.text(yLabel, cx, rowY1, labelColor.get(),     false, 1); cx += renderer.textWidth(yLabel, false, 1);
        renderer.text(yVal,   cx, rowY1, valueColor.get(),     false, 1); cx += renderer.textWidth(yVal,   false, 1);
        renderer.text(" | ",  cx, rowY1, separatorColor.get(), false, 1); cx += sepW;
        renderer.text(zLabel, cx, rowY1, labelColor.get(),     false, 1); cx += renderer.textWidth(zLabel, false, 1);
        renderer.text(zVal,   cx, rowY1, valueColor.get(),     false, 1);

        // ── Draw line 2 ───────────────────────────────────────────────────────
        if (hasLine2) {
            double line2BoxW = line2TextW + padH * 2;
            double rowY2 = y + padV + lineHeight + rowGap;
            if (showBackground.get())
                renderer.quad(x, rowY2 - 1, line2BoxW, lineHeight + 2, backgroundColor.get());

            cx = x + padH;
            renderer.text(netherLineLabel, cx, rowY2, netherLabelColor.get(), false, 1); cx += renderer.textWidth(netherLineLabel, false, 1);
            renderer.text(nxLabel, cx, rowY2, labelColor.get(),     false, 1); cx += renderer.textWidth(nxLabel, false, 1);
            renderer.text(nxVal,   cx, rowY2, valueColor.get(),     false, 1); cx += renderer.textWidth(nxVal,   false, 1);
            renderer.text(" | ",   cx, rowY2, separatorColor.get(), false, 1); cx += sepW;
            renderer.text(nzLabel, cx, rowY2, labelColor.get(),     false, 1); cx += renderer.textWidth(nzLabel, false, 1);
            renderer.text(nzVal,   cx, rowY2, valueColor.get(),     false, 1);
        }

        setSize(totalW, totalH);
    }
}