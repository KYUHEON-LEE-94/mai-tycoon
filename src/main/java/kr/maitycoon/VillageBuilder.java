package kr.maitycoon;

import java.util.*;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.*;
import org.bukkit.block.data.type.*;
import org.bukkit.block.sign.Side;

/**
 * Draws the starter town. Coordinates are relative to the plaza center and y=0 is the walking surface.
 * North (-z): market stalls. West/east roads: town hall, houses. South-east: shared plots. South-west: specialty beds.
 */
final class VillageBuilder {
    static final int RADIUS = 44, BLEND = 10, SPAWN_DZ = 7;
    static final int MARKET_STALL_X = -7, SEED_STALL_X = 7, NPC_Z = -15, PLAZA_NPC_X = 9;
    private static final int PLAZA = 12, ROAD = 2, DEPTH = 24, CLEAR_MAX_ABOVE = 80;
    private static final int[][] PLOTS = {{16, 16}, {27, 16}, {16, 27}, {27, 27}};
    private static final Village.Zone CANE = new Village.Zone(-36, 17, -30, 23), CACTUS = new Village.Zone(-28, 17, -22, 23),
        WART = new Village.Zone(-20, 17, -15, 23), MUSHROOM = new Village.Zone(-36, 27, -30, 33),
        POND = new Village.Zone(-28, 27, -22, 33), ORCHARD = new Village.Zone(-20, 27, -15, 33);
    static final List<Village.Zone> FARMS;
    static {
        List<Village.Zone> farms = new ArrayList<>(List.of(CANE, CACTUS, WART, MUSHROOM, POND, ORCHARD));
        for (int[] plot : PLOTS) farms.add(new Village.Zone(plot[0], plot[1], plot[0] + 8, plot[1] + 8));
        FARMS = List.copyOf(farms);
    }
    private final World world;
    private final int cx, y0, cz, sea;
    private final Random random;
    private final Set<Long> fences = new HashSet<>();
    VillageBuilder(World world, int cx, int cz, int y0) {
        this.world = world;
        this.cx = cx;
        this.cz = cz;
        this.y0 = y0;
        this.sea = world.getSeaLevel();
        this.random = new Random(world.getSeed() ^ 0x4D414954L);
    }
    static int surfaceY(World world, int x, int z) {
        int ground = ground(world, x, z, world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE));
        return Math.max(world.getSeaLevel() + 1, Math.min(ground, 110));
    }
    private static int ground(World world, int x, int z, int top) {
        int y = top;
        while (y > world.getMinHeight() + 1) {
            Material type = world.getBlockAt(x, y, z).getType();
            if (type.isSolid() && !Tag.LOGS.isTagged(type) && !Tag.LEAVES.isTagged(type)) break;
            y--;
        }
        return y;
    }
    int y() { return y0; }
    Iterator<int[]> columns() {
        List<int[]> columns = new ArrayList<>();
        int reach = RADIUS + BLEND;
        for (int dx = -reach; dx <= reach; dx++) for (int dz = -reach; dz <= reach; dz++) columns.add(new int[]{dx, dz});
        return columns.iterator();
    }
    /** Flat inside RADIUS, then a slope that meets the natural ground within BLEND blocks. */
    void terrain(int[] column) {
        int x = cx + column[0], z = cz + column[1];
        int distance = Math.max(Math.abs(column[0]), Math.abs(column[1]));
        boolean town = distance <= RADIUS;
        int top = world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
        int ground = ground(world, x, z, top);
        int target = town ? y0 : (int) Math.round(y0 + (ground - y0) * (double) (distance - RADIUS) / BLEND);
        if (!town && target < sea && world.getBlockAt(x, top, z).isLiquid()) return;
        for (int y = Math.min(top, y0 + CLEAR_MAX_ABOVE); y > target; y--) {
            Block block = world.getBlockAt(x, y, z);
            if (town || !block.isLiquid()) block.setType(Material.AIR, false);
        }
        if (!town && target == ground) return;
        for (int y = target; y > target - DEPTH && y > world.getMinHeight(); y--) {
            Block block = world.getBlockAt(x, y, z);
            if (y == target) block.setType(Material.GRASS_BLOCK, false);
            else if (y > target - 4) block.setType(Material.DIRT, false);
            else if (block.getType().isSolid()) break;
            else block.setType(Material.STONE, false);
        }
    }
    void structures() {
        roads();
        plaza();
        fountain();
        stall(MARKET_STALL_X, Material.YELLOW_WOOL, Material.WHITE_WOOL, "농산물 상점", "작물 판매",
            List.of(Material.HAY_BLOCK, Material.MELON, Material.PUMPKIN, Material.BARREL, Material.HAY_BLOCK));
        stall(SEED_STALL_X, Material.GREEN_WOOL, Material.WHITE_WOOL, "씨앗 상점", "씨앗·도구 구매",
            List.of(Material.BARREL, Material.COMPOSTER, Material.BARREL, Material.COMPOSTER, Material.BARREL));
        building(-36, -15, -16, -5, 5, Material.STONE_BRICKS, BlockFace.SOUTH, -26, "마을회관");
        building(16, -13, 24, -5, 4, Material.OAK_PLANKS, BlockFace.SOUTH, 22, "민가");
        building(28, -13, 36, -5, 4, Material.SPRUCE_PLANKS, BlockFace.SOUTH, 34, "민가");
        building(16, 5, 24, 12, 4, Material.BIRCH_PLANKS, BlockFace.NORTH, 22, "민가");
        building(28, 5, 36, 12, 4, Material.OAK_PLANKS, BlockFace.NORTH, 34, "민가");
        building(-36, 5, -16, 12, 5, Material.COBBLESTONE, BlockFace.NORTH, -26, "공동 창고");
        for (int i = 0; i < PLOTS.length; i++) plot(PLOTS[i][0], PLOTS[i][1], i);
        specialtyBeds();
        perimeter();
        for (int d = 10; d <= RADIUS - 6; d += 10) for (int side : new int[]{-1, 1}) {
            lamp(d * side, ROAD + 1);
            lamp(d * side, -ROAD - 1);
            if (d > PLAZA + 8) { lamp(ROAD + 1, d * side); lamp(-ROAD - 1, d * side); }
        }
        for (int[] corner : new int[][]{{-11, -11}, {11, -11}, {-11, 11}, {11, 11}}) lamp(corner[0], corner[1]);
        connectFences();
        greenery();
    }
    private void roads() {
        for (int a = -RADIUS; a <= RADIUS; a++) for (int b = -ROAD; b <= ROAD; b++) {
            set(a, 0, b, roadBlock());
            set(b, 0, a, roadBlock());
        }
    }
    private Material roadBlock() {
        int roll = random.nextInt(10);
        return roll < 6 ? Material.STONE_BRICKS : roll < 8 ? Material.CRACKED_STONE_BRICKS : Material.ANDESITE;
    }
    private void plaza() {
        for (int dx = -PLAZA; dx <= PLAZA; dx++) for (int dz = -PLAZA; dz <= PLAZA; dz++) {
            boolean edge = Math.max(Math.abs(dx), Math.abs(dz)) == PLAZA;
            set(dx, 0, dz, edge ? Material.SMOOTH_STONE : (Math.abs(dx) + Math.abs(dz)) % 4 == 0 ? Material.POLISHED_ANDESITE : Material.STONE_BRICKS);
        }
    }
    private void fountain() {
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            double d = Math.hypot(dx, dz);
            if (d <= 3.2) {
                set(dx, -1, dz, Material.STONE_BRICKS);
                set(dx, 0, dz, Material.WATER);
            } else if (d <= 4.5) {
                set(dx, 0, dz, Material.STONE_BRICKS);
                set(dx, 1, dz, Material.STONE_BRICK_SLAB);
            }
        }
        for (int y = 0; y <= 2; y++) set(0, y, 0, Material.CHISELED_STONE_BRICKS);
        set(0, 3, 0, Material.SEA_LANTERN);
        for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
            set(face.getModX(), 2, face.getModZ(), Material.STONE_BRICK_SLAB);
        }
    }
    /** Open stall on the north edge of the plaza; the NPC stands behind the counter facing south. */
    private void stall(int mid, Material stripeA, Material stripeB, String title, String purpose, List<Material> crates) {
        int back = -19, front = -13;
        rect(mid - 4, back, mid + 4, front, 0, Material.OAK_PLANKS);
        for (int dx : new int[]{mid - 4, mid + 4}) for (int dz : new int[]{back, front}) for (int y = 1; y <= 3; y++) set(dx, y, dz, Material.OAK_LOG);
        for (int dx = mid - 3; dx <= mid + 3; dx++) for (int y = 1; y <= 3; y++) set(dx, y, back, Material.SPRUCE_PLANKS);
        for (int dx = mid - 5; dx <= mid + 5; dx++) for (int dz = back - 1; dz <= front + 1; dz++) {
            set(dx, 4, dz, Math.floorMod(dx, 2) == 0 ? stripeA : stripeB);
        }
        for (int dx = mid - 3; dx <= mid + 3; dx++) set(dx, 1, front, Material.SPRUCE_PLANKS);
        for (int i = 0; i < crates.size(); i++) set(mid - 2 + i, 1, back + 1, crates.get(i));
        set(mid - 3, 2, front, Material.LANTERN);
        set(mid + 3, 2, front, Material.LANTERN);
        wallSign(mid, 1, front + 1, BlockFace.SOUTH, Material.SPRUCE_WALL_SIGN, title, purpose);
    }
    private void building(int x1, int z1, int x2, int z2, int height, Material wall, BlockFace front, int doorX, String name) {
        rect(x1, z1, x2, z2, 0, Material.OAK_PLANKS);
        int doorZ = front == BlockFace.SOUTH ? z2 : z1;
        for (int dx = x1; dx <= x2; dx++) for (int dz = z1; dz <= z2; dz++) {
            boolean edge = dx == x1 || dx == x2 || dz == z1 || dz == z2;
            if (!edge) continue;
            boolean corner = (dx == x1 || dx == x2) && (dz == z1 || dz == z2);
            for (int y = 1; y <= height; y++) {
                boolean window = !corner && y == 2 && (dx - x1 + dz - z1) % 3 == 1;
                set(dx, y, dz, corner ? Material.OAK_LOG : window ? Material.GLASS : wall);
            }
        }
        for (int y = 1; y <= 2; y++) set(doorX, y, doorZ, Material.AIR);
        door(doorX, 1, doorZ, front.getOppositeFace());
        int outward = front == BlockFace.SOUTH ? 1 : -1;
        wallSign(doorX, 3, doorZ + outward, front, Material.OAK_WALL_SIGN, name);
        for (int dz = doorZ + outward; Math.abs(dz) > ROAD; dz += outward) set(doorX, 0, dz, Material.STONE_BRICKS);
        roof(x1, z1, x2, z2, height, wall);
        set(x1 + 1, 1, z1 + 1, Material.CRAFTING_TABLE);
        set(x2 - 1, 1, z1 + 1, Material.BARREL);
        set(x2 - 1, 2, z1 + 1, Material.LANTERN);
        set(x1 + 1, 1, z2 - 1, Material.BARREL);
        set(x1 + 1, 2, z2 - 1, Material.LANTERN);
    }
    private void roof(int x1, int z1, int x2, int z2, int height, Material wall) {
        int north = z1 - 1, south = z2 + 1;
        for (int layer = 0; north + layer <= south - layer; layer++) {
            int y = height + 1 + layer, n = north + layer, s = south - layer;
            for (int dx = x1 - 1; dx <= x2 + 1; dx++) {
                if (n == s) { set(dx, y, n, Material.DARK_OAK_SLAB); continue; }
                data(dx, y, n, stairs(Material.DARK_OAK_STAIRS, BlockFace.SOUTH));
                data(dx, y, s, stairs(Material.DARK_OAK_STAIRS, BlockFace.NORTH));
            }
            for (int dz = n + 1; dz < s; dz++) { set(x1, y, dz, wall); set(x2, y, dz, wall); }
        }
    }
    private void plot(int px, int pz, int index) {
        Farmland wet = (Farmland) Material.FARMLAND.createBlockData();
        wet.setMoisture(wet.getMaximumMoisture());
        Material crop = index == 0 ? Material.WHEAT : index == 1 ? Material.CARROTS : null;
        for (int dx = px; dx <= px + 8; dx++) for (int dz = pz; dz <= pz + 8; dz++) {
            data(dx, 0, dz, wet);
            if (crop == null) continue;
            Ageable grown = (Ageable) crop.createBlockData();
            grown.setAge(grown.getMaximumAge());
            data(dx, 1, dz, grown);
        }
        set(px + 4, 0, pz + 4, Material.WATER);
        set(px + 4, 1, pz + 4, Material.AIR);
        for (int i = -1; i <= 9; i++) {
            fence(px + i, pz - 1);
            fence(px + i, pz + 9);
            fence(px - 1, pz + i);
            fence(px + 9, pz + i);
        }
        Gate gate = (Gate) Material.OAK_FENCE_GATE.createBlockData();
        gate.setFacing(BlockFace.NORTH);
        data(px + 4, 1, pz - 1, gate);
        fences.add(key(px + 4, pz - 1));
        standingSign(px + 3, pz - 2, "공용 농장 " + (index + 1));
    }
    private void specialtyBeds() {
        rect(CANE, Material.SAND);
        for (int dz = CANE.z1(); dz <= CANE.z2(); dz++) set(CANE.x1() + 3, 0, dz, Material.WATER);
        rect(CACTUS, Material.SAND);
        rect(WART, Material.SOUL_SAND);
        rect(MUSHROOM, Material.PODZOL);
        rect(ORCHARD, Material.GRASS_BLOCK);
        for (int dx = POND.x1(); dx <= POND.x2(); dx++) for (int dz = POND.z1(); dz <= POND.z2(); dz++) {
            for (int y = 0; y >= -2; y--) set(dx, y, dz, Material.WATER);
            set(dx, -3, dz, Material.GRAVEL);
        }
        for (int dx : new int[]{ORCHARD.x1() + 1, ORCHARD.x2() - 1}) {
            for (int dz : new int[]{ORCHARD.z1() + 2, ORCHARD.z2() - 2}) for (int y = 1; y <= 4; y++) set(dx, y, dz, Material.JUNGLE_LOG);
        }
        for (int dz : new int[]{ORCHARD.z1() + 2, ORCHARD.z2() - 2}) for (int dx = ORCHARD.x1() + 2; dx < ORCHARD.x2() - 1; dx++) set(dx, 4, dz, Material.JUNGLE_PLANKS);
        bedSign(CANE, "사탕수수", "모래·물가");
        bedSign(CACTUS, "선인장", "모래, 한 칸씩 띄워서");
        bedSign(WART, "네더 사마귀", "영혼 모래");
        bedSign(MUSHROOM, "버섯", "회백토");
        bedSign(POND, "다시마", "연못 안");
        bedSign(ORCHARD, "코코아·베리·대나무", "정글 나무·잔디");
        for (int dx = CANE.x1(); dx <= ORCHARD.x2(); dx++) for (int dz : new int[]{CANE.z1() - 2, MUSHROOM.z1() - 2}) set(dx, 0, dz, Material.DIRT_PATH);
        for (int dz = ROAD + 1; dz <= MUSHROOM.z1() - 2; dz++) set(-14, 0, dz, Material.DIRT_PATH);
    }
    private void bedSign(Village.Zone zone, String crop, String hint) {
        standingSign(zone.x1(), zone.z1() - 1, crop, hint);
    }
    private void perimeter() {
        for (int a = -RADIUS; a <= RADIUS; a++) {
            boolean road = Math.abs(a) <= ROAD;
            if (road) continue;
            fence(a, -RADIUS);
            fence(a, RADIUS);
            fence(-RADIUS, a);
            fence(RADIUS, a);
        }
        for (int side : new int[]{-1, 1}) {
            lamp(ROAD + 1, side * RADIUS);
            lamp(-ROAD - 1, side * RADIUS);
            lamp(side * RADIUS, ROAD + 1);
            lamp(side * RADIUS, -ROAD - 1);
        }
    }
    private void lamp(int dx, int dz) {
        for (int y = 1; y <= 3; y++) set(dx, y, dz, Material.OAK_FENCE);
        set(dx, 0, dz, Material.STONE_BRICKS);
        set(dx, 4, dz, Material.LANTERN);
    }
    private void fence(int dx, int dz) {
        set(dx, 1, dz, Material.OAK_FENCE);
        fences.add(key(dx, dz));
    }
    private static long key(int dx, int dz) { return ((long) dx << 32) ^ (dz & 0xffffffffL); }
    /** Blocks are placed without physics, so fence arms have to be set by hand. */
    private void connectFences() {
        for (long key : fences) {
            int dx = (int) (key >> 32), dz = (int) key;
            if (at(dx, 1, dz).getType() != Material.OAK_FENCE) continue;
            Fence fence = (Fence) Material.OAK_FENCE.createBlockData();
            for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
                fence.setFace(face, fences.contains(key(dx + face.getModX(), dz + face.getModZ())));
            }
            data(dx, 1, dz, fence);
        }
    }
    private void greenery() {
        List<Material> flowers = List.of(Material.POPPY, Material.DANDELION, Material.CORNFLOWER, Material.OXEYE_DAISY, Material.SHORT_GRASS, Material.SHORT_GRASS);
        for (int i = 0; i < 260; i++) {
            int dx = random.nextInt(-RADIUS + 1, RADIUS), dz = random.nextInt(-RADIUS + 1, RADIUS);
            if (inFarm(dx, dz) || at(dx, 0, dz).getType() != Material.GRASS_BLOCK || !at(dx, 1, dz).getType().isAir()) continue;
            set(dx, 1, dz, flowers.get(random.nextInt(flowers.size())));
        }
        int[][] trees = {{-34, -34}, {-22, -32}, {-10, -34}, {10, -32}, {22, -34}, {34, -30}, {-40, -20}, {40, -20}, {-40, 40}, {-8, 38}, {8, 30}, {8, 40}, {-8, 28}};
        for (int[] tree : trees) world.generateTree(new Location(world, cx + tree[0], y0 + 1, cz + tree[1]), TreeType.TREE);
    }
    private static boolean inFarm(int dx, int dz) {
        return FARMS.stream().anyMatch(zone -> zone.contains(dx, dz));
    }
    private Block at(int dx, int dy, int dz) { return world.getBlockAt(cx + dx, y0 + dy, cz + dz); }
    private void set(int dx, int dy, int dz, Material type) { at(dx, dy, dz).setType(type, false); }
    private void data(int dx, int dy, int dz, BlockData data) { at(dx, dy, dz).setBlockData(data, false); }
    private void rect(int x1, int z1, int x2, int z2, int dy, Material type) {
        for (int dx = x1; dx <= x2; dx++) for (int dz = z1; dz <= z2; dz++) set(dx, dy, dz, type);
    }
    private void rect(Village.Zone zone, Material type) { rect(zone.x1(), zone.z1(), zone.x2(), zone.z2(), 0, type); }
    private static Stairs stairs(Material type, BlockFace facing) {
        Stairs stairs = (Stairs) type.createBlockData();
        stairs.setFacing(facing);
        return stairs;
    }
    private void door(int dx, int dy, int dz, BlockFace facing) {
        for (var half : Bisected.Half.values()) {
            Door door = (Door) Material.OAK_DOOR.createBlockData();
            door.setFacing(facing);
            door.setHalf(half);
            data(dx, half == Bisected.Half.BOTTOM ? dy : dy + 1, dz, door);
        }
    }
    private void standingSign(int dx, int dz, String... lines) {
        Block block = at(dx, 1, dz);
        var data = (org.bukkit.block.data.type.Sign) Material.OAK_SIGN.createBlockData();
        data.setRotation(BlockFace.NORTH);
        block.setBlockData(data, false);
        writeSign(block, lines);
    }
    private void wallSign(int dx, int dy, int dz, BlockFace facing, Material type, String... lines) {
        Block block = at(dx, dy, dz);
        WallSign data = (WallSign) type.createBlockData();
        data.setFacing(facing);
        block.setBlockData(data, false);
        writeSign(block, lines);
    }
    private static void writeSign(Block block, String... lines) {
        var sign = (org.bukkit.block.Sign) block.getState();
        for (int i = 0; i < lines.length; i++) sign.getSide(Side.FRONT).line(i + 1, Component.text(lines[i]));
        sign.setWaxed(true);
        sign.update(true, false);
    }
}
