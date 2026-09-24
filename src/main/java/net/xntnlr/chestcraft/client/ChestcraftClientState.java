package net.xntnlr.chestcraft.client;
import net.minecraft.client.MinecraftClient;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;


public final class ChestcraftClientState {

    public static final SimpleInventory craftGrid = new SimpleInventory(4);
    public static ItemStack result = ItemStack.EMPTY;

    private ChestcraftClientState() {}

    public static void reset() {
        for (int i = 0; i < craftGrid.size(); i++) {
            craftGrid.setStack(i, ItemStack.EMPTY);
        }
        result = ItemStack.EMPTY;
    }

    public static void applyServerState(ItemStack cursor, ItemStack[] grid, ItemStack resultStack) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null && cursor != null) {
            ScreenHandler handler = client.player.currentScreenHandler;
            if (handler != null) {
                handler.setCursorStack(cursor);
            }
        }
        for (int i = 0; i < 4; i++) {
            ItemStack stack = grid != null && i < grid.length ? grid[i] : ItemStack.EMPTY;
            craftGrid.setStack(i, stack == null ? ItemStack.EMPTY : stack);
        }
        result = resultStack == null ? ItemStack.EMPTY : resultStack;
    }
}