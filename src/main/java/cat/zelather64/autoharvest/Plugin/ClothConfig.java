package cat.zelather64.autoharvest.Plugin;

import cat.zelather64.autoharvest.AutoHarvest;
import cat.zelather64.autoharvest.Configure;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class ClothConfig {
    public static Screen openConfigScreen(Screen parentScreen) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setTitle(Text.translatable("config.%s_config_screen",AutoHarvest.MOD_NAME))
                .setSavingRunnable(ClothConfig::saveConfig);

        ConfigCategory scrolling = builder.getOrCreateCategory(Text.translatable("config.%s", AutoHarvest.MOD_NAME));
        ConfigEntryBuilder entryBuilder = ConfigEntryBuilder.create();

        Configure c = AutoHarvest.INSTANCE.configure.load();

        scrolling.addEntry(entryBuilder.startBooleanToggle(Text.translatable("config.flower_is_seed"),
                c.flowerISseed.getValue()).setDefaultValue(false).setSaveConsumer(c.flowerISseed::setValue).build());

        scrolling.addEntry(entryBuilder.startBooleanToggle(Text.translatable("config.keep_water_near_by"),
                c.keepWaterNearBy.getValue()).setTooltip(Text.translatable("config.keep_water_near_by_tooltip"))
                .setDefaultValue(c.keepWaterNearBy.getValue()).setSaveConsumer(c.keepWaterNearBy::setValue).build());

        scrolling.addEntry(entryBuilder.startBooleanToggle(Text.translatable("config.keep_fishing_rod_alive"),
                c.keepFishingRodAlive.getValue()).setDefaultValue(c.keepFishingRodAlive.getValue())
                .setSaveConsumer(c.keepFishingRodAlive::setValue).build());

        scrolling.addEntry(entryBuilder.startBooleanToggle(Text.translatable("config.try_fill_items_in_hand"),
                c.tryFillItems.getValue()).setDefaultValue(c.tryFillItems.getValue()).setSaveConsumer(c.tryFillItems::setValue).build());

        scrolling.addEntry(entryBuilder.startBooleanToggle(Text.translatable("config.try_auto_look_at"),
                c.autoLookAt.getValue()).setDefaultValue(c.autoLookAt.getValue()).setSaveConsumer(c.autoLookAt::setValue).build());

        scrolling.addEntry(entryBuilder.startIntSlider(Text.translatable("config.effect_radius"),
                c.effectRadius.getValue(), c.effectRadius.getMin(), c.effectRadius.getMax())
                .setDefaultValue(c.effectRadius.getValue()).setSaveConsumer(c.effectRadius::setValue).build());

        scrolling.addEntry(entryBuilder.startIntField(Text.translatable("config.tick_skip"),c.tickSkip.getValue())
                .setTooltip(Text.translatable("config.tick_skip_tooltip")).setDefaultValue(c.tickSkip.getValue())
                .setSaveConsumer(c.tickSkip::setValue).build());

        return builder.setParentScreen(parentScreen).build();
    }

    private static void saveConfig() {
        AutoHarvest.INSTANCE.configure.save();
    }
}
