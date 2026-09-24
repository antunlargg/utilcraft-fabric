package net.xntnlr.chestcraft.server;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.recipe.ServerRecipeManager;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.collection.DefaultedList;
import net.xntnlr.chestcraft.network.ChestcraftPayloads;

public final class CraftPanelServer {

    private static final int MAX_SHIFT_CRAFTS = 64;

    private static final class PanelState {
        private final ItemStack[] grid = { ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY };
        private ItemStack result = ItemStack.EMPTY;
    }

    private static final Map<UUID, PanelState> STATES = new ConcurrentHashMap<>();

    private CraftPanelServer() {}

    public static void registerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(ChestcraftPayloads.PanelSessionPayload.ID, (payload, context) ->
            context.server().execute(() -> handleSession(context.player(), payload.kind())));
        ServerPlayNetworking.registerGlobalReceiver(ChestcraftPayloads.PanelActionPayload.ID, (payload, context) ->
            context.server().execute(() -> handleAction(context.player(), payload)));
    }

    public static void onDisconnect(ServerPlayerEntity player) {
        returnGridToPlayer(player, STATES.remove(player.getUuid()));
    }

    private static void handleSession(ServerPlayerEntity player, byte kind) {
        if (kind == 0) {
            PanelState state = STATES.computeIfAbsent(player.getUuid(), key -> new PanelState());
            updateResult(player, state);
            sendState(player, state);
        } else {
            returnGridToPlayer(player, STATES.remove(player.getUuid()));
        }
    }

    private static void handleAction(ServerPlayerEntity player, ChestcraftPayloads.PanelActionPayload payload) {
        PanelState state = STATES.get(player.getUuid());
        if (state == null) return;

        switch (payload.kind()) {
            case ChestcraftPayloads.PanelActionPayload.KIND_ARMOR -> handleArmorClick(player, payload.slot(), payload.shift());
            case ChestcraftPayloads.PanelActionPayload.KIND_GRID -> handleGridClick(player, state, payload.slot(), payload.button(), payload.shift());
            case ChestcraftPayloads.PanelActionPayload.KIND_RESULT -> handleResultClick(player, state, payload.shift());
            case ChestcraftPayloads.PanelActionPayload.KIND_DRAG_BEGIN, ChestcraftPayloads.PanelActionPayload.KIND_DRAG_END -> {
            }
            case ChestcraftPayloads.PanelActionPayload.KIND_DRAG_SLOT -> handleDragSlot(player, state, payload.slot());
            default -> { return; }
        }
        sendState(player, state);
    }

    private static void sendState(ServerPlayerEntity player, PanelState state) {
        ItemStack cursor = player.currentScreenHandler.getCursorStack();
        ServerPlayNetworking.send(player, new ChestcraftPayloads.PanelStatePayload(cursor, state.grid.clone(), state.result));
    }

    private static void returnGridToPlayer(ServerPlayerEntity player, PanelState state) {
        if (state == null) return;
        for (ItemStack stack : state.grid) {
            if (!stack.isEmpty()) player.getInventory().offerOrDrop(stack);
        }
    }

    private static void handleArmorClick(ServerPlayerEntity player, int armorIndex, boolean shift) {
        if (armorIndex < 0 || armorIndex > 3) return;
        PlayerInventory inventory = player.getInventory();
        int inventorySlot = 39 - armorIndex; 
        ScreenHandler handler = player.currentScreenHandler;
        ItemStack armor = inventory.getStack(inventorySlot);
        ItemStack cursor = handler.getCursorStack();

        if (shift) {
            if (armor.isEmpty()) return;
            inventory.setStack(inventorySlot, ItemStack.EMPTY);
            inventory.offerOrDrop(armor);
            return;
        }

        if (cursor.isEmpty()) {
            if (armor.isEmpty()) return;
            handler.setCursorStack(armor.copy());
            inventory.setStack(inventorySlot, ItemStack.EMPTY);
        } else if (canEquip(cursor, armorIndex)) {
            handler.setCursorStack(armor.copy());
            inventory.setStack(inventorySlot, cursor.copy());
        }
    }

    private static boolean canEquip(ItemStack stack, int armorIndex) {
        EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
        if (equippable == null) return false;
        EquipmentSlot wanted = switch (armorIndex) {
            case 0 -> EquipmentSlot.HEAD;
            case 1 -> EquipmentSlot.CHEST;
            case 2 -> EquipmentSlot.LEGS;
            default -> EquipmentSlot.FEET;
        };
        return equippable.slot() == wanted;
    }

    private static void handleDragSlot(ServerPlayerEntity player, PanelState state, int index) {
        if (index < 0 || index > 3) return;
        ScreenHandler handler = player.currentScreenHandler;
        ItemStack cursor = handler.getCursorStack();
        ItemStack inSlot = state.grid[index];

        if (cursor.isEmpty()) return;
        if (inSlot.isEmpty()) {
            state.grid[index] = cursor.split(1);
            if (cursor.isEmpty()) handler.setCursorStack(ItemStack.EMPTY);
        } else if (ItemStack.areItemsAndComponentsEqual(cursor, inSlot) && inSlot.getCount() < inSlot.getMaxCount()) {
            inSlot.increment(1);
            cursor.decrement(1);
            if (cursor.isEmpty()) handler.setCursorStack(ItemStack.EMPTY);
        }
        updateResult(player, state);
    }

    private static void handleGridClick(ServerPlayerEntity player, PanelState state, int index, int button, boolean shift) {
        if (index < 0 || index > 3) return;
        ScreenHandler handler = player.currentScreenHandler;
        ItemStack cursor = handler.getCursorStack();
        ItemStack inSlot = state.grid[index];

        if (shift) {
            if (!inSlot.isEmpty()) {
                player.getInventory().offerOrDrop(inSlot.copy());
                state.grid[index] = ItemStack.EMPTY;
                updateResult(player, state);
            }
            return;
        }

        if (button == 0) {
            if (cursor.isEmpty()) {
                state.grid[index] = ItemStack.EMPTY;
                handler.setCursorStack(inSlot.copy());
            } else if (inSlot.isEmpty()) {
                state.grid[index] = cursor.copy();
                handler.setCursorStack(ItemStack.EMPTY);
            } else if (ItemStack.areItemsAndComponentsEqual(cursor, inSlot)) {
                int transferable = Math.min(cursor.getCount(), inSlot.getMaxCount() - inSlot.getCount());
                if (transferable > 0) {
                    inSlot.increment(transferable);
                    cursor.decrement(transferable);
                    if (cursor.isEmpty()) handler.setCursorStack(ItemStack.EMPTY);
                }
            } else {
                state.grid[index] = cursor.copy();
                handler.setCursorStack(inSlot.copy());
            }
        } else {
            if (cursor.isEmpty()) {
                if (inSlot.isEmpty()) return;
                int half = (inSlot.getCount() + 1) / 2;
                ItemStack taken = inSlot.split(half);
                state.grid[index] = inSlot.isEmpty() ? ItemStack.EMPTY : inSlot;
                handler.setCursorStack(taken);
            } else if (inSlot.isEmpty()) {
                state.grid[index] = cursor.split(1);
            } else if (ItemStack.areItemsAndComponentsEqual(cursor, inSlot) && inSlot.getCount() < inSlot.getMaxCount()) {
                inSlot.increment(1);
                cursor.decrement(1);
                if (cursor.isEmpty()) handler.setCursorStack(ItemStack.EMPTY);
            } else {
                state.grid[index] = cursor.copy();
                handler.setCursorStack(inSlot.copy());
            }
        }
        updateResult(player, state);
    }

    private static void handleResultClick(ServerPlayerEntity player, PanelState state, boolean shift) {
        if (state.result.isEmpty()) return;
        ScreenHandler handler = player.currentScreenHandler;

        if (shift) {
            int crafted = 0;
            while (!state.result.isEmpty() && crafted < MAX_SHIFT_CRAFTS) {
                player.getInventory().offerOrDrop(state.result.copy());
                consumeOnce(player, state);
                crafted++;
            }
        } else {
            ItemStack cursor = handler.getCursorStack();
            if (cursor.isEmpty()) {
                handler.setCursorStack(state.result.copy());
            } else if (ItemStack.areItemsAndComponentsEqual(cursor, state.result)
                    && cursor.getCount() + state.result.getCount() <= cursor.getMaxCount()) {
                cursor.increment(state.result.getCount());
            } else {
                return;
            }
            consumeOnce(player, state);
        }
    }

    private static void consumeOnce(ServerPlayerEntity player, PanelState state) {
        CraftingRecipeInput input = CraftingRecipeInput.create(2, 2, List.of(state.grid));
        ItemStack[] remainders = null;
        Optional<RecipeEntry<CraftingRecipe>> entry = findRecipe(player, input);
        if (entry.isPresent()) {
            DefaultedList<ItemStack> remainderList = entry.get().value().getRecipeRemainders(input);
            remainders = new ItemStack[4];
            for (int i = 0; i < 4; i++) {
                remainders[i] = i < remainderList.size() ? remainderList.get(i) : ItemStack.EMPTY;
            }
        }

        for (int i = 0; i < 4; i++) {
            ItemStack inSlot = state.grid[i];
            if (!inSlot.isEmpty()) inSlot.decrement(1);
            if (inSlot.isEmpty()) {
                ItemStack remainder = remainders == null ? ItemStack.EMPTY : remainders[i];
                state.grid[i] = remainder == null || remainder.isEmpty() ? ItemStack.EMPTY : remainder;
            }
        }
        updateResult(player, state);
    }

    private static void updateResult(ServerPlayerEntity player, PanelState state) {
        state.result = ItemStack.EMPTY;
        CraftingRecipeInput input = CraftingRecipeInput.create(2, 2, List.of(state.grid));
        if (input.isEmpty()) return;
        Optional<RecipeEntry<CraftingRecipe>> entry = findRecipe(player, input);
        if (entry.isPresent()) {
            state.result = entry.get().value().craft(input, player.getEntityWorld().getRegistryManager());
        }
    }

    private static Optional<RecipeEntry<CraftingRecipe>> findRecipe(ServerPlayerEntity player, CraftingRecipeInput input) {
        ServerRecipeManager manager = (ServerRecipeManager) player.getEntityWorld().getRecipeManager();
        return manager.getFirstMatch(RecipeType.CRAFTING, input, player.getEntityWorld());
    }
}