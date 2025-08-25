package cat.zelather64.autoharvest;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AllayEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.AxolotlEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

public class TickListener {
    private final Configure configure;
    private ClientPlayerEntity p;

    private long fishBitesAt = 0L;
    private ItemStack lastUsedItem = null;

    public TickListener(Configure configure, ClientPlayerEntity player) {
        this.configure = configure;
        this.p = player;
        ClientTickEvents.END_CLIENT_TICK.register(e -> {
            if (AutoHarvest.INSTANCE.overlayRemainingTick > 0) {
                AutoHarvest.INSTANCE.overlayRemainingTick--;
            }
            if (AutoHarvest.INSTANCE.Switch)
                onTick(e.player);
        });
    }

    public void Reset() {
        lastUsedItem = null;
        fishBitesAt = 0L;
    }

    public void onTick(ClientPlayerEntity player) {
        try {
            if (player != p) {
                this.p = player;
                AutoHarvest.INSTANCE.Switch = false;
                AutoHarvest.msg("notify.turn.off");
                return;
            }
            if (AutoHarvest.INSTANCE.taskManager.Count() > 0) {
                AutoHarvest.INSTANCE.taskManager.RunATask();
                return;
            }
            switch (AutoHarvest.INSTANCE.mode) {
                case WEED -> weedTick();
                case HARVEST -> harvestTick();
                case PLANT -> mainPlantTick();

                case Farmer -> {
                    harvestTick();
                    mainPlantTick();
                }
                case FEED -> feedTick();
                case FISHING -> fishingTick();
                case BONEMEALING -> bonemealingTick();
                case HOEING -> {
                    mainHoeingTick();
                    offHoeingTick();
                }
                case AXEITEMS -> {
                    mainHandStripTick();
                    offHandStripTick();
                }
            }
            if (AutoHarvest.INSTANCE.mode != AutoHarvest.HarvestMode.FISHING)
                AutoHarvest.INSTANCE.taskManager.Add_TickSkip(configure.tickSkip.getValue());
        } catch (Exception ex) {
            AutoHarvest.msg("notify.tick_error");
            AutoHarvest.msg("notify.turn.off");
            ex.printStackTrace();
            AutoHarvest.INSTANCE.Switch = false;
        }
    }

    /* 手执行左键动作 */
    private void leftButton(BlockPos pos) {
        assert MinecraftClient.getInstance().interactionManager != null;
        MinecraftClient.getInstance().interactionManager.attackBlock(pos, Direction.UP);
    }

    /* 手执行右键工作 */
    private void rightButton(double X, double Y, double Z, Direction direction, BlockPos pos, Hand hand) {
        assert MinecraftClient.getInstance().interactionManager != null;
        BlockHitResult blockHitResult = new BlockHitResult(new Vec3d(X, Y, Z), direction, pos, false);
        MinecraftClient.getInstance().interactionManager.interactBlock(MinecraftClient.getInstance().player, hand, blockHitResult);
    }

