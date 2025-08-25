package cat.zelather64.autoharvest;

import cat.zelather64.autoharvest.Plugin.ClothConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

public class KeyPressListener {

    private static final int OVERLAY_DURATION_TICKS = 60;
    private static final String CATEGORY_GENERAL = "key.category.general";
    private static final String CATEGORY_SWITCH_TO = "key.category.switchTo";

    private final KeyBinding keySwitch;
    private final KeyBinding keyModeChange;
    private final KeyBinding keyConfig;
    private final KeyBinding keyHarvest;
    private final KeyBinding keyPlant;
    private final KeyBinding keyFarmer;
    private final KeyBinding keyWeed;
    private final KeyBinding keyFeed;
    private final KeyBinding keyFishing;
    private final KeyBinding keyBonemealing;
    private final KeyBinding keyHoeing;
    private final KeyBinding keyAxeItems;

    // 按键到模式的映射
    private final Map<KeyBinding, AutoHarvest.HarvestMode> keyModeMap;

    public KeyPressListener() {
        // 统一命名规范并初始化按键绑定
        String categoryGeneral = Text.translatable(CATEGORY_GENERAL).getString();
        String categorySwitchTo = Text.translatable(CATEGORY_SWITCH_TO).getString();

        keyModeChange = createKeyBinding("key.general.modechange", GLFW.GLFW_KEY_H, categoryGeneral);
        keySwitch = createKeyBinding("key.general.switch", GLFW.GLFW_KEY_J, categoryGeneral);
        keyConfig = createKeyBinding("key.general.config", GLFW.GLFW_KEY_K, categoryGeneral);

        keyHarvest = createKeyBinding("harvest", GLFW.GLFW_KEY_UNKNOWN, categorySwitchTo);
        keyPlant = createKeyBinding("plant", GLFW.GLFW_KEY_UNKNOWN, categorySwitchTo);
        keyFarmer = createKeyBinding("farmer", GLFW.GLFW_KEY_UNKNOWN, categorySwitchTo);
        keyWeed = createKeyBinding("weed", GLFW.GLFW_KEY_UNKNOWN, categorySwitchTo);
        keyFeed = createKeyBinding("feed", GLFW.GLFW_KEY_UNKNOWN, categorySwitchTo);
        keyFishing = createKeyBinding("fishing", GLFW.GLFW_KEY_UNKNOWN, categorySwitchTo);
        keyBonemealing = createKeyBinding("bonemealing", GLFW.GLFW_KEY_UNKNOWN, categorySwitchTo);
        keyHoeing = createKeyBinding("hoeing", GLFW.GLFW_KEY_UNKNOWN, categorySwitchTo);
        keyAxeItems = createKeyBinding("axeitems", GLFW.GLFW_KEY_UNKNOWN, categorySwitchTo);

        keyModeMap = new HashMap<>();
        keyModeMap.put(keyHarvest, AutoHarvest.HarvestMode.HARVEST);
        keyModeMap.put(keyPlant, AutoHarvest.HarvestMode.PLANT);
        keyModeMap.put(keyFarmer, AutoHarvest.HarvestMode.Farmer);
        keyModeMap.put(keyWeed, AutoHarvest.HarvestMode.WEED);
        keyModeMap.put(keyFeed, AutoHarvest.HarvestMode.FEED);
        keyModeMap.put(keyFishing, AutoHarvest.HarvestMode.FISHING);
        keyModeMap.put(keyBonemealing, AutoHarvest.HarvestMode.BONEMEALING);
        keyModeMap.put(keyHoeing, AutoHarvest.HarvestMode.HOEING);
        keyModeMap.put(keyAxeItems, AutoHarvest.HarvestMode.AXEITEMS);

        // 注册客户端tick事件
        ClientTickEvents.END_CLIENT_TICK.register(client -> onProcessKey());
    }

    /**
     * 创建并注册按键绑定的辅助方法
     */
    private KeyBinding createKeyBinding(String translationKey, int keyCode, String category) {
        KeyBinding keyBinding = new KeyBinding(translationKey, InputUtil.Type.KEYSYM, keyCode, category);
        KeyBindingHelper.registerKeyBinding(keyBinding);
        return keyBinding;
    }

    public void onProcessKey() {
        if (keySwitch.wasPressed()) {
            handleSwitchKey();
        } else if (keyConfig.wasPressed()) {
            handleConfigKey();
        } else {
            handleModeChangeKeys();
        }
    }

    /**
     * 处理开关按键
     */
    private void handleSwitchKey() {
        AutoHarvest.INSTANCE.Switch = !AutoHarvest.INSTANCE.Switch;
        AutoHarvest.msg("notify.turn." + (AutoHarvest.INSTANCE.Switch ? "on" : "off"));
    }

    private void handleConfigKey() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.setScreen(ClothConfig.openConfigScreen(client.currentScreen));
    }

    private void handleModeChangeKeys() {
        String modeName = null;

        if (keyModeChange.wasPressed()) {
            modeName = handleModeChangeKey();
        } else {
            // 遍历映射查找按下的键
            for (Map.Entry<KeyBinding, AutoHarvest.HarvestMode> entry : keyModeMap.entrySet()) {
                if (entry.getKey().wasPressed()) {
                    modeName = getModeName(entry.getValue());
                    break;
                }
            }
        }

        if (modeName != null) {
            AutoHarvest.msg("notify.switch_to", Text.translatable(modeName).getString());
        }
    }

    /**
     * 处理模式切换按键的特殊逻辑
     */
    private String handleModeChangeKey() {
        AutoHarvest.INSTANCE.overlayRemainingTick = OVERLAY_DURATION_TICKS;
        if (AutoHarvest.INSTANCE.overlayRemainingTick == 0) {
            return AutoHarvest.INSTANCE.mode.toString().toLowerCase();
        } else {
            return AutoHarvest.INSTANCE.toNextMode().toString().toLowerCase();
        }
    }

    /**
     * 获取指定模式的名称
     */
    private String getModeName(AutoHarvest.HarvestMode mode) {
        return AutoHarvest.INSTANCE.toSpecifiedMode(mode).toString().toLowerCase();
    }
}
