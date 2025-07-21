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

                .setTitle(Text.translatable(AutoHarvest.MOD_NAME + " config screen"))
                .setSavingRunnable(ClothConfig::saveConfig);

        ConfigCategory scrolling = builder.getOrCreateCategory(Text.translatable(AutoHarvest.MOD_NAME));
        ConfigEntryBuilder entryBuilder = ConfigEntryBuilder.create();

        Configure c = AutoHarvest.instance.configure.load();

        scrolling.addEntry(entryBuilder.startBooleanToggle(Text.translatable("config.flower_is_seed"), c.flowerISseed.value).setDefaultValue(false).setSaveConsumer(b -> c.flowerISseed.value = b).build());
        scrolling.addEntry(entryBuilder.startBooleanToggle(Text.translatable("config.keep_water_near_by"), c.keepWaterNearBy.value).setDefaultValue(new Configure.KeepWaterNearBy().value).setSaveConsumer(b -> c.keepWaterNearBy.value = b).build());
        scrolling.addEntry(entryBuilder.startBooleanToggle(Text.translatable("config.keep_fishing_rod_alive"), c.keepFishingRodAlive.value).setDefaultValue(new Configure.KeepFishingRodAlive().value).setSaveConsumer(b -> c.keepFishingRodAlive.value = b).build());

        scrolling.addEntry(entryBuilder.startIntSlider(Text.translatable("config.effect_radius"), c.effect_radius.value, c.effect_radius.Min, c.effect_radius.Max).setDefaultValue((new Configure.Effect_radius()).value).setSaveConsumer(b -> c.effect_radius.value = b).build());
        scrolling.addEntry(entryBuilder.startIntField(Text.translatable("config.tick_skip"),c.tickSkip.value).setTooltip(Text.translatable("config.tick_skip_tooltip")).setDefaultValue(new Configure.TickSkip().value).setSaveConsumer(b -> c.tickSkip.value = b).build());
        return builder.setParentScreen(parentScreen).build();
    }

    private static void saveConfig() {
        AutoHarvest.instance.configure.save();
    }
}
