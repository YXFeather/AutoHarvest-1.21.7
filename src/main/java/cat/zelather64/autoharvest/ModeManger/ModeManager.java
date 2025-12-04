package cat.zelather64.autoharvest.ModeManger;

import cat.zelather64.autoharvest.Mode.AutoMode;
import cat.zelather64.autoharvest.Config.AutoHarvestConfig;

import static cat.zelather64.autoharvest.AutoHarvest.LOGGER;

public class ModeManager {

    public static final ModeManager INSTANCE = new ModeManager();

    private AutoMode currentMode = null;
    private ModeEnum currentModeEnum = ModeEnum.FARMER; // 默认模式
    private boolean enabled = false;
    private int tickCounter = 0;

    // 私有构造器，确保单例
    private ModeManager() {
        // 从配置加载上次使用的模式
        loadFromConfig();
    }

    /**
     * 从配置加载模式
     */
    private void loadFromConfig() {
        ModeEnum savedMode = AutoHarvestConfig.getInstance().theCurrentMode;
        if (savedMode != null) {
            currentModeEnum = savedMode;
        }
    }

    /**
     * 切换到下一个模式
     */
    public void switchToNextMode() {
        currentModeEnum = currentModeEnum.next();
        applyCurrentMode();
        saveToConfig();
    }

    /**
     * 切换到指定模式
     */
    public void switchToMode(ModeEnum mode) {
        if (mode == null) return;

        currentModeEnum = mode;
        applyCurrentMode();
        saveToConfig();
    }

    /**
     * 应用当前模式
     */
    private void applyCurrentMode() {
        if (enabled && currentMode != null) {
            currentMode.onDisable();
        }

        if (enabled) {
            currentMode = currentModeEnum.createMode();
        }
    }

    /**
     * 保存到配置
     */
    private void saveToConfig() {
        AutoHarvestConfig.getInstance().theCurrentMode = currentModeEnum;
        AutoHarvestConfig.save();
    }

    /**
     * 清理当前模式（在断开连接时调用）
     */
    public void clearMode() {
        if (enabled) {
            disable();
            LOGGER.info("模式已清理");
        }
    }

    /**
     * 切换启用/禁用
     */
    public void toggle() {
        if (enabled) {
            disable();
        } else {
            enable();
        }
    }

    /**
     * 启用当前模式
     */
    private void enable() {
        if (enabled) return;

        currentMode = currentModeEnum.createMode();
        enabled = true;
        tickCounter = 0;

        // 保存配置
        saveToConfig();
    }

    /**
     * 禁用当前模式
     */
    private void disable() {
        if (!enabled) return;

        if (currentMode != null) {
            currentMode.onDisable();
            currentMode = null;
        }
        enabled = false;
    }

    /**
     * 获取当前模式枚举
     */
    public ModeEnum getCurrentModeEnum() {
        return currentModeEnum;
    }

    /**
     * 获取当前模式实例
     */
    public AutoMode getCurrentMode() {
        return currentMode;
    }

    /**
     * 检查是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置模式（从配置加载时使用）
     */
    public void setMode(ModeEnum mode) {
        if (mode != null) {
            currentModeEnum = mode;
            applyCurrentMode();
        }
    }

    /**
     * 每tick调用
     */
    public void tick() {
        if (!enabled || currentMode == null) return;

        tickCounter++;
        int interval = AutoHarvestConfig.ticksPerAction();
        if (tickCounter >= interval) {
            tickCounter = 0;
            currentMode.tick();
        }
    }
}
