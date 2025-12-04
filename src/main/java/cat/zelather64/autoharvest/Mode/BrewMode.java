package cat.zelather64.autoharvest.Mode;

import cat.zelather64.autoharvest.Config.AutoHarvestConfig;
import cat.zelather64.autoharvest.ModeManger.ModeManager;
import cat.zelather64.autoharvest.Utils.BoxUtil;
import cat.zelather64.autoharvest.Utils.HandItemRefill;
import cat.zelather64.autoharvest.Utils.InteractionHelper;
import cat.zelather64.autoharvest.Utils.SmoothLookHelper;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.potion.Potions;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;

public class BrewMode implements AutoMode{

    private static final int SEARCH_RADIUS = (int) Math.ceil(AutoHarvestConfig.getRadius());
    private BlockPos targetBrewingStand = null;
    private long lastActionTime = 0L;
    private static final long ACTION_DELAY = 15L; // 1秒 = 20 ticks
    private boolean isOpen = false;
    private boolean isWaitingToReopen = false;
    private boolean needsStateUpdate = false; // 标记是否需要更新状态

    // 取出药水相关变量
    private long takeCountdownEndTime = 0;
    private boolean isWaitingForTakeOut = false;

    // 投掷药水相关变量
    private long throwCountdownEndTime = 0;
    private boolean isWaitingForThrow = false;

    // 存储所有找到的酿造台及其酿造进度状态
    private Map<BlockPos, BrewingStandState> BREWINGSTANDS = new HashMap<>();
    private Iterator<Map.Entry<BlockPos, BrewingStandState>> BREWINGSTANDITERATOR = null;
    private boolean hasScannedAllStands = false;

    @Override
    public void tick() {
        ClientPlayerEntity player = BoxUtil.getPlayer();
        ClientWorld world = BoxUtil.getWorld();
        if (player == null || world == null) return;

        long currentTime = world.getTime();

        // 首先检查是否有3个水瓶，酿造台界面是否关闭
        if (!isOpen && haveWaterBottle(player) != 3) {
            getWaterBottle(player);
            return;
        }

        if (!isOpen && !throwSplashPotion(player)) {
            return;
        }

        // 如果有喷溅型药品取出
        if (isOpen && !takeSplashPotion(player)) {
            System.out.println("[AutoBrewing] 尝试取出喷溅型药品");
            return;
        }

        // 如果还没扫描过所有酿造台，先进行扫描
        if (!hasScannedAllStands) {
            scanAllBrewingStands(world, player.getBlockPos());
            hasScannedAllStands = true;
            initializeIterator();
            return;
        }

        // 如果正在等待重新打开，检查延迟是否结束
        if (isWaitingToReopen) {
            if (currentTime - lastActionTime >= ACTION_DELAY) {
                isWaitingToReopen = false;
                // 在关闭后打印所有酿造台状态
                // printAllBrewingStandStates();

                moveToNextBrewingStand();
            }
            return;
        }

        // 如果已经打开了酿造台，检查是否需要关闭
        if (isOpen && targetBrewingStand != null) {

            if (currentTime - lastActionTime >= ACTION_DELAY) {
                // 关闭酿造台GUI
                closeBrewingStandGUI();
                isOpen = false;
                lastActionTime = currentTime;
                isWaitingToReopen = true;
                return;
            }

            // 更新酿造台状态（只更新一次）
            if (needsStateUpdate) {
                updateCurrentBrewingStandState();
                needsStateUpdate = false;
            }

            // 先检查酿造台内部情况
            if (checkBrewingStand(player)) return;
        }

        // 如果没有打开的酿造台且不在等待状态，打开下一个酿造台
        if (!isOpen && !isWaitingToReopen) {
            openNextBrewingStand(player, world);
        }
    }

