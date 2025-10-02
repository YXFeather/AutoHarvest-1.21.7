package cat.zelather64.autoharvest;

import cat.zelather64.autoharvest.Utils.SmoothLookHelper;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.passive.AxolotlEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;

public class AutoHarvest implements ClientModInitializer {
	public static final String MOD_NAME = "autoharvest";
	public static AutoHarvest INSTANCE;
	public HarvestMode mode = HarvestMode.FISHING;
	public int overlayRemainingTick = 0;
	public TickListener listener = null;
	public KeyPressListener KeyListener = null;

	TaskManager taskManager = new TaskManager();

	public boolean Switch = false;
	public Configure configure = new Configure();

	@Override
	public void onInitializeClient() {
		if (AutoHarvest.INSTANCE == null)
			AutoHarvest.INSTANCE = new AutoHarvest();
		if (AutoHarvest.INSTANCE.KeyListener == null) {
			AutoHarvest.INSTANCE.KeyListener = new KeyPressListener();
		}
		AutoHarvest.INSTANCE.configure.load();

		// 注册客户端Tick事件
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// 确保client不为空，并且有玩家存在（在游戏内而非主菜单）
			if (client.player != null) {
				// 调用你的平滑视角更新逻辑
				SmoothLookHelper.updateSmoothLook(client.player);
			}
		});

		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (entity instanceof AxolotlEntity){
				Item heldItem = player.getStackInHand(hand).getItem();

				if (heldItem == Items.WATER_BUCKET){
					return ActionResult.FAIL;
				}
			}
			return ActionResult.PASS;
		});
	}

	public enum HarvestMode {
		HARVEST, // Harvest only
		PLANT, // Plant only
		FARMER, // Harvest then re-plant
		WEED, // Harvest seeds & flowers
		BONEMEALING,
		FEED, // Feed animals
		FISHING,// Fishing
		HOEING,// 耕地模式
		AXEITEMS;//去皮，雕刻南瓜

		private static HarvestMode[] vals = values();

		public AutoHarvest.HarvestMode next() {
			return vals[(this.ordinal() + 1) % vals.length];
		}
	}

	public HarvestMode toSpecifiedMode(HarvestMode mode) {
		// setDisabled();
		if (listener == null) {
			listener = new TickListener(configure, MinecraftClient.getInstance().player);
		} else
			listener.Reset();
		this.mode = mode;
		return mode;
	}

	public HarvestMode toNextMode() {
		// setDisabled();
		if (listener == null) {
			listener = new TickListener(configure, MinecraftClient.getInstance().player);
		} else
			listener.Reset();
		mode = mode.next();
		return mode;
	}

	public static void msg(String key, Object... obj) {
		if (MinecraftClient.getInstance() == null)
			return;
		if (MinecraftClient.getInstance().player == null)
			return;

		MinecraftClient.getInstance().player.sendMessage(Text.of(Text.translatable("notify.prefix").getString() + Text.translatable(key, obj).getString()), true);
	}
}
