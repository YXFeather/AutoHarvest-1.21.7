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
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;

public class BrewMode implements AutoMode{

    private final int radiusInt = (int) Math.ceil(AutoHarvestConfig.getRadius());
    private BlockPos targetBrewingStand = null;

    private final Item netherWart = Items.NETHER_WART;
    private final Item sugar = Items.SUGAR;
    private final Item redStone = Items.REDSTONE;
    private final Item gunpowder = Items.GUNPOWDER;
    private final Item blazePowder = Items.BLAZE_POWDER;
    private final Item glassBottle = Items.GLASS_BOTTLE;
    private int netherWart_slot = -1;
    private int sugar_slot = -1;
    private int redStone_slot = -1;
    private int gunpowder_slot = -1;
    private int blazePowder_slot = -1;
    private int glassBottle_slot = -1;

    private boolean hasScannedAllStands = false;
    private boolean isOpen = false;
    private boolean needsStateUpdate = false;

    // 倒计时相关变量
    private long lastOpenTime = 0L;
    private boolean isWaitingToReopen = false;
    private static final long OPEN_DELAY = 15L; // 1秒 = 20 ticks
    // 取出药水
    private long takeCountdownEndTime = 0;
    private boolean isWaitingForTakeOut = false;
    // 投掷药水
    private long throwCountdownEndTime = 0;
    private boolean isWaitingForThrow = false;



    // 存储所有找到的酿造台及其酿造进度状态
    private Map<BlockPos, BrewingStandState> BREWINGSTANDS = new HashMap<>();
    private Iterator<Map.Entry<BlockPos, BrewingStandState>> BREWINGSTANDITERATOR = null;

    // 酿造台状态类
    private class BrewingStandState {
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
    private boolean isSplashSwiftnessPotion(ItemStack stack) {
        if (stack.isEmpty() || !stack.isOf(Items.SPLASH_POTION)) {
            return false;
        }

        PotionContentsComponent potionContents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (potionContents == null) return false;

        Optional<RegistryEntry<Potion>> potion = potionContents.potion();
        return potion.isPresent() && potion.get().equals(Potions.LONG_SWIFTNESS); // 喷溅型延长速度药水
    }

    private boolean isLongSwiftnessPotion(ItemStack stack) {
        if (stack.isEmpty()) return false;

        PotionContentsComponent potionContents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (potionContents == null) return false;

        Optional<RegistryEntry<Potion>> potion = potionContents.potion();
        return potion.isPresent() && potion.get().equals(Potions.LONG_SWIFTNESS);
    }

    private boolean isSwiftnessPotion(ItemStack stack) {
        if (stack.isEmpty()) return false;

        PotionContentsComponent potionContents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (potionContents == null) return false;

        Optional<RegistryEntry<Potion>> potion = potionContents.potion();
        return potion.isPresent() && potion.get().equals(Potions.SWIFTNESS);
    }

    private boolean isAwkwardPotion(ItemStack stack) {
        if (stack.isEmpty()) return false;

        PotionContentsComponent potionContents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (potionContents == null) return false;

        Optional<RegistryEntry<Potion>> potion = potionContents.potion();
        return potion.isPresent() && potion.get().equals(Potions.AWKWARD);
    }

    private boolean isWaterBottle(ItemStack stack) {
        if (stack.isEmpty() || !stack.isOf(Items.POTION)) {
            return false;
        }

        PotionContentsComponent potionContents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (potionContents == null) return false;

        Optional<RegistryEntry<Potion>> potion = potionContents.potion();
        return potion.isPresent() && potion.get().equals(Potions.WATER);
    }

