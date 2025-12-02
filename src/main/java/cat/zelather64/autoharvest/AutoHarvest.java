package cat.zelather64.autoharvest;

import cat.zelather64.autoharvest.Config.KeyPressListener;
import cat.zelather64.autoharvest.ModeManger.ModeManager;
import cat.zelather64.autoharvest.Config.AutoHarvestConfig;
import cat.zelather64.autoharvest.Utils.SmoothLookHelper;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginConnectionEvents;

import java.util.logging.Logger;

public class AutoHarvest implements ClientModInitializer {
	public static final String MOD_ID = "autoharvest";
	public static final Logger LOGGER = Logger.getLogger(MOD_ID);

	// 保存 KeyPressListener 实例的引用，防止被垃圾回收
	private KeyPressListener keyPressListener;

	@Override
	public void onInitializeClient() {
		LOGGER.info("AutoHarvest 模组初始化中...");

		// 初始化配置
		initializeConfig();

		// 初始化按键监听器
		initializeKeyListener();

		// 注册事件监听器
		registerEventListeners();

		LOGGER.info("AutoHarvest 模组初始化完成");
	}

	/**
	 * 初始化配置系统
	 */
	private void initializeConfig() {
		try {
			AutoConfig.register(AutoHarvestConfig.class, GsonConfigSerializer::new);
			LOGGER.info("配置系统初始化完成");
		} catch (Exception e) {
//			LOGGER.("配置系统初始化失败: " + e.getMessage(), e);
		}
	}

	/**
	 * 初始化按键监听器
	 */
	private void initializeKeyListener() {
		try {
			// 创建按键监听器实例
			keyPressListener = new KeyPressListener();

			LOGGER.info("按键监听器初始化完成");
		} catch (Exception e) {
//			LOGGER.log("按键监听器初始化失败: " + e.getMessage(), e);
		}
	}

	/**
	 * 注册事件监听器
	 */
	private void registerEventListeners() {
		// 注册断开连接事件
		ClientLoginConnectionEvents.DISCONNECT.register((handler, client) -> {
			LOGGER.info("玩家断开连接，清理模式状态");
			ModeManager.INSTANCE.clearMode();
		});

		// 注册客户端Tick事件
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// 只在有玩家时执行（在游戏内而非主菜单）
			if (client.player != null && client.world != null) {
				try {
					if (AutoHarvestConfig.autoLookAt()) {
						// 调用平滑视角更新逻辑
						SmoothLookHelper.updateSmoothLook(client.player);
					}
					// 调用模式管理器tick
					ModeManager.INSTANCE.tick();

				} catch (Exception e) {
					// 防止单个异常导致整个tick崩溃
					LOGGER.warning("客户端tick处理异常: " + e.getMessage());
				}
			}
		});

//		// 注册客户端加入世界事件（从配置加载模式）
//		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
//			if (client.player != null) {
//				LOGGER.info("玩家加入世界，加载保存的模式");
//				loadSavedMode();
//			}
//		});
//
//		LOGGER.info("事件监听器注册完成");
	}

	/**
	 * 从配置加载保存的模式
	 */
	private void loadSavedMode() {
		try {
			AutoHarvestConfig config = AutoHarvestConfig.getInstance();
			if (config != null && config.theCurrentMode != null) {
				ModeManager.INSTANCE.setMode(config.theCurrentMode);
				LOGGER.info("已加载保存的模式: " + config.theCurrentMode);
			}
		} catch (Exception e) {
			LOGGER.warning("加载保存的模式失败: " + e.getMessage());
		}
	}

	/**
	 * 获取按键监听器实例（供其他类使用）
	 */
	public KeyPressListener getKeyPressListener() {
		return keyPressListener;
	}

}