    /* 除草 */
    private void weedTick() {
        World world = p.getWorld();
        int radius = configure.effectRadius.getValue();
        Vec3d posVec = p.getPos();
        int centerX = (int) Math.floor(posVec.x);
        int centerY = (int) Math.floor(posVec.y);// the "leg block"
        int centerZ = (int) Math.floor(posVec.z);

        // 使用可变位置减少对象创建
        BlockPos.Mutable mutablePos = new BlockPos.Mutable();

        boolean treatFlowersAsWeeds = configure.flowerISseed.getValue();

        for (int dy = 3; dy >= -2; dy--) {
            int y = centerY + dy;

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    mutablePos.set(centerX + dx, y, centerZ + dz);

                    // 快速失败检查：可达性
                    if (!canReachBlock(p, mutablePos)) continue;

                    // 检查是否为杂草或（配置开启时的）花朵
                    if (CropManager.isWeedBlock(world, mutablePos) ||
                            (treatFlowersAsWeeds && CropManager.isFlowerBlock(world, mutablePos))) {

                        // 执行清除
                        leftButton(mutablePos.toImmutable());
                        return;
                    }
                }
            }
        }
    }

    /* 主手耕地 */
    private void mainHoeingTick() {
        ItemStack MainHandItem = p.getMainHandStack();
        if (MainHandItem == null || !MainHandItem.isIn(ItemTags.HOES)) return;
        hoeingTick(Hand.MAIN_HAND);
    }

    /* 副手耕地 */
    private void offHoeingTick() {
        ItemStack OffHandItem = p.getOffHandStack();
        if (OffHandItem == null || !OffHandItem.isIn(ItemTags.HOES)) return;
        hoeingTick(Hand.OFF_HAND);
    }

    /* 耕地 */
    private void hoeingTick(Hand hand) {
        World world = p.getWorld();
        int radius = configure.effectRadius.getValue();
        Vec3d posVec = p.getPos();
        int centerX = (int) Math.floor(posVec.x);
        int centerY = (int) Math.floor(posVec.y -0.2D);// 脚下方块
        int centerZ = (int) Math.floor(posVec.z);

        // 使用可变位置减少对象创建
        BlockPos.Mutable mutablePos = new BlockPos.Mutable();
        BlockPos.Mutable abovePos = new BlockPos.Mutable();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                mutablePos.set(centerX + dx, centerY, centerZ + dz);
                abovePos.set(mutablePos).move(Direction.UP);
                BlockState aboveState = world.getBlockState(abovePos);
                if (!aboveState.isAir()) continue;

                BlockState state = world.getBlockState(mutablePos);
                Block block = state.getBlock();

                // 快速跳过不能耕地方块
                if (!CropManager.tillableBlocks.contains(block)) continue;

                // 处理需要水源检查的情况
                if (configure.keepWaterNearBy.getValue()) {
                    if (!isWaterNearby(world, mutablePos)) continue;
                }

                // 执行耕地操作
                double clickX = centerX + dx + 0.5;
                double clickZ = centerZ + dz + 0.5;
                rightButton(clickX, centerY, clickZ, Direction.UP, mutablePos.toImmutable(), hand);
                return;
            }
        }
    }

    /* 检测水源 */
    private boolean isWaterNearby(World world, BlockPos pos) {
        for (BlockPos blockPos : BlockPos.iterate(pos.add(-4, 0, -4), pos.add(4, 1, 4))) {
            if (world.getFluidState(blockPos).isIn(FluidTags.WATER)) return true;
        }
        return false;
    }

    /* 主手去皮 */
    private void mainHandStripTick() {
        ItemStack MainHandItem = p.getMainHandStack();
        if (MainHandItem == null || (!MainHandItem.isIn(ItemTags.AXES) && MainHandItem.getItem() != Items.SHEARS)) return;
        StripTick(MainHandItem, Hand.MAIN_HAND);
    }

    /* 副手去皮 */
    private void offHandStripTick() {
        ItemStack OffHandItem = p.getOffHandStack();
        if (OffHandItem == null || (!OffHandItem.isIn(ItemTags.AXES) && OffHandItem.getItem() != Items.SHEARS)) return;
        StripTick(OffHandItem, Hand.OFF_HAND);
    }

    /* 去皮 */
    private void StripTick(ItemStack itemStack, Hand hand) {
        World world = p.getWorld();
        int radius = configure.effectRadius.getValue();
        Vec3d posVec = p.getPos();
        int centerX = (int) Math.floor(posVec.x);
        int centerY = (int) Math.floor(posVec.y); //脚下方块
        int centerZ = (int) Math.floor(posVec.z);

        // 使用可变位置减少对象创建
        BlockPos.Mutable mutablePos = new BlockPos.Mutable();

        // 优化循环顺序：Y轴优先（原木通常垂直分布）
        for (int dy = 0; dy <= radius; dy++) {
            int y = centerY + dy;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    mutablePos.set(centerX + dx, y, centerZ + dz);

                    if (!canReachBlock(p, mutablePos)) continue;

                    if (!CropManager.isWood(world, mutablePos)) continue;

                    if (itemStack.getItem() == Items.SHEARS) {
                        // 雕刻南瓜
                        rightButton(mutablePos.getX() + 0.5, mutablePos.getY() + 0.5, mutablePos.getZ() + 0.5, Direction.UP, mutablePos, hand);
                        return;
                    } else if (itemStack.isIn(ItemTags.AXES)) {
                        // 给木头去皮
                        rightButton(mutablePos.getX() + 0.5, mutablePos.getY() + 0.5, mutablePos.getZ() + 0.5, Direction.UP, mutablePos, hand);
                        return;
                    }
                }
            }
        }
    }

    /* 收获所有成熟作物 */
    private void harvestTick() {
        World world = p.getWorld();
        int radius = configure.effectRadius.getValue();
        Vec3d posVec = p.getPos();
        int centerX = (int) Math.floor(posVec.x);
        int centerY = (int) Math.floor(posVec.y + 0.2D);// the "leg block", in case in soul sand
        int centerZ = (int) Math.floor(posVec.z);

        // 使用可变位置减少对象创建
        BlockPos.Mutable mutablePos = new BlockPos.Mutable();
        ClientPlayerInteractionManager interactionManager = MinecraftClient.getInstance().interactionManager;

        for (int dy = -1; dy <= 1; dy++) {
            int y = centerY + dy;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    mutablePos.set(centerX + dx, y, centerZ + dz);

                    if (!canReachBlock(p, mutablePos)) continue;

                    BlockState state = world.getBlockState(mutablePos);
                    Block block = state.getBlock();

                    // 检查作物是否成熟
                    if (!CropManager.isCropMature(world, mutablePos, state)) continue;

                    // 特殊处理甜浆果丛
                    if (block == Blocks.SWEET_BERRY_BUSH) {
                        double clickY = y - 0.5;  // 特殊点击位置
                        rightButton(centerX + dx + 0.5, clickY, centerZ + dz + 0.5, Direction.UP, mutablePos.toImmutable(), Hand.MAIN_HAND);
                        return;
                    }

                    // 处理需要破坏进度的作物（如竹子、南瓜、西瓜）
                    if (CropManager.needBreakingProgress(state)) {
                        if (interactionManager != null) {
                            interactionManager.updateBlockBreakingProgress(mutablePos.toImmutable(), Direction.UP);
                        }
                        return;
                    }

                    // 普通作物直接左键收获
                    leftButton(mutablePos.toImmutable());
                    return;
                }
            }
        }
    }

