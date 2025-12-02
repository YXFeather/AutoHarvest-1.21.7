package cat.zelather64.autoharvest.Mode;

import cat.zelather64.autoharvest.Config.AutoHarvestConfig;
import cat.zelather64.autoharvest.Utils.InteractionHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

public class FishingMode implements AutoMode{

    private static final int STATIONARY_THRESHOLD_TICKS = 60;
    private static final long REPEAT_MESSAGE_INTERVAL_TICKS = 100;
    private static final double HORIZONTAL_MOVEMENT_THRESHOLD = 0.01;
    private static final double DEPTH_THRESHOLD = 0.06;
    private static final int STABLE_DELAY_TICKS = 30;

    // 使用 volatile 保证线程可见性
    private volatile long fishBitesAt = 0;
    private volatile long bobberSpawnTime = 0;
    private volatile double baselineY = Double.NaN;
    private volatile double lastBobberX = Double.NaN;
    private volatile double lastBobberY = Double.NaN;
    private volatile double lastBobberZ = Double.NaN;
    private volatile int stationaryTicks = 0;
    private volatile long firstStuckTime = -1;
    private volatile long lastStuckMessageTime = -1;
    private volatile int rodSlotCache = -1;
    private volatile long rodSlotCacheTime = 0;
    private static final long ROD_CACHE_EXPIRE_TICKS = 200; // 10秒缓存

    // 单例访问以减少 GC
    private static final MinecraftClient MC = MinecraftClient.getInstance();

    @Override
    public void tick() {
        ClientPlayerEntity player = MC.player;
        if (player == null) {
            resetAllState();
            return;
        }

        Hand rodHand = getFishingRodHand(player);
        if (rodHand == null) {
            // 使用缓存的鱼竿槽位，减少扫描次数
            int rodSlot = getCachedFishingRodSlot(player);
            if (rodSlot != -1 && AutoHarvestConfig.autoSwitchRod()) {
                player.getInventory().setSelectedSlot(rodSlot);
                rodHand = Hand.MAIN_HAND;
            }
            if (rodHand == null) {
                resetAllState();
                return;
            }
        }

        FishingBobberEntity bobber = player.fishHook;
        long currentTime = getCurrentWorldTime();

        // 如果有鱼上钩等待重抛
        if (fishBitesAt != 0) {
            int delay = AutoHarvestConfig.fishingReCastDelay();
            if (currentTime >= fishBitesAt + delay) {
                InteractionHelper.interactItem(player, rodHand);
                resetAllState();
            }
            return;
        }

        // 没有浮漂或浮漂已死
        if (bobber == null || !bobber.isAlive()) {
            resetAllState();
            return;
        }

        // 初始化浮漂时间
        if (bobberSpawnTime == 0) {
            bobberSpawnTime = currentTime;
        }

        // 稳定期：等待浮漂稳定并设置基准高度
        if (currentTime < bobberSpawnTime + STABLE_DELAY_TICKS) {
            if (Double.isNaN(baselineY) || bobber.getY() < baselineY) {
                baselineY = bobber.getY();
            }
            updateStationaryState(bobber, false, currentTime);
            return;
        }

        // 确保基准高度已设置
        if (Double.isNaN(baselineY)) {
            baselineY = bobber.getY();
        }

        // 检查是否卡住并通知
        updateStationaryState(bobber, true, currentTime);
        checkAndNotifyStuck(player, currentTime);

        // 检查是否有鱼上钩
        if (isFishBites(bobber, baselineY)) {
            fishBitesAt = currentTime;
            InteractionHelper.interactItem(player, rodHand);
        }
    }

    private int getCachedFishingRodSlot(ClientPlayerEntity player) {
        long currentTime = getCurrentWorldTime();

        // 如果缓存未过期，直接返回
        if (rodSlotCache != -1 && currentTime - rodSlotCacheTime < ROD_CACHE_EXPIRE_TICKS) {
            return rodSlotCache;
        }

        // 刷新缓存
        rodSlotCache = -1;
        PlayerInventory inventory = player.getInventory();

        // 先检查当前手持物品
        if (player.getMainHandStack().isOf(Items.FISHING_ROD)) {
            rodSlotCache = inventory.getSelectedSlot();
        } else if (inventory.getStack(40).isOf(Items.FISHING_ROD)) { // 副手
            rodSlotCache = 40; // 特殊标记副手
        } else {
            // 检查快捷栏
            for (int i = 0; i < 9; i++) {
                if (inventory.getStack(i).isOf(Items.FISHING_ROD)) {
                    rodSlotCache = i;
                    break;
                }
            }
        }

        rodSlotCacheTime = currentTime;
        return rodSlotCache;
    }

