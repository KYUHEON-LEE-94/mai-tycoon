package kr.maitycoon.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.C2SPlayChannelEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class MaiTycoonClient implements ClientModInitializer {
    static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("maitycoon", "main"));
    static KeyMapping fullMap;
    /** A Korean keyboard layout names the M key ㅡ, so a letter key is shown by its position instead. */
    static String keyName() {
        String saved = fullMap.saveString(), prefix = "key.keyboard.";
        if (saved.startsWith(prefix) && saved.length() == prefix.length() + 1) return saved.substring(prefix.length()).toUpperCase(java.util.Locale.ROOT);
        return fullMap.getTranslatedKeyMessage().getString();
    }
    @Override public void onInitializeClient() {
        Packet.register();
        fullMap = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.maitycoon.full_map", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, CATEGORY));
        ClientPlayNetworking.registerGlobalReceiver(Packet.HELLO_ACK, (packet, context) -> Link.acknowledged(packet.data()));
        ClientPlayNetworking.registerGlobalReceiver(Packet.MAP, (packet, context) -> Link.mapArrived(packet.data()));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> Link.joined());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> Link.left());
        C2SPlayChannelEvents.REGISTER.register((handler, sender, client, channels) -> Link.channelsRegistered());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Link.tick();
            Capture.tick(client);
            Capture.menuTick(client);
            while (fullMap.consumeClick()) if (client.screen == null && client.player != null) client.setScreen(new MapScreen());
        });
    }
}
