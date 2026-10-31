package kr.maitycoon;

import java.util.*;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/** A north-up map of the terrain around each player. The world is the shared source, and other players appear on it. */
final class MiniMap implements Listener {
    private static final int BLOCKS_PER_CELL = 8, RADIUS = MiniMapTitle.CELLS / 2, UNKNOWN = 0x1A1816, ME = 0xFFFFFF, OTHER = 0xFFE040;
    private static final long CELL_LIFETIME = 30_000;
    private record Cached(int color, long at) {}
    private final MaiTycoonPlugin plugin;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, String> painted = new HashMap<>();
    private final Map<String, Cached> terrain = new HashMap<>();
    MiniMap(MaiTycoonPlugin plugin) { this.plugin = plugin; }
    /** Reads only loaded chunks: a map must never force the world to generate. Cached so players nearby share the work. */
    private int color(World world, int cellX, int cellZ, long now) {
        String key = world.getUID() + ":" + cellX + ":" + cellZ;
        Cached known = terrain.get(key);
        if (known != null && now - known.at() < CELL_LIFETIME) return known.color();
        int x = cellX * BLOCKS_PER_CELL, z = cellZ * BLOCKS_PER_CELL;
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return UNKNOWN;
        int color = world.getHighestBlockAt(x, z).getBlockData().getMapColor().asRGB();
        terrain.put(key, new Cached(color, now));
        return color;
    }
    private static int cell(int block) { return Math.floorDiv(block, BLOCKS_PER_CELL); }
    void refresh() {
        long now = System.currentTimeMillis();
        terrain.values().removeIf(cached -> now - cached.at() > CELL_LIFETIME * 4);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (!plugin.packDrawn(player)) { hide(player); continue; }
            Location at = player.getLocation();
            World world = at.getWorld();
            int centerX = cell(at.getBlockX()), centerZ = cell(at.getBlockZ());
            int[][] cells = new int[MiniMapTitle.CELLS][MiniMapTitle.CELLS];
            for (int row = 0; row < cells.length; row++) for (int column = 0; column < cells.length; column++) {
                cells[row][column] = color(world, centerX + column - RADIUS, centerZ + row - RADIUS, now);
            }
            for (Player other : world.getPlayers()) {
                int column = cell(other.getLocation().getBlockX()) - centerX + RADIUS, row = cell(other.getLocation().getBlockZ()) - centerZ + RADIUS;
                if (row >= 0 && row < cells.length && column >= 0 && column < cells.length) cells[row][column] = OTHER;
            }
            cells[RADIUS][RADIUS] = ME;
            String signature = Arrays.deepToString(cells);
            if (signature.equals(painted.get(id))) continue;
            painted.put(id, signature);
            Component title = MiniMapTitle.render(cells);
            BossBar bar = bars.get(id);
            if (bar != null) bar.name(title);
            else {
                bar = BossBar.bossBar(title, 1f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
                bars.put(id, bar);
                player.showBossBar(bar);
            }
        }
    }
    private void hide(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) player.hideBossBar(bar);
        painted.remove(player.getUniqueId());
    }
    @EventHandler
    public void onQuit(PlayerQuitEvent event) { hide(event.getPlayer()); }
}
