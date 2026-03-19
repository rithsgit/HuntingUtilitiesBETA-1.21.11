package com.example.addon.mixin;

import com.example.addon.iface.EntityRenderStateExtra;
import com.example.addon.utils.GlowingRegistry;
import net.minecraft.client.render.OutlineVertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.class_898")
public class EntityGlowingColorMixin {

    @Inject(
        method = "method_72976",
        at = @At("HEAD")
    )
    private <S extends EntityRenderState> void illushine_overrideOutlineColor(
            S state,
            CameraRenderState camera,
            double x, double y, double z,
            MatrixStack matrices,
            OrderedRenderCommandQueue vertexConsumers,
            CallbackInfo ci) {

        if (!(vertexConsumers instanceof OutlineVertexConsumerProvider outline)) return;
        if (!(state instanceof EntityRenderStateExtra extra)) return;
        int id = extra.hunting_getEntityId();
        if (!GlowingRegistry.isGlowing(id)) return;

        outline.setColor(GlowingRegistry.getColor(id));
    }
}