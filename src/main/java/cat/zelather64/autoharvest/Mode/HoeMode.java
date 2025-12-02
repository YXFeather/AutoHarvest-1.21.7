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
import net.minecraft.fluid.FluidState;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Set;

public class HoeMode implements AutoMode{

    private static final Set<Block> HOEABLE_BLOCKS = ImmutableSet.of(
            Blocks.DIRT,
            Blocks.GRASS_BLOCK,
            Blocks.COARSE_DIRT,
            Blocks.ROOTED_DIRT
    );

    // 缓存检查结果，避免重复计算
    private BlockPos lastCheckedPos = null;
    private int lastCheckedSlot = -1;
    private boolean hasCachedHoeInHand = false;
    private Hand cachedHoeHand = null;
    private long lastCacheTime = 0;
    private static final long CACHE_EXPIRE_TICKS = 20; // 1秒缓存

    // 预计算水检查范围，避免每次创建新对象
    private static final int HYDRATION_RANGE = 4;
    private static final BlockPos.Mutable MUTABLE_POS = new BlockPos.Mutable();

    @Override
    public void tick() {
        ClientWorld world = BoxUtil.getWorld();
        ClientPlayerEntity player = BoxUtil.getPlayer();
        if (world == null || player == null) return;

        Vec3d playerPos = player.getPos();
        if (playerPos == null) return;

        double radius = AutoHarvestConfig.getRadius();
        int radiusInt = (int) Math.ceil(radius);

        // 检查是否有有效的可耕地块
        boolean hasValidBlock = checkForValidBlocks(world, playerPos, radius, radiusInt);
        if (!hasValidBlock) return;

        // 获取锄头
        Hand useHand = getCachedHoeInHand(player);
        if (useHand == null) {
            // 尝试自动切换热栏
            if (AutoHarvestConfig.autoSwitchHotbar()) {
                switchToBestHoeSlot(player);
            }
            // 切换后重新获取
            useHand = getCachedHoeInHand(player, true); // 强制刷新缓存
            if (useHand == null) return;
        }

        // 找到并耕种第一个有效的方块
        findAndFarmBlock(world, player, playerPos, radius, radiusInt, useHand);
    }

    private boolean checkForValidBlocks(ClientWorld world, Vec3d playerPos, double radius, int radiusInt) {
        BlockPos playerBlockPos = BlockPos.ofFloored(playerPos);

        // 遍历以玩家为中心的方块区域
        for (BlockPos pos : BlockPos.iterateOutwards(playerBlockPos, radiusInt, radiusInt, radiusInt)) {
            // 快速跳过检查：先检查球形范围
            if (!isInSphere(pos, playerPos, radius)) continue;

            // 检查方块是否可耕种
            if (!isHoeableBlock(world, pos)) continue;

            // 检查上方是否为空气
            if (!world.getBlockState(pos.up()).isAir()) continue;

            // 检查水范围条件
            if (!meetsWaterCondition(world, pos)) {
                continue;
            }

            return true; // 找到至少一个有效方块
        }

        return false;
    }

    private void findAndFarmBlock(ClientWorld world, ClientPlayerEntity player, Vec3d playerPos,
                                  double radius, int radiusInt, Hand useHand) {
        BlockPos playerBlockPos = BlockPos.ofFloored(playerPos);

        for (BlockPos pos : BlockPos.iterateOutwards(playerBlockPos, radiusInt, radiusInt, radiusInt)) {
            // 快速跳过检查
            if (!isInSphere(pos, playerPos, radius)) continue;
            if (!isHoeableBlock(world, pos)) continue;
            if (!world.getBlockState(pos.up()).isAir()) continue;

            // 检查水范围条件
            if (!meetsWaterCondition(world, pos)) {
                continue;
            }

            // 找到有效方块，进行耕种
            InteractionHelper.interactBlock(player, pos, useHand, Direction.UP);
            return; // 每次只耕种一个方块
        }
    }

    private boolean meetsWaterCondition(ClientWorld world, BlockPos pos) {
        boolean hasWaterNearby = hasWaterInRange(world, pos);

        if (AutoHarvestConfig.getWaterNearby()) {
            // 开启保持水源附近时：只耕有水源的地方
            return hasWaterNearby;
        } else {
            // 关闭保持水源附近时：可以耕所有地方（即不检查水源条件）
            return true;
        }
    }

