package com.example.addon.mixin;

import com.example.addon.modules.RocketPilot;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;  // FIX: moved from net.minecraft.client.input to net.minecraft.util in 1.21.5
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class RocketPilotInputMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        RocketPilot rocketPilot = Modules.get().get(RocketPilot.class);
        if (rocketPilot != null && rocketPilot.isActive() && rocketPilot.useFreeLookY.get()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null && mc.player.isGliding()) {
                Input input = (Input) (Object) this;
                // PlayerInput is a record in 1.21.5+. Suppress forward movement
                // by rebuilding it with forward = false, keeping all other inputs.
                input.playerInput = new PlayerInput(
                    false,                          // forward
                    input.playerInput.backward(),
                    input.playerInput.left(),
                    input.playerInput.right(),
                    input.playerInput.jump(),
                    input.playerInput.sneak(),
                    input.playerInput.sprint()
                );
            }
        }
    }
}