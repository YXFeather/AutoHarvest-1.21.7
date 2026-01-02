package cat.zelather64.autoharvest.Utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class BoxUtil {
    public static Vec3d getPlayerPos() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return null;
        return player.getPos();
    }

    public static ClientPlayerEntity getPlayer() {
        return MinecraftClient.getInstance().player;
    }

    public static ClientWorld getWorld() {
        return MinecraftClient.getInstance().world;
    }

    public static ScreenHandler getScreenHandler() {
        return getPlayer().currentScreenHandler;
    }

    public static PlayerInventory getInventory() {
        return getPlayer().getInventory();
    }

    public static boolean isInSphere(BlockPos pos, Vec3d center, double radius){
        return !center.isInRange(pos.toCenterPos(), radius);
    }

    public static Box createSearchBox(Vec3d pos, double radius){
        return new Box(pos.x - radius, pos.y - radius, pos.z - radius,
                pos.x + radius, pos.y + radius, pos.z + radius);
    }
}
