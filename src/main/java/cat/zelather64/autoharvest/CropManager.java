package cat.zelather64.autoharvest;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import net.minecraft.block.*;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.passive.*;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.*;


public class CropManager {
    public static final Set<Block> WEED_BLOCKS = new HashSet<>() {
        {
            add(Blocks.FERN);
            add(Blocks.SHORT_GRASS);
            add(Blocks.TALL_GRASS);
            add(Blocks.DEAD_BUSH);
            add(Blocks.BROWN_MUSHROOM);
            add(Blocks.RED_MUSHROOM);
            add(Blocks.LARGE_FERN);
            add(Blocks.SEAGRASS);
            add(Blocks.TALL_SEAGRASS);
            add(Blocks.KELP);
            add(Blocks.KELP_PLANT);
            // 1.16
            add(Blocks.CRIMSON_ROOTS);
            add(Blocks.WARPED_ROOTS);
            add(Blocks.CRIMSON_FUNGUS);
            add(Blocks.WARPED_FUNGUS);
            //1.21.5
            add(Blocks.SHORT_DRY_GRASS);
            add(Blocks.TALL_DRY_GRASS);
            add(Blocks.LEAF_LITTER);
            add(Blocks.BUSH);
        }
    };

//    public static final Set<Block> FLOWER_BLOCKS = new HashSet<>() {
//        {
//            //1.21.5
//            add(Blocks.FIREFLY_BUSH);
//        }
//    };

    public static final Set<Block> WOOD_BLOCKS = new HashSet<>() {
        {
            add(Blocks.OAK_LOG);
            add(Blocks.SPRUCE_LOG);
            add(Blocks.BIRCH_LOG);
            add(Blocks.JUNGLE_LOG);
            add(Blocks.ACACIA_LOG);
            add(Blocks.CHERRY_LOG);
            add(Blocks.DARK_OAK_LOG);
            add(Blocks.MANGROVE_LOG); //红木
            add(Blocks.CRIMSON_STEM); //绯红木
            add(Blocks.WARPED_STEM); //诡异木
            add(Blocks.PALE_OAK_LOG); //苍白橡木
            add(Blocks.BAMBOO_BLOCK);
            add(Blocks.OAK_WOOD);
            add(Blocks.SPRUCE_WOOD);
            add(Blocks.BIRCH_WOOD);
            add(Blocks.JUNGLE_WOOD);
            add(Blocks.ACACIA_WOOD);
            add(Blocks.CHERRY_WOOD);
            add(Blocks.DARK_OAK_WOOD);
            add(Blocks.MANGROVE_WOOD); //红木
            add(Blocks.CRIMSON_HYPHAE); //绯红木
            add(Blocks.WARPED_HYPHAE); //诡异木
            add(Blocks.PALE_OAK_WOOD); //苍白橡木
            add(Blocks.PUMPKIN); //南瓜
        }
    };

    public static final BiMap<Block, Item> SEED_MAP = HashBiMap.create(
            new HashMap<>() {
                {
                    put(Blocks.SWEET_BERRY_BUSH, Items.SWEET_BERRIES);
                    put(Blocks.WHEAT, Items.WHEAT_SEEDS);
                    put(Blocks.POTATOES, Items.POTATO);
                    put(Blocks.CARROTS, Items.CARROT);
                    put(Blocks.BEETROOTS, Items.BEETROOT_SEEDS);
                    put(Blocks.NETHER_WART, Items.NETHER_WART);
                    put(Blocks.MELON_STEM, Items.MELON_SEEDS);
                    put(Blocks.PUMPKIN_STEM, Items.PUMPKIN_SEEDS);
                    put(Blocks.SUGAR_CANE, Items.SUGAR_CANE);
                    put(Blocks.TALL_GRASS, Items.TALL_GRASS);
                    put(Blocks.SHORT_GRASS, Items.SHORT_GRASS);
                    put(Blocks.BAMBOO, Items.BAMBOO);
                    // 1.16
                    put(Blocks.CRIMSON_FUNGUS, Items.CRIMSON_FUNGUS);
                    put(Blocks.WARPED_FUNGUS, Items.WARPED_FUNGUS);
                    put(Blocks.KELP, Items.KELP);
                    //1.20.1
                    put(Blocks.TORCHFLOWER_CROP,Items.TORCHFLOWER_SEEDS);
                    put(Blocks.PITCHER_CROP,Items.PITCHER_POD);
                }
            });

