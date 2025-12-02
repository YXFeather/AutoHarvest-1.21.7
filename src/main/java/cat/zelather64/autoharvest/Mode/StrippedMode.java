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

    private static final Set<Block> WOOD_BLOCKS = ImmutableSet.of(
            Blocks.OAK_WOOD, Blocks.OAK_LOG,
            Blocks.DARK_OAK_WOOD, Blocks.DARK_OAK_LOG,
            Blocks.JUNGLE_WOOD, Blocks.JUNGLE_LOG,
            Blocks.ACACIA_WOOD, Blocks.ACACIA_LOG,
            Blocks.BIRCH_WOOD, Blocks.BIRCH_LOG,
            Blocks.CHERRY_WOOD, Blocks.CHERRY_LOG,
            Blocks.MANGROVE_WOOD, Blocks.MANGROVE_LOG,
            Blocks.PALE_OAK_WOOD, Blocks.PALE_OAK_LOG,
            Blocks.SPRUCE_WOOD, Blocks.SPRUCE_LOG,
            Blocks.CRIMSON_HYPHAE, Blocks.CRIMSON_STEM,
            Blocks.WARPED_HYPHAE, Blocks.WARPED_STEM
    );

    // 已去皮的木头集合（用于检查）
    private static final Set<Block> STRIPPED_WOOD_BLOCKS = ImmutableSet.of(
            Blocks.STRIPPED_OAK_WOOD, Blocks.STRIPPED_OAK_LOG,
            Blocks.STRIPPED_DARK_OAK_WOOD, Blocks.STRIPPED_DARK_OAK_LOG,
            Blocks.STRIPPED_JUNGLE_WOOD, Blocks.STRIPPED_JUNGLE_LOG,
            Blocks.STRIPPED_ACACIA_WOOD, Blocks.STRIPPED_ACACIA_LOG,
            Blocks.STRIPPED_BIRCH_WOOD, Blocks.STRIPPED_BIRCH_LOG,
            Blocks.STRIPPED_CHERRY_WOOD, Blocks.STRIPPED_CHERRY_LOG,
            Blocks.STRIPPED_MANGROVE_WOOD, Blocks.STRIPPED_MANGROVE_LOG,
            Blocks.STRIPPED_PALE_OAK_WOOD, Blocks.STRIPPED_PALE_OAK_LOG,
            Blocks.STRIPPED_SPRUCE_WOOD, Blocks.STRIPPED_SPRUCE_LOG,
            Blocks.STRIPPED_CRIMSON_HYPHAE, Blocks.STRIPPED_CRIMSON_STEM,
            Blocks.STRIPPED_WARPED_HYPHAE, Blocks.STRIPPED_WARPED_STEM
    );

    // 可以雕刻的南瓜方块
    private static final Set<Block> CARVABLE_PUMPKINS = ImmutableSet.of(
            Blocks.PUMPKIN
            // 注意：雕刻过的南瓜（CARVED_PUMPKIN）已经雕刻过了，不需要再次雕刻
    );

    // 冷却时间
    private BlockPos lastInteractedPos = null;
    private long lastInteractedTime = 0;
    private static final long INTERACT_COOLDOWN_TICKS = 5;

    @Override
    public void tick() {
        ClientWorld world = BoxUtil.getWorld();
        ClientPlayerEntity player = BoxUtil.getPlayer();
        if (world == null || player == null) return;

        Vec3d playerPos = player.getPos();
        if (playerPos == null) return;

        double radius = AutoHarvestConfig.getRadius();
        int radiusInt = (int) Math.ceil(radius);

        // 寻找最佳目标
        TargetInfo targetInfo = findBestTarget(world, playerPos, radius, radiusInt);
        if (targetInfo == null || targetInfo.targetPos == null) return;

        // 检查冷却
        long currentTime = player.getWorld().getTime();
        if (lastInteractedPos != null &&
                lastInteractedPos.equals(targetInfo.targetPos) &&
                currentTime - lastInteractedTime < INTERACT_COOLDOWN_TICKS) {
            return;
        }

        // 根据目标类型选择合适的工具
        boolean success = false;
        if (targetInfo.isPumpkin) {
            // 南瓜：尝试使用剪刀
            success = tryUseShearsOnBlock(world, player, targetInfo.targetPos);
        } else {
            // 木头：尝试使用斧头
            success = tryUseAxeOnBlock(world, player, targetInfo.targetPos);
        }

        if (success) {
            lastInteractedPos = targetInfo.targetPos;
            lastInteractedTime = currentTime;
        }
    }

    private static class TargetInfo {
        BlockPos targetPos;
        boolean isPumpkin; // true: 南瓜, false: 木头

        TargetInfo(BlockPos targetPos, boolean isPumpkin) {
            this.targetPos = targetPos;
            this.isPumpkin = isPumpkin;
        }
    }

    private TargetInfo findBestTarget(ClientWorld world, Vec3d playerPos, double radius, int radiusInt) {
        BlockPos playerBlockPos = BlockPos.ofFloored(playerPos);
        BlockPos bestPumpkinPos = null;
        BlockPos bestWoodPos = null;
        double bestPumpkinDistance = Double.MAX_VALUE;
        double bestWoodDistance = Double.MAX_VALUE;

        // 搜索范围内的方块
        for (int y = radiusInt; y >= -radiusInt; y--) {
            for (int x = -radiusInt; x <= radiusInt; x++) {
                for (int z = -radiusInt; z <= radiusInt; z++) {
                    BlockPos pos = playerBlockPos.add(x, y, z);

                    // 检查球形范围
                    if (!isInSphere(pos, playerPos, radius)) continue;

                    // 检查是否是南瓜
                    if (isCarvablePumpkin(world, pos)) {
                        double distance = pos.getSquaredDistance(playerPos);
                        if (distance < bestPumpkinDistance) {
                            bestPumpkinDistance = distance;
                            bestPumpkinPos = pos;
                        }
                        continue;
                    }

                    // 检查是否是木头
                    if (isAxeableBlock(world, pos) && !isStrippedWood(world.getBlockState(pos).getBlock())) {
                        // 检查上方是否有可去皮木头（如果有，优先处理上面的）
                        BlockPos abovePos = pos.up();
                        BlockPos finalPos = pos;
                        if (isAxeableBlock(world, abovePos)) {
                            // 向上查找连续的木头，处理最上面的
                            while (isAxeableBlock(world, abovePos)) {
                                finalPos = abovePos;
                                abovePos = abovePos.up();
                            }
                        }

                        double distance = finalPos.getSquaredDistance(playerPos);
                        if (distance < bestWoodDistance) {
                            bestWoodDistance = distance;
                            bestWoodPos = finalPos;
                        }
                    }
                }
            }
        }

        // 优先处理南瓜（因为南瓜通常更少见）
        if (bestPumpkinPos != null) {
            return new TargetInfo(bestPumpkinPos, true);
        }

        // 然后处理木头
        if (bestWoodPos != null) {
            return new TargetInfo(bestWoodPos, false);
        }

        return null;
    }

    private boolean tryUseShearsOnBlock(ClientWorld world, ClientPlayerEntity player, BlockPos pos) {
        // 检查手持物品是否为剪刀
        Hand shearsHand = getShearsHand(player);
        if (shearsHand != null) {
            InteractionHelper.interactBlock(player, pos, shearsHand, Direction.UP);
            return true;
        }

        // 尝试切换到剪刀
        if (AutoHarvestConfig.autoSwitchHotbar()) {
            int bestSlot = findBestShearsSlot(player);
            if (bestSlot != -1) {
                player.getInventory().setSelectedSlot(bestSlot);
                // 重新检查手持剪刀
                shearsHand = getShearsHand(player);
                if (shearsHand != null) {
                    InteractionHelper.interactBlock(player, pos, shearsHand, Direction.UP);
                    return true;
                }
            }
        }

        return false;
    }

    private boolean tryUseAxeOnBlock(ClientWorld world, ClientPlayerEntity player, BlockPos pos) {
        // 检查手持物品是否为斧头
        Hand axeHand = getAxeHand(player);
        if (axeHand != null) {
            InteractionHelper.interactBlock(player, pos, axeHand, Direction.UP);
            return true;
        }

        // 尝试切换到斧头
        if (AutoHarvestConfig.autoSwitchHotbar()) {
            int bestSlot = findBestAxeSlot(player);
            if (bestSlot != -1) {
                player.getInventory().setSelectedSlot(bestSlot);
                // 重新检查手持斧头
                axeHand = getAxeHand(player);
                if (axeHand != null) {
                    InteractionHelper.interactBlock(player, pos, axeHand, Direction.UP);
                    return true;
                }
            }
        }

        return false;
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

    private int findBestShearsSlot(ClientPlayerEntity player) {
        int currentSlot = player.getInventory().getSelectedSlot();
        PlayerInventory inventory = player.getInventory();
        int bestSlot = -1;
        int minDistance = Integer.MAX_VALUE;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty() && stack.isOf(Items.SHEARS)) {
                int diff = Math.abs(i - currentSlot);
                int distance = Math.min(diff, 9 - diff);

                if (distance < minDistance) {
                    minDistance = distance;
                    bestSlot = i;
                }
            }
        }

        return bestSlot;
    }

    private int findBestAxeSlot(ClientPlayerEntity player) {
        int currentSlot = player.getInventory().getSelectedSlot();
        PlayerInventory inventory = player.getInventory();
        int bestSlot = -1;
        int minDistance = Integer.MAX_VALUE;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty() && stack.isIn(ItemTags.AXES)) {
                int diff = Math.abs(i - currentSlot);
                int distance = Math.min(diff, 9 - diff);

                if (distance < minDistance) {
                    minDistance = distance;
                    bestSlot = i;
                }
            }
        }

        return bestSlot;
    }

    private boolean isCarvablePumpkin(ClientWorld world, BlockPos pos) {
        Block block = world.getBlockState(pos).getBlock();
        return CARVABLE_PUMPKINS.contains(block);
    }

    private boolean isAxeableBlock(ClientWorld world, BlockPos pos) {
        Block block = world.getBlockState(pos).getBlock();
        return WOOD_BLOCKS.contains(block);
    }

    private boolean isStrippedWood(Block block) {
        return STRIPPED_WOOD_BLOCKS.contains(block);
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
        lastInteractedPos = null;
        lastInteractedTime = 0;
    }
}