    private void updateStationaryState(FishingBobberEntity bobber, boolean allowStuckDetection, long currentTime) {
        double x = bobber.getX();
        double y = bobber.getY();
        double z = bobber.getZ();

        // 提前检查是否移动，减少浮点数比较次数
        boolean hasMoved = Double.isNaN(lastBobberX) ||
                Math.abs(x - lastBobberX) > 1e-6 ||
                Math.abs(y - lastBobberY) > 1e-6 ||
                Math.abs(z - lastBobberZ) > 1e-6;

        if (hasMoved) {
            lastBobberX = x;
            lastBobberY = y;
            lastBobberZ = z;
            stationaryTicks = 0;
            firstStuckTime = -1;
            lastStuckMessageTime = -1;
        } else {
            stationaryTicks++;
            if (allowStuckDetection && firstStuckTime == -1 && stationaryTicks >= STATIONARY_THRESHOLD_TICKS) {
                firstStuckTime = currentTime;
            }
        }
    }

    private void checkAndNotifyStuck(ClientPlayerEntity player, long currentTime) {
        if (firstStuckTime == -1) {
            return;
        }

        // 避免每tick都进行模运算，使用时间间隔检查
        if (lastStuckMessageTime == -1 ||
                currentTime >= lastStuckMessageTime + REPEAT_MESSAGE_INTERVAL_TICKS) {
            player.sendMessage(Text.translatable("autoharvest.mode.fishing.error"), true);
            lastStuckMessageTime = currentTime;
        }
    }

    private boolean isFishBites(FishingBobberEntity bobber, double baselineY) {
        double dx = bobber.getX() - bobber.lastX;
        double dz = bobber.getZ() - bobber.lastZ;
        double currentY = bobber.getY();

        if (Math.abs(dx) > HORIZONTAL_MOVEMENT_THRESHOLD ||
                Math.abs(dz) > HORIZONTAL_MOVEMENT_THRESHOLD) {
            return false;
        }

        // 检查是否下沉足够深度
        double depthChange = baselineY - currentY;
        return depthChange > DEPTH_THRESHOLD;
    }

    private void resetAllState() {
        fishBitesAt = 0;
        bobberSpawnTime = 0;
        baselineY = Double.NaN;
        lastBobberX = Double.NaN;
        lastBobberY = Double.NaN;
        lastBobberZ = Double.NaN;
        stationaryTicks = 0;
        firstStuckTime = -1;
        lastStuckMessageTime = -1;
        // 不重置鱼竿缓存，因为玩家可能仍然持有鱼竿
    }

    private long getCurrentWorldTime() {
        return MC.world != null ? MC.world.getTime() : 0L;
    }

    private Hand getFishingRodHand(ClientPlayerEntity player) {
        ItemStack mainHand = player.getMainHandStack();
        ItemStack offHand = player.getOffHandStack();

        boolean mainIsRod = mainHand.isOf(Items.FISHING_ROD);
        boolean offIsRod = offHand.isOf(Items.FISHING_ROD);

        if (mainIsRod) {
            return Hand.MAIN_HAND;
        }
        if (offIsRod) {
            return Hand.OFF_HAND;
        }
        return null;
    }

    @Override
    public String getName() {
        // 缓存翻译结果，避免每次调用都翻译
        return Text.translatable("autoharvest.mode.fishing").getString();
    }

    @Override
    public void onDisable() {
        resetAllState();
        // 清理缓存
        rodSlotCache = -1;
        rodSlotCacheTime = 0;
    }

