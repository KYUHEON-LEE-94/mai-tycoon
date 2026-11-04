package kr.maitycoon;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

/**
 * Serves the bundled pack.zip over HTTP and offers it to joining players. Declining it is fine: shops fall back to plain items.
 * With host "auto" each player downloads from the address they connected with, so the same server works from this
 * machine, the LAN and a VPN share without anyone editing the config.
 */
final class ResourcePack implements Listener {
    private final byte[] pack, hash;
    private final String host, name;
    private final int port;
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
        this.host = host;
        this.port = port;
        name = HexFormat.of().formatHex(hash);
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
    /** The address the client typed to reach the server is the one address it can surely reach again. */
    String url(Player player) {
        String target = host;
        if (host.equals("auto")) {
            var virtual = player.getVirtualHost();
            String typed = virtual == null ? "" : virtual.getHostString();
            target = typed.isEmpty() ? "127.0.0.1" : typed.contains(":") ? "[" + typed + "]" : typed;
        }
        return "http://" + target + ":" + port + "/" + name + ".zip";
    }
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().setResourcePack(url(event.getPlayer()), hash, Component.text("상점 화면을 꾸며 주는 리소스팩입니다. 받지 않아도 플레이할 수 있습니다."), false);
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
