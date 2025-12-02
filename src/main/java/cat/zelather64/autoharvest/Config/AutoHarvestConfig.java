package cat.zelather64.autoharvest.Config;

import cat.zelather64.autoharvest.Mode.ModeEnum;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "autoharvest")
public class AutoHarvestConfig implements ConfigData {

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.DROPDOWN)
    public ModeEnum theCurrentMode = ModeEnum.fromName("");

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 1, max = 20)
    public int ticksPerAction = 1; // 默认每 1 tick 执行一次

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 10, max = 65)
    public int radiusCenti = 45; // 默认范围 4.5 块

    @ConfigEntry.BoundedDiscrete(min = 1, max = 60)
    @ConfigEntry.Gui.Tooltip
    public long coolDown = 5L;

    @ConfigEntry.BoundedDiscrete(min = 0, max = 3)
    @ConfigEntry.Gui.Tooltip
    public int bambooRadius = 0;

    @ConfigEntry.BoundedDiscrete(min = 10, max = 100)
    @ConfigEntry.Gui.Tooltip
    public int fishingReCastDelay = 10;

//    @ConfigEntry.Gui.Tooltip
//    public boolean flowerISseed = false;

    @ConfigEntry.Gui.Tooltip
    public boolean keepWaterNearby = false;

    @ConfigEntry.Gui.Tooltip
    public boolean autoSwitchHotbar = true;

    @ConfigEntry.Gui.Tooltip
    public boolean autoSwitchFortuneTool = true;

    @ConfigEntry.Gui.Tooltip
    public boolean enableRefill = true;

    @ConfigEntry.Gui.Tooltip
    public boolean autoSwitchRod = true;

    @ConfigEntry.Gui.Tooltip
    public boolean autoLookAt = false;

//    public static ModeEnum theCurrentMode() {
//        return getInstance().theCurrentMode;
//    }

    public static int ticksPerAction() {
        return getInstance().ticksPerAction;
    }

    public static double getRadius() {
        return getInstance().radiusCenti / 10.0;
    }

    public static long coolDown(){
        return getInstance().coolDown * 1000L;
    }

    public static int bambooRadius(){
        return getInstance().bambooRadius;
    }

    public static int fishingReCastDelay(){
        return getInstance().fishingReCastDelay;
    }

//    public static boolean getFlowerIsSeed() {
//        return getInstance().flowerISseed;
//    }

    public static boolean getWaterNearby() {
        return getInstance().keepWaterNearby;
    }

    public static boolean autoSwitchHotbar() {
        return getInstance().autoSwitchHotbar;
    }

    public static boolean autoSwitchFortuneTool() {
        return getInstance().autoSwitchFortuneTool;
    }

    public static boolean enableRefill() {
        return getInstance().enableRefill;
    }

    public static boolean autoSwitchRod() {
        return getInstance().autoSwitchRod;
    }

    public static boolean autoLookAt() {
        return getInstance().autoLookAt;
    }

    public static AutoHarvestConfig getInstance() {
        return AutoConfig.getConfigHolder(AutoHarvestConfig.class).getConfig();
    }

    public static void save(){
        AutoConfig.getConfigHolder(AutoHarvestConfig.class).save();
    }
}
