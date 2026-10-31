import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.zip.*;
import javax.imageio.ImageIO;
import kr.maitycoon.ShopLayout;

/**
 * Regenerates src/main/resources/pack.zip, the client resource pack for the shop screens.
 * After a build, run from the repository root: java -cp target/classes tools/GeneratePack.java
 * Needs the Nanum Gothic font (SIL OFL) installed; only the resulting images are shipped.
 * Slot positions come from ShopLayout. Art is drawn at SCALE times the GUI pixel size.
 */
public class GeneratePack {
    static final int SCALE = 4, WIDTH = ShopLayout.GUI_WIDTH, HEIGHT = ShopLayout.GUI_HEIGHT, SLOT = ShopLayout.SLOT, TOP = ShopLayout.TOP, FOOTER = ShopLayout.FOOTER_ROW;
    static final int PACK_FORMAT = 75;
    static final String DIGITS = "0123456789,-▲▼";
    static final int DIGIT_W = 3, DIGIT_H = 6;
    static final Color WOOD = new Color(0x4A3020), WOOD_LIGHT = new Color(0x6B4A30), PAPER = new Color(0xF0E4C8),
        PAPER_DARK = new Color(0xD8C49A), WELL = new Color(0xFFF8E8), LINE = new Color(0xB89C6C), INK = new Color(0x3A2818), CREAM = new Color(0xFFF2D0);
    static final Map<String, byte[]> FILES = new TreeMap<>();

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        png("assets/maitycoon/textures/gui/shop_sell.png", background("농산물 상점", List.of("전체", "곡물", "과일", "특수", "희귀"), "모두 판매", new Color(0x3F8F4F)));
        png("assets/maitycoon/textures/gui/shop_buy.png", background("씨앗 상점", List.of("전체", "씨앗", "모종", "도구"), "좌클릭 구매", new Color(0x8A6A3A)));
        png("assets/maitycoon/textures/gui/tab.png", tabHighlight());
        png("assets/maitycoon/textures/gui/digits.png", digits());
        text("assets/maitycoon/font/space.json", spaceFont());
        text("assets/maitycoon/font/gui.json", guiFont());
for (int row = 0; row < ShopLayout.ROWS; row++) text("assets/maitycoon/font/row" + row + ".json", digitFont(DIGIT_H, 13 - (TOP + row * SLOT + 6)));
        text("assets/maitycoon/font/header.json", digitFont(8, 13 - 5));
        text("assets/maitycoon/font/footer.json", digitFont(8, 13 - (TOP + FOOTER * SLOT + 5)));
        text("pack.mcmeta", "{\"pack\":{\"description\":\"Mai Tycoon\",\"min_format\":" + PACK_FORMAT + ",\"max_format\":" + PACK_FORMAT + "}}");
        Path out = Path.of("src/main/resources/pack.zip");
        try (var zip = new ZipOutputStream(Files.newOutputStream(out))) {
            for (var file : FILES.entrySet()) {
                var entry = new ZipEntry(file.getKey());
                entry.setTime(0);
                zip.putNextEntry(entry);
                zip.write(file.getValue());
                zip.closeEntry();
            }
        }
        if (args.length > 0) ImageIO.write(preview(), "png", Path.of(args[0]).toFile());
        System.out.println(out + " " + Files.size(out) + " bytes");
    }
    static void png(String name, BufferedImage image) throws IOException {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        FILES.put(name, bytes.toByteArray());
    }
    static void text(String name, String content) { FILES.put(name, content.getBytes(StandardCharsets.UTF_8)); }
    static Graphics2D paint(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        return g;
    }
    static Font font(int size, boolean bold) { return new Font("Nanum Gothic", bold ? Font.BOLD : Font.PLAIN, size * SCALE); }
    static void box(Graphics2D g, Color color, int x, int y, int w, int h, int round) {
        g.setColor(color);
        g.fillRoundRect(x * SCALE, y * SCALE, w * SCALE, h * SCALE, round * SCALE, round * SCALE);
    }
    static void outline(Graphics2D g, Color color, int x, int y, int w, int h, int round) {
        g.setColor(color);
        g.setStroke(new BasicStroke(SCALE / 2f));
        g.drawRoundRect(x * SCALE, y * SCALE, w * SCALE, h * SCALE, round * SCALE, round * SCALE);
    }
    static void centered(Graphics2D g, String text, Font font, Color color, double centerX, double centerY) {
        g.setFont(font);
        g.setColor(color);
        var metrics = g.getFontMetrics();
        float x = (float) (centerX * SCALE - metrics.stringWidth(text) / 2.0);
        float y = (float) (centerY * SCALE + (metrics.getAscent() - metrics.getDescent()) / 2.0);
        g.drawString(text, x, y);
    }
    static int slotX(int column) { return ShopLayout.slotX(column); }
    static int slotY(int row) { return ShopLayout.slotY(row); }
    static BufferedImage background(String title, List<String> tabs, String action, Color actionColor) {
        var image = new BufferedImage(WIDTH * SCALE, HEIGHT * SCALE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = paint(image);
        box(g, WOOD, 0, 0, WIDTH, HEIGHT, 6);
        box(g, WOOD_LIGHT, 2, 2, WIDTH - 4, 13, 4);
        centered(g, title, font(9, true), CREAM, 34, 8.5);
        centered(g, "YD", font(8, true), new Color(0xFFD860), 161, 8.5);
        box(g, PAPER_DARK, 3, 16, 21, 91, 3);
        box(g, PAPER, 25, 16, WIDTH - 28, 91, 3);
        for (int row = 0; row < ShopLayout.ROWS; row++) {
            if (row < tabs.size()) {
                box(g, WELL, slotX(0) - 3, slotY(row) - 1, 18, 17, 3);
                centered(g, tabs.get(row), font(7, true), INK, slotX(0) + 6, slotY(row) + 7.5);
            }
            for (int column = 1; column < 9; column += 2) {
                box(g, WELL, slotX(column) - 1, slotY(row) - 1, 18, 18, 3);
                outline(g, LINE, slotX(column) - 1, slotY(row) - 1, 18, 18, 3);
            }
        }
        box(g, WOOD_LIGHT, slotX(0) - 1, slotY(FOOTER) - 1, 18, 18, 3);
        centered(g, "◀", font(9, true), CREAM, slotX(0) + 8, slotY(FOOTER) + 8);
        box(g, WOOD_LIGHT, slotX(8) - 1, slotY(FOOTER) - 1, 18, 18, 3);
        centered(g, "▶", font(9, true), CREAM, slotX(8) + 8, slotY(FOOTER) + 8);
        box(g, WOOD_LIGHT, slotX(1) - 1, slotY(FOOTER) - 1, 3 * SLOT - 1, 18, 3);
        centered(g, "수량", font(8, true), CREAM, slotX(1) + 11, slotY(FOOTER) + 8);
        box(g, actionColor, slotX(4) - 1, slotY(FOOTER) - 1, 4 * SLOT - 1, 18, 3);
        centered(g, action, font(9, true), Color.WHITE, slotX(4) + 34, slotY(FOOTER) + 8);
        g.dispose();
        return image;
    }
    static BufferedImage tabHighlight() {
        var image = new BufferedImage(18 * SCALE, 17 * SCALE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = paint(image);
        outline(g, new Color(0xE06020), 1, 1, 16, 15, 3);
        g.setStroke(new BasicStroke(SCALE));
        g.drawRoundRect(SCALE, SCALE, 16 * SCALE, 15 * SCALE, 3 * SCALE, 3 * SCALE);
        image.setRGB(image.getWidth() - 1, image.getHeight() - 1, 0x01FFFFFF);
        g.dispose();
        return image;
    }
    static BufferedImage digits() {
        int w = DIGIT_W * SCALE, h = DIGIT_H * SCALE;
        var image = new BufferedImage(w * DIGITS.length(), h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = paint(image);
        for (int i = 0; i < DIGITS.length(); i++) {
            String glyph = DIGITS.substring(i, i + 1);
            Font font = glyph.equals("▲") || glyph.equals("▼") ? new Font("Apple SD Gothic Neo", Font.PLAIN, 4 * SCALE) : new Font("Menlo", Font.BOLD, (int) (5.2 * SCALE));
            g.setFont(font);
            g.setColor(Color.WHITE);
            var metrics = g.getFontMetrics();
            g.drawString(glyph, (float) (i * w + (w - metrics.stringWidth(glyph)) / 2.0), (float) (h + metrics.getAscent() - metrics.getDescent()) / 2f);
            // A nearly invisible pixel in the last column pins the advance, so right-aligned numbers stay exact.
            image.setRGB(i * w + w - 1, h - 1, 0x01FFFFFF);
        }
        g.dispose();
        return image;
    }
    static String spaceFont() {
        var advances = new StringJoiner(",");
        for (int bit = 0; bit < 10; bit++) {
            advances.add(String.format("\"\\ue%03x\":%d", bit, -(1 << bit)));
            advances.add(String.format("\"\\ue%03x\":%d", 0x100 + bit, 1 << bit));
        }
        return "{\"providers\":[{\"type\":\"space\",\"advances\":{" + advances + "}}]}";
    }
    static String bitmap(String file, int height, int ascent, String chars) {
        return "{\"type\":\"bitmap\",\"file\":\"maitycoon:gui/" + file + "\",\"height\":" + height + ",\"ascent\":" + ascent + ",\"chars\":[\"" + chars + "\"]}";
    }
    static String guiFont() {
        var providers = new StringJoiner(",");
        providers.add(bitmap("shop_sell.png", HEIGHT, 13, "\\ue200"));
        providers.add(bitmap("shop_buy.png", HEIGHT, 13, "\\ue201"));
        for (int row = 0; row < ShopLayout.ROWS; row++) providers.add(bitmap("tab.png", 17, 13 - (TOP + row * SLOT - 1), String.format("\\ue21%d", row)));
        return "{\"providers\":[" + providers + "]}";
    }
    static String digitFont(int height, int ascent) {
        var chars = new StringBuilder();
        for (char c : DIGITS.toCharArray()) chars.append(c < 128 ? String.valueOf(c) : String.format("\\u%04x", (int) c));
        return "{\"providers\":[" + bitmap("digits.png", height, ascent, chars.toString()) + "]}";
    }
    /** Debug view of the sell screen at SCALE, on a dark backdrop. */
    static BufferedImage preview() throws IOException {
        BufferedImage bg = ImageIO.read(new ByteArrayInputStream(FILES.get("assets/maitycoon/textures/gui/shop_sell.png")));
        var image = new BufferedImage(bg.getWidth() + 80, bg.getHeight() + 80, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0x303030));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.drawImage(bg, 40, 40, null);
        g.drawImage(ImageIO.read(new ByteArrayInputStream(FILES.get("assets/maitycoon/textures/gui/digits.png"))), 40, 4, null);
        g.dispose();
        return image;
    }
}