    // 扫描半径内所有酿造台
    private void scanAllBrewingStands(ClientWorld world, BlockPos center) {
        BREWINGSTANDS.clear();

        Iterable<BlockPos> positions = BlockPos.iterateOutwards(center, radiusInt, radiusInt, radiusInt);
        for (BlockPos pos : positions) {
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof BrewingStandBlock) {
                BlockPos immutablePos = pos.toImmutable();
                BREWINGSTANDS.put(immutablePos, new BrewingStandState());
                System.out.println("[AutoBrewing] 发现酿造台: " + immutablePos);
            }
        }
        System.out.println("[AutoBrewing] 共找到 " + BREWINGSTANDS.size() + " 个酿造台");
        if (BREWINGSTANDS.isEmpty()) {
            disable(2);
        }
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

    // 更新当前酿造台状态
    private void updateCurrentBrewingStandState() {
        ScreenHandler handler = BoxUtil.getScreenHandler();
        if (handler != null && targetBrewingStand != null && BREWINGSTANDS.containsKey(targetBrewingStand)) {
            BrewingStandState state = BREWINGSTANDS.get(targetBrewingStand);
            state.updateState(handler);
            System.out.println("[AutoBrewing] 更新酿造台 " + targetBrewingStand + " 状态: " + state.getProgressDescription());
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
            lastOpenTime = world.getTime();
        }
    }

    private void closeCurrentBrewingStand(Long currentTime) {
            // 关闭之前更新酿造台状态
            if (needsStateUpdate) {
                updateCurrentBrewingStandState();
                needsStateUpdate = false;
            }
            isOpen = false;
            isWaitingToReopen = true;
            lastOpenTime = currentTime;

            // 关闭酿造台GUI
            closeBrewingStandGUI();
    }

    private boolean checkBrewingStand(ClientPlayerEntity player) {
//        if (targetBrewingStand == null) return false;
        // 酿造台界面是否关闭
        if (!isOpen) {
            // 首先检查背包是否有3个水瓶
//            System.out.println("[AutoBrewing] 酿造台界面已关闭");
            if (!haveWaterBottleInPlayerInventory()) {
                getWaterBottle(player);
                return true;
            }
            if (!throwSplashPotion(player)) {
                return true;
            }
        }
        if (targetBrewingStand == null) return false;
        if (isOpen) {
            // 检查酿造台内部是否有水瓶
            if (haveWaterBottleInBrewingStandInventory()) {
                insertWaterBottles(player);
                return true;
            }

            // 如果有喷溅型药品取出
            if (!takeSplashPotion(player)) {
//            System.out.println("[AutoBrewing] 尝试取出喷溅型药品");
                return true;
            }
            Item inputItem = getInputItem();
            Item getRequiredItem = getRequiredItem().item() == null ? null : getRequiredItem().item();
            if (hasBrewingIngredients() && (inputItem == null || inputItem != getRequiredItem)) {
                addBrewingIngredients(player);
                updateCurrentBrewingStandState();
                return true;
            }
        }
        return false;
    }

    private boolean haveWaterBottleInPlayerInventory() {
        PlayerInventory inventory = BoxUtil.getInventory();
        int slotSize = inventory.size();
        int count = 0;
        for (int i = 0; i < slotSize; i++) {
            ItemStack itemStack = inventory.getStack(i);
            if (itemStack.isOf(glassBottle)) {
                glassBottle_slot = i;
            }
            if (isWaterBottle(itemStack)) {
                count++;
                if (count == 3) {return true;}
            }
        }
        if (glassBottle_slot == -1) {
            disable(3);
            return false;
        }
        return false;
    }

