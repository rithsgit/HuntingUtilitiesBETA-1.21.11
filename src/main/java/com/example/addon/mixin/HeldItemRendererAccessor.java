package com.example.addon.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 1.21.11: renderFirstPersonItem signature changed:
 *  - float tickDelta        → float tickProgress
 *  - VertexConsumerProvider → net.minecraft.client.render.command.OrderedRenderCommandQueue
 */
@Mixin(HeldItemRenderer.class)
public interface HeldItemRendererAccessor {

    @Invoker("renderFirstPersonItem")
    void invokeRenderFirstPersonItem(
        AbstractClientPlayerEntity player,
        float tickProgress,
        float pitch,
        Hand hand,
        float swingProgress,
        ItemStack item,
        float equipProgress,
        MatrixStack matrices,
        OrderedRenderCommandQueue orderedRenderCommandQueue,
        int light
    );
}