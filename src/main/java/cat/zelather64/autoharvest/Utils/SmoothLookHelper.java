package cat.zelather64.autoharvest.Utils;

import cat.zelather64.autoharvest.Config.AutoHarvestConfig;
import net.minecraft.entity.Entity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

public class SmoothLookHelper {

    // 只存储客户端玩家的平滑转向信息
    private static SmoothLookData clientSmoothLookData = null;

    // 平滑转向数据类
    private static class SmoothLookData {
        public final double targetX, targetY, targetZ;
        public final double durationTicks; // 动画持续时间（tick数）
        public final float startYaw, startPitch;
        public final float targetYaw, targetPitch;
        public int elapsedTicks; // 已过去的tick数

        public SmoothLookData(double targetX, double targetY, double targetZ,
                              double durationTicks, float startYaw, float startPitch,
                              float targetYaw, float targetPitch) {
            this.targetX = targetX;
            this.targetY = targetY;
            this.targetZ = targetZ;
            this.durationTicks = durationTicks;
            this.startYaw = startYaw;
            this.startPitch = startPitch;
            this.targetYaw = targetYaw;
            this.targetPitch = targetPitch;
            this.elapsedTicks = 0;
        }
    }

    /**
     * 平滑看向位置（只对客户端玩家有效）
     * @param targetX 目标X坐标
     * @param targetY 目标Y坐标
     * @param targetZ 目标Z坐标
     * @param durationTicks 动画持续时间（tick数，20 tick = 1秒）
     */
    private static void smoothLookAtPosition(Entity entity, double targetX, double targetY, double targetZ, double durationTicks) {
        if (entity == null) return;

        // 计算目标角度
        float[] targetAngles = calculateLookAngles(entity, targetX, targetY, targetZ);
        float targetYaw = targetAngles[0];
        float targetPitch = targetAngles[1];

        // 获取当前角度
        float currentYaw = entity.getYaw();
        float currentPitch = entity.getPitch();

        // 标准化角度，确保在 -180 到 180 度之间
        currentYaw = normalizeAngle(currentYaw);
        targetYaw = normalizeAngle(targetYaw);

        // 处理偏航角的最短路径（避免绕远路）
        if (Math.abs(targetYaw - currentYaw) > 180) {
            if (targetYaw > currentYaw) {
                currentYaw += 360;
            } else {
                targetYaw += 360;
            }
        }

        // 存储平滑转向数据
        clientSmoothLookData = new SmoothLookData(
                targetX, targetY, targetZ, durationTicks,
                currentYaw, currentPitch, targetYaw, targetPitch
        );

        // 如果持续时间很短，直接设置最终角度
        if (durationTicks <= 0) {
            entity.setYaw(targetYaw);
            entity.setPitch(targetPitch);
            clientSmoothLookData = null;
        }
    }

    public static void autoLookAt(Entity entity, Vec3d pos, Hand hand) {
        if (!AutoHarvestConfig.autoLookAt()) return;
        double posX = pos.getX();
        double posY = pos.getY();
        double posZ = pos.getZ();
        smoothLookAtPosition(entity, posX, posY, posZ, AutoHarvestConfig.ticksPerAction());
        BoxUtil.getPlayer().swingHand(hand);
    }

//    /**
//     * 立即看向位置（只对客户端玩家有效）
//     */
    public static void lookAtPosition(Entity entity, Vec3d pos) {
        double posX = pos.getX();
        double posY = pos.getY();
        double posZ = pos.getZ();
        smoothLookAtPosition(entity, posX, posY, posZ, 0);
    }

