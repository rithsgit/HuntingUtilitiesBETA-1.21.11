package com.example.addon.mixin;

import com.example.addon.iface.EntityRenderStateExtra;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(targets = "net.minecraft.class_10017")
public class EntityRenderStateMixin implements EntityRenderStateExtra {

    @Unique
    private int hunting_entityId = -1;

    @Override
    public int hunting_getEntityId() { return hunting_entityId; }

    @Override
    public void hunting_setEntityId(int id) { this.hunting_entityId = id; }
}