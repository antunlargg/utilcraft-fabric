package net.xntnlr.chestcraft.mixin;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.xntnlr.chestcraft.client.ChestcraftClientState;
import net.xntnlr.chestcraft.network.ChestcraftPayloads;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {

    @Shadow
    protected int x;
    @Shadow
    protected int y;
    @Shadow
    protected int backgroundWidth;

    @Shadow
    protected boolean cursorDragging;

    @Unique
    private static final Identifier chestcraft$BACKGROUND =
        Identifier.ofVanilla("textures/gui/container/generic_54.png");

    @Unique
    private static final Identifier[] chestcraft$ARMOR_ICONS = new Identifier[] {
        Identifier.ofVanilla("container/slot/helmet"),
        Identifier.ofVanilla("container/slot/chestplate"),
        Identifier.ofVanilla("container/slot/leggings"),
        Identifier.ofVanilla("container/slot/boots")
    };

    @Unique
    private static final int chestcraft$CRAFT_DX = 26;

    @Unique
    private static final int chestcraft$RESULT_DX = 74;

    @Unique
    private boolean chestcraft$dragActive;

    @Unique
    private int chestcraft$dragArea = ChestcraftPayloads.PanelActionPayload.AREA_GRID;

    @Unique
    private int chestcraft$dragButton;

    @Unique
    private final boolean[] chestcraft$dragVisited = new boolean[4];

    @Unique
    private boolean chestcraft$suppressRelease;

    @Unique
    private boolean chestcraft$panelAllowed() {
        return !(((Object) this) instanceof InventoryScreen)
            && !(((Object) this) instanceof CreativeInventoryScreen);
    }


    @Unique
    private int chestcraft$panelX() {
        int approxScreenWidth = 2 * this.x + this.backgroundWidth + 1;
        int right = this.x + this.backgroundWidth + 4;
        if (right + 97 <= approxScreenWidth) return right;
        int left = this.x - 101;
        if (left - 5 >= 0) return left;
        return right;
    }

    @Unique
    private int chestcraft$panelY() {
        return this.y;
    }

    @Unique
    private static boolean chestcraft$inSlot(double mouseX, double mouseY, int slotX, int slotY) {
        return mouseX >= slotX && mouseX < slotX + 18 && mouseY >= slotY && mouseY < slotY + 18;
    }

    @Unique
    private boolean chestcraft$inPanel(double mouseX, double mouseY) {
        int panelX = chestcraft$panelX();
        int panelY = chestcraft$panelY();
        return mouseX >= panelX - 5 && mouseX < panelX + 97
            && mouseY >= panelY - 5 && mouseY < panelY + 75;
    }

    @Unique
    private static void chestcraft$send(CustomPayload payload) {
        try {
            ClientPlayNetworking.send(payload);
        } catch (Exception ignored) {
        }
    }

    @Unique
    private static void chestcraft$sendAction(int kind, int slot, int button, boolean shift) {
        chestcraft$send(new ChestcraftPayloads.PanelActionPayload((byte) kind, (byte) slot, (byte) button, shift));
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void chestcraft$onInit(ScreenHandler handler, PlayerInventory inventory, Text title, CallbackInfo ci) {
        if (!chestcraft$panelAllowed()) return;
        ChestcraftClientState.reset();
        chestcraft$send(ChestcraftPayloads.PanelSessionPayload.OPEN);
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void chestcraft$onRemoved(CallbackInfo ci) {
        if (!chestcraft$panelAllowed()) return;
        chestcraft$send(ChestcraftPayloads.PanelSessionPayload.CLOSE);
        ChestcraftClientState.reset();
    }

    @Inject(method = "renderBackground", at = @At("TAIL"))
    private void chestcraft$renderPanel(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!chestcraft$panelAllowed() || client.player == null) return;

        int panelX = chestcraft$panelX();
        int panelY = chestcraft$panelY();

        context.fill(panelX - 5, panelY - 5, panelX + 97, panelY + 77, 0xFF373737);
        context.fill(panelX - 3, panelY - 3, panelX + 95, panelY + 75, 0xFFC6C6C6);

        for (int i = 0; i < 4; i++) {
            int slotY = panelY + (i * 18);
            context.drawTexture(RenderPipelines.GUI_TEXTURED, chestcraft$BACKGROUND, panelX, slotY, 7, 17, 18, 18, 256, 256);
            ItemStack armorItem = client.player.getInventory().getStack(39 - i);
            if (!armorItem.isEmpty()) {
                context.drawItem(armorItem, panelX + 1, slotY + 1);
                context.drawStackOverlay(client.textRenderer, armorItem, panelX + 1, slotY + 1);
            }
        }

        int craftX = panelX + 26;
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                int slotX = craftX + (col * 18);
                int slotY = panelY + (row * 18);
                context.drawTexture(RenderPipelines.GUI_TEXTURED, chestcraft$BACKGROUND, slotX, slotY, 7, 17, 18, 18, 256, 256);
                ItemStack stack = ChestcraftClientState.craftGrid.getStack(row * 2 + col);
                if (!stack.isEmpty()) {
                    context.drawItem(stack, slotX + 1, slotY + 1);
                    context.drawStackOverlay(client.textRenderer, stack, slotX + 1, slotY + 1);
                }
            }
        }

        int resultX = craftX + 48;
        int resultY = panelY + 9;
        context.drawTexture(RenderPipelines.GUI_TEXTURED, chestcraft$BACKGROUND, resultX, resultY, 7, 17, 18, 18, 256, 256);
        ItemStack result = ChestcraftClientState.result;
        if (!result.isEmpty()) {
            context.drawItem(result, resultX + 1, resultY + 1);
            context.drawStackOverlay(client.textRenderer, result, resultX + 1, resultY + 1);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void chestcraft$onMouseClicked(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (!chestcraft$panelAllowed()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        boolean shift = client.isShiftPressed();

        int panelX = chestcraft$panelX();
        int panelY = chestcraft$panelY();

        for (int i = 0; i < 4; i++) {
            if (chestcraft$inSlot(mouseX, mouseY, panelX, panelY + (i * 18))) {
                chestcraft$sendAction(ChestcraftPayloads.PanelActionPayload.KIND_ARMOR, i, button, shift);
                chestcraft$suppressRelease = true;
                chestcraft$cancelClick(cir);
                return;
            }
        }

        int craftX = panelX + chestcraft$CRAFT_DX;
        ScreenHandler handler = client.player.currentScreenHandler;
        ItemStack localCursor = handler != null ? handler.getCursorStack() : ItemStack.EMPTY;
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                if (chestcraft$inSlot(mouseX, mouseY, craftX + (col * 18), panelY + (row * 18))) {
                    int index = row * 2 + col;
                    if (button == 1 && !shift && !localCursor.isEmpty()) {
                        chestcraft$beginDrag(click, index, cir);
                    } else {
                        chestcraft$sendAction(ChestcraftPayloads.PanelActionPayload.KIND_GRID, index, button, shift);
                        chestcraft$suppressRelease = true;
                        chestcraft$cancelClick(cir);
                    }
                    return;
                }
            }
        }

        if (chestcraft$inSlot(mouseX, mouseY, craftX + 48, panelY + 9)) {
            chestcraft$sendAction(ChestcraftPayloads.PanelActionPayload.KIND_RESULT, 0, button, shift);
            chestcraft$suppressRelease = true;
            chestcraft$cancelClick(cir);
            return;
        }

        if (chestcraft$inPanel(mouseX, mouseY)) {
            chestcraft$suppressRelease = true;
            chestcraft$cancelClick(cir);
        }
    }

    @Unique
    private void chestcraft$beginDrag(Click click, int firstIndex, CallbackInfoReturnable<Boolean> cir) {
        chestcraft$dragActive = true;
        chestcraft$dragButton = click.button();
        chestcraft$dragArea = ChestcraftPayloads.PanelActionPayload.AREA_GRID;
        for (int i = 0; i < chestcraft$dragVisited.length; i++) {
            chestcraft$dragVisited[i] = false;
        }
        chestcraft$dragVisited[firstIndex] = true;
        chestcraft$sendAction(ChestcraftPayloads.PanelActionPayload.KIND_DRAG_BEGIN,
            chestcraft$dragArea, chestcraft$dragButton, false);
        chestcraft$sendAction(ChestcraftPayloads.PanelActionPayload.KIND_DRAG_SLOT,
            firstIndex, chestcraft$dragButton, false);
        chestcraft$suppressRelease = true;
        chestcraft$cancelClick(cir);
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void chestcraft$onMouseDragged(Click click, double offsetX, double offsetY, CallbackInfoReturnable<Boolean> cir) {
        if (!chestcraft$panelAllowed()
                || !chestcraft$dragActive
                || chestcraft$dragArea != ChestcraftPayloads.PanelActionPayload.AREA_GRID) {
            return;
        }

        int panelX = chestcraft$panelX();
        int panelY = chestcraft$panelY();
        int craftX = panelX + chestcraft$CRAFT_DX;
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                int index = row * 2 + col;
                if (!chestcraft$dragVisited[index]
                        && chestcraft$inSlot(click.x(), click.y(), craftX + (col * 18), panelY + (row * 18))) {
                    chestcraft$dragVisited[index] = true;
                    chestcraft$sendAction(ChestcraftPayloads.PanelActionPayload.KIND_DRAG_SLOT,
                        index, chestcraft$dragButton, false);
                    break;
                }
            }
        }

        chestcraft$cancelClick(cir);
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void chestcraft$onMouseReleased(Click click, CallbackInfoReturnable<Boolean> cir) {
        if (!chestcraft$panelAllowed()) return;

        if (chestcraft$dragActive) {
            chestcraft$dragActive = false;
            chestcraft$sendAction(ChestcraftPayloads.PanelActionPayload.KIND_DRAG_END,
                chestcraft$dragArea, chestcraft$dragButton, false);
            chestcraft$suppressRelease = false;
            chestcraft$cancelClick(cir);
            return;
        }

        if (chestcraft$suppressRelease) {
            chestcraft$suppressRelease = false;
            chestcraft$cancelClick(cir);
            return;
        }

        if (chestcraft$inPanel(click.x(), click.y())) {
            chestcraft$cancelClick(cir);
        }
    }

    @Unique
    private void chestcraft$cancelClick(CallbackInfoReturnable<Boolean> cir) {
        this.cursorDragging = false;
        cir.setReturnValue(true);
        cir.cancel();
    }
}