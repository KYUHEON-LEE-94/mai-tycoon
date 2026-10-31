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
    static final int SCALE = 4, WIDTH = ShopLayout.GUI_WIDTH, HEIGHT = ShopLayout.BACKGROUND_HEIGHT, SLOT = ShopLayout.SLOT, TOP = ShopLayout.TOP, FOOTER = ShopLayout.FOOTER_ROW;
    static final int PACK_FORMAT = 75;
    static final String DIGITS = "0123456789,-▲▼KMB/:";
    static final int DIGIT_W = 3, DIGIT_H = 6;
    static final Color WOOD = new Color(0x4A3020), WOOD_LIGHT = new Color(0x6B4A30), PAPER = new Color(0xF0E4C8),
        PAPER_DARK = new Color(0xD8C49A), WELL = new Color(0xFFF8E8), LINE = new Color(0xB89C6C), INK = new Color(0x3A2818), CREAM = new Color(0xFFF2D0);
    static final Map<String, byte[]> FILES = new TreeMap<>();

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        backgroundTiles("sell", background("농산물 상점", List.of("전체", "곡물", "과일", "특수", "희귀"), "모두 판매", new Color(0x3F8F4F)));
        backgroundTiles("buy", background("씨앗 상점", List.of("전체", "씨앗", "모종", "도구"), "상품 클릭 · 구매", new Color(0x3F6C42)));
        png("assets/maitycoon/textures/gui/tab.png", tabHighlight());
        png("assets/maitycoon/textures/gui/digits.png", digits());
        text("assets/maitycoon/font/space.json", spaceFont());
        text("assets/maitycoon/font/gui.json", guiFont());
        for (int i = 0; i < ShopLayout.ITEM_NAMES.size(); i++) png("assets/maitycoon/textures/gui/name_" + i + ".png", nameImage(ShopLayout.ITEM_NAMES.get(i)));
        for (int row = 0; row < ShopLayout.ROWS; row++) {
            text("assets/maitycoon/font/row" + row + ".json", digitFont(6, 13 - (TOP + row * SLOT + 11)));
            text("assets/maitycoon/font/owned" + row + ".json", digitFont(6, 13 - (TOP + row * SLOT + 12)));
            var names = new StringJoiner(",");
            for (int i = 0; i < ShopLayout.ITEM_NAMES.size(); i++) names.add(bitmap("name_" + i + ".png", 7, 13 - (TOP + row * SLOT), String.format("\\u%04x", 0xE500+i)));
            text("assets/maitycoon/font/names" + row + ".json", "{\"providers\":[" + names + "]}");
        }
        text("assets/maitycoon/font/controls.json", digitFont(6, 13 - 110));
        text("assets/maitycoon/font/footer.json", digitFont(6, 13 - 120));
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
    static final int TILE_W = ShopLayout.TILE_WIDTH, TILE_H = ShopLayout.TILE_HEIGHT, TILE_ROWS = (HEIGHT + TILE_H - 1) / TILE_H;
    static void backgroundTiles(String kind, BufferedImage bg) throws IOException {
        // Each bitmap glyph must fit in the client font atlas (256px per glyph).
        for (int row = 0; row < TILE_ROWS; row++) for (int col = 0; col < 4; col++) {
            int h = Math.min(TILE_H, HEIGHT - row * TILE_H);
            BufferedImage tile = bg.getSubimage(col * TILE_W * SCALE, row * TILE_H * SCALE, TILE_W * SCALE, h * SCALE);
            // Keep the measured advance exactly TILE_W + 1, including rounded outer corners.
            tile.setRGB(tile.getWidth() - 1, tile.getHeight() - 1, 0x01FFFFFF);
            png("assets/maitycoon/textures/gui/" + kind + "_" + row + "_" + col + ".png", tile);
        }
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
    static BufferedImage nameImage(String name) {
        var image = new BufferedImage(48*SCALE,7*SCALE,BufferedImage.TYPE_INT_ARGB);
        var g=paint(image);g.setColor(new Color(0xF7F2E7));g.setFont(font(6,true));
        if(g.getFontMetrics().stringWidth(name)>47*SCALE)g.setFont(font(5,true));
        g.drawString(name,0,6*SCALE);g.dispose();image.setRGB(48*SCALE-1,7*SCALE-1,0x01FFFFFF);return image;
    }
    static BufferedImage background(String title, List<String> tabs, String action, Color actionColor) {
        var image=new BufferedImage(WIDTH*SCALE,HEIGHT*SCALE,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=paint(image);
        box(g,new Color(0x211E1C),0,0,WIDTH,HEIGHT,0);
        box(g,new Color(0x322921),0,0,WIDTH,14,0);
        g.setColor(new Color(0xE9D49C));g.fillRect(0,14*SCALE,WIDTH*SCALE,SCALE/2);
        g.setFont(font(8,true));g.drawString(title,7*SCALE,10*SCALE);
        for(int row=0;row<ShopLayout.ROWS;row++)for(int col=0;col<2;col++) {
            int x=7+col*90,y=TOP+row*SLOT-1;
            box(g,new Color(0x302B29),x,y,72,17,0);
            outline(g,new Color(0x675B4B),x,y,72,17,0);
            box(g,new Color(0xE9D49C),x,y,72,1,0);
            outline(g,new Color(0x8D8276),x+1,y+1,17,15,0);
            box(g,new Color(0x242322),x+45,y+7,26,10,0);
            outline(g,new Color(0x887D70),x+45,y+7,26,10,0);
            centered(g,title.contains("농산물")?"판매":"구매",font(4,true),new Color(0xE16A62),x+58,y+9);
        }
        for(int i=0;i<tabs.size();i++)centered(g,tabs.get(i),font(5,true),CREAM,16+i*18,98);
        centered(g,title.contains("농산물")?"모두 판매":"상품 클릭 · 구매",font(5,true),CREAM,133,98);
        centered(g,"수량",font(5,false),CREAM,30,113);
        centered(g,"◀",font(7,true),CREAM,70,113);
        centered(g,"▶",font(7,true),CREAM,106,113);
        centered(g,"●",font(7,true),new Color(0xFFD35E),10,123);
        centered(g,"YD",font(5,true),CREAM,52,123);
        centered(g,"시세 변경",font(4,false),CREAM,151,113);
        box(g,new Color(0x181818),3,130,170,89,0);
        centered(g,"보관함",font(5,false),new Color(0xAB9E87),20,134);
        for(int row=0;row<4;row++)for(int col=0;col<9;col++) {
            int y=row==3?198:140+row*18;
            box(g,new Color(0x393632),7+col*18,y-1,18,18,0);
            box(g,new Color(0x252423),8+col*18,y,16,16,0);
        }
        g.dispose();return image;
    }
    static BufferedImage tabHighlight() {
        var image=new BufferedImage(18*SCALE,17*SCALE,BufferedImage.TYPE_INT_ARGB);
        var g=paint(image);box(g,new Color(0xF2DDA4),1,15,16,1,0);g.dispose();
        image.setRGB(image.getWidth()-1,image.getHeight()-1,0x01FFFFFF);return image;
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
        for (int kind = 0; kind < 2; kind++) for (int row = 0; row < TILE_ROWS; row++) for (int col = 0; col < 4; col++) {
            int h = Math.min(TILE_H, HEIGHT - row * TILE_H);
            providers.add(bitmap((kind == 0 ? "sell" : "buy") + "_" + row + "_" + col + ".png", h,
                13 - row * TILE_H, String.format("\\u%04x", (kind == 0 ? 0xE300 : 0xE400) + row * 4 + col)));
        }
        providers.add(bitmap("tab.png", 17, 13 - 90, "\\ue210"));
        return "{\"providers\":[" + providers + "]}";
    }
    static String digitFont(int height, int ascent) {
        var chars = new StringBuilder();
        for (char c : DIGITS.toCharArray()) chars.append(c < 128 ? String.valueOf(c) : String.format("\\u%04x", (int) c));
        return "{\"providers\":[" + bitmap("digits.png", height, ascent, chars.toString()) + "]}";
    }
    /** Debug view of the sell screen at SCALE, on a dark backdrop. */
    static BufferedImage preview() throws IOException {
        BufferedImage bg = background("농산물 상점", List.of("전체", "곡물", "과일", "특수", "희귀"), "모두 판매", new Color(0x3F6C42));
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
