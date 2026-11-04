package kr.maitycoon;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import kr.maitycoon.protocol.Wire;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.messaging.PluginMessageListener;

/**
 * The Mai Tycoon client mod's connection: the handshake, the map it asks for and, when the mod is required, holding a
 * player still until it answers. A handshake proves nothing about the client, so every request is still checked here.
 */
final class ClientLink implements PluginMessageListener, Listener {
    private record Session(int major, int minor, String modVersion, boolean fullMap, boolean shop) {}
    private static final long MAP_INTERVAL = 400;
    /** 1.1 clients get 8-block cells over 1024 blocks; 1.0 clients keep chunk cells over 1536 blocks. */
    private static final int MAP_HALF = 48, FINE_HALF = 64, FINE_CELL = 8;
    private final MaiTycoonPlugin plugin;
    private final FullMap map;
    private final Shop shop;
    private final Lamps lamps;
    /** Which mod shop each player has open; requests for any other shop are refused. */
    private final Map<UUID, Integer> openShops = new HashMap<>();
    private final boolean required;
    private final int waitSeconds;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Long> lastMap = new HashMap<>();
    private final Set<UUID> waiting = new HashSet<>();
    /** Messages for a channel the client has not registered yet; Paper drops those, so they wait for the registration. */
    private final Map<UUID, Map<String, byte[]>> held = new HashMap<>();
    ClientLink(MaiTycoonPlugin plugin, FullMap map, Shop shop, Lamps lamps, boolean required, int waitSeconds) {
        this.plugin = plugin;
        this.map = map;
        this.shop = shop;
        this.lamps = lamps;
        this.required = required;
        this.waitSeconds = waitSeconds;
    }
    void register() {
        var messenger = plugin.getServer().getMessenger();
        messenger.registerIncomingPluginChannel(plugin, Wire.HELLO, this);
        messenger.registerIncomingPluginChannel(plugin, Wire.MAP_REQUEST, this);
        messenger.registerIncomingPluginChannel(plugin, Wire.SHOP_REQUEST, this);
        messenger.registerOutgoingPluginChannel(plugin, Wire.HELLO_ACK);
        messenger.registerOutgoingPluginChannel(plugin, Wire.MAP);
        messenger.registerOutgoingPluginChannel(plugin, Wire.SHOP);
        messenger.registerOutgoingPluginChannel(plugin, Wire.SHOP_RESULT);
    }
    boolean connected(Player player) { return sessions.containsKey(player.getUniqueId()); }
    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        try {
            switch (channel) {
                case Wire.HELLO -> hello(player, Wire.hello(message));
                case Wire.MAP_REQUEST -> map(player, Wire.mapRequest(message));
                case Wire.SHOP_REQUEST -> plugin.guarded(player, () -> shopRequest(player, Wire.shopRequest(message)));
                default -> {}
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().log(Level.FINE, "Ignored a malformed " + channel + " from " + player.getName(), e);
        }
    }
    private void hello(Player player, Wire.Hello hello) {
        UUID id = player.getUniqueId();
        int status = hello.major() == Wire.MAJOR ? Wire.OK : hello.major() < Wire.MAJOR ? Wire.CLIENT_OLD : Wire.SERVER_OLD;
        String message = switch (status) {
            case Wire.OK -> "";
            case Wire.CLIENT_OLD -> "Mai Tycoon 클라이언트를 새 버전으로 업데이트해 주세요. (서버 규격 " + Wire.MAJOR + "." + Wire.MINOR + ")";
            default -> "이 서버는 아직 이 클라이언트 버전을 지원하지 않습니다. 서버 관리자에게 알려 주세요.";
        };
        if (status != Wire.OK) {
            sessions.remove(id);
            plugin.getLogger().info(player.getName() + " has an incompatible client " + hello.modVersion() + " (protocol " + hello.major() + "." + hello.minor() + ")");
            if (required) { player.kick(Component.text(message, NamedTextColor.YELLOW)); return; }
            send(player, Wire.HELLO_ACK, Wire.encode(new Wire.HelloAck(status, Wire.MAJOR, Wire.MINOR, message, List.of())));
            return;
        }
        sessions.put(id, new Session(hello.major(), hello.minor(), hello.modVersion(), hello.features().contains(Wire.FULL_MAP), hello.features().contains(Wire.SHOP_FEATURE)));
        List<String> granted = new ArrayList<>();
        for (String feature : List.of(Wire.FULL_MAP, Wire.SHOP_FEATURE)) if (hello.features().contains(feature)) granted.add(feature);
        waiting.remove(id);
        send(player, Wire.HELLO_ACK, Wire.encode(new Wire.HelloAck(Wire.OK, Wire.MAJOR, Math.min(Wire.MINOR, hello.minor()), "", granted)));
        plugin.getLogger().info(player.getName() + " joined with client " + hello.modVersion());
    }
    private void map(Player player, Wire.MapRequest request) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || !session.fullMap()) return;
        long now = System.currentTimeMillis();
        if (now - lastMap.getOrDefault(player.getUniqueId(), 0L) < MAP_INTERVAL) return;
        lastMap.put(player.getUniqueId(), now);
        boolean legacy = sessions.get(player.getUniqueId()).minor() < 1;
        send(player, Wire.MAP, Wire.encode(legacy ? map.data(player, request.requestId(), MAP_HALF, Wire.LEGACY_CELL) : map.data(player, request.requestId(), FINE_HALF, FINE_CELL), legacy));
    }
    /** Opens the mod's shop instead of the chest one when the client can show it. */
    boolean openShop(Player player, int kind) throws IOException {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || !session.shop()) return false;
        openShops.put(player.getUniqueId(), kind);
        send(player, Wire.SHOP, Wire.encode(snapshot(player, kind, Wire.OPEN)));
        return true;
    }
    private Wire.ShopData snapshot(Player player, int kind, int reason) throws IOException {
        return kind == Wire.SHOP_LAMP ? lamps.snapshot(player, reason) : shop.snapshot(player, Shop.kindOf(kind), reason);
    }
    /** Prices changed: every open mod shop gets the new numbers. */
    void refreshShops() {
        for (var entry : new ArrayList<>(openShops.entrySet())) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null) plugin.guarded(player, () -> send(player, Wire.SHOP, Wire.encode(snapshot(player, entry.getValue(), Wire.REFRESH))));
        }
    }
    private void shopRequest(Player player, Wire.ShopRequest request) throws IOException {
        Integer open = openShops.get(player.getUniqueId());
        if (request.action() == Wire.ACT_CLOSE) { openShops.remove(player.getUniqueId()); return; }
        if (open == null || open != request.kind()) { reply(player, request, Shop.Outcome.refused("상점이 닫혀 있습니다. NPC를 다시 눌러 주세요."), false); return; }
        Shop.Outcome outcome = switch (request.action()) {
            case Wire.ACT_BUY -> request.kind() == Wire.SHOP_LAMP ? lamps.tryBuy(player, request.amount()) : shop.tryBuy(player, Shop.kindOf(request.kind()), request.item(), request.amount());
            case Wire.ACT_SELL -> request.kind() == Wire.SHOP_SELL ? shop.trySell(player, request.item(), Math.max(0, request.amount())) : Shop.Outcome.refused("이 상점은 사들이지 않습니다.");
            case Wire.ACT_SELL_ALL -> request.kind() == Wire.SHOP_SELL ? shop.trySellAll(player, request.revision()) : Shop.Outcome.refused("이 상점은 사들이지 않습니다.");
            case Wire.ACT_REFRESH -> Shop.Outcome.OK;
            default -> Shop.Outcome.refused("알 수 없는 요청입니다.");
        };
        reply(player, request, outcome, true);
    }
    private void reply(Player player, Wire.ShopRequest request, Shop.Outcome outcome, boolean refresh) throws IOException {
        send(player, Wire.SHOP_RESULT, Wire.encode(new Wire.ShopResult(request.requestId(), outcome.status(), outcome.message())));
        if (refresh) send(player, Wire.SHOP, Wire.encode(snapshot(player, request.kind(), Wire.REFRESH)));
    }
    private void send(Player player, String channel, byte[] bytes) {
        if (player.getListeningPluginChannels().contains(channel)) player.sendPluginMessage(plugin, channel, bytes);
        else held.computeIfAbsent(player.getUniqueId(), id -> new HashMap<>()).put(channel, bytes);
    }
    @EventHandler
    public void onRegister(PlayerRegisterChannelEvent event) {
        Map<String, byte[]> waitingMessages = held.get(event.getPlayer().getUniqueId());
        byte[] bytes = waitingMessages == null ? null : waitingMessages.remove(event.getChannel());
        if (bytes != null) event.getPlayer().sendPluginMessage(plugin, event.getChannel(), bytes);
    }
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!required) return;
        Player player = event.getPlayer();
        waiting.add(player.getUniqueId());
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && waiting.contains(player.getUniqueId())) {
                player.kick(Component.text("Mai Tycoon 클라이언트 모드가 필요합니다.\n안내받은 모드팩을 설치한 뒤 다시 접속해 주세요.", NamedTextColor.YELLOW));
            }
        }, waitSeconds * 20L);
    }
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        sessions.remove(id);
        openShops.remove(id);
        lastMap.remove(id);
        waiting.remove(id);
        held.remove(id);
    }
    /** Until the client answers, the player may look around and chat, nothing else. */
    private void hold(Player player, Cancellable event) { if (player != null && waiting.contains(player.getUniqueId())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(PlayerMoveEvent event) { if (event.hasChangedPosition()) hold(event.getPlayer(), event); }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) { hold(event.getPlayer(), event); }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) { hold(event.getPlayer(), event); }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onOpen(InventoryOpenEvent event) { if (event.getPlayer() instanceof Player player) hold(player, event); }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventory(InventoryClickEvent event) { if (event.getWhoClicked() instanceof Player player) hold(player, event); }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) { if (event.getWhoClicked() instanceof Player player) hold(player, event); }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwap(PlayerSwapHandItemsEvent event) { hold(event.getPlayer(), event); }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) { hold(event.getPlayer(), event); }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent event) { hold(event.getPlayer(), event); }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(BlockPlaceEvent event) { hold(event.getPlayer(), event); }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) { hold(event.getPlayer(), event); }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(EntityPickupItemEvent event) { if (event.getEntity() instanceof Player player) hold(player, event); }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onHit(EntityDamageByEntityEvent event) { if (event.getDamager() instanceof Player player) hold(player, event); }
}
