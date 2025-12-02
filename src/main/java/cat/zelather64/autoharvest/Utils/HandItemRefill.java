package cat.zelather64.autoharvest.Utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

public class HandItemRefill {
    private static final int OFF_HAND_SCREEN_SLOT = 45;

    public enum HandType {
        MAIN_HAND,
        OFF_HAND
    }

    public static void refillHand(HandType handType) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        client.execute(() -> doRefillHand(client, handType));
    }

    private static void doRefillHand(MinecraftClient client, HandType handType) {
        if (client.player == null || client.world == null) return;

        // 只在库存界面或无界面时执行
        if (client.currentScreen != null && !(client.currentScreen instanceof InventoryScreen)) {
            return;
        }

        ClientPlayerEntity player = client.player;
        PlayerInventory inv = player.getInventory();

        refillSpecificHand(player, inv, handType);
    }

    private static void refillSpecificHand(ClientPlayerEntity player, PlayerInventory inv, HandType handType) {
        ItemStack handStack = handType == HandType.MAIN_HAND ?
                player.getMainHandStack() : player.getOffHandStack();

        if (handStack.isEmpty()) {
            return;
        }

        int selectedSlot = player.getInventory().getSelectedSlot();

        int excludeSlot = handType == HandType.MAIN_HAND ? selectedSlot : -1;
        int sourcePlayerSlot = findMatchingStackInInventory(inv, handStack, excludeSlot);

        if (sourcePlayerSlot == -1) return;

        ScreenHandler handler = player.currentScreenHandler;
        int targetScreenSlot = getTargetScreenSlot(handType, selectedSlot);
        int screenSourceSlot = convertToScreenSlot(sourcePlayerSlot);

        // 执行物品交换
        if (!swapItems(handler, screenSourceSlot, targetScreenSlot)) {
            // 如果交换失败，恢复原状
            clickSlot(handler, screenSourceSlot);
        }
    }

    private static int getTargetScreenSlot(HandType handType, int selectedSlot) {
        return handType == HandType.MAIN_HAND ?
                convertToScreenSlot(selectedSlot) : OFF_HAND_SCREEN_SLOT;
    }

    private static int convertToScreenSlot(int playerSlot) {
        return (playerSlot >= 0 && playerSlot < 9) ? playerSlot + 36 : playerSlot;
    }

    private static boolean swapItems(ScreenHandler handler, int sourceSlot, int targetSlot) {
        clickSlot(handler, sourceSlot);

        ItemStack cursorStack = handler.getCursorStack();
        if (cursorStack.isEmpty()) return false;

        clickSlot(handler, targetSlot);

        // 如果光标还有物品，放回原处
        if (!handler.getCursorStack().isEmpty()) {
            clickSlot(handler, sourceSlot);
            return false;
        }

        return true;
    }

    private static void clickSlot(ScreenHandler handler, int slotIndex) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.interactionManager == null) return;

        client.interactionManager.clickSlot(
                handler.syncId,
                slotIndex,
                0,
                SlotActionType.PICKUP,
                client.player
        );
    }

    private static int findMatchingStackInInventory(PlayerInventory inv, ItemStack targetStack, int excludeSlot) {
        for (int i = 0; i < 36; i++) {
            if (i == excludeSlot) continue;

            ItemStack stack = inv.getStack(i);
            if (!stack.isEmpty() && ItemStack.areItemsEqual(stack, targetStack)) {
                return i;
            }
        }
        return -1;
    }
}
