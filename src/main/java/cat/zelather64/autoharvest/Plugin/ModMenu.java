package cat.zelather64.autoharvest.Plugin;


import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class ModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return cat.zelather64.autoharvest.Plugin.ClothConfig::openConfigScreen;
    }
}
