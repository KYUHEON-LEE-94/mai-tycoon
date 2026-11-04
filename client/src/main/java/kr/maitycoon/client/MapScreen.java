package kr.maitycoon.client;

import java.util.ArrayList;
import java.util.List;
import kr.maitycoon.client.protocol.Wire;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * The whole map, one cell per 8 blocks (a chunk on a 1.0 server), drawn from what the server sends. Colours follow the server's charcoal and gold
 * menus. The server decides what is shown; this screen only moves and scales it.
 */
public final class MapScreen extends Screen {
    private static final int CHARCOAL = 0xFF211E1C, HEADER = 0xFF322921, EDGE = 0xFFE9D49C, GOLD = 0xFFF2DDA4, MUTED = 0xFFB8AE9C, UNKNOWN = 0xFF2A2725;
    private static final int ME = 0xFFFFFFFF, OTHER = 0xFFFFE040, MINE = 0x2EE66B, TAKEN = 0xFF4A4A, VILLAGE = 0xF2C230;
    /** Pixels per cell. The default shows a 1024-block map at six pixels per 8 blocks. */
    private static final int[] ZOOMS = {1, 2, 3, 4, 6, 8, 12, 16};
    private static final int DEFAULT_ZOOM = 4, MARK = 4, SELF_MARK = 5;
    private static final int REFRESH_TICKS = 40, TIMEOUT_TICKS = 100;
    private Wire.MapData map;
    private int zoom = DEFAULT_ZOOM, ticks, waitingFor = -1, waitedTicks;
    private double centerX, centerZ;
    private boolean follow = true;
    private int left, top, right, bottom, mapLeft, mapTop, mapRight, mapBottom;
    public MapScreen() { super(Component.translatable("key.maitycoon.full_map")); }
    @Override protected void init() {
        // The map takes the whole screen: detail matters more than the world behind it.
        int panelWidth = width - 12, panelHeight = height - 12;
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        right = left + panelWidth;
        bottom = top + panelHeight;
        mapLeft = left + 6;
        mapTop = top + 20;
        mapRight = right - 6;
        mapBottom = bottom - 28;
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void tick() {
        if (waitingFor >= 0 && ++waitedTicks > TIMEOUT_TICKS) waitingFor = -1;
        if (ticks++ % REFRESH_TICKS == 0 && waitingFor < 0) {
            waitingFor = Link.requestMap();
            waitedTicks = 0;
        }
    }
    void accept(Wire.MapData data) {
        if (data.requestId() != waitingFor) return;
        waitingFor = -1;
        map = data;
    }
    boolean loaded() { return map != null; }
    private int scale() { return ZOOMS[zoom]; }
    private double cell() { return map == null ? Wire.LEGACY_CELL : map.cellBlocks(); }
    private void followSelf() {
        if (!follow || map == null) return;
        centerX = map.selfX() / cell();
        centerZ = map.selfZ() / cell();
    }
    private int screenX(double cellX) { return (int) Math.floor((mapLeft + mapRight) / 2.0 + (cellX - centerX) * scale()); }
    private int screenY(double cellZ) { return (int) Math.floor((mapTop + mapBottom) / 2.0 + (cellZ - centerZ) * scale()); }
    private static int blend(int color, int tint) { return 0xFF000000 | ((color >> 1) & 0x7F7F7F) + ((tint >> 1) & 0x7F7F7F); }
    private static int cellColor(int color, int overlay) {
        int base = color == Wire.UNKNOWN ? UNKNOWN : 0xFF000000 | color;
        return switch (overlay) {
            case Wire.MINE -> blend(base, MINE);
            case Wire.TAKEN -> blend(base, TAKEN);
            case Wire.VILLAGE -> blend(base, VILLAGE);
            default -> base;
        };
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        followSelf();
        graphics.fill(left, top, right, bottom, CHARCOAL);
        graphics.fill(left, top, right, top + 16, HEADER);
        graphics.renderOutline(left, top, right - left, bottom - top, EDGE);
        graphics.hLine(left, right - 1, top + 16, EDGE);
        graphics.drawString(font, title, left + 8, top + 4, GOLD, false);
        if (map != null) {
            String place = "X " + map.selfX() + "  Z " + map.selfZ();
            graphics.drawString(font, place, right - 8 - font.width(place), top + 4, MUTED, false);
        }
        graphics.fill(mapLeft, mapTop, mapRight, mapBottom, UNKNOWN);
        graphics.enableScissor(mapLeft, mapTop, mapRight, mapBottom);
        List<Component> hover = new ArrayList<>();
        if (map != null) drawMap(graphics, mouseX, mouseY, hover);
        else drawStatus(graphics);
        graphics.disableScissor();
        graphics.renderOutline(mapLeft - 1, mapTop - 1, mapRight - mapLeft + 2, mapBottom - mapTop + 2, 0xFF5A4E3E);
        drawLegend(graphics);
        if (!hover.isEmpty() && mouseX >= mapLeft && mouseX < mapRight && mouseY >= mapTop && mouseY < mapBottom) graphics.setComponentTooltipForNextFrame(font, hover, mouseX, mouseY);
    }
    private void drawStatus(GuiGraphics graphics) {
        String text = switch (Link.status()) {
            case READY, WAITING -> "서버에서 지도를 받는 중입니다...";
            case TIMEOUT -> Link.message();
            case OUTDATED -> Link.message().isEmpty() ? "클라이언트 버전이 서버와 맞지 않습니다." : Link.message();
            case UNSUPPORTED -> "이 서버는 Mai Tycoon 지도를 지원하지 않습니다.";
        };
        graphics.drawCenteredString(font, text, (mapLeft + mapRight) / 2, (mapTop + mapBottom) / 2 - 4, MUTED);
    }
    private void drawMap(GuiGraphics graphics, int mouseX, int mouseY, List<Component> hover) {
        int scale = scale();
        for (int row = 0; row < map.height(); row++) {
            int y = screenY(map.originZ() + row);
            if (y + scale < mapTop || y > mapBottom) continue;
            for (int column = 0; column < map.width(); column++) {
                int x = screenX(map.originX() + column);
                if (x + scale < mapLeft || x > mapRight) continue;
                int index = row * map.width() + column;
                graphics.fill(x, y, x + scale, y + scale, cellColor(map.colors()[index], map.overlays()[index]));
                if (mouseX >= x && mouseX < x + scale && mouseY >= y && mouseY < y + scale) describe(hover, map.originX() + column, map.originZ() + row, map.colors()[index], map.overlays()[index]);
            }
        }
        for (Wire.Mark mark : map.players()) drawPlayer(graphics, mark.name(), mark.x(), mark.z(), OTHER, MARK, mouseX, mouseY, hover);
        drawPlayer(graphics, "나", map.selfX(), map.selfZ(), ME, SELF_MARK, mouseX, mouseY, hover);
    }
    private void describe(List<Component> hover, int cellX, int cellZ, int color, int overlay) {
        int size = (int) cell();
        hover.add(Component.literal("X " + cellX * size + " ~ " + (cellX * size + size - 1) + ", Z " + cellZ * size + " ~ " + (cellZ * size + size - 1)));
        hover.add(Component.literal(switch (overlay) {
            case Wire.MINE -> "내 땅";
            case Wire.TAKEN -> "다른 사람의 땅";
            case Wire.VILLAGE -> "마을 구역";
            default -> color == Wire.UNKNOWN ? "아직 아무도 가지 않은 곳" : "주인 없는 땅";
        }));
    }
    /**
     * A player is a bright square with a dark rim and a ring that pulses, over a name tag with its own dark box, so
     * it reads on any terrain. Someone off the visible part stays on its edge, so the direction is never lost.
     */
    private void drawPlayer(GuiGraphics graphics, String name, int blockX, int blockZ, int color, int size, int mouseX, int mouseY, List<Component> hover) {
        int x = screenX(blockX / cell()), y = screenY(blockZ / cell());
        int edge = size + 3;
        boolean outside = x < mapLeft + edge || x > mapRight - edge || y < mapTop + edge || y > mapBottom - edge;
        x = Math.clamp(x, mapLeft + edge, mapRight - edge);
        y = Math.clamp(y, mapTop + edge, mapBottom - edge);
        int ring = size + 2 + (ticks / 10) % 3;
        graphics.renderOutline(x - ring, y - ring, ring * 2 + 1, ring * 2 + 1, (color & 0xFFFFFF) | 0x90000000);
        graphics.fill(x - size - 1, y - size - 1, x + size + 2, y + size + 2, 0xFF16120F);
        graphics.fill(x - size, y - size, x + size + 1, y + size + 1, color);
        String label = outside && map != null ? name + " " + (int) Math.hypot(blockX - map.selfX(), blockZ - map.selfZ()) + "m" : name;
        int width = font.width(label), labelX = Math.clamp(x - width / 2, mapLeft + 2, mapRight - 3 - width), labelY = y - size - 14 < mapTop + 2 ? y + size + 5 : y - size - 14;
        graphics.fill(labelX - 2, labelY - 2, labelX + width + 2, labelY + font.lineHeight + 1, 0xC016120F);
        graphics.drawString(font, label, labelX, labelY, color, false);
        if (Math.abs(mouseX - x) <= size + 1 && Math.abs(mouseY - y) <= size + 1) {
            hover.clear();
            hover.add(Component.literal(name));
            hover.add(Component.literal("X " + blockX + ", Z " + blockZ));
        }
    }
    private void drawLegend(GuiGraphics graphics) {
        int x = mapLeft, y = mapBottom + 5;
        x = swatch(graphics, x, y, ME, "나");
        x = swatch(graphics, x, y, OTHER, "다른 유저");
        x = swatch(graphics, x, y, 0xFF000000 | MINE, "내 땅");
        x = swatch(graphics, x, y, 0xFF000000 | TAKEN, "남의 땅");
        swatch(graphics, x, y, 0xFF000000 | VILLAGE, "마을");
        String hint = "드래그 이동 · 휠 확대 · R 내 위치 · " + MaiTycoonClient.keyName() + " 닫기";
        graphics.drawString(font, hint, mapLeft, y + 11, MUTED, false);
        String scale = "1칸 = " + (int) cell() + "블록 · x" + scale();
        graphics.drawString(font, scale, mapRight - font.width(scale), y, MUTED, false);
    }
    private int swatch(GuiGraphics graphics, int x, int y, int color, String name) {
        graphics.fill(x, y + 1, x + 6, y + 7, color);
        graphics.drawString(font, name, x + 9, y, GOLD, false);
        return x + 9 + font.width(name) + 10;
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int next = Math.clamp(zoom + (vertical > 0 ? 1 : vertical < 0 ? -1 : 0), 0, ZOOMS.length - 1);
        if (next == zoom) return true;
        // Keep the chunk under the cursor where it is, the way most maps zoom.
        double underX = centerX + (mouseX - (mapLeft + mapRight) / 2.0) / scale(), underZ = centerZ + (mouseY - (mapTop + mapBottom) / 2.0) / scale();
        zoom = next;
        if (!follow) {
            centerX = underX - (mouseX - (mapLeft + mapRight) / 2.0) / scale();
            centerZ = underZ - (mouseY - (mapTop + mapBottom) / 2.0) / scale();
        }
        return true;
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseDragged(event, dragX, dragY);
        follow = false;
        centerX -= dragX / scale();
        centerZ -= dragY / scale();
        return true;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (MaiTycoonClient.fullMap.matches(event)) { onClose(); return true; }
        if (event.key() == GLFW.GLFW_KEY_R) { follow = true; return true; }
        return super.keyPressed(event);
    }
}
