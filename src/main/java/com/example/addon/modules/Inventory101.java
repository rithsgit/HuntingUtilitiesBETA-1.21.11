package com.example.addon.modules;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.lwjgl.glfw.GLFW;

import com.example.addon.HuntingUtilities;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.RegistryOps;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.DyeColor;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

public class Inventory101 extends Module {
    private final SettingGroup sgRegearing = settings.createGroup("Regearing 101");
    private final SettingGroup sgCleaner   = settings.createGroup("Inventory Cleaner");
    private final SettingGroup sgRefill    = settings.createGroup("Refill");
    private final SettingGroup sgOrganizer = settings.createGroup("Shulker Organizer");
    private final SettingGroup sgAutoTool  = settings.createGroup("Auto Tool");

    // ── Regearing ──
    private final Setting<Integer> regearDelay = sgRegearing.add(new IntSetting.Builder()
        .name("regear-movement-delay")
        .description("Delay in ticks between moving items from shulker to inventory.")
        .defaultValue(4).min(1).sliderMax(20)
        .build()
    );
    private final Setting<String> preset1Data = sgRegearing.add(new StringSetting.Builder()
        .name("preset-1-data").description("Saved data for inventory preset 1.")
        .defaultValue("").visible(() -> false)
        .build()
    );
    private final Setting<String> preset2Data = sgRegearing.add(new StringSetting.Builder()
        .name("preset-2-data").description("Saved data for inventory preset 2.")
        .defaultValue("").visible(() -> false)
        .build()
    );