    private Hand getCachedHoeInHand(ClientPlayerEntity player) {
        return getCachedHoeInHand(player, false);
    }

    private Hand getCachedHoeInHand(ClientPlayerEntity player, boolean forceRefresh) {
        long currentTime = player.getWorld().getTime();

        // 检查缓存是否有效
        if (!forceRefresh && hasCachedHoeInHand &&
                currentTime - lastCacheTime < CACHE_EXPIRE_TICKS &&
                player.getInventory().getSelectedSlot() == lastCheckedSlot &&
                player.getPos().equals(lastCheckedPos)) {
            return cachedHoeHand;
        }

        // 刷新缓存
        lastCheckedPos = BlockPos.ofFloored(player.getPos());
        lastCheckedSlot = player.getInventory().getSelectedSlot();
        hasCachedHoeInHand = false;
        cachedHoeHand = null;

        // 检查主手和副手
        ItemStack mainHand = player.getMainHandStack();
        ItemStack offHand = player.getOffHandStack();

        if (!mainHand.isEmpty() && mainHand.isIn(ItemTags.HOES)) {
            cachedHoeHand = Hand.MAIN_HAND;
            hasCachedHoeInHand = true;
        } else if (!offHand.isEmpty() && offHand.isIn(ItemTags.HOES)) {
            cachedHoeHand = Hand.OFF_HAND;
            hasCachedHoeInHand = true;
        }

        lastCacheTime = currentTime;
        return cachedHoeHand;
    }

    private void switchToBestHoeSlot(ClientPlayerEntity player) {
        int currentSlot = player.getInventory().getSelectedSlot();
        PlayerInventory inventory = player.getInventory();
        int bestSlot = -1;
        int minDistance = Integer.MAX_VALUE;

        // 只检查主手栏（0-8）
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty() && stack.isIn(ItemTags.HOES)) {
                // 计算环绕距离（考虑热栏循环）
                int diff = Math.abs(i - currentSlot);
                int distance = Math.min(diff, 9 - diff);

                if (distance < minDistance) {
                    minDistance = distance;
                    bestSlot = i;
                }
            }
        }

        if (bestSlot != -1) {
            inventory.setSelectedSlot(bestSlot);
            // 切换后清除缓存
            hasCachedHoeInHand = false;
            cachedHoeHand = null;
        }
    }

    private boolean isHoeableBlock(ClientWorld world, BlockPos pos) {
        return HOEABLE_BLOCKS.contains(world.getBlockState(pos).getBlock());
    }

    private boolean isInSphere(BlockPos pos, Vec3d center, double radius) {
        // 使用平方比较，避免开方计算
        double dx = pos.getX() + 0.5 - center.x;
        double dy = pos.getY() + 0.5 - center.y;
        double dz = pos.getZ() + 0.5 - center.z;

        return (dx * dx + dy * dy + dz * dz) <= (radius * radius);
    }

    public static boolean hasWaterInRange(World world, BlockPos center) {
        // 检查周围4格范围内是否有水源
        for (int dx = -HYDRATION_RANGE; dx <= HYDRATION_RANGE; dx++) {
            for (int dz = -HYDRATION_RANGE; dz <= HYDRATION_RANGE; dz++) {
                for (int dy = -1; dy <= 0; dy++) {
                    MUTABLE_POS.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState state = world.getBlockState(MUTABLE_POS);

                    if (state.getBlock() == Blocks.WATER) {
                        FluidState fluid = state.getFluidState();
                        if (fluid.isIn(FluidTags.WATER) && fluid.getLevel() == 8) {
                            return true; // 找到有效水源
                        }
                    }
                }
            }
        }
        return false; // 范围内没有有效水源
    }

    @Override
    public String getName() {
        // 缓存翻译结果
        return Text.translatable("autoharvest.mode.hoeing").getString();
    }

    @Override
    public void onDisable() {
        // 清理缓存
        lastCheckedPos = null;
        lastCheckedSlot = -1;
        hasCachedHoeInHand = false;
        cachedHoeHand = null;
        lastCacheTime = 0;
    }
}
