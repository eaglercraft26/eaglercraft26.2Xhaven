package squeek.appleskin.helpers;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;

/**
 * Exhaustion is not part of vanilla's client sync. The original mod sends it with a custom payload
 * from its server half. In Eaglercraft singleplayer the integrated server lives in the same process,
 * so the authoritative value is read straight from the server-side player's FoodData and mirrored
 * to the client FoodData (this is what the original's ExhaustionSyncPayload accomplishes).
 */
public class ExhaustionHelper {
    public static float getExhaustion(Player player) {
        syncFromIntegratedServer(player);
        return player.getFoodData().getExhaustionLevel();
    }

    public static void setExhaustion(Player player, float exhaustion) {
        player.getFoodData().setExhaustionLevel(exhaustion);
    }

    private static void syncFromIntegratedServer(Player player) {
        try {
            MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
            if (server == null) {
                return;
            }
            ServerPlayer sp = server.getPlayerList().getPlayer(player.getUUID());
            if (sp != null) {
                FoodData serverFood = sp.getFoodData();
                FoodData clientFood = player.getFoodData();
                if (serverFood != clientFood) {
                    clientFood.setExhaustionLevel(serverFood.getExhaustionLevel());
                }
            }
        } catch (RuntimeException ignored) {
        }
    }
}