//    private void minusOneInHand() {
//        ItemStack st = p.getMainHandStack();
//        if (st != null) {
//            if (st.getCount() <= 1) {
//                p.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
//            } else {
//                st.setCount(st.getCount() - 1);
//            }
//        }
//    }

    /* 查找背包内物品 */
    private ItemStack tryFillItemInHand() {
        ItemStack itemStack = p.getMainHandStack();
        if (itemStack.isEmpty() || itemStack.isOf(Items.WATER_BUCKET)) {
            if (lastUsedItem != null && !lastUsedItem.isEmpty()) {
                DefaultedList<ItemStack> inv = p.getInventory().getMainStacks();
                for (int idx = 0; idx < 36; ++idx) {
                    ItemStack s = inv.get(idx);
                    if (s.getItem() == lastUsedItem.getItem() &&
                            s.getDamage() == lastUsedItem.getDamage()) {
                        AutoHarvest.INSTANCE.taskManager.Add_MoveItem(idx, p.getInventory().getSelectedSlot());
                        return s;
                    }
                }
            }
            return null;
        } else {
            return itemStack;
        }
    }

    /* 副手种植 */
//    private void offplantTick() {
//        ItemStack offHandItem = p.getOffHandStack();
//        if (offHandItem == null) return;
//        plantTick(offHandItem, Hand.OFF_HAND);
//    }

    /* 主手种植 */
    private void mainPlantTick() {
        ItemStack HandItem = p.getMainHandStack();
        if (HandItem == null) return;
        plantTick(HandItem, Hand.MAIN_HAND);
    }

    /* 种植 */
    private void plantTick(ItemStack itemStack, Hand hand) {
        if (CropManager.isCocoa(itemStack)) {
            plantCocoaTick();
            return;
        }
        if (lastUsedItem == null && !CropManager.isSeed(itemStack)) return;
        if (configure.tryFillItems.getValue()) itemStack = tryFillItemInHand();
        if (itemStack == null) return;

        World w = p.getWorld();
        int radius = configure.effectRadius.getValue();
        Vec3d posVec = p.getPos();
        int X = (int) Math.floor(posVec.x);
        int Y = (int) Math.floor(posVec.y + 0.2D);// the "leg block" , in case in soul sand
        int Z = (int) Math.floor(posVec.z);

        BlockPos.Mutable mutablePos = new BlockPos.Mutable();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                // 设置目标位置（玩家脚下方块层）
                mutablePos.set(X + dx, Y, Z + dz);
                BlockState targetState = w.getBlockState(mutablePos);
                BlockPos downPos = mutablePos.down();

                if (!CropManager.canPaint(targetState, itemStack)) continue;
                if (!CropManager.canPlantOn(itemStack.getItem(), w, mutablePos)) continue;

                // 执行种植动作
                lastUsedItem = itemStack.copy();
                double clickX = X + dx + 0.5;
                double clickZ = Z + dz + 0.5;

                rightButton(clickX, Y, clickZ, Direction.UP, downPos, hand);
                return;
            }
        }
    }

    /* 可可豆种植 */
    private void plantCocoaTick() {
        ItemStack handItem = p.getMainHandStack();
        if (!CropManager.isCocoa(handItem)) {
            if (configure.tryFillItems.getValue()) {
                handItem = tryFillItemInHand();
            }
            if (handItem == null || !CropManager.isCocoa(handItem)) return;
        }

        World world = p.getWorld();
        int radius = configure.effectRadius.getValue();
        Vec3d posVec = p.getPos();
        int X = (int) Math.floor(posVec.x);
        int Y = (int) Math.floor(posVec.y + 0.2D);// the "leg block" , in case in soul sand
        int Z = (int) Math.floor(posVec.z);

        Direction[] directions = {Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH};

        for (int dy = 0; dy <= 7; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos logPos = new BlockPos(X + dx, Y + dy, Z + dz);

                    // 提前终止条件：不可到达或非丛林原木
                    if (!canReachBlock(p, logPos)) continue;
                    if (!CropManager.isJungleLog(world.getBlockState(logPos))) continue;

                    // 检查所有可种植方向
                    for (Direction dir : directions) {
                        BlockPos plantPos = logPos.offset(dir);

                        // 跳过非空气方块
                        if (!world.getBlockState(plantPos).isAir()) continue;

                        // 计算点击坐标（中心点偏移）
                        double clickX = plantPos.getX() + 0.5;
                        double clickY = plantPos.getY() + 0.5;
                        double clickZ = plantPos.getZ() + 0.5;

                        // 执行种植动作
                        lastUsedItem = handItem.copy();
                        rightButton(clickX, clickY, clickZ, dir, logPos, Hand.MAIN_HAND);
                        return;
                    }
                }
            }
        }
    }

    /* 可以够到的方块 */
    private boolean canReachBlock(ClientPlayerEntity playerEntity, BlockPos blockpos) {
        double d0 = playerEntity.getX() - ((double) blockpos.getX() + 0.5D);
        double d1 = playerEntity.getY() - ((double) blockpos.getY() + 0.5D) + 1.5D;
        double d2 = playerEntity.getZ() - ((double) blockpos.getZ() + 0.5D);
        double d3 = d0 * d0 + d1 * d1 + d2 * d2;
        return d3 <= 36D;
    }

    /* 动物喂养 */
    private void feedTick() {
        ItemStack handItem = p.getMainHandStack();
        if (configure.tryFillItems.getValue()) handItem = tryFillItemInHand();
        if (handItem == null) return;

        int radius = configure.effectRadius.getValue();
        Vec3d posVec = p.getPos();
        Box box = new Box(posVec.x - radius, posVec.y - radius,
                posVec.z - radius, posVec.x + radius,
                posVec.y + radius, posVec.z + radius);

        if (handItem.isOf(Items.AMETHYST_SHARD)) {
            processAllayFeeding(box, handItem);
        } else if (handItem.isOf(Items.TROPICAL_FISH_BUCKET)){
            feedAxolotTick(box);
        }else {processAnimals(box, handItem);}
    }

    private void processAllayFeeding(Box box, ItemStack handItem) {
        p.getWorld().getEntitiesByClass(AllayEntity.class, box, allay ->
                allay != null && allay.isAlive() && !allay.isHoldingItem() && allay.isDancing()
        ).forEach(allay -> {
            lastUsedItem = handItem.copy();
            interactWithEntity(handItem, allay);
        });
    }

    /* 普通动物喂养处理 */
    private void processAnimals(Box box, ItemStack handItem) {
        for (AnimalEntity animal : CropManager.getFeedableAnimals(p, box, handItem)) {
            if (handItem.isOf(Items.SHEARS) && animal instanceof SheepEntity sheep) {
                lastUsedItem = handItem.copy();
                interactWithEntity(handItem, sheep);
                return; // 每次只处理一只动物
            }
            else {
                lastUsedItem = handItem.copy();
                interactWithEntity(handItem, animal);
            }
        }
    }

    private void interactWithEntity(ItemStack handItem, Entity entity) {
        lastUsedItem = handItem.copy();
        assert MinecraftClient.getInstance().interactionManager != null;
        MinecraftClient.getInstance().interactionManager.interactEntity(p, entity, Hand.MAIN_HAND);
    }

    // 繁殖美西螈
    private void feedAxolotTick(Box box) {
        ItemStack mainHandItem = p.getMainHandStack();
        ItemStack handItem = mainHandItem;
        if (configure.tryFillItems.getValue()) handItem = tryFillItemInHand();
        if (handItem == null || handItem.isOf(Items.WATER_BUCKET)) return;

        List<AxolotlEntity> axolotlEntities = p.getWorld().getEntitiesByClass(AxolotlEntity.class, box, axolotl ->
                axolotl != null && axolotl.isAlive() && axolotl.canEat() &&
                        axolotl.isBreedingItem(mainHandItem) && axolotl.getBreedingAge() >= 0
        );
        for (AxolotlEntity axolot : axolotlEntities) {
            if (mainHandItem.isOf(Items.TROPICAL_FISH_BUCKET)) {
                lastUsedItem = handItem.copy();
                interactWithEntity(handItem, axolot);
                return;
            }
        }
    }

    /**
     * @return -1: doesn't have rod; 0: no change; 1: change
     * 若手上不是鱼竿尝试替换成鱼竿
     **/
    private int tryReplacingFishingRod() {
        ItemStack itemStack = p.getMainHandStack();
        boolean keepRodAlive = configure.keepFishingRodAlive.getValue();

        if (isUsableFishingRod(itemStack, keepRodAlive)) return 0; // 当前鱼竿可用

        DefaultedList<ItemStack> inventory = p.getInventory().getMainStacks();

        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.get(slot);
            if (isUsableFishingRod(stack, keepRodAlive)) {
                // 添加物品移动任务
                AutoHarvest.INSTANCE.taskManager.Add_MoveItem(slot, p.getInventory().getSelectedSlot());
                return 1; // 找到并计划更换鱼竿
            }
        }
        return -1; // 没有可用鱼竿
    }

    private boolean isUsableFishingRod(ItemStack stack, boolean keepAlive) {
        if (!CropManager.isRod(stack)) {
            return false;
        }
        // 检查耐久度（如果开启鱼竿保护）
        if (keepAlive) {
            int remainingUses = stack.getMaxDamage() - stack.getDamage();
            return remainingUses > 1; // 保留至少1点耐久
        }
        return true; // 不考虑耐久
    }

    private long getWorldTime() {
        assert MinecraftClient.getInstance().world != null;
        return MinecraftClient.getInstance().world.getTime();
    }

    /* 鱼咬钩 */
    private boolean isFishBites(ClientPlayerEntity player) {
        FishingBobberEntity fishEntity = player.fishHook;
        return fishEntity != null && (fishEntity.lastX - fishEntity.getX()) == 0
                && (fishEntity.lastZ - fishEntity.getZ()) == 0 && (fishEntity.lastY - fishEntity.getY()) < -0.05d;
    }

    /* 钓鱼 */
    private void fishingTick() {
        switch (tryReplacingFishingRod()) {
            case -1:
                AutoHarvest.msg("notify.turn.off");
                AutoHarvest.INSTANCE.Switch = false;
                break;
            case 0:
                /* Reel */
                if (fishBitesAt == 0 && isFishBites(p)) {
                    fishBitesAt = getWorldTime();
                    interactWithItem();
                }

                /* Cast */
                if (fishBitesAt != 0 && fishBitesAt + 20 <= getWorldTime()) {
                    interactWithItem();
                    fishBitesAt = 0;
                }
                break;
            case 1:
        }
    }

    private void interactWithItem() {
        assert MinecraftClient.getInstance().interactionManager != null;
            MinecraftClient.getInstance().interactionManager.interactItem(p, Hand.MAIN_HAND);
    }

    /* 骨粉催熟 */
    private void bonemealingTick() {
        ItemStack handItem = p.getMainHandStack();

        if (configure.tryFillItems.getValue()  && !CropManager.isBoneMeal(handItem)){
            handItem = tryFillItemInHand();
            if (!CropManager.isBoneMeal(handItem)) return;
        }

        int radius = configure.effectRadius.getValue();
        World w = p.getWorld();
        BlockPos playerPos = new BlockPos(p.getBlockPos());
        Vec3d posVec = p.getPos();
        int X = (int) Math.floor(posVec.x);
        int Y = (int) Math.floor(posVec.y);
        int Z = (int) Math.floor(posVec.z);
        for (int deltaY = 3; deltaY >= -2; --deltaY) {
            for (int deltaX = -radius; deltaX <= radius; ++deltaX) {
                for (int deltaZ = -radius; deltaZ <= radius; ++deltaZ) {
                    BlockPos targetPos = playerPos.add(deltaX, deltaY, deltaZ);
                    BlockState state = w.getBlockState(targetPos);
                    if (state.getBlock() instanceof GrassBlock) {
                        continue; // 跳过草方块
                    }
                    if (CropManager.canBeBonemealed(w, targetPos)) {
                        lastUsedItem = handItem.copy();
                        rightButton(X + deltaX + 0.5, Y + deltaY + 0.5, Z + deltaZ + 0.5, Direction.UP, targetPos, Hand.MAIN_HAND);
                        return;
                    }
                }
            }
        }
    }
}
