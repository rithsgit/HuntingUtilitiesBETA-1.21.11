package com.example.addon.mixin;

import com.example.addon.modules.PortalTracker;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin {

    @Shadow public float nauseaIntensity;
    @Shadow public float lastNauseaIntensity;  // FIX: renamed from prevNauseaIntensity in 1.21.5

    /**
     * When portalGui is enabled, cancel the portal nausea tick entirely and keep
     * nauseaIntensity at 0. This prevents two things:
     *   1. nauseaIntensity reaching >= 1.0f, which Minecraft uses to block inventory
     *      interactions (chat uses a separate path and is unaffected).
     *   2. tickNausea calling setScreen(null/portalScreen), which would close or
     *      replace any open screen (inventory, chat, etc.).
     */
    @Inject(method = "tickNausea", at = @At("HEAD"), cancellable = true)
    private void onTickNausea(boolean fromPortalEffect, CallbackInfo ci) {
        if (!fromPortalEffect) return;
        PortalTracker portalTracker = Modules.get().get(PortalTracker.class);
        if (portalTracker != null && portalTracker.isPortalGuiEnabled()) {
            this.lastNauseaIntensity = this.nauseaIntensity;  // FIX: prevNauseaIntensity -> lastNauseaIntensity
            this.nauseaIntensity = 0.0f;
            ci.cancel();
        }
    }
}