    /**
     * 更新客户端玩家的平滑转向（需要在每tick调用）
     */
    public static void updateSmoothLook(Entity entity) {
        if (clientSmoothLookData == null) return;

        if (entity == null || !entity.isAlive()) {
            clientSmoothLookData = null;
            return;
        }

        // 增加已过去的tick数
        clientSmoothLookData.elapsedTicks++;

        // 计算动画进度（0.0 到 1.0）
        float progress = (float)(clientSmoothLookData.elapsedTicks / clientSmoothLookData.durationTicks);

        if (progress >= 1.0f) {
            // 动画完成
            entity.setYaw(clientSmoothLookData.targetYaw);
            entity.setPitch(clientSmoothLookData.targetPitch);
            clientSmoothLookData = null;
        } else {
            // 使用缓动函数实现平滑过渡
            float easedProgress = easeInOutQuad(progress);
            // 插值计算当前角度
            float currentYaw = lerpAngle(clientSmoothLookData.startYaw, clientSmoothLookData.targetYaw, easedProgress);
            float currentPitch = lerp(clientSmoothLookData.startPitch, clientSmoothLookData.targetPitch, easedProgress);

            // 应用角度
            entity.setYaw(currentYaw);
            entity.setPitch(currentPitch);
        }
    }

    /**
     * 停止客户端玩家的平滑转向
     */
    public static void stopSmoothLook() {
        clientSmoothLookData = null;
    }

//    /**
//     * 检查是否正在进行平滑转向
//     */
//    public static boolean isSmoothLooking() {
//        return clientSmoothLookData != null;
//    }
//
//    /**
//     * 获取当前转向目标位置
//     */
//    public static double[] getCurrentTarget() {
//        if (clientSmoothLookData == null) return null;
//        return new double[]{
//                clientSmoothLookData.targetX,
//                clientSmoothLookData.targetY,
//                clientSmoothLookData.targetZ
//        };
//    }
//
//    /**
//     * 获取转向进度（0.0 到 1.0）
//     */
//    public static float getLookProgress() {
//        if (clientSmoothLookData == null) return 0.0f;
//        return (float)clientSmoothLookData.elapsedTicks / clientSmoothLookData.durationTicks;
//    }

    /**
     * 使用向量计算看向目标位置所需的角度
     */
    private static float[] calculateLookAngles(Entity entity, double targetX, double targetY, double targetZ) {
        double deltaX = targetX - entity.getX();
        double deltaY = targetY - (entity.getY() + entity.getEyeHeight(entity.getPose()));
        double deltaZ = targetZ - entity.getZ();

        // 计算水平距离
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        // 计算yaw - 修复向西方向的问题
        float yaw = (float)(Math.atan2(deltaZ, deltaX) * (180.0 / Math.PI)) - 90.0f;

        // 确保yaw在正确范围内
        while (yaw < -180.0F) {
            yaw += 360.0F;
        }
        while (yaw >= 180.0F) {
            yaw -= 360.0F;
        }

        // 计算pitch
        float pitch = (float)(-(Math.atan2(deltaY, horizontalDistance) * (180.0 / Math.PI)));

        return new float[]{yaw, pitch};
    }

    /**
     * 线性插值
     */
    private static float lerp(float start, float end, float progress) {
        return start + (end - start) * progress;
    }

    /**
     * 角度线性插值（考虑角度环绕）
     */
    private static float lerpAngle(float start, float end, float progress) {
        // 处理角度差值
        float difference = end - start;
        if (difference > 180) {
            end -= 360;
        } else if (difference < -180) {
            end += 360;
        }

        float result = start + (end - start) * progress;
        return normalizeAngle(result);
    }

    /**
     * 标准化角度到 -180 到 180 范围
     */
    private static float normalizeAngle(float angle) {
        angle %= 360;
        if (angle > 180) {
            angle -= 360;
        } else if (angle < -180) {
            angle += 360;
        }
        return angle;
    }

    /**
     * 缓动函数：三次缓入缓出（提供平滑的开始和结束）
     */
    private static float easeInOutQuad(float x) {
        x = Math.max(0, Math.min(1, x));
        return x < 0.5f ? 2 * x * x : 1 - (float)Math.pow(-2 * x + 2, 2) / 2;
    }
}
