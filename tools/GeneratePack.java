import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.zip.*;
import javax.imageio.ImageIO;
import kr.maitycoon.MiniMapTitle;
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
        backgroundTiles("sell", background("농산물 상점", List.of("전체", "곡물", "과일", "특수", "특산"), GOLD));
        backgroundTiles("buy", background("씨앗 상점", List.of("전체", "씨앗", "모종", "비료", "특산"), GREEN));
        backgroundTiles("panel", panel());
        backgroundTiles("lamp", lampScreen());
        backgroundTiles("furniture", background("가구 상점", List.of("전체", "침대", "조명", "수납", "장식"), GOLD));
        backgroundTiles("build", background("건축 자재 상점", List.of("전체", "목재", "석재", "장식", "색상"), GOLD));
        png("assets/maitycoon/textures/gui/tab.png", tabHighlight());
        npcSkins();
        miniMapFonts();
        // The land banner is only text; an empty white bar keeps it from looking like a boss fight.
        png("assets/minecraft/textures/gui/sprites/boss_bar/white_background.png", new BufferedImage(182, 5, BufferedImage.TYPE_INT_ARGB));
        png("assets/minecraft/textures/gui/sprites/boss_bar/white_progress.png", new BufferedImage(182, 5, BufferedImage.TYPE_INT_ARGB));
        customCrops();
        png("assets/maitycoon/textures/gui/digits.png", digits());
        text("assets/maitycoon/font/space.json", spaceFont());
        text("assets/maitycoon/font/gui.json", guiFont());
        for (int i = 0; i < ShopLayout.ITEM_NAMES.size(); i++) png("assets/maitycoon/textures/gui/name_" + i + ".png", nameImage(ShopLayout.ITEM_NAMES.get(i)));
        for (int row = 0; row < ShopLayout.ROWS; row++) {
            text("assets/maitycoon/font/row" + row + ".json", digitFont(6, 13 - (TOP + row * SLOT + 11)));
            text("assets/maitycoon/font/owned" + row + ".json", digitFont(6, 13 - (TOP + row * SLOT + 12)));
            var names = new StringJoiner(",");
            for (int i = 0; i < ShopLayout.ITEM_NAMES.size(); i++) names.add(bitmap("name_" + i + ".png", 7, 13 - (TOP + row * SLOT), String.format("\\u%04x", ShopLayout.NAME_BASE+i)));
            text("assets/maitycoon/font/names" + row + ".json", "{\"providers\":[" + names + "]}");
        }
        text("assets/maitycoon/font/controls.json", digitFont(6, 13 - 110));
        png("assets/maitycoon/textures/gui/gold_label.png", goldLabel());
        text("assets/maitycoon/font/footer.json", digitFont(6, 13 - 120).replace("]}]}", "]}," + bitmap("gold_label.png", 7, 13 - 119, "\\ue220") + "]}"));
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
        if (args.length > 1) cropPreview(Path.of(args[1]));
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
    /** Colours of a shop screen. The seed shop is green so it cannot be mistaken for the crop shop beside it. */
    record Theme(Color base, Color head, Color accent, Color card, Color edge) {}
    static final Theme GOLD = new Theme(new Color(0x211E1C), new Color(0x322921), new Color(0xE9D49C), new Color(0x302B29), new Color(0x675B4B));
    static final Theme GREEN = new Theme(new Color(0x1C211B), new Color(0x26361F), new Color(0xB7D88C), new Color(0x2A332A), new Color(0x566B4A));
    static BufferedImage background(String title, List<String> tabs, Theme theme) {
        var image=new BufferedImage(WIDTH*SCALE,HEIGHT*SCALE,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=paint(image);
        box(g,theme.base(),0,0,WIDTH,HEIGHT,0);
        box(g,theme.head(),0,0,WIDTH,14,0);
        g.setColor(theme.accent());g.fillRect(0,14*SCALE,WIDTH*SCALE,SCALE/2);
        g.setFont(font(8,true));g.drawString(title,7*SCALE,10*SCALE);
        for(int row=0;row<ShopLayout.ROWS;row++)for(int col=0;col<2;col++) {
            int x=7+col*90,y=TOP+row*SLOT-1;
            box(g,theme.card(),x,y,72,17,0);
            outline(g,theme.edge(),x,y,72,17,0);
            box(g,theme.accent(),x,y,72,1,0);
            outline(g,new Color(0x8D8276),x+1,y+1,17,15,0);
            box(g,new Color(0x242322),x+40,y+8,31,9,0);
            outline(g,new Color(0x887D70),x+40,y+8,31,9,0);
            centered(g,title.contains("농산물")?"판매":"구매",font(5,true),new Color(0xF07C72),x+47,y+12.5);
        }
        for(int i=0;i<tabs.size();i++){
            button(g,7+i*18,90,17,15,new Color(0x3A332E));
            centered(g,tabs.get(i),font(5,true),CREAM,15.5+i*18,97.5);
        }
        boolean selling=title.contains("농산물");
        button(g,97,90,72,15,selling?new Color(0x3F6C42):new Color(0x2A2725));
        centered(g,selling?"모두 판매":"상품 클릭 · 구매",font(selling?7:5,true),CREAM,133,97.5);
        button(g,25,108,36,10,new Color(0x3A332E));
        centered(g,"수량",font(5,false),CREAM,33,113);
        button(g,61,108,18,10,new Color(0x3A332E));
        centered(g,"◀",font(6,true),CREAM,70,113);
        button(g,97,108,18,10,new Color(0x3A332E));
        centered(g,"▶",font(6,true),CREAM,106,113);
        centered(g,"●",font(7,true),new Color(0xFFD35E),10,123);
        centered(g,title.contains("농산물") ? "시세 변경" : "고정 가격",font(5,false),CREAM,150,113);
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
        new CropArt("strawberry", 0xE23B52, 0xA8243A), new CropArt("grape", 0x6B3FA0, 0x472A70), new CropArt("pineapple", 0xE3A62F, 0xA8741B),
        new CropArt("onion", 0xE8BF70, 0xA56D38), new CropArt("radish", 0xF0EBD5, 0xB6C4A0),
        new CropArt("eggplant", 0x8755AF, 0x452C68), new CropArt("cucumber", 0x65A548, 0x286135),
        new CropArt("bell_pepper", 0xEEBD34, 0xC66E24), new CropArt("blueberry", 0x6577C7, 0x354679));
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
    // Native 32px pixel art: individual silhouettes, a consistent upper-left light and dark outlines.
    static BufferedImage cropIcon(CropArt crop) {
        var image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        int green=0x508C3D, darkGreen=0x294C32, highlight=0xFFF0BC;
        switch (crop.id()) {
            case "cabbage" -> {
                dot(g,darkGreen,2,8,28,22);dot(g,crop.shade(),4,6,24,24);dot(g,crop.fruit(),7,5,20,23);
                dot(g,0xAFD46F,11,7,12,17);dot(g,0xCFE899,14,9,7,12);
                rect(g,0x638F43,14,10,2,17);rect(g,0x90B956,8,15,6,2);rect(g,0x90B956,17,20,7,2);
            }
            case "tomato" -> {
                dot(g,crop.shade(),3,8,27,22);dot(g,crop.fruit(),4,7,24,21);dot(g,0xF36842,7,9,13,12);
                rect(g,darkGreen,14,2,3,7);rect(g,green,8,6,16,3);rect(g,green,12,4,8,8);
                rect(g,0xFFB58A,8,11,4,3);rect(g,0xEF7950,7,15,2,3);
            }
            case "corn" -> {
                dot(g,crop.shade(),9,2,14,27);dot(g,crop.fruit(),10,2,11,25);
                for(int y=5;y<25;y+=4)for(int x=11;x<22;x+=4){rect(g,0xFFE67B,x,y,2,2);rect(g,crop.shade(),x,y+2,3,1);}
                g.setColor(new Color(green));g.fillPolygon(new int[]{3,10,17,10},new int[]{12,17,29,26},4);
                g.setColor(new Color(darkGreen));g.fillPolygon(new int[]{28,22,16,24},new int[]{9,18,30,25},4);
            }
            case "strawberry" -> {
                g.setColor(new Color(crop.shade()));g.fillPolygon(new int[]{3,29,26,16,6},new int[]{8,8,20,30,21},5);
                g.setColor(new Color(crop.fruit()));g.fillPolygon(new int[]{5,26,23,15,8},new int[]{9,9,20,27,20},5);
                rect(g,green,5,6,22,3);rect(g,green,10,3,12,9);rect(g,darkGreen,15,1,3,5);
                for(int[] xy:new int[][]{{9,12},{17,11},{22,14},{12,18},{18,20},{14,24}})rect(g,highlight,xy[0],xy[1],2,2);
            }
            case "grape", "blueberry" -> {
                int[][] fruit=crop.id().equals("grape")?new int[][]{{4,6},{13,5},{21,7},{8,14},{17,14},{12,22}}:new int[][]{{3,13},{13,5},{21,13},{11,21}};
                rect(g,darkGreen,15,1,2,8);dot(g,green,5,2,10,5);
                for(int[] xy:fruit){dot(g,crop.shade(),xy[0],xy[1],10,10);dot(g,crop.fruit(),xy[0],xy[1],8,8);rect(g,0xB9ACED,xy[0]+2,xy[1]+2,2,2);
                    if(crop.id().equals("blueberry")){rect(g,crop.shade(),xy[0]+4,xy[1]+4,3,1);rect(g,crop.shade(),xy[0]+5,xy[1]+3,1,3);}}
            }
            case "pineapple" -> {
                dot(g,crop.shade(),6,11,21,20);dot(g,crop.fruit(),7,10,18,19);
                for(int y=13;y<28;y+=4)for(int x=9+(y%8==1?2:0);x<23;x+=5){rect(g,crop.shade(),x,y,2,2);rect(g,0xFAD56B,x,y-1,2,1);}
                for(int[] leaf:new int[][]{{7,3,3,9},{12,0,3,12},{17,1,3,11},{22,4,3,8}})rect(g,green,leaf[0],leaf[1],leaf[2],leaf[3]);
                rect(g,darkGreen,14,6,3,7);
            }
            case "onion" -> {
                dot(g,crop.shade(),4,10,25,20);dot(g,crop.fruit(),5,9,22,20);dot(g,0xF6DFA4,10,10,12,16);
                rect(g,crop.shade(),15,9,2,19);rect(g,green,12,2,3,9);rect(g,darkGreen,17,1,3,9);
                rect(g,0x9D754B,12,29,2,2);rect(g,0x9D754B,18,29,2,2);
            }
            case "radish" -> {
                dot(g,crop.shade(),8,10,17,18);dot(g,crop.fruit(),8,9,15,18);rect(g,0xFFFFFF,11,12,4,10);
                rect(g,crop.fruit(),14,25,5,4);rect(g,crop.shade(),16,29,2,2);
                rect(g,darkGreen,14,2,3,11);rect(g,green,6,4,8,5);rect(g,green,18,2,8,5);rect(g,0x76AE50,10,1,4,8);
            }
            case "eggplant" -> {
                dot(g,crop.shade(),6,11,20,19);dot(g,crop.fruit(),7,9,17,19);dot(g,0xAC7DC8,9,13,5,11);
                rect(g,green,12,7,12,5);rect(g,darkGreen,18,2,3,7);rect(g,0xD9B3DF,10,14,2,4);
            }
            case "cucumber" -> {
                dot(g,crop.shade(),8,3,15,28);dot(g,crop.fruit(),8,3,12,26);rect(g,0x98C363,10,6,3,18);
                for(int y=8;y<27;y+=5){rect(g,darkGreen,16,y,2,2);rect(g,0xA3C978,11,y+2,1,1);}rect(g,darkGreen,14,0,3,4);
            }
            case "bell_pepper" -> {
                dot(g,crop.shade(),3,9,27,21);dot(g,crop.fruit(),4,8,11,20);dot(g,crop.fruit(),13,8,15,20);
                dot(g,0xF5D85B,11,9,10,20);rect(g,highlight,7,12,3,7);rect(g,darkGreen,15,2,3,8);rect(g,green,12,7,10,3);
            }
        }
        g.dispose();return image;
    }
    static BufferedImage seedIcon(CropArt crop) {
        var image=new BufferedImage(32,32,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();
        rect(g,0x634B35,5,3,22,28);rect(g,0xD8BC83,6,4,20,25);rect(g,0xF1DCAB,7,7,18,20);
        rect(g,crop.shade(),6,4,20,4);rect(g,crop.fruit(),7,5,18,2);
        g.drawImage(cropIcon(crop),9,9,14,14,null);rect(g,0xB59762,9,25,14,2);
        g.dispose();return image;
    }
    static BufferedImage plantStage(CropArt crop, int stage) {
        var image=new BufferedImage(32,32,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();
        int green=0x548F3C,dark=0x2A5834,light=0x8EBE58;
        if(stage==0){rect(g,dark,15,24,2,8);dot(g,green,9,22,7,4);dot(g,light,17,19,7,5);}
        else {
            String id=crop.id();
            if(Set.of("cabbage","onion","radish","pineapple").contains(id)) {
                for(int[] xy:new int[][]{{3,20},{9,16},{17,17},{22,21}}){dot(g,dark,xy[0],xy[1],9,11);dot(g,green,xy[0],xy[1],7,8);}
                if(stage==2)g.drawImage(cropIcon(crop),6,id.equals("pineapple")?1:10,22,id.equals("pineapple")?30:22,null);
            } else if(id.equals("corn")) {
                rect(g,dark,15,stage==1?12:3,3,29);for(int y=12;y<30;y+=6){rect(g,green,5,y,10,3);rect(g,light,18,y-3,9,3);}
                if(stage==2){g.drawImage(cropIcon(crop),5,7,12,19,null);g.drawImage(cropIcon(crop),17,13,11,17,null);rect(g,0xCEB96B,13,1,7,3);}
            } else {
                boolean vine=id.equals("grape")||id.equals("cucumber");
                if(vine){rect(g,0x886443,7,3,2,29);rect(g,0x886443,25,3,2,29);rect(g,0xA58354,7,8,20,2);rect(g,0xA58354,7,21,20,2);}
                int top=stage==1?13:4;rect(g,dark,15,top,2,32-top);
                for(int y=top+2;y<28;y+=6){dot(g,dark,4,y,12,8);dot(g,green,5,y,10,6);dot(g,green,16,y-2,12,8);dot(g,light,18,y-2,8,4);}
                if(stage==1){dot(g,0xF0DFA5,13,11,4,4);}else {
                    g.drawImage(cropIcon(crop),3,11,13,13,null);g.drawImage(cropIcon(crop),17,15,13,13,null);
                    if(id.equals("strawberry")||id.equals("blueberry"))g.drawImage(cropIcon(crop),10,23,11,9,null);
                }
            }
        }
        g.dispose();return image;
    }
    static void cropPreview(Path path) throws IOException {
        var image=new BufferedImage(960, CROPS.size()*150+60,BufferedImage.TYPE_INT_RGB);var g=paint(image);
        g.setColor(new Color(0x211E1C));g.fillRect(0,0,image.getWidth(),image.getHeight());
        g.setColor(new Color(0xE9D49C));g.setFont(new Font("Nanum Gothic",Font.BOLD,22));
        g.drawString("커스텀 작물 · 수확물 / 씨앗 / 새싹 / 성장 / 수확 가능",24,36);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        for(int i=0;i<CROPS.size();i++){
            CropArt crop=CROPS.get(i);int y=60+i*150;
            g.setColor(new Color(0x302B29));g.fillRect(12,y,936,140);
            int index=ShopLayout.ITEM_IDS.indexOf(crop.id().toUpperCase(Locale.ROOT));
            g.setColor(new Color(0xF1E7CE));g.setFont(new Font("Nanum Gothic",Font.BOLD,20));g.drawString(ShopLayout.ITEM_NAMES.get(index),28,y+70);
            BufferedImage[] sprites={cropIcon(crop),seedIcon(crop),plantStage(crop,0),plantStage(crop,1),plantStage(crop,2)};
            for(int j=0;j<sprites.length;j++)g.drawImage(sprites[j],160+j*155,y+8,124,124,null);
        }
        g.dispose();ImageIO.write(image,"png",path.toFile());
    }
    /** Repaints four rarely used vanilla default skins; without the pack the NPCs simply keep those defaults. */
    static void npcSkins() throws IOException {
        png("assets/minecraft/textures/entity/player/wide/kai.png", skin(0xC98F63, 0x3B2A1C, 0x4F7A3A, 0xE3D4A8, 0x4A3A2A, 0xD9B44A));
        png("assets/minecraft/textures/entity/player/wide/makena.png", skin(0xE0AC84, 0x6B3A1E, 0xA8562E, 0x3F6C42, 0x3D2E22, 0x7A9A4A));
        png("assets/minecraft/textures/entity/player/wide/efe.png", skin(0xD8A27A, 0x22242C, 0x2F3F6B, 0xE9E2CF, 0x2A2A33, 0));
        png("assets/minecraft/textures/entity/player/wide/sunny.png", skin(0xE0AC84, 0x8A4A24, 0x6B5A8A, 0xD9C7A0, 0x3D2E22, 0));
        png("assets/minecraft/textures/entity/player/wide/noor.png", skin(0xC98F63, 0x2A2018, 0x7A6A4A, 0xC9A050, 0x3A3028, 0x8A6A3A));
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
    static void button(Graphics2D g, int x, int y, int w, int h, Color fill) {
        box(g,fill,x,y,w,h,1);
        outline(g,new Color(0x887D70),x,y,w,h,1);
    }
    /** Target, current option and lamp stock on top, the options the tool can get below, three buttons at the bottom. */
    static BufferedImage lampScreen() {
        var image=new BufferedImage(WIDTH*SCALE,HEIGHT*SCALE,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=paint(image);
        Color accent=new Color(0xE9D49C);
        box(g,new Color(0x211E1C),0,0,WIDTH,HEIGHT,0);
        box(g,new Color(0x3A2A18),0,0,WIDTH,14,0);
        g.setColor(accent);g.fillRect(0,14*SCALE,WIDTH*SCALE,SCALE/2);
        g.setFont(font(8,true));g.drawString("생활 램프",7*SCALE,10*SCALE);
        String[] labels={"대상 도구","현재 능력","보유 램프"};
        int[] columns={1,4,7};
        for(int i=0;i<3;i++){
            int x=7+columns[i]*18;
            box(g,new Color(0x302B29),x-14,16,46,38,0);
            outline(g,new Color(0x675B4B),x-14,16,46,38,0);
            box(g,accent,x-14,16,46,1,0);
            centered(g,labels[i],font(5,true),CREAM,x+9,26);
            box(g,new Color(0x393632),x,35,18,18,0);
            box(g,new Color(0x2A2725),x+1,36,16,16,0);
            outline(g,accent,x,35,18,18,0);
        }
        centered(g,"이 도구에 붙을 수 있는 능력",font(5,true),accent,88,63);
        for(int row=3;row<5;row++)for(int col=0;col<9;col++){
            box(g,new Color(0x393632),7+col*18,17+row*18,18,18,0);
            box(g,new Color(0x2A2725),8+col*18,18+row*18,16,16,0);
        }
        button(g,7,107,53,19,new Color(0x4A3A1E));
        centered(g,"램프 구매",font(5,true),CREAM,43,113);
        button(g,61,107,53,19,new Color(0x3F6C42));
        centered(g,"적용",font(7,true),CREAM,97,116.5);
        button(g,115,107,54,19,new Color(0x3A332E));
        centered(g,"닫기",font(7,true),CREAM,151,116.5);
        box(g,new Color(0x181818),3,130,170,89,0);
        for(int row=0;row<4;row++)for(int col=0;col<9;col++){
            int y=row==3?198:140+row*18;
            box(g,new Color(0x393632),7+col*18,y-1,18,18,0);
            box(g,new Color(0x252423),8+col*18,y,16,16,0);
        }
        g.dispose();return image;
    }
    /** Dark panel with a header line and one well per chest slot, for every menu without its own layout. */
    static BufferedImage panel() {
        var image=new BufferedImage(WIDTH*SCALE,HEIGHT*SCALE,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=paint(image);
        box(g,new Color(0x211E1C),0,0,WIDTH,HEIGHT,0);
        box(g,new Color(0x322921),0,0,WIDTH,14,0);
        g.setColor(new Color(0xE9D49C));g.fillRect(0,14*SCALE,WIDTH*SCALE,SCALE/2);
        for(int row=0;row<6;row++)for(int col=0;col<9;col++){
            box(g,new Color(0x393632),7+col*18,17+row*18,18,18,0);
            box(g,new Color(0x2A2725),8+col*18,18+row*18,16,16,0);
        }
        box(g,new Color(0x181818),3,130,170,89,0);
        for(int row=0;row<4;row++)for(int col=0;col<9;col++){
            int y=row==3?198:140+row*18;
            box(g,new Color(0x393632),7+col*18,y-1,18,18,0);
            box(g,new Color(0x252423),8+col*18,y,16,16,0);
        }
        g.dispose();return image;
    }
    static BufferedImage goldLabel() {
        var image=new BufferedImage(13*SCALE,7*SCALE,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=paint(image);
        g.setFont(font(6,true));g.setColor(new Color(0xF2DDA4));
        g.drawString("골드",SCALE,6*SCALE);
        g.dispose();
        image.setRGB(image.getWidth()-1,image.getHeight()-1,0x01FFFFFF);
        return image;
    }
    static final int MINIMAP_TOP = 7, MINIMAP_FRAME = MiniMapTitle.MAP_SIZE + MiniMapTitle.FRAME_MARGIN * 2;
    /** One white cell per row font; the second glyph cancels the gap the client leaves after every bitmap glyph. */
    static void miniMapFonts() throws IOException {
        var cell = new BufferedImage(MiniMapTitle.CELL_SIZE * SCALE, MiniMapTitle.CELL_SIZE * SCALE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = cell.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, cell.getWidth(), cell.getHeight());
        g.dispose();
        png("assets/maitycoon/textures/gui/minimap_cell.png", cell);
        for (int row = 0; row < MiniMapTitle.CELLS; row++) {
            text("assets/maitycoon/font/mm" + row + ".json", "{\"providers\":[" + bitmap("minimap_cell.png", MiniMapTitle.CELL_SIZE, MiniMapTitle.CELL_SIZE - row * MiniMapTitle.CELL_SIZE, "\\ue230")
                + ",{\"type\":\"space\",\"advances\":{\"\\ue231\":-1}}]}");
        }
        // Bitmap glyphs over 256px are dropped by the client, so the frame is drawn at 3x rather than SCALE.
        int frameScale = 3;
        var frame = new BufferedImage(MINIMAP_FRAME * frameScale, MINIMAP_FRAME * frameScale, BufferedImage.TYPE_INT_ARGB);
        g = frame.createGraphics();
        g.setColor(new Color(0x211E1C));
        g.fillRect(0, 0, frame.getWidth(), frame.getHeight());
        g.setColor(new Color(0xE9D49C));
        g.setStroke(new BasicStroke(frameScale));
        g.drawRect(1, 1, frame.getWidth() - 3, frame.getHeight() - 3);
        g.dispose();
        png("assets/maitycoon/textures/gui/minimap_frame.png", frame);
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
        for (int kind = 0; kind < 6; kind++) for (int row = 0; row < TILE_ROWS; row++) for (int col = 0; col < 4; col++) {
            int h = Math.min(TILE_H, HEIGHT - row * TILE_H);
            providers.add(bitmap(List.of("sell", "buy", "build", "panel", "furniture", "lamp").get(kind) + "_" + row + "_" + col + ".png", h,
                13 - row * TILE_H, String.format("\\u%04x", List.of(0xE300, 0xE400, 0xE600, 0xE800, 0xE900, 0xE500).get(kind) + row * 4 + col)));
        }
        providers.add(bitmap("tab.png", 17, 13 - 90, "\\ue210"));
        providers.add(bitmap("minimap_frame.png", MINIMAP_FRAME, MINIMAP_TOP - 2, "\\ue221"));
        return "{\"providers\":[" + providers + "]}";
    }
    static String digitFont(int height, int ascent) {
        var chars = new StringBuilder();
        for (char c : DIGITS.toCharArray()) chars.append(c < 128 ? String.valueOf(c) : String.format("\\u%04x", (int) c));
        return "{\"providers\":[" + bitmap("digits.png", height, ascent, chars.toString()) + "]}";
    }
    /** Debug view of the sell screen at SCALE, on a dark backdrop. */
    static BufferedImage preview() throws IOException {
        BufferedImage bg = background("농산물 상점", List.of("전체", "곡물", "과일", "특수", "희귀"), GOLD);
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