    // 利用脚下含水方块获取水瓶
    private void getWaterBottle(ClientPlayerEntity player) {
        World world = BoxUtil.getWorld();

        Vec3d playerPos = BoxUtil.getPlayerPos();
        if (playerPos == null) return;

        int slotIndex = -1;
        int glassBottleIndex = glassBottle_slot;
        PlayerInventory playerInventory = BoxUtil.getInventory();
        ItemStack glassBottle = player.getInventory().getStack(glassBottleIndex);

        if (glassBottle.getCount() <= 2) {
            slotIndex = findItemSlot(playerInventory.size(), Items.GLASS_BOTTLE);
            if (slotIndex != -1 ) {
                glassBottleIndex = slotIndex;
            }
        }
        if (glassBottleIndex == -1 || slotIndex == -1){
            disable(3);
            return;
        }

        HandItemRefill.swapWithSelected(glassBottleIndex);
        SmoothLookHelper.lookAtPosition(player, playerPos);

        BlockState blockState = world.getBlockState(BlockPos.ofFloored(playerPos).down());
        if (blockState.isIn(BlockTags.LEAVES)) {
            FluidState fluidState = blockState.getFluidState();
            if (fluidState.isIn(FluidTags.WATER)) {
                InteractionHelper.interactItem(player, Hand.MAIN_HAND);
//                    System.out.println("Water Bottle Found");
                return;
            }
        }
        disable(1);
    }

    private boolean haveWaterBottleInBrewingStandInventory() {
        ScreenHandler handler = BoxUtil.getScreenHandler();
        if (handler == null) return false;
        int waterBottleCount = 0;
        int slotCount = 0;
        for (int i = 0; i < 3; i ++) {
            ItemStack itemStack = handler.getSlot(i).getStack();
            if (isWaterBottle(itemStack)) {
                waterBottleCount++;
            } else if (itemStack.isEmpty()) {
                slotCount++;
            } else {
                return false;
            }
        }
        if (waterBottleCount == 3) return false;
        else if (slotCount == 3) return true;
        else return true;
    }

    private int findItemInPlayerContainer(ScreenHandler handler, Item requiredItem, java.util.function.Predicate<ItemStack> additionalCheck) {
        // 遍历玩家在容器中的槽位（索引5-43）
        for (int i = 5; i < 46; i++) {
            // 确保索引不超出实际槽位数量
            if (i >= handler.slots.size()) break;

            ItemStack itemStack = handler.getSlot(i).getStack();
            if (itemStack.isEmpty()) continue;

            // 检查物品类型是否匹配
            if (itemStack.isOf(requiredItem)) {
                // 如果提供了额外检查条件，则执行检查
                if (additionalCheck == null || additionalCheck.test(itemStack)) {
                    return i; // 返回容器中的绝对索引
                }
            }
        }
        disable(-1);
        return -1; // 未找材料到则退出循环
    }

    private boolean hasBrewingIngredients() {
        ScreenHandler handler = BoxUtil.getScreenHandler();
        if (handler == null) return false;

        netherWart_slot = -1;
        sugar_slot = -1;
        redStone_slot = -1;
        gunpowder_slot = -1;
        blazePowder_slot = -1;

        for (int i = 5; i < 46; i++) {
            // 确保索引不超出实际槽位数量
            if (i >= handler.slots.size()) break;

            ItemStack itemStack = handler.getSlot(i).getStack();
            if (itemStack.isEmpty()) continue;
            if (itemStack.isOf(netherWart)) netherWart_slot = i;
            if (itemStack.isOf(sugar)) sugar_slot = i;
            if (itemStack.isOf(redStone)) redStone_slot = i;
            if (itemStack.isOf(gunpowder)) gunpowder_slot = i;
            if (itemStack.isOf(blazePowder)) blazePowder_slot = i;
        }
        if (netherWart_slot == -1 || sugar_slot == -1 ||
                redStone_slot == -1 || gunpowder_slot == -1 ||
                blazePowder_slot == -1) {
            disable(-1);
            return false;
        }
        return true;
    }

    private Item getItemFromInventory() {
        if (netherWart_slot == -1) return netherWart;
        if (sugar_slot == -1) return sugar;
        if (redStone_slot == -1) return redStone;
        if (gunpowder_slot == -1) return gunpowder;
        if (blazePowder_slot == -1) return blazePowder;
        return getRequiredItem().item();
    }

