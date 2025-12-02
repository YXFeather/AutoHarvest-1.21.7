package cat.zelather64.autoharvest.Config;

import cat.zelather64.autoharvest.Mode.ModeEnum;
import cat.zelather64.autoharvest.ModeManger.ModeManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class KeyPressListener {

    private static final String CATEGORY_GENERAL = "key.category.general";
    private static final String CATEGORY_SWITCH_TO = "key.category.switchTo";

    // 重新组织按键绑定
    private final KeyBinding keyToggle;          // 原 keyAutoHarvest: 切换启用/禁用
    private final KeyBinding keyNextMode;        // 原 keySwitch: 切换到下一个模式

    // 直接模式切换按键
    private final Map<KeyBinding, ModeEnum> directModeKeys;

    public KeyPressListener() {
        String categoryGeneral = Text.translatable(CATEGORY_GENERAL).getString();
        String categorySwitchTo = Text.translatable(CATEGORY_SWITCH_TO).getString();

        // 创建按键绑定
        keyToggle = createKeyBinding("key.general.toggle", GLFW.GLFW_KEY_H, categoryGeneral);
        keyNextMode = createKeyBinding("key.general.nextMode", GLFW.GLFW_KEY_J, categoryGeneral);

        // 初始化直接模式按键映射
        directModeKeys = new LinkedHashMap<>();
        directModeKeys.put(createKeyBinding("harvest", GLFW.GLFW_KEY_UNKNOWN, categorySwitchTo), ModeEnum.HARVEST);
        directModeKeys.put(createKeyBinding("plant", GLFW.GLFW_KEY_UNKNOWN, categorySwitchTo), ModeEnum.PLANT);
        directModeKeys.put(createKeyBinding("farmer", GLFW.GLFW_KEY_UNKNOWN, categorySwitchTo), ModeEnum.FARMER);
        directModeKeys.put(createKeyBinding("weed", GLFW.GLFW_KEY_UNKNOWN, categorySwitchTo), ModeEnum.WEED);
        directModeKeys.put(createKeyBinding("feed", GLFW.GLFW_KEY_UNKNOWN, categorySwitchTo), ModeEnum.FEED);
        directModeKeys.put(createKeyBinding("fishing", GLFW.GLFW_KEY_UNKNOWN, categorySwitchTo), ModeEnum.FISHING);
        directModeKeys.put(createKeyBinding("bonemealing", GLFW.GLFW_KEY_UNKNOWN, categorySwitchTo), ModeEnum.BONE_MEALING);
        directModeKeys.put(createKeyBinding("hoeing", GLFW.GLFW_KEY_UNKNOWN, categorySwitchTo), ModeEnum.HOEING);
        directModeKeys.put(createKeyBinding("stripping", GLFW.GLFW_KEY_UNKNOWN, categorySwitchTo), ModeEnum.STRIPPING);

        // 注册客户端tick事件
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    }

    /**
     * 创建并注册按键绑定的辅助方法
     */
    private KeyBinding createKeyBinding(String translationKey, int keyCode, String category) {
        String fullTranslationKey = translationKey.startsWith("key.") ?
                translationKey : "key.general." + translationKey;
        KeyBinding keyBinding = new KeyBinding(fullTranslationKey, InputUtil.Type.KEYSYM, keyCode, category);
        KeyBindingHelper.registerKeyBinding(keyBinding);
        return keyBinding;
    }

    /**
     * 客户端tick事件处理器
     */
    private void onClientTick(MinecraftClient client) {
        if (client.player == null) return;

        // 检查所有按键
        if (keyToggle.wasPressed()) {
            handleToggleKey(client.player);
        } else if (keyNextMode.wasPressed()) {
            handleNextModeKey(client.player);
        } else {
            handleDirectModeKeys(client.player);
        }
    }

    /**
     * 处理切换启用/禁用按键
     */
    private void handleToggleKey(ClientPlayerEntity player) {
        ModeManager.INSTANCE.toggle();
        sendToggleMessage(player);
    }

    /**
     * 处理切换到下一个模式按键
     */
    private void handleNextModeKey(ClientPlayerEntity player) {
        ModeManager.INSTANCE.switchToNextMode();
        sendModeSwitchMessage(player, ModeManager.INSTANCE.getCurrentModeEnum());
    }

    /**
     * 处理直接模式切换按键
     */
    private void handleDirectModeKeys(ClientPlayerEntity player) {
        for (Map.Entry<KeyBinding, ModeEnum> entry : directModeKeys.entrySet()) {
            if (entry.getKey().wasPressed()) {
                ModeManager.INSTANCE.switchToMode(entry.getValue());
                sendModeSwitchMessage(player, entry.getValue());
                break; // 一次只处理一个按键
            }
        }
    }

    /**
     * 发送切换启用/禁用消息
     */
    private void sendToggleMessage(ClientPlayerEntity player) {
        String modeName = Text.translatable(
                "autoharvest.mode." + ModeManager.INSTANCE.getCurrentModeEnum().getTranslationKey()
        ).getString();
        if (ModeManager.INSTANCE.isEnabled()) {
            player.sendMessage(
                    Text.translatable("autoharvest.message.enabled", modeName),
                    false
            );
        } else {
            player.sendMessage(
                    Text.translatable("autoharvest.message.disabled", modeName),
                    false
            );
        }
    }

    /**
     * 发送模式切换消息
     */
    private void sendModeSwitchMessage(ClientPlayerEntity player, ModeEnum mode) {
        String modeName = Text.translatable("autoharvest.mode." + mode.getTranslationKey()).getString();
        player.sendMessage(
                Text.translatable("autoharvest.message.switched", modeName),
                false
        );

        // 如果当前是启动状态，自动停止
        if (ModeManager.INSTANCE.isEnabled()) {
            ModeManager.INSTANCE.toggle();
            player.sendMessage(
                    Text.translatable("autoharvest.message.disabled", modeName),
                    false
            );
        }
    }

    /**
     * 获取所有按键绑定（用于显示或其他用途）
     */
    public Collection<KeyBinding> getAllKeyBindings() {
        List<KeyBinding> bindings = new ArrayList<>();
        bindings.add(keyToggle);
        bindings.add(keyNextMode);
        bindings.addAll(directModeKeys.keySet());
        return bindings;
    }
}
