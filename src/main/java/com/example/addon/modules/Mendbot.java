package com.example.addon.modules;

import com.example.addon.HuntingUtilities;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;  // FIX: was net.minecraft.item.ItemTags
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

public class Mendbot extends Module {
    public enum MendTarget { Elytra, Tools, Armour, All }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<MendTarget> mendTarget = sgGeneral.add(new EnumSetting.Builder<MendTarget>()
        .name("mend-target")
        .description("What to repair.")
        .defaultValue(MendTarget.Elytra)
        .build()
    );

    private final Setting<Integer> packetsPerBurst = sgGeneral.add(new IntSetting.Builder()
        .name("packets-per-burst")
        .description("How many XP bottles to throw per burst.")
        .defaultValue(3)
        .min(1)
        .sliderMax(10)
        .build()
    );

    private final Setting<Integer> burstDelay = sgGeneral.add(new IntSetting.Builder()
        .name("burst-delay")
        .description("Ticks to wait between bursts.")
        .defaultValue(4)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> autoDisable = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-disable")
        .description("Disable module when finished or out of XP.")
        .defaultValue(true)
        .build()
    );

    private int mendTimer = 0;

    public Mendbot() {
        super(HuntingUtilities.CATEGORY, "mendbot", "Automatically mends items using XP bottles.");
    }

    @Override
    public void onActivate() {
        mendTimer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (mendTimer > 0) {
            mendTimer--;
            return;
        }

        if (!InvUtils.find(Items.EXPERIENCE_BOTTLE).found()) {
            info("No more XP bottles — stopping.");
            if (autoDisable.get()) toggle();
            return;
        }

        boolean finished = false;
        switch (mendTarget.get()) {
            case Elytra -> finished = !handleElytraMending();
            case Tools  -> finished = !handleToolMending();
            case Armour -> finished = !handleArmourMending();
            case All    -> finished = !handleElytraMending() && !handleToolMending() && !handleArmourMending();
        }

        if (finished) {
            info("Mending complete.");
            if (autoDisable.get()) toggle();
        }
    }

    private boolean handleElytraMending() {
        ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (!chest.isOf(Items.ELYTRA) || !chest.isDamaged()) {
            FindItemResult damaged = InvUtils.find(stack -> stack.isOf(Items.ELYTRA) && stack.isDamaged());
            if (damaged.found()) {
                InvUtils.move().from(damaged.slot()).toArmor(2);
                return true;
            } else {
                return false;
            }
        }

        throwXpBottles();
        return true;
    }

    private boolean handleToolMending() {
        ItemStack offHand = mc.player.getOffHandStack();

        if (isTool(offHand)) {
            if (offHand.isDamaged()) {
                throwXpBottles();
                return true;
            } else {
                int slot = InvUtils.findEmpty().slot();
                if (slot != -1) { InvUtils.move().fromOffhand().to(slot); return true; }
            }
        }

        FindItemResult damagedTool = InvUtils.find(s -> isTool(s) && s.isDamaged());
        if (damagedTool.found()) {
            InvUtils.move().from(damagedTool.slot()).toOffhand();
            return true;
        }

        return false;
    }

    private boolean handleArmourMending() {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = mc.player.getEquippedStack(slot);
            if (isMendableArmour(stack) && !stack.isOf(Items.ELYTRA)) {
                throwXpBottles();
                return true;
            }
        }

        FindItemResult damagedArmour = InvUtils.find(s -> isMendableArmour(s) && !s.isOf(Items.ELYTRA));
        if (damagedArmour.found()) {
            ItemStack stack = mc.player.getInventory().getStack(damagedArmour.slot());
            var equippable = stack.get(DataComponentTypes.EQUIPPABLE);
            if (equippable != null) {
                EquipmentSlot slot = equippable.slot();
                ItemStack equipped = mc.player.getEquippedStack(slot);
                if (equipped.isEmpty() || !equipped.isDamaged()) {
                    InvUtils.move().from(damagedArmour.slot()).toArmor(slot.getEntitySlotId());
                    return true;
                }
            }
        }

        return false;
    }

    // FIX: ArmorItem was removed in 1.21.5.
    // Use the EQUIPPABLE component to detect armour instead.
    private boolean isMendableArmour(ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamaged()) return false;
        var equippable = stack.get(DataComponentTypes.EQUIPPABLE);
        if (equippable == null) return false;
        EquipmentSlot slot = equippable.slot();
        return slot == EquipmentSlot.HEAD
            || slot == EquipmentSlot.CHEST
            || slot == EquipmentSlot.LEGS
            || slot == EquipmentSlot.FEET;
    }

    // FIX: PickaxeItem/SwordItem/AxeItem etc. were removed in 1.21.5.
    // Use item tags instead.
    private boolean isTool(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        return stack.isIn(ItemTags.PICKAXES)
            || stack.isIn(ItemTags.SWORDS)
            || stack.isIn(ItemTags.AXES)
            || stack.isIn(ItemTags.SHOVELS)
            || item == Items.BOW
            || item == Items.FLINT_AND_STEEL
            || item == Items.SHIELD
            || item == Items.TRIDENT
            || item == Items.FISHING_ROD;
    }

    private void throwXpBottles() {
        float yaw   = mc.player.getYaw()   + (float)(Math.random() * 0.2 - 0.1);
        float pitch = 90                   + (float)(Math.random() * 0.2 - 0.1);

        Rotations.rotate(yaw, pitch, () -> {
            FindItemResult xp = InvUtils.find(Items.EXPERIENCE_BOTTLE);
            if (!xp.found()) return;

            if (xp.isHotbar()) {
                InvUtils.swap(xp.slot(), true);
                for (int i = 0; i < packetsPerBurst.get(); i++) {
                    mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                }
                InvUtils.swapBack();
            } else {
                int emptySlot = InvUtils.findEmpty().slot();

                if (emptySlot != -1) {
                    InvUtils.move().from(xp.slot()).toHotbar(emptySlot);
                    InvUtils.swap(emptySlot, true);
                    for (int i = 0; i < packetsPerBurst.get(); i++) mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                    InvUtils.swapBack();
                    InvUtils.move().from(emptySlot).to(xp.slot());
                } else {
                    // FIX: selectedSlot is now private — use getSelectedSlot()
                    int prevSlot = mc.player.getInventory().getSelectedSlot();
                    InvUtils.move().from(xp.slot()).toHotbar(prevSlot);
                    for (int i = 0; i < packetsPerBurst.get(); i++) mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                    InvUtils.move().from(prevSlot).to(xp.slot());
                }
            }
        });
        mendTimer = burstDelay.get();
    }
}