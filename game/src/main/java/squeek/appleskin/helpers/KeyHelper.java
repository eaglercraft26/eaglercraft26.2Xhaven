package squeek.appleskin.helpers;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

public class KeyHelper {
    public static boolean isCtrlKeyDown() {
        Window window = Minecraft.getInstance().getWindow();
        boolean down = InputConstants.isKeyDown(window, 341) || InputConstants.isKeyDown(window, 345);
        if (!down && Util.getPlatform() == Util.OS.OSX) {
            down = InputConstants.isKeyDown(window, 343) || InputConstants.isKeyDown(window, 347);
        }
        return down;
    }

    public static boolean isShiftKeyDown() {
        Window window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, 340) || InputConstants.isKeyDown(window, 344);
    }
}