    // 扫描半径内所有酿造台
    private void scanAllBrewingStands(ClientWorld world, BlockPos center) {
        BREWINGSTANDS.clear();

        Iterable<BlockPos> positions = BlockPos.iterateOutwards(center, SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS);
        for (BlockPos pos : positions) {
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof BrewingStandBlock) {
                BlockPos immutablePos = pos.toImmutable();
                BREWINGSTANDS.put(immutablePos, new BrewingStandState());
                System.out.println("[AutoBrewing] 发现酿造台: " + immutablePos);
            }
        }
        System.out.println("[AutoBrewing] 共找到 " + BREWINGSTANDS.size() + " 个酿造台");
    }

    // 初始化迭代器
    private void initializeIterator() {
        if (!BREWINGSTANDS.isEmpty()) {
            BREWINGSTANDITERATOR = BREWINGSTANDS.entrySet().iterator();
        }
    }

    // 移动到下一个酿造台
    private void moveToNextBrewingStand() {
        if (BREWINGSTANDITERATOR == null || !BREWINGSTANDITERATOR.hasNext()) {
            // 重新开始迭代
            initializeIterator();
        }

        if (BREWINGSTANDITERATOR != null && BREWINGSTANDITERATOR.hasNext()) {
            Map.Entry<BlockPos, BrewingStandState> entry = BREWINGSTANDITERATOR.next();
            targetBrewingStand = entry.getKey();
            System.out.println("[AutoBrewing] 切换到酿造台: " + targetBrewingStand);
        } else {
            targetBrewingStand = null;
        }
    }

    // 打开下一个酿造台
    private void openNextBrewingStand(ClientPlayerEntity player, ClientWorld world) {
        if (targetBrewingStand == null) {
            moveToNextBrewingStand();
        }

        if (targetBrewingStand != null) {
            openBrewingStand(player, targetBrewingStand);
            isOpen = true;
            needsStateUpdate = true; // 标记需要更新状态
            lastActionTime = world.getTime();
        }
    }

    // 更新当前酿造台状态
    private void updateCurrentBrewingStandState() {
        ScreenHandler handler = BoxUtil.getScreenHandler();
        if (handler != null && targetBrewingStand != null && BREWINGSTANDS.containsKey(targetBrewingStand)) {
            BrewingStandState state = BREWINGSTANDS.get(targetBrewingStand);
            state.updateState(handler);
            System.out.println("[AutoBrewing] 更新酿造台 " + targetBrewingStand + " 状态: " + state.getProgressDescription());
        }
    }

    private boolean checkBrewingStand(ClientPlayerEntity player) {
        if (isOpen) {
            // 如果酿造台还没有放入瓶子，检查并放入空瓶
            if (hasInsertedBottles()) {
                System.out.println("[AutoBrewing] 尝试放入空瓶");
                insertEmptyBottles(player);
                addNetherWart(player);
                return true;
            }

            // 如果已经放入瓶子但还没有添加材料，检查并添加酿造材料
            if (hasAddedIngredients() || getInputItem() != getRequiredItem()) {
                System.out.println("[AutoBrewing] 尝试添加酿造材料");
                addBrewingIngredientsIfNeeded(player);
                return true;
            }
        }
        return false;
    }

    public boolean hasInsertedBottles() {
        ScreenHandler handler = BoxUtil.getScreenHandler();
        if (handler == null) return false;
        int potionCount = 0;
        int waterBottleCount = 0;
        for (int i = 0; i < 3; i++) {
            ItemStack bottleSlot = handler.getSlot(i).getStack();
            if (!bottleSlot.isEmpty()) {
                if (!isWaterBottle2(bottleSlot)) {
                    potionCount++;
                }
                waterBottleCount++;
            }
        }
        if (potionCount > 0) {
            return false;
        }
        return !(waterBottleCount == 3);
        }

    private void insertEmptyBottles(ClientPlayerEntity player) {
        ScreenHandler handler = BoxUtil.getScreenHandler();
        if (handler == null) return;

        for (int bottleSlot = 0; bottleSlot < 3; ++ bottleSlot) {
            // 从玩家背包中寻找水瓶
            int sourceSlot = findItemInPlayerContainer(handler, Items.POTION, this::isWaterBottle2);
            if (sourceSlot != -1) {
                InteractionHelper.clickSlot(handler, sourceSlot, 0, SlotActionType.QUICK_MOVE, player);
            }
        }
    }

    // 添加酿造材料
    private void addBrewingIngredientsIfNeeded(ClientPlayerEntity player) {
        ScreenHandler handler = BoxUtil.getScreenHandler();
        if (handler == null || targetBrewingStand == null) return;

        Item requiredIngredient = getRequiredItem();

        if (requiredIngredient == null) return;

        // 检查输入槽位（索引3）是否为空
        ItemStack inputSlot = handler.getSlot(3).getStack();
        int sourceSlot = findItemInPlayerContainer(handler, requiredIngredient, null);

        if (inputSlot.isEmpty()) {
            // 从玩家背包中寻找所需材料
            if (sourceSlot != -1) {
                // 将材料放入输入槽位
                InteractionHelper.clickSlot(handler, sourceSlot, 0, SlotActionType.PICKUP, player);
                InteractionHelper.clickSlot(handler, 3, 1, SlotActionType.PICKUP, player);
                InteractionHelper.clickSlot(handler, sourceSlot, 0, SlotActionType.PICKUP, player);
            }
        } else if (inputSlot != requiredIngredient.getDefaultStack()) {
            InteractionHelper.clickSlot(handler, 3, 0, SlotActionType.QUICK_MOVE, player);
        }
    }

    private void addNetherWart(ClientPlayerEntity  player) {
        ScreenHandler handler = BoxUtil.getScreenHandler();
        // 检查输入槽位（索引3）是否为空
        ItemStack inputSlot = handler.getSlot(3).getStack();
        int sourceSlot = findItemInPlayerContainer(handler, Items.NETHER_WART, null);

        if (inputSlot.isEmpty()) {
            // 从玩家背包中寻找所需材料
            if (sourceSlot != -1) {
                // 将材料放入输入槽位
                InteractionHelper.clickSlot(handler, sourceSlot, 0, SlotActionType.PICKUP, player);
                InteractionHelper.clickSlot(handler, 3, 1, SlotActionType.PICKUP, player);
                InteractionHelper.clickSlot(handler, sourceSlot, 0, SlotActionType.PICKUP, player);
            }
        }
    }

    private boolean hasAnySplashPotion() {
        ScreenHandler handler = BoxUtil.getScreenHandler();
        if (handler == null) return false;

        for (int i = 0; i < 3; i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (isSplashSwiftnessPotion(stack)) {
                return true;
            }
        }
        return false;
    }

    // 独立的取出药水方法
    private boolean takeSplashPotion(ClientPlayerEntity player) {
        // 如果正在等待倒计时结束
        if (isWaitingForTakeOut) {
            if (isCountdownFinished(true)) {
                isWaitingForTakeOut = false;
                takeCountdownEndTime = 0;
                return true; // 取出完成
            } else {
                return false; // 仍在等待
            }
        }

        // 执行取出操作
        if (hasAnySplashPotion()) {
            int splashSlot = getSplashItem();
            if (splashSlot != -1) {
                ScreenHandler handler = BoxUtil.getScreenHandler();
                InteractionHelper.clickSlot(handler, splashSlot, 0, SlotActionType.QUICK_MOVE, player);
//                closeBrewingStandGUI();

                // 设置倒计时
                isWaitingForTakeOut = true;
                startCountdown(60, true); // 500ms取出等待时间
                return false; // 正在处理
            }
        }

        return true; // 没有待处理的取出操作
    }

    // 独立的投掷药水方法
    private boolean throwSplashPotion(ClientPlayerEntity player) {
        // 如果正在等待倒计时结束
        if (isWaitingForThrow) {
            if (isCountdownFinished(false)) {
                isWaitingForThrow = false;
                throwCountdownEndTime = 0;
                throwPotionFromInventory(player);
                return true; // 投掷完成
            } else {
                return false; // 仍在等待
            }
        }

        // 执行投掷操作（这里可以根据你的逻辑调整）
        // 比如检查背包中是否有药水等
        // 现在假设直接开始投掷倒计时
        isWaitingForThrow = true;
        startCountdown(60, false); // 800ms投掷等待时间
        return false; // 正在处理
    }

    // 通用倒计时方法
    private void startCountdown(long milliseconds, boolean isTakeOperation) {
        if (isTakeOperation) {
            takeCountdownEndTime = System.currentTimeMillis() + milliseconds;
        } else {
            throwCountdownEndTime = System.currentTimeMillis() + milliseconds;
        }
    }

    private boolean isCountdownFinished(boolean isTakeOperation) {
        if (isTakeOperation) {
            return System.currentTimeMillis() >= takeCountdownEndTime;
        } else {
            return System.currentTimeMillis() >= throwCountdownEndTime;
        }
    }

    private boolean isCountingDown(boolean isTakeOperation) {
        if (isTakeOperation) {
            return takeCountdownEndTime > 0 && !isCountdownFinished(true);
        } else {
            return throwCountdownEndTime > 0 && !isCountdownFinished(false);
        }
    }

    private void throwPotionFromInventory(ClientPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        int slotIndex = findItemSlot(player, inventory.size(), Items.SPLASH_POTION);

        if (slotIndex == -1) return;

        HandItemRefill.swapWithSelected(slotIndex);
        InteractionHelper.interactItem(player, Hand.MAIN_HAND);
    }

    private int findItemSlot(ClientPlayerEntity player, int slotSize, Item item) {
        int currentSlot = player.getInventory().getSelectedSlot();
        int bestSlot = -1;
        int minDistance = Integer.MAX_VALUE;

        PlayerInventory inventory = player.getInventory();

        for (int slot = 0; slot < slotSize; slot++) {
            if (inventory.getStack(slot).getItem() == item) {
                int distance = Math.abs(slot - currentSlot);
                if (distance < minDistance) {
                    minDistance = distance;
                    bestSlot = slot;
                }
            }
        }
        return bestSlot;
    }

