package com.example.addon.modules;

import com.example.addon.HuntingUtilities;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.option.Perspective;


public class ThirdSight extends Module {

    /**
     * Selects the active camera feature. Shoulder can also be triggered by its
     * keybind regardless of which feature is currently selected here.
     * Zoom is always keybind-driven and is independent of this setting.
     */
    public enum CameraFeature { ThirdPerson, BirdsEye, Shoulder }
    public enum ShoulderSide  { Right, Left }

    private final SettingGroup sgGeneral  = settings.getDefaultGroup();
    private final SettingGroup sgShoulder = settings.createGroup("Shoulder");
    private final SettingGroup sgZoom     = settings.createGroup("Zoom");
    private final SettingGroup sgATW      = settings.createGroup("Around the World");

    // ── General ──────────────────────────────────────────────────────────────

    private final Setting<Keybind> noDistanceKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("no-distance-key")
        .description("Toggles a mode that disables camera distance modifications, allowing vanilla third person unless zooming.")
        .defaultValue(Keybind.none())
        .build()
    );

    public final Setting<CameraFeature> cameraFeature = sgGeneral.add(new EnumSetting.Builder<CameraFeature>()
        .name("camera-feature")
        .description("Active camera feature. Shoulder can also be triggered by its keybind even when not selected here. Zoom is always keybind-driven.")
        .defaultValue(CameraFeature.ThirdPerson)
        .build()
    );

    public final Setting<Double> distance = sgGeneral.add(new DoubleSetting.Builder()
        .name("distance")
        .description("Camera distance from the player. In BirdsEye mode this controls how far below.")
        .defaultValue(4.0)
        .min(1.0)
        .max(30.0)
        .sliderRange(1.0, 30.0)
        .build()
    );

    public final Setting<Boolean> freeLook = sgGeneral.add(new BoolSetting.Builder()
        .name("free-look")
        .description("Orbit the camera around the player without affecting movement direction. Disabled in BirdsEye mode.")
        .defaultValue(true)
        .visible(() -> cameraFeature.get() == CameraFeature.ThirdPerson
                    || cameraFeature.get() == CameraFeature.Shoulder)
        .build()
    );

    public final Setting<Double> sensitivity = sgGeneral.add(new DoubleSetting.Builder()
        .name("sensitivity")
        .description("Free-look mouse sensitivity.")
        .defaultValue(1.0)
        .min(1.0)
        .max(20.0)
        .sliderRange(1.0, 20.0)
        .visible(() -> (cameraFeature.get() == CameraFeature.ThirdPerson
                     || cameraFeature.get() == CameraFeature.Shoulder) && freeLook.get())
        .build()
    );

    public final Setting<Double> followSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("follow-speed")
        .description("How quickly the camera yaw catches up to the direction you're looking when free-look is off. 1.0 = instant.")
        .defaultValue(0.12)
        .min(0.01)
        .max(1.0)
        .sliderRange(0.02, 0.5)
        .visible(() -> !freeLook.get()
               && (cameraFeature.get() == CameraFeature.ThirdPerson
                || cameraFeature.get() == CameraFeature.Shoulder))
        .build()
    );


    public final Setting<ShoulderSide> shoulderSide = sgShoulder.add(new EnumSetting.Builder<ShoulderSide>()
        .name("side")
        .description("Which shoulder the camera sits behind.")
        .defaultValue(ShoulderSide.Right)
        .build()
    );

    public final Setting<Double> shoulderOffset = sgShoulder.add(new DoubleSetting.Builder()
        .name("offset")
        .description("How far left or right the camera is shifted, in blocks.")
        .defaultValue(0.75)
        .min(0.1)
        .max(3.0)
        .sliderRange(0.1, 2.0)
        .build()
    );

    public final Setting<Keybind> shoulderToggleKey = sgShoulder.add(new KeybindSetting.Builder()
        .name("toggle-key")
        .description("Press to flip between left and right shoulder. Also activates shoulder mode if another feature is selected.")
        .defaultValue(Keybind.none())
        .build()
    );

    public final Setting<Boolean> smoothTransitions = sgShoulder.add(new BoolSetting.Builder()
        .name("smooth-transitions")
        .description("Smoothly interpolate between camera positions.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Double> transitionSpeed = sgShoulder.add(new DoubleSetting.Builder()
        .name("transition-speed")
        .description("Speed of the smoothing.")
        .defaultValue(0.15)
        .min(0.01)
        .max(1.0)
        .sliderRange(0.05, 0.5)
        .visible(smoothTransitions::get)
        .build()
    );

    // ── Zoom ─────────────────────────────────────────────────────────────────

    public final Setting<Double> zoomDistance = sgZoom.add(new DoubleSetting.Builder()
        .name("zoom-distance")
        .description("Camera distance when zoomed in.")
        .defaultValue(2.0)
        .min(0.5)
        .max(30.0)
        .sliderRange(0.5, 10.0)
        .build()
    );

    public final Setting<Double> zoomFov = sgZoom.add(new DoubleSetting.Builder()
        .name("zoom-fov")
        .description("Field of View when zooming in First Person.")
        .defaultValue(30.0)
        .min(1.0)
        .max(110.0)
        .sliderRange(10.0, 110.0)
        .build()
    );

    public final Setting<Keybind> zoomKey = sgZoom.add(new KeybindSetting.Builder()
        .name("zoom-key")
        .description("Key to activate zoom. Also activates zoom mode if another feature is selected.")
        .defaultValue(Keybind.none())
        .build()
    );

    public final Setting<Boolean> zoomToggle = sgZoom.add(new BoolSetting.Builder()
        .name("toggle-mode")
        .description("If true, press to toggle zoom. If false, hold to zoom.")
        .defaultValue(false)
        .build()
    );

    // ── Around the World ──────────────────────────────────────────────────────

    private final Setting<Keybind> atwKey = sgATW.add(new KeybindSetting.Builder()
        .name("key")
        .description("Press to toggle the Around the World spin. Module must already be active.")
        .defaultValue(Keybind.none())
        .build()
    );

    public final Setting<Double> atwSpeed = sgATW.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Yaw degrees added per tick. 1 = lazy drift, 360 = full rotation per tick (extreme).")
        .defaultValue(6.0)
        .min(0.1)
        .max(360.0)
        .sliderRange(0.5, 72.0)
        .build()
    );

    private final Setting<Boolean> atwClockwise = sgATW.add(new BoolSetting.Builder()
        .name("clockwise")
        .description("Spin direction. True = clockwise (yaw increases), false = counter-clockwise.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> atwLockPitch = sgATW.add(new BoolSetting.Builder()
        .name("lock-pitch")
        .description("Keep the camera at a fixed pitch angle while spinning.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> atwPitch = sgATW.add(new DoubleSetting.Builder()
        .name("pitch")
        .description("Camera pitch to hold during the spin. 0 = level, positive = look down, negative = look up.")
        .defaultValue(15.0)
        .min(-89.9)
        .max(89.9)
        .sliderRange(-60.0, 60.0)
        .visible(atwLockPitch::get)
        .build()
    );

    // ── State ─────────────────────────────────────────────────────────────────

    // Free-look / BirdsEye camera angles
    public float cameraYaw   = 0f;
    public float cameraPitch = 0f;

    // Lateral offset read by ThirdSightCameraMixin
    public float  lateralOffset       = 0f;
    private float targetLateralOffset = 0f;

    private double  currentDistance         = 4.0;
    private boolean isZooming               = false;
    private boolean wasZoomKeyPressed       = false;
    private boolean noDistanceActive        = false;
    private boolean wasNoDistanceKeyPressed = false;
    private double  originalFov             = -1;
    private double  currentFov              = 0;

    // True when the shoulder keybind is overriding the selected CameraFeature
    private boolean shoulderKeyActive    = false;
    private boolean wasShoulderKeyPressed = false;

    private Perspective previousPerspective = null;
    private boolean     wasKeyPressed       = false;

    // ── Around the World state ────────────────────────────────────────────────

    private boolean atwActive        = false;
    private boolean wasAtwKeyPressed = false;
    // Accumulated yaw that wraps continuously — written into cameraYaw each render frame
    private float   atwCurrentYaw    = 0f;

    // ── Constructor ───────────────────────────────────────────────────────────

    public ThirdSight() {
        super(HuntingUtilities.CATEGORY, "third-sight",
            "Third-person camera with configurable distance, no block clipping, free look, BirdsEye mode, shoulder offset, and Around the World spin.");
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onActivate() {
        if (mc.player == null || mc.options == null) return;

        cameraYaw   = mc.player.getYaw();
        cameraPitch = Math.max(-89.9f, Math.min(89.9f, mc.player.getPitch()));

        previousPerspective = mc.options.getPerspective();
        if (previousPerspective == Perspective.FIRST_PERSON)
            mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);

        updateLateralOffset();
        if (smoothTransitions.get()) lateralOffset = 0f;

        currentDistance = distance.get();
        isZooming       = false;
        wasZoomKeyPressed       = false;
        wasShoulderKeyPressed   = false;
        shoulderKeyActive       = false;
        noDistanceActive        = false;
        wasNoDistanceKeyPressed = false;
        originalFov = -1;

        atwActive        = false;
        wasAtwKeyPressed = false;
        atwCurrentYaw    = cameraYaw;
    }

    @Override
    public void onDeactivate() {
        if (mc.options != null) {
            if (previousPerspective != null)
                mc.options.setPerspective(previousPerspective);
            if (originalFov != -1)
                mc.options.getFov().setValue((int) originalFov);
        }

        previousPerspective = null;
        lateralOffset = 0f;
        originalFov = -1;

        atwActive = false;
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.options == null) return;

        if (mc.currentScreen == null) {
            // No Distance toggle
            boolean noDistPressed = noDistanceKey.get().isPressed();
            if (noDistPressed && !wasNoDistanceKeyPressed) {
                noDistanceActive = !noDistanceActive;
                info("No Distance mode %s.", noDistanceActive ? "§aenabled" : "§cdisabled");
            }
            wasNoDistanceKeyPressed = noDistPressed;

            // ── Shoulder keybind ─────────────────────────────────────────────
            // The toggle-key flips shoulder side when shoulder is the active feature,
            // OR activates shoulder override mode when another feature is selected.
            boolean shoulderKeyPressed = shoulderToggleKey.get().isPressed();
            if (shoulderKeyPressed && !wasShoulderKeyPressed) {
                if (isShoulderActive()) {
                    // Already in shoulder mode — just flip the side
                    shoulderSide.set(shoulderSide.get() == ShoulderSide.Right
                        ? ShoulderSide.Left : ShoulderSide.Right);
                } else {
                    // Keybind override: activate shoulder regardless of selected feature
                    shoulderKeyActive = !shoulderKeyActive;
                    if (shoulderKeyActive) info("Shoulder override §aon§r.");
                    else                   info("Shoulder override §coff§r.");
                }
            }
            // Also handle legacy wasKeyPressed used for the side-flip guard
            wasKeyPressed        = shoulderKeyPressed;
            wasShoulderKeyPressed = shoulderKeyPressed;

            // ── Zoom keybind ─────────────────────────────────────────────────
            // Zoom is always keybind-driven and works in any feature mode except BirdsEye.
            boolean zoomPressed = zoomKey.get().isPressed();
            if (cameraFeature.get() != CameraFeature.BirdsEye) {
                if (zoomToggle.get()) {
                    if (zoomPressed && !wasZoomKeyPressed) isZooming = !isZooming;
                } else {
                    isZooming = zoomPressed;
                }
            } else {
                isZooming = false;
            }
            wasZoomKeyPressed = zoomPressed;

            // ── Around the World keybind ──────────────────────────────────────
            boolean atwPressed = atwKey.get().isPressed();
            if (atwPressed && !wasAtwKeyPressed) {
                atwActive = !atwActive;
                if (atwActive) {
                    atwCurrentYaw = cameraYaw; // start spin from current camera angle
                    info("Around the World §aon§r.");
                } else {
                    info("Around the World §coff§r.");
                }
            }
            wasAtwKeyPressed = atwPressed;

        } else {
            wasNoDistanceKeyPressed = false;
            wasKeyPressed           = false;
            wasShoulderKeyPressed   = false;
            wasZoomKeyPressed       = false;
            wasAtwKeyPressed        = false;
            if (!zoomToggle.get()) isZooming = false;
        }



        // ── Normal camera tick ────────────────────────────────────────────────
        if (noDistanceActive) {
            if (previousPerspective != null) {
                mc.options.setPerspective(previousPerspective);
                previousPerspective = null;
            }
            if (isZooming) {
                if (mc.options.getPerspective().isFirstPerson()) {
                    targetLateralOffset = 0f;
                } else {
                    updateLateralOffset();
                }
            } else {
                targetLateralOffset = 0f;
            }
        } else {
            if (previousPerspective == null)
                previousPerspective = mc.options.getPerspective();
            if (mc.options.getPerspective() != Perspective.THIRD_PERSON_BACK)
                mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);

            if (cameraFeature.get() == CameraFeature.BirdsEye && !shoulderKeyActive) {
                cameraYaw   = mc.player.getYaw();
                cameraPitch = 90f;
            }
            updateLateralOffset();
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        // During Around the World, inject the continuously advancing yaw and
        // optionally lock pitch; distance and lateral offset use normal lerp.
        float speed;
        if (atwActive) {
            // Advance yaw every render frame scaled by tickDelta so speed is
            // frame-rate independent and the rotation is perfectly smooth.
            float delta = (float)(atwSpeed.get() * event.tickDelta);
            if (!atwClockwise.get()) delta = -delta;
            atwCurrentYaw += delta;
            if      (atwCurrentYaw >  180f) atwCurrentYaw -= 360f;
            else if (atwCurrentYaw < -180f) atwCurrentYaw += 360f;

            cameraYaw   = atwCurrentYaw;
            cameraPitch = atwLockPitch.get()
                ? (float) atwPitch.get().doubleValue()
                : cameraPitch; // leave pitch wherever free-look left it

            speed = smoothTransitions.get() ? transitionSpeed.get().floatValue() : 1.0f;
            double targetDist = isZooming ? zoomDistance.get() : distance.get();
            currentDistance += (targetDist - currentDistance) * speed;
            if (Math.abs(targetDist - currentDistance) < 0.01) currentDistance = targetDist;

            lateralOffset += (targetLateralOffset - lateralOffset) * speed;
            if (Math.abs(targetLateralOffset - lateralOffset) < 0.001f)
                lateralOffset = targetLateralOffset;

        } else {
            double targetDist = isZooming ? zoomDistance.get() : distance.get();
            speed = smoothTransitions.get() ? transitionSpeed.get().floatValue() : 1.0f;

            // When free-look is off, smoothly chase the player's look direction
            // so the camera glides back behind wherever the player is facing
            // rather than snapping or sitting frozen at a stale angle.
            boolean shouldFollow = !atwActive
                && !freeLook.get()
                && mc.player != null
                && (cameraFeature.get() == CameraFeature.ThirdPerson
                 || cameraFeature.get() == CameraFeature.Shoulder
                 || shoulderKeyActive);
            if (shouldFollow) {
                float playerYaw = mc.player.getYaw();
                float yawDiff = playerYaw - cameraYaw;
                // Shortest-arc wrap so we always take the <180° path
                if (yawDiff >  180f) yawDiff -= 360f;
                if (yawDiff < -180f) yawDiff += 360f;
                float fs = (float) followSpeed.get().doubleValue();
                cameraYaw += yawDiff * fs;
            }

            lateralOffset += (targetLateralOffset - lateralOffset) * speed;
            if (Math.abs(targetLateralOffset - lateralOffset) < 0.001f)
                lateralOffset = targetLateralOffset;

            currentDistance += (targetDist - currentDistance) * speed;
            if (Math.abs(targetDist - currentDistance) < 0.01) currentDistance = targetDist;
        }

        // FOV smoothing (first-person zoom) — only when not spinning
        if (!atwActive) {
            if (noDistanceActive && mc.options.getPerspective().isFirstPerson()) {
                if (isZooming) {
                    if (originalFov == -1) {
                        originalFov = mc.options.getFov().getValue();
                        currentFov  = originalFov;
                    }
                    double targetFov = zoomFov.get();
                    currentFov += (targetFov - currentFov) * speed;
                    if (Math.abs(targetFov - currentFov) < 0.1) currentFov = targetFov;
                    mc.options.getFov().setValue((int) currentFov);
                } else if (originalFov != -1) {
                    currentFov += (originalFov - currentFov) * speed;
                    if (Math.abs(originalFov - currentFov) < 0.1) {
                        currentFov  = originalFov;
                        mc.options.getFov().setValue((int) originalFov);
                        originalFov = -1;
                    } else {
                        mc.options.getFov().setValue((int) currentFov);
                    }
                }
            } else if (originalFov != -1) {
                mc.options.getFov().setValue((int) originalFov);
                originalFov = -1;
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void updateLateralOffset() {
        if (!isShoulderActive()) {
            targetLateralOffset = 0f;
        } else {
            float offset = (float) shoulderOffset.get().doubleValue();
            targetLateralOffset = shoulderSide.get() == ShoulderSide.Right ? offset : -offset;
        }
        if (!smoothTransitions.get()) lateralOffset = targetLateralOffset;
    }

    /**
     * Returns true when shoulder mode is effectively active — either because
     * Shoulder is the selected CameraFeature, or the shoulder keybind override
     * is engaged.
     */
    public boolean isShoulderActive() {
        return cameraFeature.get() == CameraFeature.Shoulder || shoulderKeyActive;
    }

    public double getDistance() { return currentDistance; }

    public boolean isZooming() { return isZooming; }

    public void setZooming(boolean z) { this.isZooming = z; }

    public boolean isNoDistanceActive() { return noDistanceActive; }

    public boolean isAtwActive() { return atwActive; }

    /**
     * Called by ThirdSightMouseMixin — free look is active in ThirdPerson and
     * Shoulder modes (or shoulder keybind override), but never in BirdsEye.
     */
    public boolean isFreeLookActive() {
        if (!isActive()) return false;
        if (mc.options.getPerspective().isFirstPerson()) return false;
        if (noDistanceActive && !isZooming()) return false;
        CameraFeature f = cameraFeature.get();
        return (f == CameraFeature.ThirdPerson || f == CameraFeature.Shoulder || shoulderKeyActive)
            && freeLook.get();
    }
}