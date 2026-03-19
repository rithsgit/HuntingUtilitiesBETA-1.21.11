package com.example.addon.mixin;

import com.example.addon.modules.RocketPilot;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class RocketPilotMixin {

    @Shadow protected abstract void setPos(double x, double y, double z);

    /**
     * 1.21.11: Camera.getPos() renamed to getCameraPos().
     * Shadow the new name.
     */
    @Shadow public abstract Vec3d getCameraPos();

    /**
     * 1.21.11: Camera.update() signature changed:
     *  - BlockView area  → World area
     *  - float tickDelta → float tickProgress
     */
    @Inject(method = "update", at = @At("RETURN"))
    private void onUpdate(World area, Entity focusedEntity, boolean thirdPerson,
                          boolean inverseView, float tickProgress, CallbackInfo ci) {
        RocketPilot rocketPilot = Modules.get().get(RocketPilot.class);
        if (rocketPilot != null && rocketPilot.isActive() && rocketPilot.useFreeLookY.get()) {
            if (focusedEntity instanceof LivingEntity living && living.isGliding()) {
                Vec3d pos = getCameraPos();
                setPos(pos.x, rocketPilot.freeLookY.get(), pos.z);
            }
        }
    }
}