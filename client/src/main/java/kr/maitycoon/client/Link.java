package kr.maitycoon.client;

import java.util.List;
import kr.maitycoon.client.protocol.Wire;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This connection's handshake with the Mai Tycoon server. Everything here runs on the client thread. */
public final class Link {
    public enum Status { WAITING, READY, OUTDATED, UNSUPPORTED, TIMEOUT }
    static final Logger LOG = LoggerFactory.getLogger("MaiTycoon");
    /** A server without the plugin never registers our channel; after this long the map says so instead of waiting. */
    private static final int REGISTER_TICKS = 100;
    private static Status status = Status.UNSUPPORTED;
    private static String message = "";
    private static int serverMinor = Wire.MINOR;
    private static boolean helloSent;
    private static int ticksSinceJoin, nextRequest;
    private Link() {}
    public static Status status() { return status; }
    public static String message() { return message; }
    static void joined() {
        status = Status.WAITING;
        message = "";
        helloSent = false;
        ticksSinceJoin = 0;
        if (ClientPlayNetworking.canSend(Packet.HELLO)) sendHello();
    }
    static void left() { status = Status.UNSUPPORTED; helloSent = false; }
    static void channelsRegistered() { if (status == Status.WAITING && ClientPlayNetworking.canSend(Packet.HELLO)) sendHello(); }
    static void tick() {
        if (status == Status.WAITING && ++ticksSinceJoin > REGISTER_TICKS) {
            status = helloSent ? Status.TIMEOUT : Status.UNSUPPORTED;
            message = helloSent ? "서버 응답이 없습니다. 다시 접속해 주세요." : "";
        }
    }
    private static void sendHello() {
        if (helloSent) return;
        helloSent = true;
        // Lets the server's missing-client path be tried with this build.
        if (System.getenv("MAITYCOON_SKIP_HELLO") != null) return;
        // Lets a mismatched version be tried without building an old client.
        int major = Integer.parseInt(System.getenv().getOrDefault("MAITYCOON_PROTOCOL_MAJOR", Integer.toString(Wire.MAJOR)));
        String version = FabricLoader.getInstance().getModContainer("maitycoon").map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("dev");
        ClientPlayNetworking.send(new Packet(Packet.HELLO, Wire.encode(new Wire.Hello(major, Wire.MINOR, version, List.of(Wire.FULL_MAP, Wire.SHOP_FEATURE)))));
    }
    static void acknowledged(byte[] bytes) {
        Wire.HelloAck ack;
        try { ack = Wire.helloAck(bytes); }
        catch (IllegalArgumentException e) { LOG.warn("Malformed hello_ack", e); return; }
        boolean ready = ack.status() == Wire.OK && ack.major() == Wire.MAJOR && ack.features().contains(Wire.FULL_MAP);
        serverMinor = ack.minor();
        status = ready ? Status.READY : Status.OUTDATED;
        message = ack.message();
        LOG.info("Handshake {} (server protocol {}.{})", ready ? "ready" : "refused", ack.major(), ack.minor());
        Capture.handshakeReady(Minecraft.getInstance());
    }
    /** Returns the id the answer will carry, or -1 when there is no one to ask. */
    static int requestMap() {
        if (status != Status.READY) return -1;
        int id = ++nextRequest;
        ClientPlayNetworking.send(new Packet(Packet.MAP_REQUEST, Wire.encode(new Wire.MapRequest(id))));
        return id;
    }
    static int requestShop(int kind, int action, String item, int amount, int revision) {
        if (status != Status.READY) return -1;
        int id = ++nextRequest;
        ClientPlayNetworking.send(new Packet(Packet.SHOP_REQUEST, Wire.encode(new Wire.ShopRequest(id, kind, action, item, amount, revision))));
        return id;
    }
    static void shopArrived(byte[] bytes) {
        Wire.ShopData shop;
        try { shop = Wire.shop(bytes); }
        catch (IllegalArgumentException e) { LOG.warn("Malformed shop", e); return; }
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof ShopScreen screen && screen.kind() == shop.kind()) screen.update(shop);
        else if (shop.reason() == Wire.OPEN) client.setScreen(new ShopScreen(shop));
    }
    static void shopResult(byte[] bytes) {
        Wire.ShopResult result;
        try { result = Wire.shopResult(bytes); }
        catch (IllegalArgumentException e) { LOG.warn("Malformed shop_result", e); return; }
        if (Minecraft.getInstance().screen instanceof ShopScreen screen) screen.result(result);
    }
    static void mapArrived(byte[] bytes) {
        Wire.MapData map;
        try { map = Wire.map(bytes, serverMinor < 1); }
        catch (IllegalArgumentException e) { LOG.warn("Malformed map", e); return; }
        if (Minecraft.getInstance().screen instanceof MapScreen screen) screen.accept(map);
    }
}