//    private boolean haveSplashPotion () {
//        ScreenHandler handler = BoxUtil.getScreenHandler();
//        if (handler == null) return false;
//
//        for (int i = 0; i < 3; i++) {
//            Item bottleSlot = handler.getSlot(i).getStack().getItem();
//            if (bottleSlot == Items.SPLASH_POTION) {
//                isWaitingToReopen = true;
//                return true;
//            }
//        }
//        return false;
//    }

    private int getSplashItem() {
        ScreenHandler handler = BoxUtil.getScreenHandler();
        if (handler == null) return -1;
        for (int i = 0; i < 3; i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (isSplashSwiftnessPotion(stack)) return i;
        }
        return -1;
    }

    private void getWaterBottle(ClientPlayerEntity player) {
        World world = BoxUtil.getWorld();
        PlayerInventory inventory = player.getInventory();
        Vec3d playerPos = BoxUtil.getPlayerPos();
        if (playerPos == null) return;

        SmoothLookHelper.lookAtPosition(player, playerPos);
        int slotSize = inventory.size();
        int slotIndex = findItemSlot(player, slotSize, Items.GLASS_BOTTLE);
        if (slotIndex == -1) disable(player);

        HandItemRefill.swapWithSelected(slotIndex);

        for (BlockPos pos : BlockPos.iterateOutwards(BlockPos.ofFloored(playerPos), SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS)) {
            BlockState blockState = world.getBlockState(pos);
            if (blockState.isIn(BlockTags.LEAVES)) {
                FluidState fluidState = blockState.getFluidState();
                if (fluidState.isIn(FluidTags.WATER)) {
                    InteractionHelper.interactItem(player, Hand.MAIN_HAND);
                    System.out.println("Water Bottle Found");
                    return;
                }
            }
        }
    }

    private int haveWaterBottle(ClientPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        int slotSize = inventory.size();
        int count = 0;
        for (int i = 0; i < slotSize; i++) {
            ItemStack itemStack = inventory.getStack(i);
            if (isWaterBottle(itemStack)) {
                count++;
                if (count == 3) {return count;}
            }
        }
        return -1;
    }

    private Item getRequiredItem() {
        ScreenHandler handler = BoxUtil.getScreenHandler();
        System.out.println("[AutoBrewing] 获取酿造台" + targetBrewingStand);
        if (handler == null || targetBrewingStand == null) return null;

        // 获取当前酿造台状态
        BrewingStandState state = BREWINGSTANDS.get(targetBrewingStand);

        if (state == null) return null;

        int progress = state.getProgress();

        System.out.println("[AutoBrewing] 酿造进度: " + progress);

        // 根据进度确定需要的材料
        return switch (progress) {
            case 1 -> // 水瓶 -> 粗制药水，需要地狱疣
                    Items.NETHER_WART;
            case 2 -> // 粗制药水 -> 速度药水，需要糖
                    Items.SUGAR;
            case 3 -> // 速度药水 -> 延长速度药水，需要红石
                    Items.REDSTONE;
            case 4 -> // 延长速度药水 -> 喷溅型延长速度药水，需要火药
                    Items.GUNPOWDER;
            default -> // 其他状态下不需要添加材料
                    null;
        };
    }

    private Item getInputItem() {
        ScreenHandler handler = BoxUtil.getScreenHandler();
        return handler.getSlot(3).getStack().getItem();
    }

    private boolean hasAddedIngredients() {
        ScreenHandler handler = BoxUtil.getScreenHandler();
        if (handler == null) return false;
        ItemStack inputSlot = handler.getSlot(3).getStack();
        return inputSlot.isEmpty();
    }

    private int findItemInPlayerContainer(ScreenHandler handler, Item requiredItem, java.util.function.Predicate<ItemStack> additionalCheck) {
        // 遍历玩家在容器中的槽位（索引5-43）
        for (int i = 5; i < 43; i++) {
            // 确保索引不超出实际槽位数量
            if (i >= handler.slots.size()) break;

            Slot slot = handler.slots.get(i);

            if (!slot.hasStack()) continue;

            ItemStack stack = slot.getStack();

            // 检查物品类型是否匹配
            if (stack.isOf(requiredItem)) {
                // 如果提供了额外检查条件，则执行检查
                if (additionalCheck == null || additionalCheck.test(stack)) {
                    return i; // 返回容器中的绝对索引
                }
            }
        }
        disable(BoxUtil.getPlayer());
        return -1; // 未找材料到则退出循环
    }

    // 酿造台状态类
    private static class BrewingStandState {
        private boolean hasWaterBottles = false;
        private boolean hasAwkwardPotions = false;
        private boolean hasSwiftnessPotions = false;
        private boolean hasLongSwiftnessPotions = false;
        private boolean hasSplashSwiftnessPotions = false;

        // 更新酿造台状态
        public void updateState(ScreenHandler handler) {
            if (handler == null) return;

            // 重置状态
            reset();

            // 检查前3个槽位（水瓶槽位）
            for (int i = 0; i < 3; i++) {
                if (i < handler.slots.size()) {
                    ItemStack stack = handler.getSlot(i).getStack();
                    if (!stack.isEmpty()) {
                        updatePotionState(stack);
                    }
                }
            }
        }

        // 根据药水类型更新对应状态（注意顺序：喷溅型在前）
        private void updatePotionState(ItemStack stack) {
            if (isSplashSwiftnessPotion(stack)) {
                hasSplashSwiftnessPotions = true;
            } else if (isLongSwiftnessPotion(stack)) {
                hasLongSwiftnessPotions = true;
            } else if (isSwiftnessPotion(stack)) {
                hasSwiftnessPotions = true;
            } else if (isAwkwardPotion(stack)) {
                hasAwkwardPotions = true;
            } else if (isWaterBottle(stack)) {
                hasWaterBottles = true;
            }
        }

        // 重置状态
        public void reset() {
            hasWaterBottles = false;
            hasAwkwardPotions = false;
            hasSwiftnessPotions = false;
            hasLongSwiftnessPotions = false;
            hasSplashSwiftnessPotions = false;
        }

        // 获取完成度（根据酿造阶段）
        public int getProgress() {
            if (hasSplashSwiftnessPotions) return 5; // 完成 - 喷溅型延长时间速度药水
            if (hasLongSwiftnessPotions) return 4;   // 延长速度药水
            if (hasSwiftnessPotions) return 3;       // 速度药水
            if (hasAwkwardPotions) return 2;         // 粗制药水
            if (hasWaterBottles) return 1;           // 水瓶
            return 0; // 空
        }

        // 获取进度描述
        public String getProgressDescription() {
            return switch (getProgress()) {
                case 0 -> "空";
                case 1 -> "水瓶";
                case 2 -> "粗制药水";
                case 3 -> "速度药水";
                case 4 -> "延长速度药水";
                case 5 -> "喷溅型延长速度药水";
                default -> "未知";
            };
        }
    }

    // 药水类型检查方法（注意顺序：喷溅型在前）
    private static boolean isSplashSwiftnessPotion(ItemStack stack) {
        if (stack.isEmpty() || !stack.isOf(Items.SPLASH_POTION)) {
            return false;
        }

        PotionContentsComponent potionContents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (potionContents == null) return false;

        Optional<RegistryEntry<Potion>> potion = potionContents.potion();
        return potion.isPresent() && potion.get().equals(Potions.LONG_SWIFTNESS); // 喷溅型延长速度药水
    }

    private static boolean isLongSwiftnessPotion(ItemStack stack) {
        if (stack.isEmpty()) return false;

        PotionContentsComponent potionContents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (potionContents == null) return false;

        Optional<RegistryEntry<Potion>> potion = potionContents.potion();
        return potion.isPresent() && potion.get().equals(Potions.LONG_SWIFTNESS);
    }

    private static boolean isSwiftnessPotion(ItemStack stack) {
        if (stack.isEmpty()) return false;

        PotionContentsComponent potionContents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (potionContents == null) return false;

        Optional<RegistryEntry<Potion>> potion = potionContents.potion();
        return potion.isPresent() && potion.get().equals(Potions.SWIFTNESS);
    }

    private static boolean isAwkwardPotion(ItemStack stack) {
        if (stack.isEmpty()) return false;

        PotionContentsComponent potionContents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (potionContents == null) return false;

        Optional<RegistryEntry<Potion>> potion = potionContents.potion();
        return potion.isPresent() && potion.get().equals(Potions.AWKWARD);
    }

    private static boolean isWaterBottle(ItemStack stack) {
        if (stack.isEmpty() || !stack.isOf(Items.POTION)) {
            return false;
        }

        PotionContentsComponent potionContents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (potionContents == null) return false;

        Optional<RegistryEntry<Potion>> potion = potionContents.potion();
        return potion.isPresent() && potion.get().equals(Potions.WATER);
    }

    private boolean isWaterBottle2(ItemStack stack) {
        if (stack.isEmpty() || !stack.isOf(Items.POTION)) {
            return false;
        }

        PotionContentsComponent potionContents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (potionContents == null) return false;

        Optional<RegistryEntry<Potion>> potion = potionContents.potion();
        return potion.isPresent() && potion.get().equals(Potions.WATER);
    }

    @Override
    public String getName() {
        return Text.translatable("autoharvest.mode.brewing").getString();
    }

    @Override
    public void onDisable() {
        targetBrewingStand = null;
        isOpen = false;
        isWaitingToReopen = false;
        needsStateUpdate = false;
        lastActionTime = 0L;
        BREWINGSTANDS.clear();
        BREWINGSTANDITERATOR = null;
        hasScannedAllStands = false;
    }

    private void openBrewingStand(ClientPlayerEntity player, BlockPos pos) {
        // System.out.println("[AutoBrewing] 打开酿造台: " + pos);
        if (pos != null) {
            SmoothLookHelper.lookAtPosition(player, pos.toCenterPos());
        }
        InteractionHelper.interactBlock(player, pos, Hand.MAIN_HAND, Direction.UP);
    }

    private void closeBrewingStandGUI() {
        // System.out.println("[AutoBrewing] 关闭酿造台GUI");
        if (MinecraftClient.getInstance().currentScreen != null) {
            MinecraftClient.getInstance().currentScreen.close();
        }
    }

    private void disable (ClientPlayerEntity player){
        player.sendMessage(
                Text.translatable("autoharvest.message.materialShortage", getName()),
                false
        );
        ModeManager.INSTANCE.toggle();
    }
}


