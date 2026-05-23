package cat.zelather64.autoharvest.Mode;

import cat.zelather64.autoharvest.Config.AutoHarvestConfig;
import cat.zelather64.autoharvest.Utils.BoxUtil;
import cat.zelather64.autoharvest.Utils.HandItemRefill;
import cat.zelather64.autoharvest.Utils.InteractionHelper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.Fertilizable;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Set;

public class BoneMealMode implements AutoMode {

    private static final Set<Block> BONEMEAL_BLACKLIST = Set.of(
            Blocks.GRASS_BLOCK
    );

    private static final int MAX_STACK_SIZE = 64;
    private static final int HOTBAR_SIZE = 9;

    private int cachedRadiusInt = -1;
    private double cachedRadius = -1;

    @Override
    public void tick() {
        ClientWorld world = BoxUtil.getWorld();
        ClientPlayerEntity player = BoxUtil.getPlayer();

        if (world == null || player == null || player.isSpectator()) {
            return;
        }

        // 提前返回优化：检查骨粉补充
        if (AutoHarvestConfig.enableRefill()) {
            checkAndRefillBoneMeal(player);
        }

        // 获取配置并检查是否需要更新缓存
        double currentRadius = AutoHarvestConfig.getInstance().getRadius();
        if (currentRadius != cachedRadius) {
            cachedRadius = currentRadius;
            cachedRadiusInt = (int) Math.ceil(currentRadius);
        }

        Vec3d playerPos = BoxUtil.getPlayerPos();
        if (playerPos == null || cachedRadiusInt <= 0) return;

        // 搜索并处理可催熟的方块
        processGrowableBlocks(world, player, playerPos, currentRadius);
    }

    /**
     * 检查并补充玩家手中的骨粉
     */
    private void checkAndRefillBoneMeal(ClientPlayerEntity player) {
        ItemStack mainHand = player.getMainHandStack();
        ItemStack offHand = player.getOffHandStack();

        if (isBoneMealLow(mainHand)) {
            HandItemRefill.refillHand(HandItemRefill.HandType.MAIN_HAND);
        }
        if (isBoneMealLow(offHand)) {
            HandItemRefill.refillHand(HandItemRefill.HandType.OFF_HAND);
        }
    }

    /**
     * 检查骨粉堆叠数是否过低
     */
    private boolean isBoneMealLow(ItemStack stack) {
        return !stack.isEmpty() &&
                stack.getItem() == Items.BONE_MEAL &&
                stack.getCount() < MAX_STACK_SIZE;
    }

    /**
     * 处理可催熟的方块
     */
    private void processGrowableBlocks(ClientWorld world, ClientPlayerEntity player,
                                       Vec3d playerPos, double radius) {
        BlockPos playerBlockPos = BlockPos.ofFloored(playerPos);
        double radiusSquared = radius * radius; // 使用平方距离避免开方运算

        for (BlockPos pos : BlockPos.iterateOutwards(playerBlockPos, cachedRadiusInt, cachedRadiusInt, cachedRadiusInt)) {
            // 快速距离检查
            if (getDistanceSquared(pos, playerPos) > radiusSquared) {
                continue;
            }

            BlockState state = world.getBlockState(pos);
            Block block = state.getBlock();

            // 快速黑名单检查
            if (BONEMEAL_BLACKLIST.contains(block)) {
                continue;
            }

            // 检查是否可以被催熟
            if (!canBeBonemealed(world, pos)) {
                continue;
            }

            // 尝试使用骨粉
            if (tryUseBoneMeal(player, pos)) {
                return; // 成功使用后立即返回，减少不必要的迭代
            }
        }
    }

    /**
     * 计算方块到玩家位置的平方距离（性能优化）
     */
    private double getDistanceSquared(BlockPos pos, Vec3d playerPos) {
        double dx = pos.getX() + 0.5 - playerPos.x;
        double dy = pos.getY() + 0.5 - playerPos.y;
        double dz = pos.getZ() + 0.5 - playerPos.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * 检查作物是否可以被催熟
     */
    private boolean canBeBonemealed(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();

        if (block instanceof Fertilizable fertilizable) {
            return fertilizable.isFertilizable(world, pos, state);
        }
        return false;
    }

    /**
     * 尝试使用骨粉催熟
     */
    private boolean tryUseBoneMeal(ClientPlayerEntity player, BlockPos pos) {
        Hand boneMealHand = findBoneMealInHands(player);

        if (boneMealHand != null) {
            InteractionHelper.interactBlock(player, pos, boneMealHand, Direction.UP);
            return true;
        }

        int bestSlot = findBestBoneMealSlot(player);
        if (bestSlot != -1) {
            player.getInventory().setSelectedSlot(bestSlot);
            InteractionHelper.interactBlock(player, pos, Hand.MAIN_HAND, Direction.UP);
            return true;
        }

        return false;
    }

    /**
     * 查找玩家手中是否有骨粉
     */
    private Hand findBoneMealInHands(ClientPlayerEntity player) {
        if (player.getMainHandStack().getItem() == Items.BONE_MEAL) {
            return Hand.MAIN_HAND;
        }
        if (player.getOffHandStack().getItem() == Items.BONE_MEAL) {
            return Hand.OFF_HAND;
        }
        return null;
    }

    /**
     * 查找快捷栏中最近的骨粉槽位
     */
    private int findBestBoneMealSlot(ClientPlayerEntity player) {
        int currentSlot = player.getInventory().getSelectedSlot();
        int bestSlot = -1;
        int minDistance = Integer.MAX_VALUE;

        PlayerInventory inventory = player.getInventory();

        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            if (inventory.getStack(slot).getItem() == Items.BONE_MEAL) {
                int distance = Math.abs(slot - currentSlot);
                if (distance < minDistance) {
                    minDistance = distance;
                    bestSlot = slot;
                }
            }
        }

        return bestSlot;
    }

    @Override
    public String getName() {
        return Text.translatable("autoharvest.mode.bonemeal").getString();
    }

    @Override
    public void onDisable() {
        AutoMode.super.onDisable();
    }
}
