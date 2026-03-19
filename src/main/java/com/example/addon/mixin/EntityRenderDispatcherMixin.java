package com.example.addon.mixin;

import com.example.addon.iface.EntityRenderStateExtra;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.class_898")
public class EntityRenderDispatcherMixin {

    @Inject(
        method = "method_72977",
        at = @At("RETURN")
    )
    private <E extends Entity> void hunting_captureEntityId(
            E entity,
            float tickProgress,
            CallbackInfoReturnable<?> cir) {
        Object state = cir.getReturnValue();
        if (state instanceof EntityRenderStateExtra extra) {
            extra.hunting_setEntityId(entity.getId());
        }
    }
}