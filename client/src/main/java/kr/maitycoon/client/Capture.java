package kr.maitycoon.client;

import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/**
 * A development check, off unless MAITYCOON_CAPTURE names a folder: once the server answers, open the map, save a
 * screenshot and quit, so the real screen can be looked at without anyone at the keyboard.
 */
final class Capture {
    private static final String FOLDER = System.getenv("MAITYCOON_CAPTURE");
    private static int countdown = -1;
    private Capture() {}
    static void handshakeReady(Minecraft client) {
        if (FOLDER == null || countdown >= 0) return;
        client.setScreen(new MapScreen());
        countdown = 80;
    }
    static void tick(Minecraft client) {
        if (FOLDER == null || countdown < 0) return;
        // A resource pack reload covers the screen and may replace it; start over once the map is up with its data.
        if (client.getOverlay() != null || !(client.screen instanceof MapScreen screen) || (!screen.loaded() && Link.status() == Link.Status.READY)) {
            if (client.getOverlay() == null && !(client.screen instanceof MapScreen)) client.setScreen(new MapScreen());
            countdown = 60;
            return;
        }
        if (countdown == 20) Screenshot.grab(new File(FOLDER), "maitycoon-map.png", client.getMainRenderTarget(), 1, message -> Link.LOG.info("Capture: {}", message.getString()));
        if (countdown-- == 0) client.stop();
    }
}
