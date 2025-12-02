package cat.zelather64.autoharvest.Utils;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ModContainerHelper {
    private static final Map<String, ModContainer> MODS_BY_ID = new HashMap<>();

    static {
        // 获取所有模组容器
        FabricLoader.getInstance().getAllMods().forEach(mod -> {
            MODS_BY_ID.put(mod.getMetadata().getId(), mod);
        });
    }

    /**
     * 根据模组ID获取模组容器
     */
    public static Optional<ModContainer> getMod(String modId) {
        return Optional.ofNullable(MODS_BY_ID.get(modId));
    }
}
