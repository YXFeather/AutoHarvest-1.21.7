package cat.zelather64.autoharvest.Mode;

import cat.zelather64.autoharvest.Config.AutoHarvestConfig;
import cat.zelather64.autoharvest.Utils.BoxUtil;
import cat.zelather64.autoharvest.Utils.HandItemRefill;
import cat.zelather64.autoharvest.Utils.InteractionHelper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

public class PlantMode implements AutoMode{
    public static final Set<Item> CROP_SEEDS = Set.of(
            Items.WHEAT_SEEDS,
            Items.POTATO,
            Items.CARROT,
            Items.BEETROOT_SEEDS,
            Items.MELON_SEEDS,
            Items.PUMPKIN_SEEDS,
            //1.20.1
            Items.TORCHFLOWER_SEEDS,
            Items.PITCHER_POD
    );

    private static final Item NETHER_WART_ITEM = Items.NETHER_WART;
    private static final Item SUGAR_CANE_ITEM = Items.SUGAR_CANE;
    private static final Item BAMBOO_ITEM = Items.BAMBOO;
    private static final Item COCOA_BEANS_ITEM = Items.COCOA_BEANS;
    private static final Item SWEET_BERRIES_ITEM = Items.SWEET_BERRIES;

    private static final Set<Item> REFILLABLE_PLANT_ITEMS;

    static {
        Set<Item> set = new HashSet<>(CROP_SEEDS);
        set.add(NETHER_WART_ITEM);
        set.add(SUGAR_CANE_ITEM);
        set.add(BAMBOO_ITEM);
        set.add(COCOA_BEANS_ITEM);
        REFILLABLE_PLANT_ITEMS = Set.copyOf(set);
    }

    private static final Set<Block> JUNGLE_LOG_BLOCKS = Set.of(
            Blocks.JUNGLE_LOG,
            Blocks.STRIPPED_JUNGLE_LOG,
            Blocks.JUNGLE_WOOD,
            Blocks.STRIPPED_JUNGLE_WOOD
    );


