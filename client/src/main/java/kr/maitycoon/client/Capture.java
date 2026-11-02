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
        if (countdown-- == 0) {
            if (System.getenv("MAITYCOON_CAPTURE_MENU") == null) { client.stop(); return; }
            client.setScreen(null);
            client.player.connection.sendCommand(System.getenv("MAITYCOON_CAPTURE_MENU"));
            menuCountdown = 60;
        }
    }
    private static int menuCountdown = -1;
    /** Second stage: the pointer on a menu slot, to see the hover box the way a player does. */
    static void menuTick(Minecraft client) {
        if (FOLDER == null || menuCountdown < 0) return;
        if (menuCountdown == 30 && client.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen) {
            var slot = screen.getMenu().slots.get(Integer.parseInt(System.getenv().getOrDefault("MAITYCOON_CAPTURE_SLOT", "30")));
            var position = (kr.maitycoon.client.mixin.ContainerScreenAccessor) screen;
            var window = client.getWindow();
            double ratio = window.getScreenWidth() / (double) window.getGuiScaledWidth();
            var mouse = (kr.maitycoon.client.mixin.MouseHandlerAccessor) client.mouseHandler;
            mouse.maitycoon$setX((position.maitycoon$left() + slot.x + 8) * ratio);
            mouse.maitycoon$setY((position.maitycoon$top() + slot.y + 8) * ratio);
        }
        if (menuCountdown == 10) Screenshot.grab(new File(FOLDER), "maitycoon-menu.png", client.getMainRenderTarget(), 1, message -> Link.LOG.info("Capture: {}", message.getString()));
        if (menuCountdown-- == 0) client.stop();
    }
}
