package kr.maitycoon;

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
    private record Session(int major, int minor, String modVersion, boolean fullMap) {}
    private static final long MAP_INTERVAL = 400;
    /** 1.1 clients get 8-block cells over 1024 blocks; 1.0 clients keep chunk cells over 1536 blocks. */
    private static final int MAP_HALF = 48, FINE_HALF = 64, FINE_CELL = 8;
    private final MaiTycoonPlugin plugin;
    private final FullMap map;
    private final boolean required;
    private final int waitSeconds;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Long> lastMap = new HashMap<>();
    private final Set<UUID> waiting = new HashSet<>();
    /** Messages for a channel the client has not registered yet; Paper drops those, so they wait for the registration. */
    private final Map<UUID, Map<String, byte[]>> held = new HashMap<>();
    ClientLink(MaiTycoonPlugin plugin, FullMap map, boolean required, int waitSeconds) {
        this.plugin = plugin;
        this.map = map;
        this.required = required;
        this.waitSeconds = waitSeconds;
    }
    void register() {
        var messenger = plugin.getServer().getMessenger();
        messenger.registerIncomingPluginChannel(plugin, Wire.HELLO, this);
        messenger.registerIncomingPluginChannel(plugin, Wire.MAP_REQUEST, this);
        messenger.registerOutgoingPluginChannel(plugin, Wire.HELLO_ACK);
        messenger.registerOutgoingPluginChannel(plugin, Wire.MAP);
    }
    boolean connected(Player player) { return sessions.containsKey(player.getUniqueId()); }
    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        try {
            switch (channel) {
                case Wire.HELLO -> hello(player, Wire.hello(message));
                case Wire.MAP_REQUEST -> map(player, Wire.mapRequest(message));
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
        sessions.put(id, new Session(hello.major(), hello.minor(), hello.modVersion(), hello.features().contains(Wire.FULL_MAP)));
        waiting.remove(id);
        send(player, Wire.HELLO_ACK, Wire.encode(new Wire.HelloAck(Wire.OK, Wire.MAJOR, Math.min(Wire.MINOR, hello.minor()), "", hello.features().contains(Wire.FULL_MAP) ? List.of(Wire.FULL_MAP) : List.of())));
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
