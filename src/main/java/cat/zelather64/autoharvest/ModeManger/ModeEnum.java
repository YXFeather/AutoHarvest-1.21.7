package cat.zelather64.autoharvest.ModeManger;

import cat.zelather64.autoharvest.Mode.*;

import java.util.function.Supplier;

public enum ModeEnum {
    BONE_MEALING("bonemeal", BoneMealMode::new),
    HARVEST("harvest", HarvestMode::new),
    PLANT("plant", PlantMode::new),
    FARMER("farmer", CompositeMode::new),
    WEED("weed", WeedMode::new),
    FEED("feed", FeedMode::new),
    FISHING("fishing", FishingMode::new),
    HOEING("hoeing", HoeMode::new),
    STRIPPING("stripping", StrippedMode::new),
    BREWMODE("brewing", BrewMode::new);

    private static final ModeEnum[] VALUES = values();
    private final String translationKey;
    private final Supplier<AutoMode> modeSupplier;

    ModeEnum(String translationKey, Supplier<AutoMode> modeSupplier) {
        this.translationKey = translationKey;
        this.modeSupplier = modeSupplier;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public AutoMode createMode() {
        return modeSupplier.get();
    }

    // 实例方法：获取下一个模式
    public ModeEnum next() {
        int nextIndex = (this.ordinal() + 1) % VALUES.length;
        return VALUES[nextIndex];
    }

    // 静态方法：根据当前模式获取下一个
    public static ModeEnum getNext(ModeEnum current) {
        if (current == null) return VALUES[0];
        return current.next();
    }

    // 根据名称查找枚举
    public static ModeEnum fromName(String name) {
        for (ModeEnum mode : VALUES) {
            if (mode.name().equalsIgnoreCase(name)) {
                return mode;
            }
        }
        return PLANT; // 默认值
    }
}
