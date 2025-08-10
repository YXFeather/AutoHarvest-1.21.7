package cat.zelather64.autoharvest;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import net.minecraft.block.*;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.passive.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.*;


public class CropManager {
    public static final Block REED_BLOCK = Blocks.SUGAR_CANE;
    public static final Block NETHER_WART = Blocks.NETHER_WART;
    public static final Block BERRY = Blocks.SWEET_BERRY_BUSH;
    public static final Block BAMBOO = Blocks.BAMBOO;
    public static final Block KELP = Blocks.KELP;
    public static final Block KELP_PLANT = Blocks.KELP_PLANT;

    public static final Set<Block> WEED_BLOCKS = new HashSet<>() {
        {
            add(Blocks.OAK_SAPLING);
            add(Blocks.SPRUCE_SAPLING);
            add(Blocks.BIRCH_SAPLING);
            add(Blocks.JUNGLE_SAPLING);
            add(Blocks.ACACIA_SAPLING);
            add(Blocks.DARK_OAK_SAPLING);
            add(Blocks.CHERRY_SAPLING);
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
            //1.21.5
            add(Blocks.SHORT_DRY_GRASS);
            add(Blocks.TALL_DRY_GRASS);
            add(Blocks.LEAF_LITTER);
            add(Blocks.BUSH);
        }
    };

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

    public static final Set<Block> FLOWER_BLOCKS = new HashSet<>() {
        {
            add(Blocks.DANDELION);
            add(Blocks.POPPY);
            add(Blocks.BLUE_ORCHID);
            add(Blocks.ALLIUM);
            add(Blocks.AZURE_BLUET);
            add(Blocks.RED_TULIP);
            add(Blocks.ORANGE_TULIP);
            add(Blocks.WHITE_TULIP);
            add(Blocks.PINK_TULIP);
            add(Blocks.OXEYE_DAISY);
            add(Blocks.CORNFLOWER);
            add(Blocks.LILY_OF_THE_VALLEY);
            add(Blocks.WITHER_ROSE);
            add(Blocks.SUNFLOWER);
            add(Blocks.LILAC);
            add(Blocks.ROSE_BUSH);
            add(Blocks.PEONY);
            //1.20.1
            add(Blocks.TORCHFLOWER);
            add(Blocks.PINK_PETALS);
            //1.21.5
            add(Blocks.CLOSED_EYEBLOSSOM);
            add(Blocks.OPEN_EYEBLOSSOM);
            add(Blocks.FIREFLY_BUSH);
            add(Blocks.WILDFLOWERS);
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
        return WEED_BLOCKS.contains(b);
    }

    //是有皮木头
    public static boolean isWood(World w, BlockPos pos) {
        Block b = w.getBlockState(pos).getBlock();
        return WOOD_BLOCKS.contains(b);
    }

    public static boolean isFlowerBlock(World w, BlockPos pos) {
        Block b = w.getBlockState(pos).getBlock();
        return FLOWER_BLOCKS.contains(b);
    }

    public static boolean isCropMature(World w, BlockPos pos, BlockState stat, Block b) {
        if (b instanceof CropBlock) {
            return ((CropBlock) b).isMature(stat);
        } else if (b == BERRY) {
            return stat.get(SweetBerryBushBlock.AGE) == 3;
        } else if (b == NETHER_WART) {
            if (b instanceof NetherWartBlock)
                return stat.get(NetherWartBlock.AGE) >= 3;
            return false;
        } else if (b == REED_BLOCK || b == BAMBOO || (b == KELP || b == KELP_PLANT)) {
            Block blockDown = w.getBlockState(pos.down()).getBlock();
            Block blockDown2 = w.getBlockState(pos.down(2)).getBlock();
            return (blockDown == REED_BLOCK && blockDown2 != REED_BLOCK) ||
                    (blockDown == BAMBOO && blockDown2 != BAMBOO) ||
                    (blockDown == KELP_PLANT && blockDown2 != KELP_PLANT);
        } else if (b instanceof PitcherCropBlock) {
            return stat.get(PitcherCropBlock.AGE) >= 4;
        } else if (b instanceof CocoaBlock) {
            return stat.get(CocoaBlock.AGE) >= 2;
        } else if (b == Blocks.PUMPKIN || b == Blocks.MELON) {
            return true;
        }
        return false;
    }

    public static boolean isBoneMeal(ItemStack stack) {
        return (!stack.isEmpty()
                && stack.getItem() == Items.BONE_MEAL);
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
            return s.getBlock() == Blocks.WATER && s.getEntries().values().toArray()[0].equals(0);
        }
        return s.getBlock() == Blocks.AIR;
    }

    public static boolean isJungleLog(BlockState s) {
        return s.getBlock() == Blocks.JUNGLE_LOG || s.getBlock() == Blocks.STRIPPED_JUNGLE_LOG ||
                s.getBlock() == Blocks.JUNGLE_WOOD || s.getBlock() == Blocks.STRIPPED_JUNGLE_WOOD;
    }

    public static boolean isRod(ItemStack stack) {
        return (!stack.isEmpty()
                && stack.getItem() == Items.FISHING_ROD);
    }

    public static boolean canPlantOn(Item m, World w, BlockPos pos, BlockPos downPos) {
        if (w.getBlockState(downPos).getBlock() == REED_BLOCK || w.getBlockState(downPos).getBlock() == BAMBOO ||
                (w.getBlockState(downPos).getBlock() == KELP || w.getBlockState(downPos).getBlock() == KELP_PLANT)) {
            return false;
        }
        if (!SEED_MAP.containsValue(m)) return false;
        return SEED_MAP.inverse().get(m).getDefaultState().canPlaceAt(w, pos);
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