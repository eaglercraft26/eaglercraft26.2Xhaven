package squeek.appleskin;

import java.io.File;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import squeek.appleskin.client.DebugInfoHudEntry;
import squeek.appleskin.client.HUDOverlayHandler;
import squeek.appleskin.client.TooltipOverlayHandler;

/** AppleSkin client initialisation (replaces the Fabric ClientModInitializer entrypoint). */
public class AppleSkin {
    public static final Logger LOGGER = LoggerFactory.getLogger("AppleSkin");
    private static boolean initialized;

    public static void init(File gameDirectory) {
        if (initialized) return;
        initialized = true;
        ModConfig.init(gameDirectory);
        HUDOverlayHandler.init();
        TooltipOverlayHandler.init();
        DebugScreenEntries.registerExternal(DebugInfoHudEntry.ENTRY_ID, new DebugInfoHudEntry());
        LOGGER.info("AppleSkin initialized");
    }
}
