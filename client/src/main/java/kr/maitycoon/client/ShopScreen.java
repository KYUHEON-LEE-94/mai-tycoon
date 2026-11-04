package kr.maitycoon.client;

import java.util.ArrayList;
import java.util.List;
import kr.maitycoon.client.protocol.Wire;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * The shops as the mod shows them: a product list with tabs on the left, the chosen product with its numbers and the
 * amount and buttons on the right. Every number comes from the server's latest snapshot; a click only sends a request
 * and the server's answer and fresh snapshot decide what the screen says next.
 */
public final class ShopScreen extends Screen {
    private static final int CHARCOAL = 0xFF211E1C, HEADER = 0xFF322921, EDGE = 0xFFE9D49C, GOLD = 0xFFF2DDA4, BODY = 0xFFEDE3CC, MUTED = 0xFFB8AE9C, CARD = 0xFF2B2724, WELL = 0xFF1B1816;
    private static final int BLUE = 0xFF7FD3E3, BLUE_INK = 0xFF142530, GREEN = 0xFF2EE66B, RED = 0xFFFF6B6B, UP = 0xFFFF7A7A, DOWN = 0xFF7FA6FF, SELECT = 0x40F2DDA4, HOVER = 0x22FFFFFF;
    private static final int ROW = 24, DETAIL_WIDTH = 196, TAB_HEIGHT = 14, CONFIRM_TICKS = 100, MESSAGE_TICKS = 100;
    private static final int[] SELL_PRESETS = {1, 8, 16, 32, 64};
    private record Box(int x, int y, int w, int h) {
        boolean has(double px, double py) { return px >= x && px < x + w && py >= y && py < y + h; }
    }
    private final List<Runnable> clicks = new ArrayList<>();
    private final List<Box> boxes = new ArrayList<>();
    private Wire.ShopData data;
    private String tab, selectedId;
    private int scroll, amount = 1, pending = -1, ticks, confirmLeft, messageLeft, messageColor;
    private String message = "";
    private boolean typing;
    private int left, top, right, bottom, listLeft, listTop, listRight, listBottom, detailLeft;
    public ShopScreen(Wire.ShopData data) {
        super(Component.literal(data.title()));
        this.data = data;
        this.tab = data.tabs().isEmpty() ? "" : data.tabs().get(0);
        if (!data.items().isEmpty()) selectedId = data.items().get(0).id();
    }
    int kind() { return data.kind(); }
    void update(Wire.ShopData fresh) {
        data = fresh;
        if (!fresh.tabs().contains(tab)) tab = fresh.tabs().isEmpty() ? "" : fresh.tabs().get(0);
        if (selected() == null && !visible().isEmpty()) selectedId = visible().get(0).id();
    }
    void result(Wire.ShopResult result) {
        if (result.requestId() != pending) return;
        pending = -1;
        confirmLeft = 0;
        say(result.status() == Wire.RESULT_OK ? (result.message().isEmpty() ? "완료했습니다." : result.message()) : result.message(),
            result.status() == Wire.RESULT_OK ? GREEN : result.status() == Wire.RESULT_CHANGED ? GOLD : RED);
    }
    private void say(String text, int color) { message = text; messageColor = color; messageLeft = MESSAGE_TICKS; }
    @Override protected void init() {
        left = 6;
        top = 6;
        right = width - 6;
        bottom = height - 6;
        listLeft = left + 6;
        listTop = top + 20 + TAB_HEIGHT + 6;
        detailLeft = right - 6 - DETAIL_WIDTH;
        listRight = detailLeft - 6;
        listBottom = bottom - (data.kind() == Wire.SHOP_SELL ? 44 : 24);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void tick() {
        ticks++;
        if (messageLeft > 0) messageLeft--;
        if (confirmLeft > 0) confirmLeft--;
        if (ticks % 100 == 0 && pending < 0 && data.kind() == Wire.SHOP_SELL) Link.requestShop(data.kind(), Wire.ACT_REFRESH, "", 0, data.revision());
    }
    @Override public void onClose() {
        Link.requestShop(data.kind(), Wire.ACT_CLOSE, "", 0, data.revision());
        super.onClose();
    }
    private List<Wire.ShopItem> visible() {
        List<Wire.ShopItem> rows = new ArrayList<>();
        for (Wire.ShopItem item : data.items()) if (tab.isEmpty() || data.tabs().indexOf(tab) == 0 || item.category().equals(tab)) rows.add(item);
        return rows;
    }
    private Wire.ShopItem selected() {
        for (Wire.ShopItem item : data.items()) if (item.id().equals(selectedId)) return item;
        return null;
    }
    private boolean selling() { return data.kind() == Wire.SHOP_SELL; }
    private int maxAmount(Wire.ShopItem item) { return selling() ? Math.max(1, item.owned()) : item.maxStack(); }
    private void button(GuiGraphics graphics, int x, int y, int w, int h, int fill, int ink, String text, boolean enabled, Runnable action) {
        graphics.fill(x, y, x + w, y + h, enabled ? fill : CARD);
        graphics.renderOutline(x, y, w, h, enabled ? 0xFF000000 | (fill & 0xFFFFFF) : 0xFF3A332E);
        graphics.drawCenteredString(font, text, x + w / 2, y + (h - 8) / 2, enabled ? ink : MUTED);
        if (enabled) { boxes.add(new Box(x, y, w, h)); clicks.add(action); }
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        boxes.clear();
        clicks.clear();
        graphics.fill(left, top, right, bottom, CHARCOAL);
        graphics.fill(left, top, right, top + 16, HEADER);
        graphics.renderOutline(left, top, right - left, bottom - top, EDGE);
        graphics.hLine(left, right - 1, top + 16, EDGE);
        graphics.drawString(font, data.title(), left + 8, top + 4, GOLD, false);
        String purse = "보유 " + Icons.money(data.coins()) + " 골드";
        graphics.drawString(font, purse, right - 8 - font.width(purse), top + 4, GOLD, false);
        if (data.secondsToNext() >= 0) {
            String timer = String.format("시세 변경까지 %d:%02d", data.secondsToNext() / 60, data.secondsToNext() % 60);
            graphics.drawString(font, timer, right - 8 - font.width(purse) - 16 - font.width(timer), top + 4, MUTED, false);
        }
        drawTabs(graphics, mouseX, mouseY);
        drawList(graphics, mouseX, mouseY);
        drawDetail(graphics);
        drawFooter(graphics);
        Wire.ShopItem hovered = rowAt(mouseX, mouseY);
        if (hovered != null) graphics.setTooltipForNextFrame(font, Icons.stack(hovered.material(), hovered.model(), hovered.name()), mouseX, mouseY);
    }
    private void drawTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = listLeft, y = top + 20;
        for (String name : data.tabs()) {
            int w = font.width(name) + 14;
            boolean on = name.equals(tab);
            graphics.fill(x, y, x + w, y + TAB_HEIGHT, on ? EDGE : CARD);
            graphics.drawCenteredString(font, name, x + w / 2, y + 3, on ? BLUE_INK : MUTED);
            String picked = name;
            boxes.add(new Box(x, y, w, TAB_HEIGHT));
            clicks.add(() -> { tab = picked; scroll = 0; });
            x += w + 4;
        }
    }
    private Wire.ShopItem rowAt(double mouseX, double mouseY) {
        if (mouseX < listLeft || mouseX >= listRight || mouseY < listTop || mouseY >= listBottom) return null;
        int index = (int) (mouseY - listTop) / ROW + scroll;
        List<Wire.ShopItem> rows = visible();
        return index >= 0 && index < rows.size() ? rows.get(index) : null;
    }
    private void drawList(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(listLeft, listTop, listRight, listBottom, WELL);
        graphics.renderOutline(listLeft - 1, listTop - 1, listRight - listLeft + 2, listBottom - listTop + 2, 0xFF3A332E);
        List<Wire.ShopItem> rows = visible();
        int perPage = Math.max(1, (listBottom - listTop) / ROW);
        scroll = Math.clamp(scroll, 0, Math.max(0, rows.size() - perPage));
        graphics.enableScissor(listLeft, listTop, listRight, listBottom);
        for (int i = scroll; i < rows.size() && i < scroll + perPage + 1; i++) {
            Wire.ShopItem item = rows.get(i);
            int y = listTop + (i - scroll) * ROW;
            boolean on = item.id().equals(selectedId), hover = mouseX >= listLeft && mouseX < listRight && mouseY >= y && mouseY < y + ROW && mouseY < listBottom;
            if (on) graphics.fill(listLeft, y, listRight, y + ROW, SELECT);
            else if (hover) graphics.fill(listLeft, y, listRight, y + ROW, HOVER);
            ItemStack stack = Icons.stack(item.material(), item.model(), item.name());
            graphics.renderItem(stack, listLeft + 5, y + 4);
            graphics.drawString(font, stack.getHoverName().getString(), listLeft + 27, y + 3, on ? GOLD : BODY, false);
            graphics.drawString(font, "보유 " + Icons.money(item.owned()) + "개", listLeft + 27, y + 13, MUTED, false);
            String price = Icons.money(item.price()) + " 골드";
            int priceX = listRight - 8 - font.width(price);
            graphics.drawString(font, price, priceX, y + 3, GOLD, false);
            if (selling()) {
                String trend = item.change() > 0 ? "▲" + Icons.money(item.change()) : item.change() < 0 ? "▼" + Icons.money(-item.change()) : "—";
                graphics.drawString(font, trend, listRight - 8 - font.width(trend), y + 13, item.change() > 0 ? UP : item.change() < 0 ? DOWN : MUTED, false);
            }
            graphics.hLine(listLeft + 4, listRight - 5, y + ROW - 1, 0xFF2B2724);
        }
        graphics.disableScissor();
        if (rows.size() > perPage) {
            int trackTop = listTop + 2, trackHeight = listBottom - listTop - 4, thumb = Math.max(10, trackHeight * perPage / rows.size());
            int thumbY = trackTop + (trackHeight - thumb) * scroll / Math.max(1, rows.size() - perPage);
            graphics.fill(listRight - 4, trackTop, listRight - 2, trackTop + trackHeight, CARD);
            graphics.fill(listRight - 4, thumbY, listRight - 2, thumbY + thumb, MUTED);
        }
        if (rows.isEmpty()) graphics.drawCenteredString(font, "표시할 상품이 없습니다.", (listLeft + listRight) / 2, (listTop + listBottom) / 2 - 4, MUTED);
    }
    private void drawDetail(GuiGraphics graphics) {
        int x = detailLeft, y = listTop - TAB_HEIGHT - 6, w = DETAIL_WIDTH, h = listBottom - y;
        graphics.fill(x, y, x + w, y + h, CARD);
        graphics.renderOutline(x, y, w, h, 0xFF3A332E);
        Wire.ShopItem item = selected();
        if (item == null) { graphics.drawCenteredString(font, "왼쪽에서 상품을 고르세요.", x + w / 2, y + h / 2 - 4, MUTED); return; }
        ItemStack stack = Icons.stack(item.material(), item.model(), item.name());
        graphics.pose().pushMatrix();
        graphics.pose().translate(x + 8, y + 8);
        graphics.pose().scale(2f, 2f);
        graphics.renderItem(stack, 0, 0);
        graphics.pose().popMatrix();
        graphics.drawString(font, stack.getHoverName().getString(), x + 46, y + 10, GOLD, false);
        graphics.drawString(font, item.category() + " · 보유 " + Icons.money(item.owned()) + "개", x + 46, y + 22, MUTED, false);
        int line = y + 46;
        graphics.hLine(x + 8, x + w - 9, line - 4, 0xFF3A332E);
        String priceLabel = selling() ? "현재 판매가" : "가격";
        graphics.drawString(font, priceLabel, x + 8, line, MUTED, false);
        String price = Icons.money(item.price()) + " 골드";
        graphics.drawString(font, price, x + w - 8 - font.width(price), line, GOLD, false);
        line += 12;
        if (selling()) {
            graphics.drawString(font, "범위", x + 8, line, MUTED, false);
            String range = Icons.money(item.floor()) + " ~ " + Icons.money(item.ceiling());
            graphics.drawString(font, range, x + w - 8 - font.width(range), line, BODY, false);
            line += 12;
        }
        for (String note : item.notes()) {
            if (note.isEmpty()) { line += 5; continue; }
            graphics.drawWordWrap(font, Component.literal(note), x + 8, line, w - 16, MUTED);
            line += font.wordWrapHeight(Component.literal(note), w - 16) + 2;
        }
        int controls = Math.max(line + 6, y + h - 92);
        drawAmount(graphics, item, x + 8, controls, w - 16);
        drawActions(graphics, item, x + 8, controls + 46, w - 16);
    }
    private void amountIs(int value, Wire.ShopItem item) { amount = Math.clamp(value, 1, maxAmount(item)); typing = false; }
    private void drawAmount(GuiGraphics graphics, Wire.ShopItem item, int x, int y, int w) {
        if (data.kind() != Wire.SHOP_LAMP) amount = Math.clamp(amount, 1, Math.max(1, maxAmount(item)));
        graphics.drawString(font, "수량", x, y, MUTED, false);
        int boxX = x + 30, boxW = w - 30 - 44;
        button(graphics, x + w - 42, y - 3, 20, 14, CARD, BODY, "-", amount > 1, () -> amountIs(amount - 1, item));
        button(graphics, x + w - 20, y - 3, 20, 14, CARD, BODY, "+", amount < maxAmount(item), () -> amountIs(amount + 1, item));
        graphics.fill(boxX, y - 3, boxX + boxW, y + 11, typing ? WELL : CARD);
        graphics.renderOutline(boxX, y - 3, boxW, 14, typing ? EDGE : 0xFF3A332E);
        String shown = amount + "개" + (typing && ticks / 10 % 2 == 0 ? "_" : "");
        graphics.drawCenteredString(font, shown, boxX + boxW / 2, y, BODY);
        boxes.add(new Box(boxX, y - 3, boxW, 14));
        clicks.add(() -> { typing = !typing; if (typing) amount = 0; });
        int px = x, py = y + 18;
        int[] presets = selling() ? SELL_PRESETS : new int[]{1, 8, 16, 32, item.maxStack()};
        for (int preset : presets) {
            int value = Math.min(preset, maxAmount(item));
            String label = preset == item.maxStack() && !selling() ? "최대" : Integer.toString(preset);
            button(graphics, px, py, 30, 14, amount == value ? EDGE : CARD, amount == value ? BLUE_INK : BODY, label, value >= 1, () -> amountIs(value, item));
            px += 34;
        }
    }
    private void drawActions(GuiGraphics graphics, Wire.ShopItem item, int x, int y, int w) {
        boolean busy = pending >= 0;
        if (selling()) {
            int count = Math.min(amount, item.owned());
            long total = item.price() * count;
            button(graphics, x, y, w, 18, GOLD, BLUE_INK, count + "개 판매 · " + Icons.money(total) + " 골드", !busy && item.owned() > 0 && count > 0, () -> send(Wire.ACT_SELL, item.id(), count));
            button(graphics, x, y + 22, w, 16, CARD, BODY, "이 작물 전부 판매 (" + Icons.money(item.owned()) + "개)", !busy && item.owned() > 0, () -> send(Wire.ACT_SELL, item.id(), item.owned()));
        } else {
            long total = item.price() * amount;
            boolean affordable = total <= data.coins();
            button(graphics, x, y, w, 18, BLUE, BLUE_INK, amount + "개 구매 · " + Icons.money(total) + " 골드", !busy && amount >= 1 && affordable, () -> send(Wire.ACT_BUY, item.id(), amount));
            if (!affordable) graphics.drawCenteredString(font, "골드가 " + Icons.money(total - data.coins()) + " 부족합니다", x + w / 2, y + 24, RED);
            else graphics.drawCenteredString(font, "구매 후 잔액 " + Icons.money(data.coins() - total) + " 골드", x + w / 2, y + 24, MUTED);
        }
    }
    private long totalOfEverything() {
        long total = 0;
        for (Wire.ShopItem item : data.items()) total += item.price() * item.owned();
        return total;
    }
    private void drawFooter(GuiGraphics graphics) {
        int y = listBottom + 6;
        if (selling()) {
            long total = totalOfEverything();
            boolean any = total > 0 && pending < 0;
            String label = confirmLeft > 0 ? "정말 모두 팔까요? 다시 누르면 " + Icons.money(total) + " 골드에 판매 (" + (confirmLeft / 20 + 1) + ")" : "모두 판매 · 합계 " + Icons.money(total) + " 골드";
            button(graphics, listLeft, y, listRight - listLeft, 18, confirmLeft > 0 ? RED : GOLD, BLUE_INK, label, any, () -> {
                if (confirmLeft > 0) send(Wire.ACT_SELL_ALL, "", 0);
                else confirmLeft = CONFIRM_TICKS;
            });
            y += 22;
        }
        String hint = "휠 스크롤 · 숫자 입력은 수량 칸 클릭 · Esc 닫기";
        graphics.drawString(font, hint, listLeft, y + 2, MUTED, false);
        if (messageLeft > 0) {
            int alpha = Math.min(255, messageLeft * 8) << 24;
            graphics.drawString(font, message, listLeft + font.width(hint) + 16, y + 2, (messageColor & 0xFFFFFF) | alpha, false);
        }
        if (pending >= 0) graphics.drawString(font, "서버 확인 중...", right - 8 - font.width("서버 확인 중..."), y + 2, MUTED, false);
    }
    private void send(int action, String item, int amount) {
        int id = Link.requestShop(data.kind(), action, item, amount, data.revision());
        if (id < 0) { say("서버와 연결되어 있지 않습니다.", RED); return; }
        pending = id;
        confirmLeft = 0;
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick);
        for (int i = 0; i < boxes.size(); i++) if (boxes.get(i).has(event.x(), event.y())) { clicks.get(i).run(); return true; }
        Wire.ShopItem row = rowAt(event.x(), event.y());
        if (row != null) { selectedId = row.id(); typing = false; if (!selling()) amount = Math.min(amount, row.maxStack()); return true; }
        typing = false;
        return true;
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseX >= listLeft && mouseX < listRight) scroll -= (int) Math.signum(vertical);
        else if (selected() != null) amountIs(amount + (int) Math.signum(vertical), selected());
        return true;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        Wire.ShopItem item = selected();
        if (typing && item != null) {
            int key = event.key();
            int digit = key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9 ? key - GLFW.GLFW_KEY_0 : key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_9 ? key - GLFW.GLFW_KEY_KP_0 : -1;
            if (digit >= 0) { amount = Math.min(maxAmount(item), amount * 10 + digit); return true; }
            if (key == GLFW.GLFW_KEY_BACKSPACE) { amount /= 10; return true; }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { amountIs(amount, item); return true; }
        }
        return super.keyPressed(event);
    }
}
