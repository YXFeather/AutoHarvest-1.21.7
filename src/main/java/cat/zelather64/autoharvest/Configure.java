package cat.zelather64.autoharvest;

import cat.zelather64.autoharvest.Utils.IConfigValue;
import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class Configure {
    private final File configFile;
    private final List<IConfigValue<?>> configEntries = new ArrayList<>();

    // 配置项定义
    public final BooleanConfig flowerISseed = new BooleanConfig("flowerISseed", false);
    public final IntConfig effectRadius = new IntConfig("effect_radius", 3, 0, 3);
    public final IntConfig tickSkip = new IntConfig("tick_skip", 2, 0, 100);
    public final BooleanConfig keepFishingRodAlive = new BooleanConfig("keepFishingRodAlive", true);
    public final BooleanConfig keepWaterNearBy = new BooleanConfig("keepWaterNearBy", true);
    public final BooleanConfig tryFillItems = new BooleanConfig("TryFillItemsInHand", true);

    public Configure() {
        this.configFile = FabricLoader.getInstance()
                .getConfigDir()
                .resolve("AutoHarvest.json")
                .toFile();

        // 注册所有配置项
        registerConfig(flowerISseed);
        registerConfig(effectRadius);
        registerConfig(tickSkip);
        registerConfig(keepFishingRodAlive);
        registerConfig(keepWaterNearBy);
        registerConfig(tryFillItems);
    }

    private void registerConfig(IConfigValue<?> config) {
        configEntries.add(config);
    }

    public Configure load() {
        try {
            if (!Files.exists(configFile.toPath())) return this;

            String jsonStr = new String(Files.readAllBytes(configFile.toPath()));
            if (jsonStr.isEmpty()) return this;

            JsonObject jsonObject = JsonParser.parseString(jsonStr).getAsJsonObject();

            // 统一加载所有配置项
            for (IConfigValue<?> config : configEntries) {
                if (jsonObject.has(config.getName())) {
                    config.loadFromJson(jsonObject);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return this;
    }

    public Configure save() {
        JsonObject jsonObject = new JsonObject();

        // 统一保存所有配置项
        for (IConfigValue<?> config : configEntries) {
            config.saveToJson(jsonObject);
        }

        // 写入文件
        try (PrintWriter out = new PrintWriter(configFile)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            out.println(gson.toJson(jsonObject));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return this;
    }

    // ===== 配置项实现类 =====

    // 布尔型配置
    public static class BooleanConfig implements IConfigValue<Boolean> {
        private boolean value;
        private final String name;

        public BooleanConfig(String name, boolean defaultValue) {
            this.name = name;
            this.value = defaultValue;
        }

        @Override public Boolean getValue() { return value; }
        @Override public void setValue(Boolean value) { this.value = value; }
        @Override public String getName() { return name; }

        @Override
        public void loadFromJson(JsonObject json) {
            try {
                value = json.getAsJsonPrimitive(name).getAsBoolean();
            } catch (Exception e) {
                // 保持默认值
            }
        }

        @Override
        public void saveToJson(JsonObject json) {
            json.addProperty(name, value);
        }
    }

    // 整型配置（带范围验证）
    public static class IntConfig implements IConfigValue<Integer> {
        private int value;
        private final String name;
        private final int min;
        private final int max;

        public IntConfig(String name, int defaultValue, int min, int max) {
            this.name = name;
            this.min = min;
            this.max = max;
            this.value = clamp(defaultValue); // 初始值也验证范围
        }
        public int getMin() {
            return min;
        }

        public int getMax() {
            return max;
        }

        @Override public Integer getValue() { return value; }

        @Override
        public void setValue(Integer newValue) {
            this.value = clamp(newValue);
        }

        @Override public String getName() { return name; }

        private int clamp(int value) {
            return Math.max(min, Math.min(max, value));
        }

        @Override
        public void loadFromJson(JsonObject json) {
            try {
                int loadedValue = json.getAsJsonPrimitive(name).getAsInt();
                setValue(loadedValue); // 使用setter确保范围验证
            } catch (Exception e) {
                // 保持默认值
            }
        }

        @Override
        public void saveToJson(JsonObject json) {
            json.addProperty(name, value);
        }
    }
}