    private boolean canPlantSugarcane(World world, BlockPos pos) {
        BlockState blockState = world.getBlockState(pos);
        if (blockState.isOf(Blocks.SUGAR_CANE)) return false;
        if (blockState.isIn(BlockTags.DIRT) || blockState.isIn(BlockTags.SAND)) {
            for (Direction direction : Direction.Type.HORIZONTAL) {
                BlockState blockState2 = world.getBlockState(pos.offset(direction));
                FluidState fluidState = world.getFluidState(pos.offset(direction));
                if (fluidState.isIn(FluidTags.WATER) || blockState2.isOf(Blocks.FROSTED_ICE)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasSeeds(ClientPlayerEntity player, Set<Item> seedItems) {
        // 检查主手和副手
        if (seedItems.contains(player.getMainHandStack().getItem()) ||
                seedItems.contains(player.getOffHandStack().getItem())) {
            return true;
        }

        // 检查快捷栏
        return IntStream.range(0, 9)
                .mapToObj(i -> player.getInventory().getStack(i))
                .anyMatch(stack -> seedItems.contains(stack.getItem()));
    }

    private SeedSearchResult findBestSeed(ClientPlayerEntity player, Set<Item> allowedSeeds) {
        // 检查主手和副手
        ItemStack mainHand = player.getMainHandStack();
        ItemStack offHand = player.getOffHandStack();

        if (allowedSeeds.contains(mainHand.getItem())) {
            return new SeedSearchResult(mainHand.getItem(), -1, Hand.MAIN_HAND);
        }
        if (allowedSeeds.contains(offHand.getItem())) {
            return new SeedSearchResult(offHand.getItem(), -1, Hand.OFF_HAND);
        }

        // 在快捷栏中查找最近的种子
        int currentSlot = player.getInventory().getSelectedSlot();
        return IntStream.range(0, 9)
                .mapToObj(i -> new SlotItem(i, player.getInventory().getStack(i)))
                .filter(slotItem -> !slotItem.stack.isEmpty() && allowedSeeds.contains(slotItem.stack.getItem()))
                .min(Comparator.comparingInt(slotItem -> Math.abs(slotItem.slot - currentSlot)))
                .map(slotItem -> new SeedSearchResult(slotItem.stack.getItem(), slotItem.slot, null))
                .orElse(null);
    }

    private record SlotItem(int slot, ItemStack stack) {
    }

    /**
     * @param slot -1 表示在手部
     * @param hand 主手或副手
     */
    private record SeedSearchResult(Item seed, int slot, Hand hand) {
    }

    private static class PlantingContext {
        boolean canPlant = false;
        boolean canPlantCrop, canPlantSugarcane, canPlantNetherWart, canPlantBamboo, canPlantCocoa, canPlantSweetBerries;
        Direction cocoaFacing;
        BlockPos cocoaLogPos, basePos;
        SeedSearchResult targetSeed;
    }

    private void refillHandItems(ClientPlayerEntity player) {
        ItemStack mainHand = player.getMainHandStack();
        ItemStack offHand = player.getOffHandStack();

        if (!mainHand.isEmpty() && mainHand.getCount() < 64 &&
                REFILLABLE_PLANT_ITEMS.contains(mainHand.getItem())) {
            HandItemRefill.refillHand(HandItemRefill.HandType.MAIN_HAND);
        }
        if (!offHand.isEmpty() && offHand.getCount() < 64 &&
                REFILLABLE_PLANT_ITEMS.contains(offHand.getItem())) {
            HandItemRefill.refillHand(HandItemRefill.HandType.OFF_HAND);
        }
    }

    @Override
    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = BoxUtil.getPlayer();

        // 手持物品自动补充逻辑
        if (player != null && !player.isCreative() && !player.isSpectator() &&
                client.world != null && AutoHarvestConfig.enableRefill()) {
            refillHandItems(player);
        }

        // 自动种植逻辑
        ClientWorld world = BoxUtil.getWorld();
        if (world == null || player == null) return;

        Vec3d playerPos = BoxUtil.getPlayerPos();
        if (playerPos == null) return;

        processPlanting(world, player, playerPos);
    }

    private void processPlanting(ClientWorld world, ClientPlayerEntity player, Vec3d playerPos) {
        double radius = AutoHarvestConfig.getRadius();
        Box searchBox = BoxUtil.createSearchBox(playerPos, radius);
        int radiusInt = (int) Math.ceil(radius);

        for (BlockPos pos : BlockPos.iterateOutwards(BlockPos.ofFloored(playerPos), radiusInt, radiusInt, radiusInt)) {
            if (!searchBox.contains(pos.toCenterPos()) || BoxUtil.isInSphere(pos, playerPos, radius)) {
                continue;
            }

            if (!world.getBlockState(pos).isAir()) continue;

            PlantingContext context = analyzePlantingSpot(world, player, pos);
            if (context.canPlant && context.targetSeed != null) {
                executePlanting(world, player, context);
                return; // 每次tick只种植一个方块
            }
        }
    }

    private void executePlanting(ClientWorld world, ClientPlayerEntity player, PlantingContext context) {
        SeedSearchResult seedResult = context.targetSeed;

        // 如果种子已经在手上
        if (seedResult.hand != null) {
            plantSeed(player, context, seedResult.hand);
            return;
        }

        // 需要切换快捷栏
        if (seedResult.slot != -1 && AutoHarvestConfig.autoSwitchHotbar()) {
            player.getInventory().setSelectedSlot(seedResult.slot);
        }
        plantSeed(player, context, Hand.MAIN_HAND);
    }

    private void plantSeed(ClientPlayerEntity player, PlantingContext context, Hand hand) {
        if (context.canPlantCocoa) {
            InteractionHelper.interactBlock(player, context.cocoaLogPos, hand, context.cocoaFacing);
        } else {
            InteractionHelper.interactBlock(player, context.basePos, hand, Direction.UP);
        }
    }

    private PlantingContext analyzePlantingSpot(ClientWorld world, ClientPlayerEntity player, BlockPos pos) {
        PlantingContext context = new PlantingContext();
        BlockPos basePos = pos.down();
        BlockState blockState = world.getBlockState(basePos);
        Block baseBlock = world.getBlockState(basePos).getBlock();

        // 分析种植条件
        context.canPlantCrop = (baseBlock == Blocks.FARMLAND)
                && hasSeeds(player, CROP_SEEDS);
        context.canPlantSugarcane = (blockState.isIn(BlockTags.SAND)
                || blockState.isIn(BlockTags.DIRT))
                && canPlantSugarcane(world, basePos)
                && hasSeeds(player, Set.of(SUGAR_CANE_ITEM));
        context.canPlantNetherWart = baseBlock == Blocks.SOUL_SAND;
        context.canPlantBamboo = blockState.isIn(BlockTags.DIRT)
                && hasSeeds(player, Set.of(BAMBOO_ITEM));

        // 可可豆种植条件
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos logPos = pos.offset(dir.getOpposite());
            if (JUNGLE_LOG_BLOCKS.contains(world.getBlockState(logPos).getBlock())) {
                context.cocoaFacing = dir;
                context.cocoaLogPos = logPos;
                break;
            }
        }
        context.canPlantCocoa = context.cocoaFacing != null;
        context.canPlantSweetBerries = baseBlock.getDefaultState().isIn(BlockTags.DIRT);

        // 确定目标种子（按优先级）
        if (context.canPlantNetherWart) {
            context.targetSeed = findBestSeed(player, Set.of(NETHER_WART_ITEM));
        } else if (context.canPlantCocoa) {
            context.targetSeed = findBestSeed(player, Set.of(COCOA_BEANS_ITEM));
        } else if (context.canPlantSugarcane) {
            context.targetSeed = findBestSeed(player, Set.of(SUGAR_CANE_ITEM));
        } else if (context.canPlantBamboo && shouldPlantBamboo(world, basePos)) {
            context.targetSeed = findBestSeed(player, Set.of(BAMBOO_ITEM));
        } else if (context.canPlantCrop) {
            context.targetSeed = findBestSeed(player, CROP_SEEDS);
        } else if (context.canPlantSweetBerries) {
            context.targetSeed = findBestSeed(player, Set.of(SWEET_BERRIES_ITEM));
        }

        context.basePos = basePos;
        context.canPlant = context.targetSeed != null;
        return context;
    }

    private boolean shouldPlantBamboo(ClientWorld world, BlockPos basePos) {
        int bambooSpacing = AutoHarvestConfig.bambooRadius();
        if (bambooSpacing == 0) return true;

        return IntStream.rangeClosed(-bambooSpacing, bambooSpacing)
                .boxed()
                .flatMap(dx -> IntStream.rangeClosed(-bambooSpacing, bambooSpacing)
                        .mapToObj(dz -> basePos.add(dx, 1, dz)))
                .noneMatch(checkPos -> {
                    Block block = world.getBlockState(checkPos).getBlock();
                    return block == Blocks.BAMBOO_BLOCK || block == Blocks.BAMBOO_SAPLING;
                });
    }

    @Override
    public String getName() {
        return Text.translatable("autoharvest.mode.plant").getString();
    }

    @Override
    public void onDisable() {
        AutoMode.super.onDisable();
    }
}
