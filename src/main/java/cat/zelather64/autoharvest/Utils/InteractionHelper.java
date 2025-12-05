package cat.zelather64.autoharvest.Utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class InteractionHelper {
    private InteractionHelper() {}

    // 和方块交互
    public static void interactBlock(ClientPlayerEntity player, BlockPos blockPos, Hand hand, Direction side) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.interactionManager == null) return;
        Vec3d hitPos = blockPos.toCenterPos();
        BlockHitResult hitResult = new BlockHitResult(hitPos, side, blockPos, false);
        client.interactionManager.interactBlock(player, hand, hitResult);
        SmoothLookHelper.autoLookAt(player, blockPos.toCenterPos(), hand);
    }

    // 直接破坏的方块
    public static void breakBlock(BlockPos blockPos, Direction side){
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.interactionManager == null) return;
        client.interactionManager.attackBlock(blockPos, side);
        SmoothLookHelper.autoLookAt(BoxUtil.getPlayer(), blockPos.toCenterPos(), Hand.MAIN_HAND);
    }

    // 需要长按破坏的方块
    public static void updateBlockBreakingProgress(BlockPos blockPos, Direction direction) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.interactionManager == null) return;
        client.interactionManager.updateBlockBreakingProgress(blockPos, direction);
        SmoothLookHelper.autoLookAt(BoxUtil.getPlayer(), blockPos.toCenterPos(), Hand.MAIN_HAND);
    }

    // 和实体交互
    public static void interactEntity(ClientPlayerEntity player, Entity target, Hand hand) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.interactionManager == null) return;
        client.interactionManager.interactEntity(player, target, hand);
        SmoothLookHelper.autoLookAt(player, target.getPos(), hand);
    }

    // 和物品交互
    public static void interactItem(ClientPlayerEntity player, Hand hand){
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.interactionManager != null) {
            client.interactionManager.interactItem(player, hand);
            SmoothLookHelper.autoLookAt(player, player.getPos(), hand);
        }
    }

    public static void clickSlot(ScreenHandler handler, int slot, int button, SlotActionType actionType, ClientPlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.interactionManager != null) {
            client.interactionManager.clickSlot(handler.syncId, slot, button, actionType, player);
        }
    }

    public static void swapClickSlot(ScreenHandler handler,int targetSlot, int requiredItem, ClientPlayerEntity player) {
        InteractionHelper.clickSlot(handler, requiredItem, 0, SlotActionType.PICKUP, player);
        InteractionHelper.clickSlot(handler, targetSlot, 1, SlotActionType.PICKUP, player);
        InteractionHelper.clickSlot(handler, requiredItem, 0, SlotActionType.PICKUP, player);
    }
}