    // 添加一个方法来手动刷新鱼竿缓存（例如当玩家物品栏变化时调用）
    public void refreshRodCache() {
        rodSlotCache = -1;
        rodSlotCacheTime = 0;
    }

//    private long fishBitesAt = 0;
//    private long bobberSpawnTime = 0;
//    private double baselineY = Double.NaN;
//    private double lastBobberX = Double.NaN;
//    private double lastBobberY = Double.NaN;
//    private double lastBobberZ = Double.NaN;
//    private int stationaryTicks = 0;
//    private long firstStuckTime = -1;
//
//    private static final int STATIONARY_THRESHOLD_TICKS = 60;
//    private static final long REPEAT_MESSAGE_INTERVAL = 100;
//    private static final double HORIZONTAL_MOVEMENT_THRESHOLD = 0.01;
//    private static final double DEPTH_THRESHOLD = 0.06;
//    private static final int STABLE_DELAY_TICKS = 30;
//
//    @Override
//    public void tick() {
//        ClientPlayerEntity player = MinecraftClient.getInstance().player;
//        if (player == null) {
//            resetAllState();
//            return;
//        }
//        Hand rodHand = getFishingRodHand(player);
//        if (rodHand == null) {
//            int rodSlot = findFishingRodSlot(player);
//            if (rodSlot != -1) {
//                if (AutoHarvestConfig.autoSwitchRod()) {
//                    player.getInventory().setSelectedSlot(rodSlot);
//                }
//                rodHand = getFishingRodHand(player);
//            }
//            if (rodHand == null) {
//                resetAllState();
//                return;
//            }
//        }
//
//        FishingBobberEntity bobber = player.fishHook;
//        long currentTime = getCurrentWorldTime();
//
//        if (fishBitesAt != 0) {
//            int delay = AutoHarvestConfig.fishingReCastDelay();
//            if (currentTime >= fishBitesAt + delay) {
//                InteractionHelper.interactItem(player, rodHand);
//                resetAllState();
//            }
//            return;
//        }
//
//        if (bobber == null || !bobber.isAlive()) {
//            resetAllState();
//            return;
//        }
//
//        if (bobberSpawnTime == 0) {
//            bobberSpawnTime = currentTime;
//        }
//
//        if (currentTime < bobberSpawnTime + STABLE_DELAY_TICKS) {
//            if (Double.isNaN(baselineY) || bobber.getY() < baselineY) {
//                baselineY = bobber.getY();
//            }
//            updateStationaryState(bobber, false, currentTime);
//            return;
//        }
//
//        if (Double.isNaN(baselineY)) {
//            baselineY = bobber.getY();
//        }
//
//        updateStationaryState(bobber, true, currentTime);
//        checkAndNotifyStuck(player, currentTime);
//
//        if (isFishBites(bobber, baselineY)) {
//            fishBitesAt = currentTime;
//            InteractionHelper.interactItem(player, rodHand);
//        }
//    }
//
//    private int findFishingRodSlot(ClientPlayerEntity player) {
//        for (int i = 0; i < 9; i++) {
//            if (player.getInventory().getStack(i).isOf(Items.FISHING_ROD)) {
//                return i;
//            }
//        }
//        return -1;
//    }
//
//    private void updateStationaryState(FishingBobberEntity bobber, boolean allowStuckDetection, long currentTime) {
//        double x = bobber.getX();
//        double y = bobber.getY();
//        double z = bobber.getZ();
//
//        boolean hasMoved = Double.isNaN(lastBobberX) ||
//                x != lastBobberX ||
//                y != lastBobberY ||
//                z != lastBobberZ;
//
//        if (hasMoved) {
//            lastBobberX = x;
//            lastBobberY = y;
//            lastBobberZ = z;
//            stationaryTicks = 0;
//            firstStuckTime = -1;
//        } else {
//            stationaryTicks++;
//            if (allowStuckDetection && firstStuckTime == -1 && stationaryTicks >= STATIONARY_THRESHOLD_TICKS) {
//                firstStuckTime = currentTime;
//            }
//        }
//    }
//
//    private void checkAndNotifyStuck(ClientPlayerEntity player, long currentTime) {
//        if (firstStuckTime == -1) {
//            return;
//        }
//        if ((currentTime - firstStuckTime) % REPEAT_MESSAGE_INTERVAL == 0) {
//            if (currentTime > firstStuckTime) {
//                player.sendMessage(Text.translatable("autoharvest.mode.fishing.error"), true);
//            }
//        }
//    }
//
//    private boolean isFishBites(FishingBobberEntity bobber, double baselineY) {
//        double dx = bobber.getX() - bobber.lastX;
//        double dz = bobber.getZ() - bobber.lastZ;
//        double currentY = bobber.getY();
//
//        boolean horizontalStill = Math.abs(dx) < HORIZONTAL_MOVEMENT_THRESHOLD
//                && Math.abs(dz) < HORIZONTAL_MOVEMENT_THRESHOLD;
//        boolean hasSunk = (baselineY - currentY) > DEPTH_THRESHOLD;
//
//        return horizontalStill && hasSunk;
//    }
//
//    private void resetAllState() {
//        fishBitesAt = 0;
//        bobberSpawnTime = 0;
//        baselineY = Double.NaN;
//        lastBobberX = lastBobberY = lastBobberZ = Double.NaN;
//        stationaryTicks = 0;
//        firstStuckTime = -1;
//    }
//
//    private long getCurrentWorldTime() {
//        var world = MinecraftClient.getInstance().world;
//        return world != null ? world.getTime() : 0L;
//    }
//
//    private Hand getFishingRodHand(ClientPlayerEntity player) {
//        if (player.getMainHandStack().isOf(Items.FISHING_ROD)) {
//            return Hand.MAIN_HAND;
//        }
//        if (player.getOffHandStack().isOf(Items.FISHING_ROD)) {
//            return Hand.OFF_HAND;
//        }
//        return null;
//    }
//
//    @Override
//    public String getName() {
//        return Text.translatable("autoharvest.mode.fishing").getString();
//    }
//
//    @Override
//    public void onDisable() {
//        resetAllState();
//    }
}
