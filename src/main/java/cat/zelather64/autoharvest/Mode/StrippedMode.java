package cat.zelather64.autoharvest.Mode;

import cat.zelather64.autoharvest.Config.AutoHarvestConfig;
import cat.zelather64.autoharvest.Utils.BoxUtil;
import cat.zelather64.autoharvest.Utils.InteractionHelper;
import com.google.common.collect.ImmutableSet;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public class StrippedMode implements AutoMode{
    
    private boolean useAxe = false;
    private int shearSlot = -1;
    private int axeSlot = -1;

    private static final Set<Block> WOOD_BLOCKS = ImmutableSet.of(
            Blocks.OAK_WOOD, Blocks.OAK_LOG,
            Blocks.DARK_OAK_WOOD, Blocks.DARK_OAK_LOG,
            Blocks.JUNGLE_WOOD, Blocks.JUNGLE_LOG,
            Blocks.ACACIA_WOOD, Blocks.ACACIA_LOG,
            Blocks.BIRCH_WOOD, Blocks.BIRCH_LOG,
            Blocks.CHERRY_WOOD, Blocks.CHERRY_LOG,
            Blocks.MANGROVE_WOOD, Blocks.MANGROVE_LOG,
            Blocks.SPRUCE_WOOD, Blocks.SPRUCE_LOG,
            Blocks.CRIMSON_HYPHAE, Blocks.CRIMSON_STEM,
            Blocks.WARPED_HYPHAE, Blocks.WARPED_STEM
    );

    // 可以雕刻的南瓜方块
    private static final Set<Block> CARVABLE_PUMPKINS = ImmutableSet.of(
            Blocks.PUMPKIN
    );

    @Override
    public void tick() {
        ClientWorld world = BoxUtil.getWorld();
        ClientPlayerEntity player = BoxUtil.getPlayer();
        if (world == null || player == null) return;

        Vec3d playerPos = player.getPos();
        if (playerPos == null) return;

        double radius = AutoHarvestConfig.getRadius();
        int radiusInt = (int) Math.ceil(radius);

        findItemInSlot(player);

        BlockPos hasValidBock = checkForValidBlock(world, playerPos, radius, radiusInt);

        if (hasValidBock == null) return;

        if (!useAxe && shearSlot != -1) {
            tryUseShearsOnBlock(player, hasValidBock);
            return;
        }
        if (axeSlot != -1) {
            tryUseAxeOnBlock(player, hasValidBock);
            useAxe = false;
        }
    }

    private BlockPos checkForValidBlock(ClientWorld world, Vec3d playerPos, double radius, int radiusInt) {
        BlockPos playerBlockPos = BlockPos.ofFloored(playerPos);

        // 搜索范围内的方块
        for (BlockPos pos : BlockPos.iterateOutwards(playerBlockPos, radiusInt, radiusInt, radiusInt)) {
            // 检查球形范围
            if (!isInSphere(pos, playerPos, radius)) continue;

            // 检查是否是南瓜
            if (isCarvablePumpkin(world, pos)) return pos;

            // 检查是否有可去皮木头
            if (isAxeableBlock(world, pos)) {
                useAxe = true;
                return pos;
            }
        }
        return null;
    }

    private void tryUseShearsOnBlock(ClientPlayerEntity player, BlockPos pos) {
        // 检查手持物品是否为剪刀
        Hand shearsHand = getShearsHand(player);
        if (shearsHand != null) {
            InteractionHelper.interactBlock(player, pos, shearsHand, Direction.UP);
            return;
        }

        // 尝试切换到剪刀
        if (AutoHarvestConfig.autoSwitchHotbar()) {
            if (shearSlot != -1) {
                player.getInventory().setSelectedSlot(shearSlot);

                InteractionHelper.interactBlock(player, pos, Hand.MAIN_HAND, Direction.UP);
            }
        }
    }

    private void tryUseAxeOnBlock(ClientPlayerEntity player, BlockPos pos) {
        // 检查手持物品是否为斧头
        Hand axeHand = getAxeHand(player);
        if (axeHand != null) {
            InteractionHelper.interactBlock(player, pos, axeHand, Direction.UP);
            return;
        }

        // 尝试切换到斧头
        if (AutoHarvestConfig.autoSwitchHotbar()) {
            if (axeSlot != -1) {
                player.getInventory().setSelectedSlot(axeSlot);

                InteractionHelper.interactBlock(player, pos, Hand.MAIN_HAND, Direction.UP);
            }
        }
    }

    private Hand getShearsHand(ClientPlayerEntity player) {
        ItemStack mainHand = player.getMainHandStack();
        ItemStack offHand = player.getOffHandStack();

        if (mainHand.isOf(Items.SHEARS)) {
            return Hand.MAIN_HAND;
        } else if (offHand.isOf(Items.SHEARS)) {
            return Hand.OFF_HAND;
        }

        return null;
    }

    private Hand getAxeHand(ClientPlayerEntity player) {
        ItemStack mainHand = player.getMainHandStack();
        ItemStack offHand = player.getOffHandStack();

        if (!mainHand.isEmpty() && mainHand.isIn(ItemTags.AXES)) {
            return Hand.MAIN_HAND;
        } else if (!offHand.isEmpty() && offHand.isIn(ItemTags.AXES)) {
            return Hand.OFF_HAND;
        }

        return null;
    }

    private void findItemInSlot(ClientPlayerEntity player) {
        int currentSlot = player.getInventory().getSelectedSlot();
        PlayerInventory inventory = player.getInventory();
        int minDistance = Integer.MAX_VALUE;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty() && stack.isOf(Items.SHEARS)) {
                int diff = Math.abs(i - currentSlot);
                int distance = Math.min(diff, 9 - diff);

                if (distance < minDistance) {
                    minDistance = distance;
                    shearSlot = i;
                }
            }
            if (!stack.isEmpty() && stack.isIn(ItemTags.AXES)) {
                int diff = Math.abs(i - currentSlot);
                int distance = Math.min(diff, 9 - diff);

                if (distance < minDistance) {
                    minDistance = distance;
                    axeSlot = i;
                }
            }
        }
    }

    private boolean isCarvablePumpkin(ClientWorld world, BlockPos pos) {
        Block block = world.getBlockState(pos).getBlock();
        return CARVABLE_PUMPKINS.contains(block);
    }

    private boolean isAxeableBlock(ClientWorld world, BlockPos pos) {
        Block block = world.getBlockState(pos).getBlock();
        return WOOD_BLOCKS.contains(block);
    }

    private boolean isInSphere(BlockPos pos, Vec3d center, double radius) {
        double dx = pos.getX() + 0.5 - center.x;
        double dy = pos.getY() + 0.5 - center.y;
        double dz = pos.getZ() + 0.5 - center.z;

        return (dx * dx + dy * dy + dz * dz) <= (radius * radius);
    }

    @Override
    public String getName() {
        return Text.translatable("autoharvest.mode.Stripping").getString();
    }

    @Override
    public void onDisable() {
        useAxe = false;
        shearSlot = -1;
        axeSlot = -1;
    }
}
