package kr.maitycoon;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

/** Serves the bundled pack.zip over HTTP and offers it to joining players. Declining it is fine: shops fall back to plain items. */
final class ResourcePack implements Listener {
    private final byte[] pack, hash;
    private final String url;
    private final HttpServer server;
    private final Shop shop;
    ResourcePack(MaiTycoonPlugin plugin, Shop shop, String host, int port) throws IOException {
        this.shop = shop;
        try (InputStream in = plugin.getResource("pack.zip")) {
            if (in == null) throw new IOException("Missing pack.zip");
            pack = in.readAllBytes();
            hash = MessageDigest.getInstance("SHA-1").digest(pack);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        String name = HexFormat.of().formatHex(hash);
        url = "http://" + host + ":" + port + "/" + name + ".zip";
        // A local host stays off the network; anything else has to accept outside connections.
        var address = host.equals("127.0.0.1") ? new InetSocketAddress(host, port) : new InetSocketAddress(port);
        server = HttpServer.create(address, 0);
        server.createContext("/" + name + ".zip", exchange -> {
            try (exchange) {
                exchange.getResponseHeaders().set("Content-Type", "application/zip");
                exchange.sendResponseHeaders(200, pack.length);
                exchange.getResponseBody().write(pack);
            }
        });
        server.start();
    }
    void stop() { server.stop(0); }
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().setResourcePack(url, hash, Component.text("상점 화면을 꾸며 주는 리소스팩입니다. 받지 않아도 플레이할 수 있습니다."), false);
    }
    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> shop.setPackReady(event.getPlayer(), true);
            case ACCEPTED, DOWNLOADED -> {}
            default -> shop.setPackReady(event.getPlayer(), false);
        }
    }
    @EventHandler
    public void onQuit(PlayerQuitEvent event) { shop.forget(event.getPlayer()); }
}