    private void insertWaterBottles(ClientPlayerEntity player) {
        ScreenHandler handler = BoxUtil.getScreenHandler();
        if (handler == null) return;

        int sourceSlot = findItemInPlayerContainer(handler, Items.POTION, this::isWaterBottle);
        if (sourceSlot != -1) {
            InteractionHelper.clickSlot(handler, sourceSlot, 0, SlotActionType.QUICK_MOVE, player);
        }
    }

    // 添加酿造材料
    private void addBrewingIngredients(ClientPlayerEntity player) {
        ScreenHandler handler = BoxUtil.getScreenHandler();
        if (handler == null || targetBrewingStand == null) return;

        Item requiredIngredient = getRequiredItem().item();
        int sourceSlot = getRequiredItem().slot();
        if (requiredIngredient == null || sourceSlot == -1) return;

        // 检查输入槽位（索引3和4）是否为空
        ItemStack inputSlot = handler.getSlot(3).getStack();
        ItemStack fuelSlot = handler.getSlot(4).getStack();
        if (fuelSlot.isEmpty()) {
            // 从玩家背包中寻找所需烈焰粉
            int fuelItem = blazePowder_slot;
            if (fuelItem != -1) {
                // 将 fuelItem 放入燃料槽位
                InteractionHelper.swapClickSlot(handler, 4, fuelItem, player);
            }
        }

        if (inputSlot.isEmpty()) {
            // 将材料放入输入槽位
            InteractionHelper.swapClickSlot(handler, 3, sourceSlot, player);
        } else if (inputSlot != requiredIngredient.getDefaultStack()) {
            InteractionHelper.clickSlot(handler, 3, 0, SlotActionType.QUICK_MOVE, player);
        }
    }

    public record ItemSlot(Item item, int slot) {}

    private ItemSlot getRequiredItem() {
        ScreenHandler handler = BoxUtil.getScreenHandler();
        if (handler == null || targetBrewingStand == null) return null;

        // 获取当前酿造台状态
        BrewingStandState state = BREWINGSTANDS.get(targetBrewingStand);
        if (state == null) return null;

        int progress = state.getProgress();
//        System.out.println("[AutoBrewing] 酿造进度: " + progress);

        // 根据进度确定需要的材料
        return switch (progress) {
            case 1 -> // 水瓶 -> 粗制药水，需要地狱疣
                    new ItemSlot(netherWart, netherWart_slot);
            case 2 -> // 粗制药水 -> 速度药水，需要糖
                    new ItemSlot(sugar, sugar_slot);
            case 3 -> // 速度药水 -> 延长速度药水，需要红石
                    new ItemSlot(redStone, redStone_slot);
            case 4 -> // 延长速度药水 -> 喷溅型延长速度药水，需要火药
                    new ItemSlot(gunpowder, gunpowder_slot);
            default -> // 其他状态下不需要添加材料
                    new ItemSlot(null, -1);
        };
    }

    private Item getInputItem() {
        ScreenHandler handler = BoxUtil.getScreenHandler();
        return handler.getSlot(3).getStack().getItem();
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

        int splashSlot = hasAnySplashPotion();
        // 执行取出操作
        if (splashSlot != -1) {
            ScreenHandler handler = BoxUtil.getScreenHandler();
            int waterBottleSlot = findItemInPlayerContainer(handler, Items.POTION, this::isWaterBottle);
            InteractionHelper.clickSlot(handler, splashSlot, 0, SlotActionType.QUICK_MOVE, player);
            InteractionHelper.clickSlot(handler, waterBottleSlot, 0, SlotActionType.QUICK_MOVE, player);
//            addNetherWart(player);

            // 设置倒计时
            isWaitingForTakeOut = true;
            startCountdown(60, true); // 500ms取出等待时间
            return false; // 正在处理
        }
        return true; // 没有待处理的取出操作
    }

