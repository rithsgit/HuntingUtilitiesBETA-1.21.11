package com.example.addon.mixin;

import com.example.addon.modules.Handmold;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PotionItem;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class HandmoldMixin {

    private static final ThreadLocal<Boolean> RENDERING_CENTERED = ThreadLocal.withInitial(() -> false);

    /**
     * 1.21.11: renderFirstPersonItem signature changed:
     *  - float tickDelta        → float tickProgress
     *  - VertexConsumerProvider → net.minecraft.client.render.command.OrderedRenderCommandQueue
     */
    @Inject(
        method = "renderFirstPersonItem",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onRenderFirstPersonItem(
        AbstractClientPlayerEntity player,
        float tickProgress,
        float pitch,
        Hand hand,
        float swingProgress,
        ItemStack item,
        float equipProgress,
        MatrixStack matrices,
        OrderedRenderCommandQueue orderedRenderCommandQueue,
        int light,
        CallbackInfo ci
    ) {
        if (RENDERING_CENTERED.get()) return;

        Handmold mod = Modules.get().get(Handmold.class);
        if (mod == null || !mod.isActive()) return;

        boolean isMain = hand == Hand.MAIN_HAND;

        // ── Hide main hand if empty ───────────────────────────────────────────
        if (isMain && mod.shouldHideEmptyMainhand() && item.isEmpty()) {
            ci.cancel();
            return;
        }

        // ── Hide offhand if enabled ───────────────────────────────────────────
        if (!isMain && mod.shouldHideOffhandCompletely()) {
            ci.cancel();
            return;
        }

        // ── Shared transform values ───────────────────────────────────────────
        double x     = isMain ? mod.getMainX()     : mod.getOffX();
        double y     = isMain ? mod.getMainY()     : mod.getOffY();
        double z     = isMain ? mod.getMainZ()     : mod.getOffZ();
        double scale = isMain ? mod.getMainScale() : mod.getOffScale();
        double rotX  = isMain ? mod.getMainRotX()  : mod.getOffRotX();
        double rotY  = isMain ? mod.getMainRotY()  : mod.getOffRotY();
        double rotZ  = isMain ? mod.getMainRotZ()  : mod.getOffRotZ();

        // ── Center eating/drinking animation ─────────────────────────────────
        if (player.isUsingItem() && player.getActiveHand() == hand) {
            boolean isFood      = item.get(DataComponentTypes.FOOD) != null;
            boolean isDrinkable = item.getItem() instanceof PotionItem
                || item.isOf(Items.MILK_BUCKET)
                || item.isOf(Items.HONEY_BOTTLE);

            if (isFood || isDrinkable) {
                ci.cancel();

                float vanillaHandOffset = isMain ? -0.5f : 0.5f;
                float smoothOffset = vanillaHandOffset * (1.0f - equipProgress);

                matrices.translate(smoothOffset + x, y, z);
                matrices.scale((float) scale, (float) scale, (float) scale);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) rotX));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) rotY));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) rotZ));

                RENDERING_CENTERED.set(true);
                try {
                    ((HeldItemRendererAccessor)(Object)this).invokeRenderFirstPersonItem(
                        player, tickProgress, pitch, hand, 0.0f, item, equipProgress,
                        matrices, orderedRenderCommandQueue, light
                    );
                } finally {
                    RENDERING_CENTERED.set(false);
                }
                return;
            }
        }

        // ── Apply transforms for normal rendering ─────────────────────────────
        matrices.translate(x, y, z);
        matrices.scale((float) scale, (float) scale, (float) scale);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) rotX));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) rotY));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) rotZ));
    }
}