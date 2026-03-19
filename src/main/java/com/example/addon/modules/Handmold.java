package com.example.addon.modules;

import com.example.addon.HuntingUtilities;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

public class Handmold extends Module {

    // ═══════════════════════════════════════════════════════════════════════════
    // Setting Groups
    // ═══════════════════════════════════════════════════════════════════════════

    private final SettingGroup sgGeneral  = settings.getDefaultGroup();
    private final SettingGroup sgMainHand = settings.createGroup("Main Hand");
    private final SettingGroup sgOffHand  = settings.createGroup("Off Hand");

    // ═══════════════════════════════════════════════════════════════════════════
    // Settings — General
    // ═══════════════════════════════════════════════════════════════════════════

    private final Setting<Boolean> noHandBob = sgGeneral.add(new BoolSetting.Builder()
        .name("no-hand-bob")
        .description("Disables the hand bobbing movement while walking or running.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> hideEmptyMainhand = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-empty-mainhand")
        .description("Hides the main hand when it is not holding any item.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> hideOffhandCompletely = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-offhand-completely")
        .description("Hides the offhand completely, regardless of what it is holding.")
        .defaultValue(false)
        .build()
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Settings — Main Hand
    // ═══════════════════════════════════════════════════════════════════════════
    
    private final HandTransformSettings mainHandSettings;
    private final HandTransformSettings offHandSettings;

    // ═══════════════════════════════════════════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════════════════════════════════════════

    public Handmold() {
        super(HuntingUtilities.CATEGORY, "handmold",
            "Adjusts the position, scale, and rotation of each hand independently.");

        mainHandSettings = new HandTransformSettings(sgMainHand, "Main");
        offHandSettings  = new HandTransformSettings(sgOffHand, "Off");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Public API — read by mixins
    // ═══════════════════════════════════════════════════════════════════════════

    public double getMainX()     { return mainHandSettings.getX(); }
    public double getMainY()     { return mainHandSettings.getY(); }
    public double getMainZ()     { return mainHandSettings.getZ(); }
    public double getMainScale() { return mainHandSettings.getScale(); }
    public double getMainRotX()  { return mainHandSettings.getRotX(); }
    public double getMainRotY()  { return mainHandSettings.getRotY(); }
    public double getMainRotZ()  { return mainHandSettings.getRotZ(); }

    public double getOffX()      { return offHandSettings.getX(); }
    public double getOffY()      { return offHandSettings.getY(); }
    public double getOffZ()      { return offHandSettings.getZ(); }
    public double getOffScale()  { return offHandSettings.getScale(); }
    public double getOffRotX()   { return offHandSettings.getRotX(); }
    public double getOffRotY()   { return offHandSettings.getRotY(); }
    public double getOffRotZ()   { return offHandSettings.getRotZ(); }

    public boolean shouldDisableHandBob()        { return isActive() && noHandBob.get(); }
    public boolean shouldHideEmptyMainhand()     { return isActive() && hideEmptyMainhand.get(); }
    public boolean shouldHideOffhandCompletely() { return isActive() && hideOffhandCompletely.get(); }

    // ═══════════════════════════════════════════════════════════════════════════
    // Inner Class for Hand Settings
    // ═══════════════════════════════════════════════════════════════════════════

    private static class HandTransformSettings {
        public final Setting<Double> x;
        public final Setting<Double> y;
        public final Setting<Double> z;
        public final Setting<Double> scale;
        public final Setting<Double> rotX;
        public final Setting<Double> rotY;
        public final Setting<Double> rotZ;

        public HandTransformSettings(SettingGroup sg, String handName) {
            x = sg.add(new DoubleSetting.Builder()
                .name("x").description(handName + " hand horizontal offset.")
                .defaultValue(0.0).min(-2.0).max(2.0).sliderRange(-1.0, 1.0)
                .build()
            );
            y = sg.add(new DoubleSetting.Builder()
                .name("y").description(handName + " hand vertical offset.")
                .defaultValue(0.0).min(-2.0).max(2.0).sliderRange(-1.0, 1.0)
                .build()
            );
            z = sg.add(new DoubleSetting.Builder()
                .name("z").description(handName + " hand depth offset.")
                .defaultValue(0.0).min(-2.0).max(2.0).sliderRange(-1.0, 1.0)
                .build()
            );
            scale = sg.add(new DoubleSetting.Builder()
                .name("scale").description(handName + " hand scale multiplier.")
                .defaultValue(1.0).min(0.1).max(3.0).sliderRange(0.1, 2.0)
                .build()
            );
            rotX = sg.add(new DoubleSetting.Builder()
                .name("rot-x").description(handName + " hand rotation around the X axis (degrees).")
                .defaultValue(0.0).min(-180.0).max(180.0).sliderRange(-180.0, 180.0)
                .build()
            );
            rotY = sg.add(new DoubleSetting.Builder()
                .name("rot-y").description(handName + " hand rotation around the Y axis (degrees).")
                .defaultValue(0.0).min(-180.0).max(180.0).sliderRange(-180.0, 180.0)
                .build()
            );
            rotZ = sg.add(new DoubleSetting.Builder()
                .name("rot-z").description(handName + " hand rotation around the Z axis (degrees).")
                .defaultValue(0.0).min(-180.0).max(180.0).sliderRange(-180.0, 180.0)
                .build()
            );
        }

        public double getX()     { return x.get(); }
        public double getY()     { return y.get(); }
        public double getZ()     { return z.get(); }
        public double getScale() { return scale.get(); }
        public double getRotX()  { return rotX.get(); }
        public double getRotY()  { return rotY.get(); }
        public double getRotZ()  { return rotZ.get(); }
    }
}