    private int hasAnySplashPotion() {
        ScreenHandler handler = BoxUtil.getScreenHandler();
        if (handler == null) return -1;

        for (int i = 0; i < 3; i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (isSplashSwiftnessPotion(stack)) {
                return i;
            }
        }
        return -1;
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

        // 执行投掷操作
        // 开始投掷倒计时
        isWaitingForThrow = true;
        startCountdown(60, false); // 800ms投掷等待时间
        return false; // 正在处理
    }

    private void throwPotionFromInventory(ClientPlayerEntity player) {
        PlayerInventory inventory = BoxUtil.getInventory();
        int slotIndex = findItemSlot(inventory.size(), Items.SPLASH_POTION);

        if (slotIndex == -1) return;

        SmoothLookHelper.lookAtPosition(player, player.getPos());
        HandItemRefill.swapWithSelected(slotIndex);
        InteractionHelper.interactItem(player, Hand.MAIN_HAND);
    }

    private int findItemSlot(int slotSize, Item item) {
        PlayerInventory inventory = BoxUtil.getInventory();
        int bestSlot = -1;

        for (int slot = 0; slot < slotSize; slot++) {
            if (inventory.getStack(slot).getItem() == item) {
                bestSlot = slot;
                return bestSlot;
            }
        }
        return bestSlot;
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

    private void disable (int reason){
        ClientPlayerEntity player = BoxUtil.getPlayer();
        if (reason == 1) {
            player.sendMessage(
                    Text.translatable("autoharvest.message.checkWaterBlock", getName()),
                    false
            );
        } else if (reason == 2) {
            player.sendMessage(
                    Text.translatable("autoharvest.message.checkBrewingStand", getName()),
                    false
            );
        } else if (reason == 3) {
            player.sendMessage(
                    Text.translatable("autoharvest.message.checkGlassBottle", getName(), glassBottle),
                    false
            );
        }
        else {
            player.sendMessage(
                    Text.translatable("autoharvest.message.materialShortage", getName() , getItemFromInventory()),
                    false
            );
        }
        closeBrewingStandGUI();
        ModeManager.INSTANCE.toggle();
    }

    @Override
    public void tick() {
        ClientPlayerEntity player = BoxUtil.getPlayer();
        ClientWorld world = BoxUtil.getWorld();
        if (player == null || world == null) return;

        long currentTime = world.getTime();

        // 先检查酿造台内部和背包情况
        if (checkBrewingStand(player)) return;

        // 如果还没扫描过所有酿造台，先进行扫描
        if (!hasScannedAllStands) {
            scanAllBrewingStands(world, player.getBlockPos());
            hasScannedAllStands = true;
            initializeIterator();
            return;
        }

        // 如果正在等待重新打开，检查延迟是否结束
        if (isWaitingToReopen) {
            if (currentTime - lastOpenTime >= OPEN_DELAY) {
                isWaitingToReopen = false;
                moveToNextBrewingStand();
            }
            return;
        }

        // 如果已经打开了酿造台，检查是否需要关闭
        if (isOpen && targetBrewingStand != null) {
            if (currentTime - lastOpenTime >= OPEN_DELAY) {
                closeCurrentBrewingStand(currentTime);
            }
        }

        // 如果没有打开酿造台，尝试打开下一个
        if (!isOpen  && !isWaitingToReopen) {
            openNextBrewingStand(player, world);
        }
    }

    @Override
    public String getName() {
        return Text.translatable("autoharvest.mode.brewing").getString();
    }

    @Override
    public void onDisable() {
        BREWINGSTANDS.clear();
        targetBrewingStand = null;
        hasScannedAllStands = false;
        isOpen = false;
        needsStateUpdate = false;
        netherWart_slot = -1;
        sugar_slot = -1;
        redStone_slot = -1;
        gunpowder_slot = -1;
        blazePowder_slot = -1;
        glassBottle_slot = -1;
    }
}