    // ── Refill ──
    private final Setting<Boolean> enableRefill = sgRefill.add(new BoolSetting.Builder()
        .name("enable-refill")
        .description("Adds a button to refill your inventory from a shulker based on a saved preset.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Integer> refillDelay = sgRefill.add(new IntSetting.Builder()
        .name("refill-delay")
        .description("Delay in ticks between moving items from shulker to inventory during a refill.")
        .defaultValue(4).min(1).sliderMax(20)
        .visible(enableRefill::get)
        .build()
    );
    private final Setting<Integer> elytraThreshold = sgRefill.add(new IntSetting.Builder()
        .name("elytra-threshold")
        .description("Durability threshold to consider an elytra as needing replacement.")
        .defaultValue(15).min(1).sliderMax(100)
        .build()
    );

    // ── Cleaner ──
    private final Setting<Boolean> autoDrop = sgCleaner.add(new BoolSetting.Builder()
        .name("inventory-cleaner")
        .description("Automatically drops whitelisted items from your inventory when no GUI is open.")
        .defaultValue(false).build()
    );
    private final Setting<List<Item>> itemsToDrop = sgCleaner.add(new ItemListSetting.Builder()
        .name("items-to-drop").description("Items to automatically drop.")
        .defaultValue(new ArrayList<>()).visible(autoDrop::get)
        .build()
    );
    private final Setting<Integer> dropDelay = sgCleaner.add(new IntSetting.Builder()
        .name("drop-delay").description("Delay in ticks between drops.")
        .defaultValue(2).min(1).visible(autoDrop::get)
        .build()
    );
    private final Setting<Boolean> autoTrash = sgCleaner.add(new BoolSetting.Builder()
        .name("auto-trash")
        .description("Automatically throws away junk items when opening a container.")
        .defaultValue(false).build()
    );
    private final Setting<List<Item>> trashItems = sgCleaner.add(new ItemListSetting.Builder()
        .name("trash-items").description("Items to throw away.")
        .defaultValue(new ArrayList<>()).visible(autoTrash::get)
        .build()
    );
    private final Setting<Integer> trashDelay = sgCleaner.add(new IntSetting.Builder()
        .name("trash-delay").description("Delay in ticks between throwing items.")
        .defaultValue(2).min(1).visible(autoTrash::get)
        .build()
    );

    // ── Organizer ──
    private final Setting<Boolean> showSortButton = sgOrganizer.add(new BoolSetting.Builder()
        .name("show-sort-button").description("Show a sort button in chests.")
        .defaultValue(true).build()
    );
    private final Setting<Integer> sortDelay = sgOrganizer.add(new IntSetting.Builder()
        .name("sort-delay").description("Delay in ticks between sort actions.")
        .defaultValue(2).min(1).visible(showSortButton::get)
        .build()
    );
    private final Setting<Boolean> shiftClickAll = sgOrganizer.add(new BoolSetting.Builder()
        .name("shift-click-all")
        .description("When shift-clicking an item, moves all items of the same type from that inventory.")
        .defaultValue(true)
        .build()
    );

    // ── Auto Tool ──
    private final Setting<Boolean> autoTool = sgAutoTool.add(new BoolSetting.Builder()
        .name("auto-tool")
        .description("Automatically swaps to the best tool when breaking blocks.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> silentAutoTool = sgAutoTool.add(new BoolSetting.Builder()
        .name("silent-swap")
        .description("Swaps to the tool silently.")
        .defaultValue(true)
        .visible(autoTool::get)
        .build()
    );

    // ── State ──
    private boolean isRegearing   = false;
    private int     regearTimer   = 0;
    private boolean isEquipping   = false;
    private int     equipTimer    = 0;
    private boolean isRefilling   = false;
    private int     refillTimer   = 0;
    private boolean isSorting     = false;
    private int     sortTimer     = 0;
    private boolean isInvSorting  = false;
    private int     invSortTimer  = 0;
    private int     invSortPreset = 0;
    private int     cleanerTimer  = 0;
    private boolean isTrashing    = false;
    private int     trashTimer    = 0;
    private boolean trashedForCurrentScreen = false;
    private boolean wasClicking   = false;
    private int     targetPresetIndex = 0;
    private boolean saveMode      = false;
    private double  lastMouseX    = -1;
    private double  lastMouseY    = -1;
    private final Set<Integer> processedInDrag = new HashSet<>();
    private boolean moveAllActionTaken = false;
    private boolean wasBreaking        = false;
    private int     prevSlotAutoTool   = -1;

    public Inventory101() {
        super(HuntingUtilities.CATEGORY, "inventory-101", "Manages inventory layouts with shulker boxes.");
    }

    @Override
    public void onDeactivate() {
        isRegearing  = false;
        isEquipping  = false;
        equipTimer   = 0;
        saveMode     = false;
        isRefilling  = false;
        refillTimer  = 0;
        isSorting    = false;
        isInvSorting = false;
        invSortTimer = 0;
        isTrashing   = false;
        wasClicking  = false;
        lastMouseX   = -1;
        lastMouseY   = -1;
        processedInDrag.clear();
        moveAllActionTaken   = false;
        wasBreaking          = false;
        prevSlotAutoTool     = -1;
        trashTimer           = 0;
        cleanerTimer         = 0;
        trashedForCurrentScreen = false;
    }

    // ─────────────────────── Public API for HandledScreenMixin ───────────────────────

    public boolean isSortButtonEnabled() { return showSortButton.get(); }

    public void startSorting() {
        if (isBusy()) return;
        isSorting = true;
        sortTimer = 0;
    }

    public boolean isRefillEnabled() { return enableRefill.get(); }

    public void startRefilling(int presetIndex) {
        if (isBusy()) return;
        List<ItemStack> target = getPreset(presetIndex);
        if (target.stream().allMatch(ItemStack::isEmpty)) {
            warning("Preset " + presetIndex + " is empty. Cannot refill.");
            return;
        }
        targetPresetIndex = presetIndex;
        isRefilling = true;
        refillTimer = 0;
        info("Refilling from preset " + presetIndex + "...");
    }

    public void toggleSaveMode() {
        if (isBusy()) return;
        saveMode = !saveMode;
        info(saveMode ? "Select a preset slot (1 or 2) to SAVE." : "Save mode cancelled.");
    }

    public boolean isSaveMode() { return saveMode; }

    public boolean isPresetEmpty(int index) {
        String data = (index == 1) ? preset1Data.get() : preset2Data.get();
        return data == null || data.isEmpty();
    }

    public void handlePreset(int index) {
        if (isBusy() && !saveMode) return;
        if (saveMode) {
            saveInventory(index);
            saveMode = false;
            info("Saved inventory to preset " + index + ".");
        } else {
            List<ItemStack> target = getPreset(index);
            if (target.stream().allMatch(ItemStack::isEmpty)) {
                warning("Preset " + index + " is empty.");
                return;
            }
            targetPresetIndex = index;
            isRegearing = true;
            regearTimer = 0;
            info("Loading preset " + index + "...");
        }
    }

    public void clearPresets() {
        preset1Data.set("");
        preset2Data.set("");
        saveMode = false;
        info("Presets cleared.");
    }

    public void startInvSort(int presetIndex) {
        if (isBusy()) return;
        if (isPresetEmpty(presetIndex)) {
            warning("Preset " + presetIndex + " is empty. Cannot sort.");
            return;
        }
        invSortPreset = presetIndex;
        isInvSorting  = true;
        invSortTimer  = 0;
        info("Sorting inventory from preset " + presetIndex + "...");
    }

    public boolean isInvSorting() { return isInvSorting; }

    // ─────────────────────── Tick Handler ───────────────────────

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Auto Tool
        if (autoTool.get()) {
            if (mc.interactionManager.isBreakingBlock()) {
                HitResult hit = mc.crosshairTarget;
                if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK) {
                    BlockState state = mc.world.getBlockState(bhr.getBlockPos());
                    if (!state.isAir()) {
                        int bestSlot = findBestTool(state);
                        // FIX: selectedSlot is now private — use getSelectedSlot()
                        if (bestSlot != -1 && bestSlot != mc.player.getInventory().getSelectedSlot()) {
                            if (!wasBreaking) {
                                prevSlotAutoTool = mc.player.getInventory().getSelectedSlot();
                                wasBreaking = true;
                            }
                            InvUtils.swap(bestSlot, silentAutoTool.get());
                        }
                    }
                }
            } else if (wasBreaking) {
                if (silentAutoTool.get() && prevSlotAutoTool != -1) {
                    InvUtils.swap(prevSlotAutoTool, true);
                }
                wasBreaking = false;
                prevSlotAutoTool = -1;
            }
        }

        // High-priority blocking tasks
        if (isRefilling) {
            if (!(mc.currentScreen instanceof ShulkerBoxScreen)) { isRefilling = false; return; }
            if (refillTimer > 0) { refillTimer--; return; }
            if (performRefillStep()) {
                refillTimer = refillDelay.get();
            } else {
                mc.player.closeHandledScreen();
                info("Refill complete.");
                mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                isRefilling = false;
            }
            return;
        }

        if (isRegearing) {
            if (!(mc.currentScreen instanceof ShulkerBoxScreen)) {
                isRegearing = false;
                isEquipping = true;
                equipTimer  = 0;
                return;
            }
            if (regearTimer > 0) { regearTimer--; return; }
            if (performRegearStep()) {
                regearTimer = regearDelay.get();
            } else {
                isRegearing = false;
                isEquipping = true;
                equipTimer  = 0;
                mc.player.closeHandledScreen();
            }
            return;
        }

        if (isEquipping) {
            if (equipTimer > 0) { equipTimer--; return; }
            if (performEquipStep()) {
                equipTimer = regearDelay.get();
            } else {
                info("Regearing complete.");
                mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                isEquipping = false;
            }
            return;
        }

        if (isSorting) {
            if (!(mc.currentScreen instanceof GenericContainerScreen)) { isSorting = false; return; }
            if (sortTimer > 0) { sortTimer--; return; }
            if (performSortStep()) { sortTimer = sortDelay.get(); } else { isSorting = false; info("Sorting complete."); }
            return;
        }

        if (isInvSorting) {
            if (mc.currentScreen != null && !(mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.InventoryScreen)) {
                isInvSorting = false;
                return;
            }
            if (invSortTimer > 0) { invSortTimer--; return; }
            if (performInvSortStep()) {
                invSortTimer = sortDelay.get();
            } else {
                isInvSorting = false;
                info("Inventory sort complete.");
                mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
            return;
        }

        // Mouse Drag / Click-All Item Move
        if (mc.currentScreen instanceof HandledScreen) {
            HandledScreen<?> screen   = (HandledScreen<?>) mc.currentScreen;
            boolean isClicking = Input.isButtonPressed(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            boolean isShift    = Input.isKeyPressed(GLFW.GLFW_KEY_LEFT_SHIFT)
                              || Input.isKeyPressed(GLFW.GLFW_KEY_RIGHT_SHIFT);

            if (isClicking) {
                if (isShift) {
                    if (!wasClicking) {
                        if (shiftClickAll.get()) {
                            Slot focused = getFocusedSlot(screen);
                            if (focused != null && focused.hasStack()) {
                                moveAllActionTaken = true;
                                Item targetItem = focused.getStack().getItem();
                                boolean clickedInPlayerInventory = focused.inventory == mc.player.getInventory();

                                for (Slot slot : screen.getScreenHandler().slots) {
                                    boolean slotInPlayerInventory = slot.inventory == mc.player.getInventory();
                                    if (slot.hasStack() && slot.getStack().getItem() == targetItem) {
                                        if (clickedInPlayerInventory == slotInPlayerInventory) {
                                            mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, slot.id, 0, SlotActionType.QUICK_MOVE, mc.player);
                                        }
                                    }
                                }
                            }
                        }
                        if (!moveAllActionTaken) {
                            processedInDrag.clear();
                            lastMouseX = mc.mouse.getX();
                            lastMouseY = mc.mouse.getY();
                            Slot focused = getFocusedSlot(screen);
                            if (focused != null && focused.hasStack() && !processedInDrag.contains(focused.id)) {
                                mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, focused.id, 0, SlotActionType.QUICK_MOVE, mc.player);
                                processedInDrag.add(focused.id);
                            }
                        }
                    } else if (!moveAllActionTaken) {
                        double mouseX = mc.mouse.getX();
                        double mouseY = mc.mouse.getY();

                        if (lastMouseX != -1) {
                            double deltaX = mouseX - lastMouseX;
                            double deltaY = mouseY - lastMouseY;
                            double dist   = Math.sqrt(deltaX * deltaX + deltaY * deltaY);

                            if (dist > 1) {
                                int steps = (int) Math.ceil(dist / 2.0);
                                for (int i = 0; i <= steps; i++) {
                                    double currentX = lastMouseX + (deltaX * i / steps);
                                    double currentY = lastMouseY + (deltaY * i / steps);
                                    Slot slot = getSlotAt(screen, currentX, currentY);
                                    if (slot != null && slot.hasStack() && !processedInDrag.contains(slot.id)) {
                                        mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, slot.id, 0, SlotActionType.QUICK_MOVE, mc.player);
                                        processedInDrag.add(slot.id);
                                    }
                                }
                            }
                        }

                        Slot focused = getFocusedSlot(screen);
                        if (focused != null && focused.hasStack() && !processedInDrag.contains(focused.id)) {
                            mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, focused.id, 0, SlotActionType.QUICK_MOVE, mc.player);
                            processedInDrag.add(focused.id);
                        }

                        lastMouseX = mouseX;
                        lastMouseY = mouseY;
                    }
                }

                wasClicking = true;
                return;
            } else {
                if (wasClicking) {
                    processedInDrag.clear();
                    lastMouseX = -1;
                    moveAllActionTaken = false;
                }
                wasClicking = false;
            }
        } else {
            if (wasClicking) {
                processedInDrag.clear();
                lastMouseX = -1;
                moveAllActionTaken = false;
            }
            wasClicking = false;
        }

        // Auto-trash
        if (autoTrash.get() && !isBusy()) {
            if (mc.currentScreen instanceof GenericContainerScreen || mc.currentScreen instanceof ShulkerBoxScreen) {
                if (!trashedForCurrentScreen) { isTrashing = true; trashedForCurrentScreen = true; }
            } else {
                trashedForCurrentScreen = false;
                isTrashing = false;
            }
            if (isTrashing) {
                if (trashTimer > 0) {
                    trashTimer--;
                } else if (performTrashStep()) {
                    trashTimer = trashDelay.get();
                    return;
                } else {
                    isTrashing = false;
                }
            }
        }

        // Auto-drop
        if (autoDrop.get() && mc.currentScreen == null) {
            if (cleanerTimer > 0) { cleanerTimer--; }
            else {
                for (int i = 0; i < 36; i++) {
                    ItemStack stack = mc.player.getInventory().getStack(i);
                    if (!stack.isEmpty() && itemsToDrop.get().contains(stack.getItem())) {
                        InvUtils.drop().slot(i);
                        cleanerTimer = dropDelay.get();
                        return;
                    }
                }
            }
        }
    }

    // ─────────────────────── Internal Logic ───────────────────────

    private void saveInventory(int index) {
        NbtCompound nbt  = new NbtCompound();
        NbtList     list = new NbtList();

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            encodeSlot(list, stack, i, mc);
        }
        encodeSlot(list, mc.player.getOffHandStack(),                          36, mc);
        encodeSlot(list, mc.player.getEquippedStack(EquipmentSlot.FEET),       37, mc);
        encodeSlot(list, mc.player.getEquippedStack(EquipmentSlot.LEGS),       38, mc);
        encodeSlot(list, mc.player.getEquippedStack(EquipmentSlot.CHEST),      39, mc);
        encodeSlot(list, mc.player.getEquippedStack(EquipmentSlot.HEAD),       40, mc);

        nbt.put("Items", list);
        if (index == 1) preset1Data.set(nbt.toString());
        else            preset2Data.set(nbt.toString());
    }

    private void encodeSlot(NbtList list, ItemStack stack, int slot, net.minecraft.client.MinecraftClient mc) {
        if (stack.isEmpty()) return;
        NbtCompound itemTag = new NbtCompound();
        itemTag.putInt("Slot", slot);
        NbtElement encodedItem = ItemStack.CODEC
            .encodeStart(RegistryOps.of(NbtOps.INSTANCE, mc.world.getRegistryManager()), stack)
            .getOrThrow();
        itemTag.put("item", encodedItem);
        list.add(itemTag);
    }

    private static final int PRESET_OFFHAND = 36;
    private static final int PRESET_FEET    = 37;
    private static final int PRESET_LEGS    = 38;
    private static final int PRESET_CHEST   = 39;
    private static final int PRESET_HEAD    = 40;
    private static final int PRESET_SIZE    = 41;

    private List<ItemStack> getPreset(int index) {
        String nbtString = (index == 1) ? preset1Data.get() : preset2Data.get();
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < PRESET_SIZE; i++) items.add(ItemStack.EMPTY);
        if (nbtString == null || nbtString.isEmpty()) return items;
        try {
            // FIX: StringNbtReader.parse() does not exist; correct method is readCompound(String)
            NbtCompound nbt = StringNbtReader.readCompound(nbtString);

            if (nbt.contains("Items")) {
                // FIX: getList(String, byte) removed in 1.21.5 — use getListOrEmpty(String) instead
                NbtList list = nbt.getListOrEmpty("Items");
                for (int i = 0; i < list.size(); i++) {
                    // FIX: NbtList.getCompound(int) now returns Optional<NbtCompound>
                    NbtCompound itemTag = list.getCompound(i).orElse(null);
                    if (itemTag == null) continue;

                    int slot;
                    if (itemTag.contains("Slot")) {
                        // FIX: getInt/getByte now return Optional — use orElse
                        slot = itemTag.getInt("Slot").orElseGet(
                            () -> itemTag.getByte("Slot").map(b -> (int)(b & 255)).orElse(0)
                        );
                    } else {
                        slot = 0;
                    }

                    NbtElement itemNbt = itemTag.get("item");
                    if (slot < PRESET_SIZE && itemNbt != null) {
                        final int finalSlot = slot;
                        ItemStack.CODEC
                            .parse(RegistryOps.of(NbtOps.INSTANCE, mc.world.getRegistryManager()), itemNbt)
                            .result()
                            .ifPresent(s -> items.set(finalSlot, s));
                    }
                }
            }
        } catch (Exception e) {
            error("Failed to parse inventory preset: " + e.getMessage());
        }
        return items;
    }

    private boolean performRegearStep() {
        if (!(mc.player.currentScreenHandler instanceof ShulkerBoxScreenHandler handler)) return false;
        for (int i = 0; i < 27; i++) {
            if (!handler.getSlot(i).getStack().isEmpty()) {
                mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                return true;
            }
        }
        return false;
    }

    private boolean performEquipStep() {
        List<ItemStack> preset = getPreset(targetPresetIndex);

        int[][] armorMap = {
            { PRESET_FEET,  0 },
            { PRESET_LEGS,  1 },
            { PRESET_CHEST, 2 },
            { PRESET_HEAD,  3 },
        };
        EquipmentSlot[] equipSlots = {
            EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD
        };

        for (int a = 0; a < armorMap.length; a++) {
            int presetIdx = armorMap[a][0];
            int armorIdx  = armorMap[a][1];
            ItemStack desired = preset.get(presetIdx);
            if (desired.isEmpty() || desired.isOf(Items.ELYTRA)) continue;

            ItemStack current = mc.player.getEquippedStack(equipSlots[a]);
            if (isSameItemType(current, desired)) continue;

            for (int i = 0; i < 36; i++) {
                if (isSameItemType(mc.player.getInventory().getStack(i), desired)) {
                    InvUtils.move().from(i).toArmor(armorIdx);
                    return true;
                }
            }
        }

        ItemStack desiredChest = preset.get(PRESET_CHEST);
        if (desiredChest.isOf(Items.ELYTRA)) {
            ItemStack currentChest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
            if (!currentChest.isOf(Items.ELYTRA) || isLowDurabilityElytra(currentChest)) {
                for (int i = 0; i < 36; i++) {
                    ItemStack inv = mc.player.getInventory().getStack(i);
                    if (inv.isOf(Items.ELYTRA) && !isLowDurabilityElytra(inv)) {
                        InvUtils.move().from(i).toArmor(2);
                        return true;
                    }
                }
            }
        }

        ItemStack desiredOffhand = preset.get(PRESET_OFFHAND);
        if (!desiredOffhand.isEmpty()) {
            ItemStack currentOffhand = mc.player.getOffHandStack();
            if (!isSameItemType(currentOffhand, desiredOffhand)) {
                for (int i = 0; i < 36; i++) {
                    if (isSameItemType(mc.player.getInventory().getStack(i), desiredOffhand)) {
                        InvUtils.move().from(i).toOffhand();
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean performRefillStep() {
        List<ItemStack> preset = getPreset(targetPresetIndex);
        if (preset.stream().allMatch(ItemStack::isEmpty)) return false;
        if (!(mc.player.currentScreenHandler instanceof ShulkerBoxScreenHandler handler)) return false;

        // Step 0: Consolidate player inventory
        for (int i = 27; i < 63; i++) {
            ItemStack s1 = handler.getSlot(i).getStack();
            if (s1.isEmpty() || s1.getMaxCount() <= 1 || s1.getCount() >= s1.getMaxCount()) continue;
            for (int j = i + 1; j < 63; j++) {
                ItemStack s2 = handler.getSlot(j).getStack();
                if (!s2.isEmpty() && s2.getMaxCount() > 1 && isSameItemType(s1, s2)) {
                    move(j, i); return true;
                }
            }
        }

        // Step 1: Aggregate desired item counts from preset
        Map<Item, Integer> desiredCounts = new HashMap<>();
        for (ItemStack stack : preset) {
            if (!stack.isEmpty())
                desiredCounts.put(stack.getItem(), desiredCounts.getOrDefault(stack.getItem(), 0) + stack.getCount());
        }

        // Step 2: Aggregate current item counts from player inventory
        Map<Item, Integer> currentCounts = new HashMap<>();
        boolean hasLowDuraElytra = false;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                if (stack.isOf(Items.ELYTRA) && isLowDurabilityElytra(stack)) {
                    hasLowDuraElytra = true;
                    continue;
                }
                currentCounts.put(stack.getItem(), currentCounts.getOrDefault(stack.getItem(), 0) + stack.getCount());
            }
        }

        ItemStack chestStack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (!chestStack.isEmpty() && !isLowDurabilityElytra(chestStack))
            currentCounts.put(chestStack.getItem(), currentCounts.getOrDefault(chestStack.getItem(), 0) + chestStack.getCount());

        int goodElytraCount    = currentCounts.getOrDefault(Items.ELYTRA, 0);
        int desiredElytraCount = desiredCounts.getOrDefault(Items.ELYTRA, 0);

        // Step 3: Determine what is needed / excess
        Map<Item, Integer> neededCounts = new HashMap<>();
        Map<Item, Integer> excessCounts = new HashMap<>();
        for (Item item : desiredCounts.keySet()) {
            int needed = desiredCounts.get(item) - currentCounts.getOrDefault(item, 0);
            if (needed > 0) neededCounts.put(item, needed);
        }
        for (Item item : currentCounts.keySet()) {
            if (desiredCounts.containsKey(item)) {
                int diff = currentCounts.get(item) - desiredCounts.get(item);
                if (diff > 0) excessCounts.put(item, diff);
            }
        }

        // Step 4: Swap low-durability elytra
        if (hasLowDuraElytra) {
            boolean hasSpace = false;
            for (int j = 0; j < 36; j++) {
                if (mc.player.getInventory().getStack(j).isEmpty()) { hasSpace = true; break; }
            }

            if (goodElytraCount < desiredElytraCount && hasSpace) {
                int replacementSlot = findGoodElytraInShulker(handler);
                if (replacementSlot != -1) {
                    mc.interactionManager.clickSlot(handler.syncId, replacementSlot, 0, SlotActionType.QUICK_MOVE, mc.player);
                    return true;
                }
            }

            for (int j = 0; j < 36; j++) {
                ItemStack s = mc.player.getInventory().getStack(j);
                if (s.isOf(Items.ELYTRA) && isLowDurabilityElytra(s)) {
                    int playerSlotId = mapInventoryToSlotId(j);
                    if (playerSlotId != -1 && findEmptyShulkerSlot(handler) != -1) {
                        mc.interactionManager.clickSlot(handler.syncId, playerSlotId, 0, SlotActionType.QUICK_MOVE, mc.player);
                        return true;
                    }
                    break;
                }
            }
        }

        // Step 5: Pull needed items from shulker
        if (!neededCounts.isEmpty()) {
            for (int i = 0; i < 27; i++) {
                ItemStack shulkerStack = handler.getSlot(i).getStack();
                if (!shulkerStack.isEmpty() && neededCounts.containsKey(shulkerStack.getItem())) {
                    if (isLowDurabilityElytra(shulkerStack)) continue;
                    mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                    return true;
                }
            }
        }

        // Step 6: Dump excess items back into the shulker
        if (!excessCounts.isEmpty()) {
            for (Map.Entry<Item, Integer> entry : excessCounts.entrySet()) {
                Item item   = entry.getKey();
                int  excess = entry.getValue();

                List<Integer> slots = new ArrayList<>();
                for (int i = 27; i < 63; i++) {
                    if (handler.getSlot(i).getStack().getItem() == item) slots.add(i);
                }
                slots.sort(Comparator.comparingInt(s -> handler.getSlot(s).getStack().getCount()));

                for (int i : slots) {
                    ItemStack stack = handler.getSlot(i).getStack();
                    int count = stack.getCount();

                    if (count <= excess) {
                        mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                        return true;
                    } else {
                        int shulkerTarget = findSlotForDeposit(handler, item);
                        if (shulkerTarget == -1) continue;
                        mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.PICKUP, mc.player);
                        for (int k = 0; k < excess; k++)
                            mc.interactionManager.clickSlot(handler.syncId, shulkerTarget, 1, SlotActionType.PICKUP, mc.player);
                        mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.PICKUP, mc.player);
                        if (!handler.getCursorStack().isEmpty())
                            mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.PICKUP, mc.player);
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean performSortStep() {
        if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler)) return false;
        int invSize = handler.getRows() * 9;
        List<ItemStack> current = new ArrayList<>();
        for (int i = 0; i < invSize; i++) current.add(handler.getSlot(i).getStack());
        List<ItemStack> sorted = new ArrayList<>(current);
        sorted.sort(new ShulkerColorComparator());
        for (int i = 0; i < invSize; i++) {
            if (!ItemStack.areEqual(current.get(i), sorted.get(i))) {
                for (int j = i + 1; j < invSize; j++) {
                    if (ItemStack.areEqual(current.get(j), sorted.get(i))) {
                        move(j, i); return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean performInvSortStep() {
        List<ItemStack> preset = getPreset(invSortPreset);

        boolean[] satisfied        = new boolean[36];
        boolean[] inventoryClaimed = new boolean[36];

        for (int i = 0; i < 36; i++) {
            ItemStack desired = preset.get(i);
            if (desired.isEmpty()) {
                satisfied[i] = mc.player.getInventory().getStack(i).isEmpty();
                continue;
            }
            if (!inventoryClaimed[i] && isSameItemType(mc.player.getInventory().getStack(i), desired)) {
                satisfied[i]        = true;
                inventoryClaimed[i] = true;
            } else {
                for (int j = 0; j < 36; j++) {
                    if (!inventoryClaimed[j] && isSameItemType(mc.player.getInventory().getStack(j), desired)) {
                        inventoryClaimed[j] = true;
                        break;
                    }
                }
            }
        }

        // Pass 1: move an item directly into its correct slot IF that slot is empty
        for (int i = 0; i < 36; i++) {
            if (satisfied[i]) continue;
            ItemStack desired = preset.get(i);
            if (desired.isEmpty()) continue;
            if (!mc.player.getInventory().getStack(i).isEmpty()) continue;

            for (int j = 0; j < 36; j++) {
                if (j == i) continue;
                if (!isSameItemType(mc.player.getInventory().getStack(j), desired)) continue;
                if (satisfied[j]) continue;
                InvUtils.move().from(j).to(i);
                return true;
            }
        }

        // Pass 2: displace a misplaced item into any empty slot to make room
        for (int i = 0; i < 36; i++) {
            if (satisfied[i]) continue;
            ItemStack current = mc.player.getInventory().getStack(i);
            if (current.isEmpty()) continue;

            for (int j = 0; j < 36; j++) {
                if (j == i) continue;
                if (!mc.player.getInventory().getStack(j).isEmpty()) continue;
                InvUtils.move().from(i).to(j);
                return true;
            }
        }

        return false;
    }

    private void move(int from, int to) {
        if (mc.interactionManager == null || mc.player == null) return;
        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) return;
        int syncId = mc.player.currentScreenHandler.syncId;
        mc.interactionManager.clickSlot(syncId, from, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, to,   0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, from, 0, SlotActionType.PICKUP, mc.player);
    }

    private boolean performTrashStep() {
        if (mc.player.currentScreenHandler == null) return false;
        ScreenHandler handler = mc.player.currentScreenHandler;
        int playerStart = handler.slots.size() - 36;
        for (int i = playerStart; i < handler.slots.size(); i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (!stack.isEmpty() && trashItems.get().contains(stack.getItem())) {
                mc.interactionManager.clickSlot(handler.syncId, i, 1, SlotActionType.THROW, mc.player);
                return true;
            }
        }
        return false;
    }

    // ─────────────────────── Slot / Item Helpers ───────────────────────

    private Slot getSlotAt(HandledScreen<?> screen, double mouseX, double mouseY) {
        double scaledMouseX = mouseX * mc.getWindow().getScaledWidth()  / (double) mc.getWindow().getWidth();
        double scaledMouseY = mouseY * mc.getWindow().getScaledHeight() / (double) mc.getWindow().getHeight();
        int[] pos = getGuiPos(screen);
        if (pos == null) return null;
        for (Slot slot : screen.getScreenHandler().slots) {
            int x = pos[0] + slot.x, y = pos[1] + slot.y;
            if (scaledMouseX >= x && scaledMouseX < x + 16 && scaledMouseY >= y && scaledMouseY < y + 16) return slot;
        }
        return null;
    }

    private int[] getGuiPos(HandledScreen<?> screen) {
        try {
            Field fX = HandledScreen.class.getDeclaredField("x"); fX.setAccessible(true);
            Field fY = HandledScreen.class.getDeclaredField("y"); fY.setAccessible(true);
            return new int[]{ fX.getInt(screen), fY.getInt(screen) };
        } catch (Exception ignored) {}

        try {
            Field fX = HandledScreen.class.getDeclaredField("field_2776"); fX.setAccessible(true);
            Field fY = HandledScreen.class.getDeclaredField("field_2777"); fY.setAccessible(true);
            return new int[]{ fX.getInt(screen), fY.getInt(screen) };
        } catch (Exception ignored) {}

        try {
            Field fW = HandledScreen.class.getDeclaredField("backgroundWidth");  fW.setAccessible(true);
            Field fH = HandledScreen.class.getDeclaredField("backgroundHeight"); fH.setAccessible(true);
            int bgW = fW.getInt(screen), bgH = fH.getInt(screen);
            return new int[]{ (screen.width - bgW) / 2, (screen.height - bgH) / 2 };
        } catch (Exception ignored) {}

        return new int[]{ (screen.width - 176) / 2, (screen.height - 166) / 2 };
    }

    private Slot getFocusedSlot(HandledScreen<?> screen) {
        try {
            Field f = HandledScreen.class.getDeclaredField("focusedSlot");
            f.setAccessible(true);
            return (Slot) f.get(screen);
        } catch (Exception e) {
            try {
                Field f = HandledScreen.class.getDeclaredField("field_2787");
                f.setAccessible(true);
                return (Slot) f.get(screen);
            } catch (Exception e2) {
                return getSlotUnderMouse(screen);
            }
        }
    }

    private Slot getSlotUnderMouse(HandledScreen<?> screen) {
        return getSlotAt(screen, mc.mouse.getX(), mc.mouse.getY());
    }

    private int mapInventoryToSlotId(int invIndex) {
        if (invIndex >= 0 && invIndex < 9)  return 54 + invIndex;
        if (invIndex >= 9 && invIndex < 36) return 27 + (invIndex - 9);
        return -1;
    }

    private int findGoodElytraInShulker(ShulkerBoxScreenHandler handler) {
        for (int i = 0; i < 27; i++) {
            ItemStack s = handler.getSlot(i).getStack();
            if (s.isOf(Items.ELYTRA) && !isLowDurabilityElytra(s)) return i;
        }
        return -1;
    }

    private int findSlotForDeposit(ShulkerBoxScreenHandler handler, Item item) {
        for (int i = 0; i < 27; i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack.getItem() == item && stack.getCount() < stack.getMaxCount()) return i;
        }
        return findEmptyShulkerSlot(handler);
    }

    private int findEmptyShulkerSlot(ShulkerBoxScreenHandler handler) {
        for (int i = 0; i < 27; i++) if (handler.getSlot(i).getStack().isEmpty()) return i;
        return -1;
    }

    private int findBestTool(BlockState state) {
        int bestSlot = -1; float bestSpeed = 1.0f;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            float speed = stack.getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) { bestSpeed = speed; bestSlot = i; }
        }
        return bestSlot;
    }

    private boolean isSameItemType(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        return a.getItem() == b.getItem();
    }

    private boolean isLowDurabilityElytra(ItemStack stack) {
        return stack.isOf(Items.ELYTRA) && (stack.getMaxDamage() - stack.getDamage() < elytraThreshold.get());
    }

    private boolean isBusy() {
        return isRegearing || isEquipping || isSorting || isInvSorting || isTrashing || isRefilling;
    }

    // ─────────────────────── ShulkerColorComparator ───────────────────────

    private static class ShulkerColorComparator implements Comparator<ItemStack> {
        @Override
        public int compare(ItemStack o1, ItemStack o2) {
            boolean s1 = isShulker(o1), s2 = isShulker(o2);
            if (s1 && !s2) return -1;
            if (!s1 && s2) return  1;
            if (!s1)       return  0;
            return Integer.compare(getColorId(o1), getColorId(o2));
        }

        private boolean isShulker(ItemStack stack) {
            return stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock;
        }

        private int getColorId(ItemStack stack) {
            if (stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock sb) {
                DyeColor c = sb.getColor();
                // FIX: DyeColor.getId() now returns a String in 1.21.5+ — use ordinal() instead
                return c == null ? 16 : c.ordinal();
            }
            return 17;
        }
    }
}