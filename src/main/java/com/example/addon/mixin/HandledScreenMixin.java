package com.example.addon.mixin;

import com.example.addon.modules.DungeonAssistant;
import com.example.addon.modules.Inventory101;
import com.example.addon.modules.LootLens;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.math.ColorHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.BooleanSupplier;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin extends Screen {
    @Shadow protected int backgroundWidth;
    @Shadow protected int x;
    @Shadow protected int y;

    protected HandledScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        Inventory101 inv101       = Modules.get().get(Inventory101.class);
        boolean      inv101Active = inv101 != null && inv101.isActive();

        // ── InventoryScreen: S1 / S2 sort-to-preset buttons ──────────────────────────
        if ((Object) this instanceof InventoryScreen) {
            if (inv101Active) {
                int bx = this.x - 25;
                int by = this.y;

                this.addDrawableChild(mouseOnly(Text.literal("S1"),
                    btn -> inv101.startInvSort(1), bx, by, 20, 20,
                    net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Sort inventory to Preset 1")),
                    () -> !inv101.isPresetEmpty(1)));

                this.addDrawableChild(mouseOnly(Text.literal("S2"),
                    btn -> inv101.startInvSort(2), bx, by + 25, 20, 20,
                    net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Sort inventory to Preset 2")),
                    () -> !inv101.isPresetEmpty(2)));
            }
            return;
        }

        HandledScreen<?> screen         = (HandledScreen<?>) (Object) this;
        int              containerSlots = screen.getScreenHandler().slots.size() - 36;

        // ── Inventory101 buttons (LEFT side) ──────────────────────────────────────────
        if (inv101Active) {
            if ((Object) this instanceof ShulkerBoxScreen) {
                int bx = this.x - 25;
                int by = this.y;

                this.addDrawableChild(mouseOnly(Text.literal("S"),
                    btn -> inv101.toggleSaveMode(), bx, by, 20, 20,
                    net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Save / Load preset"))));

                this.addDrawableChild(mouseOnly(Text.literal("1"),
                    btn -> inv101.handlePreset(1), bx, by + 25, 20, 20,
                    net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Preset 1")),
                    () -> !inv101.isPresetEmpty(1)));

                this.addDrawableChild(mouseOnly(Text.literal("2"),
                    btn -> inv101.handlePreset(2), bx, by + 50, 20, 20,
                    net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Preset 2")),
                    () -> !inv101.isPresetEmpty(2)));

                this.addDrawableChild(mouseOnly(Text.literal("C"),
                    btn -> inv101.clearPresets(), bx, by + 75, 20, 20,
                    net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Clear presets"))));

                if (inv101.isRefillEnabled()) {
                    this.addDrawableChild(mouseOnly(Text.literal("R1"),
                        btn -> inv101.startRefilling(1), bx, by + 100, 20, 20,
                        net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Refill from Preset 1")),
                        () -> !inv101.isPresetEmpty(1)));

                    this.addDrawableChild(mouseOnly(Text.literal("R2"),
                        btn -> inv101.startRefilling(2), bx, by + 125, 20, 20,
                        net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Refill from Preset 2")),
                        () -> !inv101.isPresetEmpty(2)));
                }

                return;
            }

            if ((Object) this instanceof GenericContainerScreen && inv101.isSortButtonEnabled()) {
                int bx = this.x - 35;
                int by = this.y;
                this.addDrawableChild(mouseOnly(Text.literal("Sort"),
                    btn -> inv101.startSorting(), bx, by, 30, 20,
                    net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Sort shulkers by colour"))));
            }
        }

        // ── LootLens / DungeonAssistant Steal+Dump buttons (RIGHT side) ──────────────
        if (containerSlots <= 0) return;

        LootLens ll        = Modules.get().get(LootLens.class);
        boolean  llHandled = ll != null && ll.isActive();
        if (llHandled) {
            addLootLensButtons(ll, screen, containerSlots);
        } else {
            DungeonAssistant da = Modules.get().get(DungeonAssistant.class);
            if (da != null && da.isActive()) {
                addDungeonAssistantButtons(da, screen, containerSlots);
            }
        }
    }

    /**
     * Overload without hasData — button is always fully active visually.
     */
    private static ButtonWidget mouseOnly(Text label, ButtonWidget.PressAction action,
                                          int x, int y, int width, int height,
                                          net.minecraft.client.gui.tooltip.Tooltip tooltip) {
        return mouseOnly(label, action, x, y, width, height, tooltip, null);
    }

    /**
     * Creates a ButtonWidget that only reacts to mouse clicks (no keyboard focus).
     *
     * 1.21.11 constraints:
     *  - renderWidget() is final in PressableWidget      → cannot override
     *  - drawIcon()     is abstract, no super body       → must implement, cannot call super
     *  - keyPressed/keyReleased signatures changed       → replaced with setFocusable(false)
     *
     * "Grey when no data" strategy:
     *   drawIcon() is invoked by the final renderWidget() after the background and label
     *   are already painted. We draw a semi-transparent dark overlay here when hasData
     *   returns false, visually dimming the button without disabling click handling.
     */
    private static ButtonWidget mouseOnly(Text label, ButtonWidget.PressAction action,
                                          int x, int y, int width, int height,
                                          net.minecraft.client.gui.tooltip.Tooltip tooltip,
                                          BooleanSupplier hasData) {
        ButtonWidget btn = new ButtonWidget(x, y, width, height, label, action,
                textSupplier -> textSupplier.get().copy()) {

            /**
             * 1.21.11: drawIcon() is abstract in PressableWidget — no super body exists.
             * This is the only safe render extension point available.
             * We use it to paint a dimming overlay when hasData returns false.
             */
            @Override
            protected void drawIcon(DrawContext context, int mouseX, int mouseY, float delta) {
                if (hasData != null && !hasData.getAsBoolean()) {
                    // Semi-transparent dark overlay to signal "no data" state.
                    // this.active stays true so mouse clicks still fire normally.
                    context.fill(
                        this.getX(),
                        this.getY(),
                        this.getX() + this.getWidth(),
                        this.getY() + this.getHeight(),
                        ColorHelper.getArgb(160, 0, 0, 0)
                    );
                }
                // No super call — PressableWidget.drawIcon() is abstract with no body.
            }
        };

        if (tooltip != null) btn.setTooltip(tooltip);
        return btn;
    }

    // ── Helper: DungeonAssistant S/D buttons ─────────────────────────────────────────

    private void addDungeonAssistantButtons(DungeonAssistant da, HandledScreen<?> screen, int containerSlots) {
        int buttonX = this.x + this.backgroundWidth + 5;
        int buttonY = this.y + 5;

        this.addDrawableChild(new ButtonWidget.Builder(Text.literal("S"), button -> {
            for (int i = 0; i < containerSlots; i++) {
                if (screen.getScreenHandler().getSlot(i).hasStack()) {
                    client.interactionManager.clickSlot(
                        screen.getScreenHandler().syncId, i, 0,
                        SlotActionType.QUICK_MOVE, client.player);
                }
            }
        }).dimensions(buttonX, buttonY, 20, 20).build());

        this.addDrawableChild(new ButtonWidget.Builder(Text.literal("D"), button -> {
            int end = screen.getScreenHandler().slots.size() - 9;
            for (int i = containerSlots; i < end; i++) {
                if (screen.getScreenHandler().getSlot(i).getStack().isEmpty()) continue;
                client.interactionManager.clickSlot(
                    screen.getScreenHandler().syncId, i, 0,
                    SlotActionType.QUICK_MOVE, client.player);
            }
        }).dimensions(buttonX, buttonY + 25, 20, 20).build());
    }

    // ── Helper: LootLens S/D buttons ─────────────────────────────────────────────────

    private void addLootLensButtons(LootLens ll, HandledScreen<?> screen, int containerSlots) {
        int buttonX = this.x + this.backgroundWidth + 5;
        int buttonY = this.y + 5;

        this.addDrawableChild(new ButtonWidget.Builder(Text.literal("S"), button -> {
            for (int i = 0; i < containerSlots; i++) {
                if (screen.getScreenHandler().getSlot(i).hasStack()) {
                    client.interactionManager.clickSlot(
                        screen.getScreenHandler().syncId, i, 0,
                        SlotActionType.QUICK_MOVE, client.player);
                }
            }
        }).dimensions(buttonX, buttonY, 20, 20).build());

        this.addDrawableChild(new ButtonWidget.Builder(Text.literal("D"), button -> {
            int end = screen.getScreenHandler().slots.size() - 9;
            for (int i = containerSlots; i < end; i++) {
                if (screen.getScreenHandler().getSlot(i).getStack().isEmpty()) continue;
                client.interactionManager.clickSlot(
                    screen.getScreenHandler().syncId, i, 0,
                    SlotActionType.QUICK_MOVE, client.player);
            }
        }).dimensions(buttonX, buttonY + 25, 20, 20).build());
    }
}