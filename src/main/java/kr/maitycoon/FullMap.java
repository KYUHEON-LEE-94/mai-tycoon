package kr.maitycoon;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import kr.maitycoon.protocol.Wire;

/**
 * The terrain record behind both full maps: one colour per 8-block cell, four to a chunk, taken whenever a chunk
 * loads. Reading an unloaded chunk would load it, so elsewhere the map shows what was seen last.
 */
final class FullMap implements Listener {
    private static final class Holder implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }
    private static final int COLUMNS = MiniMapTitle.FULL_COLUMNS, ROWS = MiniMapTitle.FULL_ROWS, CENTER_COLUMN = COLUMNS / 2 - 1, CENTER_ROW = ROWS / 2 - 1;
    private static final int UNKNOWN = 0x1A1816, ME = 0xFFFFFF, VILLAGE = 0xF2C230;
    /** Cells are 8 blocks: two per chunk on each axis, sampled at each cell's middle. */
    static final int CELL = 8, CELL_SHIFT = 3, PER_CHUNK = 2;
    private static final String FORMAT = "cells-8";
    private final MaiTycoonPlugin plugin;
    private final Village village;
    private final Lands lands;
    private final Path file;
    private final Map<Long, Integer> colors = new HashMap<>();
    private boolean changed;
    FullMap(MaiTycoonPlugin plugin, Village village, Lands lands, Path file) throws IOException {
        this.plugin = plugin;
        this.village = village;
        this.lands = lands;
        this.file = file;
        if (!Files.exists(file)) return;
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        // Files from before the cell format hold one colour per chunk; it fills all four cells until the chunk loads again.
        boolean perChunk = lines.isEmpty() || !lines.get(0).equals(FORMAT);
        for (String line : lines) {
            String[] parts = line.split(",");
            if (parts.length != 3) continue;
            int x = Integer.parseInt(parts[0]), z = Integer.parseInt(parts[1]), color = Integer.parseInt(parts[2]);
            if (!perChunk) colors.put(key(x, z), color);
            else for (int i = 0; i < PER_CHUNK; i++) for (int j = 0; j < PER_CHUNK; j++) colors.put(key(x * PER_CHUNK + i, z * PER_CHUNK + j), color);
        }
        changed = perChunk;
    }
    private static long key(int chunkX, int chunkZ) { return (long) chunkX << 32 | chunkZ & 0xFFFFFFFFL; }
    private static int sampleAt(World world, int cellX, int cellZ) { return world.getHighestBlockAt((cellX << CELL_SHIFT) + CELL / 2, (cellZ << CELL_SHIFT) + CELL / 2).getBlockData().getMapColor().asRGB(); }
    private void sampleCell(World world, int cellX, int cellZ) {
        Integer color = sampleAt(world, cellX, cellZ);
        if (!color.equals(colors.put(key(cellX, cellZ), color))) changed = true;
    }
    private void sample(World world, int chunkX, int chunkZ) {
        for (int i = 0; i < PER_CHUNK; i++) for (int j = 0; j < PER_CHUNK; j++) sampleCell(world, chunkX * PER_CHUNK + i, chunkZ * PER_CHUNK + j);
    }
    @EventHandler
    public void onLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        if (chunk.getWorld().equals(village.world())) sample(chunk.getWorld(), chunk.getX(), chunk.getZ());
    }
    void saveIfChanged() {
        if (!changed) return;
        List<String> lines = new ArrayList<>(colors.size() + 1);
        lines.add(FORMAT);
        colors.forEach((key, color) -> lines.add((int) (key >> 32) + "," + (int) (long) key + "," + color));
        try {
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            changed = false;
        } catch (IOException e) { plugin.getLogger().log(Level.WARNING, "Cannot save map colours", e); }
    }
    /** The recorded colour of a cell, refreshed when its chunk is loaded now. Only the village world is recorded. */
    private Integer terrainCell(World world, int cellX, int cellZ) {
        boolean recorded = world.equals(village.world());
        int chunkX = Math.floorDiv(cellX, PER_CHUNK), chunkZ = Math.floorDiv(cellZ, PER_CHUNK);
        if (world.isChunkLoaded(chunkX, chunkZ)) {
            if (!recorded) return sampleAt(world, cellX, cellZ);
            sampleCell(world, cellX, cellZ);
        }
        return recorded ? colors.get(key(cellX, cellZ)) : null;
    }
    /** A chunk's colour for the chest map and 1.0 clients: the cell holding the chunk's middle. */
    private Integer terrain(World world, int chunkX, int chunkZ) { return terrainCell(world, chunkX * PER_CHUNK + 1, chunkZ * PER_CHUNK + 1); }
    /** What the client mod draws: raw colours and who holds each cell, so the client decides how to show them. Cells are cellBlocks wide (8 or a whole chunk). */
    Wire.MapData data(Player player, int requestId, int half, int cellBlocks) {
        World world = player.getWorld();
        Location at = player.getLocation();
        int shift = Integer.numberOfTrailingZeros(cellBlocks), side = half * 2, originX = (at.getBlockX() >> shift) - half, originZ = (at.getBlockZ() >> shift) - half;
        int[] terrain = new int[side * side];
        byte[] overlays = new byte[side * side];
        for (int row = 0; row < side; row++) for (int column = 0; column < side; column++) {
            int cellX = originX + column, cellZ = originZ + row, index = row * side + column;
            int chunkX = (cellX << shift) >> 4, chunkZ = (cellZ << shift) >> 4;
            Integer color = cellBlocks == CELL ? terrainCell(world, cellX, cellZ) : terrain(world, chunkX, chunkZ);
            terrain[index] = color == null ? Wire.UNKNOWN : color;
            int owner = lands.ownership(player, chunkX, chunkZ);
            overlays[index] = (byte) (owner != Wire.NONE ? owner : village.overlaps(world, chunkX, chunkZ) ? Wire.VILLAGE : Wire.NONE);
        }
        List<Wire.Mark> players = new ArrayList<>();
        for (Player other : MiniMap.others(player)) {
            if (players.size() == Wire.MAX_LIST) break;
            players.add(new Wire.Mark(other.getName(), other.getLocation().getBlockX(), other.getLocation().getBlockZ()));
        }
        return new Wire.MapData(requestId, originX, originZ, side, side, cellBlocks, terrain, overlays, at.getBlockX(), at.getBlockZ(), players);
    }
    private int color(Player player, World world, int chunkX, int chunkZ) {
        Integer known = terrain(world, chunkX, chunkZ);
        int tint = lands.tint(player, chunkX, chunkZ);
        if (tint != Lands.NO_TINT) return MiniMapTitle.mix(known == null ? UNKNOWN : known, tint);
        if (village.overlaps(world, chunkX, chunkZ)) return MiniMapTitle.mix(known == null ? UNKNOWN : known, VILLAGE);
        return known == null ? UNKNOWN : known;
    }
    void open(Player player) {
        World world = player.getWorld();
        if (!world.equals(village.world())) { plugin.tell(player, "전체 지도는 마을이 있는 월드에서만 볼 수 있습니다."); return; }
        if (!plugin.packDrawn(player)) { plugin.tell(player, "전체 지도는 리소스팩을 받아야 볼 수 있습니다."); return; }
        Location at = player.getLocation();
        int centerX = at.getBlockX() >> 4, centerZ = at.getBlockZ() >> 4;
        int[][] cells = new int[ROWS][COLUMNS];
        for (int row = 0; row < ROWS; row++) for (int column = 0; column < COLUMNS; column++) {
            cells[row][column] = color(player, world, centerX + column - CENTER_COLUMN, centerZ + row - CENTER_ROW);
        }
        List<MiniMapTitle.Marker> markers = new ArrayList<>();
        for (Player other : MiniMap.others(player)) {
            Location there = other.getLocation();
            MiniMapTitle.Marker marker = MiniMapTitle.pin(CENTER_COLUMN, CENTER_ROW, (there.getBlockX() >> 4) - centerX, (there.getBlockZ() >> 4) - centerZ, (int) there.distance(at), other.getName());
            cells[marker.row()][marker.column()] = MiniMapTitle.OTHER;
            markers.add(marker);
        }
        cells[CENTER_ROW][CENTER_COLUMN] = ME;
        Holder holder = new Holder();
        holder.inventory = plugin.getServer().createInventory(holder, Ui.SIZE, new ShopTitle().mapPanel("전체 지도", cells, markers).build());
        for (int slot = 0; slot < Ui.SIZE; slot++) holder.inventory.setItem(slot, tooltip(slot, centerX, centerZ));
        player.openInventory(holder.inventory);
    }
    /** A slot covers six cells; the blank model leaves the map visible while its tooltip names the place and the colours. */
    private static ItemStack tooltip(int slot, int centerX, int centerZ) {
        int middle = MiniMapTitle.CELLS_PER_SLOT / 2;
        int x = centerX + slot % 9 * MiniMapTitle.CELLS_PER_SLOT + middle - CENTER_COLUMN, z = centerZ + slot / 9 * MiniMapTitle.CELLS_PER_SLOT + middle - CENTER_ROW;
        return Ui.blank(Ui.icon(Material.PAPER, Ui.line("X " + (x << 4) + ", Z " + (z << 4) + " 부근", NamedTextColor.YELLOW), List.of(
            Ui.line("흰색 나 · 노란색 다른 유저", NamedTextColor.GRAY), Ui.line("초록색 내 땅 · 빨간색 남의 땅 · 금색 마을", NamedTextColor.GRAY), Ui.line("어두운 곳은 아직 아무도 가지 않은 땅입니다.", NamedTextColor.DARK_GRAY))));
    }
    @EventHandler
    public void onClick(InventoryClickEvent event) { if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true); }
    @EventHandler
    public void onDrag(InventoryDragEvent event) { if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true); }
}
