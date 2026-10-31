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
    static final String DIGITS = "0123456789,-▲▼KMB/:YD";
    static final int DIGIT_W = 3, DIGIT_H = 6;
    static final Color WOOD = new Color(0x4A3020), WOOD_LIGHT = new Color(0x6B4A30), PAPER = new Color(0xF0E4C8),
        PAPER_DARK = new Color(0xD8C49A), WELL = new Color(0xFFF8E8), LINE = new Color(0xB89C6C), INK = new Color(0x3A2818), CREAM = new Color(0xFFF2D0);
    static final Map<String, byte[]> FILES = new TreeMap<>();

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        backgroundTiles("sell", background("농산물 상점", List.of("전체", "곡물", "과일", "특수", "특산"), "모두 판매", new Color(0x3F8F4F)));
        backgroundTiles("buy", background("씨앗 상점", List.of("전체", "씨앗", "모종", "도구", "특산"), "상품 클릭 · 구매", new Color(0x3F6C42)));
        png("assets/maitycoon/textures/gui/tab.png", tabHighlight());
        npcSkins();
        customCrops();
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
            box(g,new Color(0x242322),x+40,y+8,31,9,0);
            outline(g,new Color(0x887D70),x+40,y+8,31,9,0);
            centered(g,title.contains("농산물")?"판매":"구매",font(5,true),new Color(0xF07C72),x+47,y+12.5);
        }
        for(int i=0;i<tabs.size();i++)centered(g,tabs.get(i),font(5,true),CREAM,16+i*18,98);
        centered(g,title.contains("농산물")?"모두 판매":"상품 클릭 · 구매",font(5,true),CREAM,133,98);
        centered(g,"수량",font(5,false),CREAM,30,113);
        centered(g,"◀",font(7,true),CREAM,70,113);
        centered(g,"▶",font(7,true),CREAM,106,113);
        centered(g,"●",font(7,true),new Color(0xFFD35E),10,123);
        centered(g,"시세 변경",font(5,false),CREAM,150,113);
        box(g,new Color(0x181818),3,130,170,89,0);
        for(int row=0;row<4;row++)for(int col=0;col<9;col++) {
            int y=row==3?198:140+row*18;
            box(g,new Color(0x393632),7+col*18,y-1,18,18,0);
            box(g,new Color(0x252423),8+col*18,y,16,16,0);
        }
        g.dispose();return image;
    }
    /** Item, seed and three growth sprites per custom crop, plus the item definitions that select them. */
    record CropArt(String id, int fruit, int shade) {}
    static final List<CropArt> CROPS = List.of(new CropArt("cabbage", 0x7DBB4A, 0x4E8A2E), new CropArt("tomato", 0xD8392B, 0x9E2118), new CropArt("corn", 0xF0C93A, 0xC79A1E),
        new CropArt("strawberry", 0xE23B52, 0xA8243A), new CropArt("grape", 0x6B3FA0, 0x472A70), new CropArt("pineapple", 0xE3A62F, 0xA8741B));
    static void customCrops() throws IOException {
        var items = new StringJoiner(",");
        var stages = new StringJoiner(",");
        for (CropArt crop : CROPS) {
            png("assets/maitycoon/textures/item/" + crop.id() + ".png", cropIcon(crop));
            png("assets/maitycoon/textures/item/" + crop.id() + "_seeds.png", seedIcon(crop));
            items.add(selectCase(crop.id(), "maitycoon:item/" + crop.id()));
            items.add(selectCase(crop.id() + "_seeds", "maitycoon:item/" + crop.id() + "_seeds"));
            text("assets/maitycoon/models/item/" + crop.id() + ".json", flatModel(crop.id()));
            text("assets/maitycoon/models/item/" + crop.id() + "_seeds.json", flatModel(crop.id() + "_seeds"));
            for (int stage = 0; stage < 3; stage++) {
                png("assets/maitycoon/textures/item/" + crop.id() + "_" + stage + ".png", plantStage(crop, stage));
                stages.add(selectCase(crop.id() + "_" + stage, "maitycoon:item/" + crop.id() + "_" + stage));
                text("assets/maitycoon/models/item/" + crop.id() + "_" + stage + ".json", "{\"parent\":\"minecraft:block/cross\",\"textures\":{\"cross\":\"maitycoon:item/" + crop.id() + "_" + stage + "\"}}");
            }
        }
        text("assets/minecraft/items/paper.json", selector(items.toString(), "minecraft:item/paper"));
        text("assets/minecraft/items/short_grass.json", selector(stages.toString(), "minecraft:item/short_grass"));
    }
    static String flatModel(String name) { return "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"maitycoon:item/" + name + "\"}}"; }
    static String selectCase(String when, String model) { return "{\"when\":\"" + when + "\",\"model\":{\"type\":\"minecraft:model\",\"model\":\"" + model + "\"}}"; }
    static String selector(String cases, String fallback) {
        return "{\"model\":{\"type\":\"minecraft:select\",\"property\":\"minecraft:custom_model_data\",\"cases\":[" + cases + "],\"fallback\":{\"type\":\"minecraft:model\",\"model\":\"" + fallback + "\"}}}";
    }
    static BufferedImage sprite() { return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB); }
    static void dot(Graphics2D g, int color, int x, int y, int w, int h) { g.setColor(new Color(color)); g.fillOval(x, y, w, h); }
    static void rect(Graphics2D g, int color, int x, int y, int w, int h) { g.setColor(new Color(color)); g.fillRect(x, y, w, h); }
    static BufferedImage cropIcon(CropArt crop) {
        var image = sprite();
        Graphics2D g = image.createGraphics();
        int leaf = 0x3E8E3A, leafDark = 0x2C6B2A;
        switch (crop.id()) {
            case "cabbage" -> { dot(g, crop.shade(), 1, 2, 14, 13); dot(g, crop.fruit(), 3, 3, 10, 11); dot(g, 0xB9DE86, 5, 5, 6, 7); rect(g, leafDark, 7, 4, 1, 10); }
            case "tomato" -> { dot(g, crop.shade(), 2, 3, 12, 12); dot(g, crop.fruit(), 3, 3, 10, 10); rect(g, leaf, 5, 2, 6, 2); rect(g, leaf, 7, 1, 2, 3); rect(g, 0xF08A7E, 5, 6, 2, 2); }
            case "corn" -> { dot(g, crop.shade(), 4, 1, 8, 13); dot(g, crop.fruit(), 5, 1, 6, 12); for (int y = 3; y < 12; y += 2) rect(g, crop.shade(), 5, y, 6, 1); rect(g, leaf, 2, 8, 3, 7); rect(g, leaf, 11, 8, 3, 7); rect(g, leafDark, 5, 12, 6, 3); }
            case "strawberry" -> { g.setColor(new Color(crop.shade())); g.fillPolygon(new int[]{1, 15, 8}, new int[]{4, 4, 15}, 3); g.setColor(new Color(crop.fruit())); g.fillPolygon(new int[]{3, 13, 8}, new int[]{4, 4, 13}, 3); rect(g, leaf, 3, 2, 10, 2); rect(g, leaf, 7, 1, 2, 2); for (int[] s : new int[][]{{5, 6}, {9, 6}, {7, 8}, {6, 10}, {9, 9}}) rect(g, 0xF7E27A, s[0], s[1], 1, 1); }
            case "grape" -> { for (int[] b : new int[][]{{2, 3}, {6, 3}, {10, 3}, {4, 6}, {8, 6}, {6, 9}, {3, 8}, {9, 9}, {6, 12}}) { dot(g, crop.shade(), b[0], b[1], 5, 5); dot(g, crop.fruit(), b[0], b[1], 4, 4); } rect(g, leaf, 7, 0, 2, 3); }
            default -> { dot(g, crop.shade(), 3, 5, 10, 11); dot(g, crop.fruit(), 4, 5, 8, 10); for (int y = 7; y < 14; y += 2) rect(g, crop.shade(), 4, y, 8, 1); for (int x : new int[]{4, 7, 10}) rect(g, leaf, x, 1, 2, 5); rect(g, leafDark, 6, 3, 4, 3); }
        }
        g.dispose();
        return image;
    }
    static BufferedImage seedIcon(CropArt crop) {
        var image = sprite();
        Graphics2D g = image.createGraphics();
        for (int[] s : new int[][]{{3, 4}, {9, 3}, {6, 8}, {11, 9}, {3, 11}}) { dot(g, crop.shade(), s[0], s[1], 3, 3); rect(g, crop.fruit(), s[0] + 1, s[1], 1, 1); }
        g.dispose();
        return image;
    }
    static BufferedImage plantStage(CropArt crop, int stage) {
        var image = sprite();
        Graphics2D g = image.createGraphics();
        int leaf = 0x4C9A3F, dark = 0x2F6E2C;
        if (stage == 0) { rect(g, dark, 7, 11, 2, 5); rect(g, leaf, 5, 10, 3, 2); rect(g, leaf, 8, 9, 3, 2); }
        else {
            rect(g, dark, 7, 5, 2, 11);
            for (int y = 6; y < 15; y += 3) { rect(g, leaf, 3, y, 5, 2); rect(g, leaf, 8, y - 1, 5, 2); }
            if (stage == 1) dot(g, leaf, 5, 2, 6, 5);
            else {
                dot(g, leaf, 3, 1, 10, 8);
                for (int[] f : new int[][]{{3, 3}, {9, 2}, {6, 6}, {10, 8}, {2, 9}}) dot(g, crop.fruit(), f[0], f[1], 4, 4);
            }
        }
        g.dispose();
        return image;
    }
    /** Repaints four rarely used vanilla default skins; without the pack the NPCs simply keep those defaults. */
    static void npcSkins() throws IOException {
        png("assets/minecraft/textures/entity/player/wide/kai.png", skin(0xC98F63, 0x3B2A1C, 0x4F7A3A, 0xE3D4A8, 0x4A3A2A, 0xD9B44A));
        png("assets/minecraft/textures/entity/player/wide/makena.png", skin(0xE0AC84, 0x6B3A1E, 0xA8562E, 0x3F6C42, 0x3D2E22, 0x7A9A4A));
        png("assets/minecraft/textures/entity/player/wide/efe.png", skin(0xD8A27A, 0x22242C, 0x2F3F6B, 0xE9E2CF, 0x2A2A33, 0));
        png("assets/minecraft/textures/entity/player/wide/zuri.png", skin(0xB57C52, 0x8A8378, 0x6B4A30, 0xE9D49C, 0x3A2A1E, 0x5A4632));
    }
    static BufferedImage skin(int face, int hair, int shirt, int apron, int pants, int hat) {
        var image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        rect(g, face, 0, 0, 32, 16);
        rect(g, hair, 8, 0, 8, 8);
        rect(g, hair, 0, 8, 32, 3);
        rect(g, hair, 24, 8, 8, 8);
        rect(g, 0xFFFFFF, 9, 12, 2, 1); rect(g, 0xFFFFFF, 13, 12, 2, 1);
        rect(g, 0x2A2018, 10, 12, 1, 1); rect(g, 0x2A2018, 13, 12, 1, 1);
        rect(g, 0xA96B50, 11, 14, 2, 1);
        for (int[] a : new int[][]{{16, 16, 24, 16}, {40, 16, 16, 16}, {32, 48, 16, 16}}) rect(g, shirt, a[0], a[1], a[2], a[3]);
        rect(g, face, 40, 28, 16, 4); rect(g, face, 32, 60, 16, 4);
        rect(g, apron, 20, 20, 8, 12);
        rect(g, pants, 0, 16, 16, 16); rect(g, pants, 16, 48, 16, 16);
        rect(g, 0x2A2018, 0, 29, 16, 3); rect(g, 0x2A2018, 16, 61, 16, 3);
        if (hat != 0) {
            // The outer head layer stands off the head, which reads as a hat brim.
            rect(g, hat, 40, 0, 8, 8);
            rect(g, hat, 32, 8, 32, 2);
        }
        g.dispose();
        return image;
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