    public static List<AnimalEntity> getFeedableAnimals(ClientPlayerEntity p, Box box, ItemStack handItem) {
        return p.getWorld().getEntitiesByClass(
            AnimalEntity.class, box, animal -> {
                // 实体有效、存活且不是美西螈
                if (animal == null || !animal.isAlive() || animal instanceof AxolotlEntity) {
                    return false;
                }
                // 手上是剪刀时剪羊毛
                if (handItem.isOf(Items.SHEARS)) {
                    // 剪羊毛：必须是未剪毛的成年绵羊
                    return animal instanceof SheepEntity sheep && !sheep.isSheared() && !sheep.isBaby();
                }
                // 喂养普通动物
                return animal.canEat() && animal.isBreedingItem(handItem) && animal.getBreedingAge() >= 0;
            }
        );
    }

    public static final Set<Block> tillableBlocks = Set.of(
            Blocks.DIRT,
            Blocks.GRASS_BLOCK,
            Blocks.COARSE_DIRT,
            Blocks.ROOTED_DIRT
    );

    public static boolean isWeedBlock(World w, BlockPos pos) {
        Block b = w.getBlockState(pos).getBlock();
        return WEED_BLOCKS.contains(b) || b.getDefaultState().isIn(BlockTags.SAPLINGS);
    }

    //是有皮木头
    public static boolean isWood(World w, BlockPos pos) {
        Block b = w.getBlockState(pos).getBlock();
        return WOOD_BLOCKS.contains(b);
    }

    public static boolean isFlowerBlock(World w, BlockPos pos) {
        Block b = w.getBlockState(pos).getBlock();
        return b == Blocks.FIREFLY_BUSH || b.getDefaultState().isIn(BlockTags.FLOWERS);
    }

    public static boolean isCropMature(World world, BlockPos pos, BlockState state) {
        Block block = state.getBlock();

        // 使用多态处理作物块
        if (block instanceof CropBlock cropBlock) return cropBlock.isMature(state);

        // 使用属性键常量处理特定作物
        if (block == Blocks.SWEET_BERRY_BUSH) return state.get(SweetBerryBushBlock.AGE) == 3;

        if (block == Blocks.NETHER_WART) return state.get(NetherWartBlock.AGE) >= 3;

        if (block == Blocks.PITCHER_CROP) return state.get(PitcherCropBlock.AGE) >= 4;

        if (block == Blocks.COCOA) return state.get(CocoaBlock.AGE) >= 2;

        // 处理即时成熟的作物
        if (block == Blocks.PUMPKIN || block == Blocks.MELON) return true;

        // 4. 处理高杆作物（甘蔗、竹子、海带）
        if (isTallCrop(block)) {
            BlockState downState = world.getBlockState(pos.down());
            Block downBlock = downState.getBlock();

            if (downBlock == block) {
                BlockState downDownState = world.getBlockState(pos.down(2));
                return downDownState.getBlock() != block;
            }
        }

        return false;
    }

    // 高杆作物判断（可扩展）
    private static boolean isTallCrop(Block block) {
        return block == Blocks.SUGAR_CANE || block == Blocks.BAMBOO ||
                block == Blocks.KELP || block == Blocks.KELP_PLANT;
    }

    public static boolean isSeed(ItemStack stack) {
        return (!stack.isEmpty() && SEED_MAP.containsValue(stack.getItem()));
    }

    public static boolean isCocoa(ItemStack stack) {
        return (!stack.isEmpty()
                && stack.getItem() == Items.COCOA_BEANS);
    }

    public static boolean canPaint(BlockState s, ItemStack stack) {
        if (stack.getItem() == Items.KELP) {
            // is water and the water is stationary
            return s.getBlock() == Blocks.WATER && s.get(FluidBlock.LEVEL) == 0;
        }
        return s.isAir();
    }

    public static boolean canPlantOn(Item m, World w, BlockPos pos) {
        BlockPos downPos = pos.down();
        BlockState downState = w.getBlockState(downPos);
        Block downBlock = downState.getBlock();
        if (isTallCrop(downBlock)) return false;

        if (!SEED_MAP.containsValue(m)) return false;
        return SEED_MAP.inverse().get(m).getDefaultState().canPlaceAt(w, pos);
    }

    public static boolean isBoneMeal(ItemStack stack) {
        return stack != null && stack.isOf(Items.BONE_MEAL);
    }

    public static boolean isJungleLog(BlockState s) {
        return s.isIn(BlockTags.JUNGLE_LOGS);
    }

    public static boolean isRod(ItemStack stack) {
        return stack.getItem() instanceof FishingRodItem;
    }

    public static boolean needBreakingProgress(BlockState s) {
        return s.getBlock() == Blocks.COCOA || s.getBlock() == Blocks.BAMBOO ||
                s.getBlock() == Blocks.PUMPKIN || s.getBlock() == Blocks.MELON;
    }

    public static boolean canBeBonemealed(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();

        if (block instanceof Fertilizable fertilizable) {
            return fertilizable.isFertilizable(world, pos, state);
        }
        return false;
    }
}