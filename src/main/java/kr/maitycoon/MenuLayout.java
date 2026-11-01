package kr.maitycoon;

import java.util.ArrayList;
import java.util.List;

/**
 * Slots and text positions of the lamp and land screens, shared by the click handlers and the pack generator.
 * A background is only a picture, so every wide button lists all the slots that act as that button.
 */
public final class MenuLayout {
    public static final int SIZE = 54, COLUMN = 18, LEFT = 8, TOP = 18;
    /** Text sits on fixed heights, one font per height: a glyph's height is part of its font. */
    public static final int TEXT_TOP = 7, TEXT_STEP = 5, TEXT_LINES = 24;
    public static final int LABEL_WIDTH = 80, LABEL_HEIGHT = 7, LABEL_BASE = 0xF000;

    public static final int SHOP_ICON = 9, SHOP_CLOSE = 8;
    public static final List<Integer> SHOP_BUY = List.of(27, 28, 29, 30, 31, 32, 33, 34, 35), SHOP_USE = List.of(51, 52, 53);
    public static final int USE_TARGET = 9, USE_BACK = 8;
    public static final List<Integer> USE_LIST = List.of(45, 46, 47, 48), USE_APPLY = List.of(50, 51, 52, 53);
    public static final int MAP_COLUMNS = 5, MAP_ROWS = 5, LAND_SUMMARY = 15;
    public static final List<Integer> LAND_BORDER = List.of(45, 46), LAND_HOME = List.of(47, 48), LAND_FARM = List.of(49, 50), LAND_CONFIRM = List.of(51, 52, 53);
    public static final List<Integer> FARM_WORKERS = List.of(0, 1, 2, 3), FARM_PARCELS = List.of(5, 6, 7, 8);
    public static final List<Integer> FARM_BACK = List.of(45, 46, 47), FARM_STATUS = List.of(48, 49, 50), FARM_AUTO = List.of(51, 52, 53);
    public static final int BUY_SUMMARY = 13;
    public static final List<Integer> BUY_YES = List.of(45, 46, 47, 48), BUY_NO = List.of(50, 51, 52, 53);

    public static final List<String> LABEL_IDS, LABEL_TEXTS;
    static {
        List<String> ids = new ArrayList<>(), texts = new ArrayList<>();
        String[][] materials = {{"WOODEN", "나무"}, {"STONE", "돌"}, {"COPPER", "구리"}, {"IRON", "철"}, {"GOLDEN", "금"}, {"DIAMOND", "다이아몬드"}, {"NETHERITE", "네더라이트"}};
        for (String[] material : materials) for (LifeLamp.Tool tool : LifeLamp.Tool.values()) {
            ids.add(material[0] + "_" + tool.name());
            texts.add(material[1] + " " + tool.label);
        }
        for (LifeLamp.Kind kind : LifeLamp.Kind.values()) {
            ids.add("KIND_" + kind.name());
            texts.add(kind.label);
        }
        String[] words = {"없음", "도구 없음", "구매 가능", "내 땅", "타인 소유", "마을 구역", "보유", "필요", "소모 없음", "구매 확정", "돌아가기", "경계 보기", "농장 관리", "구매 완료", "골드", "개", "골드 부족", "한도 초과", "신고할 기록 없음"};
        for (String word : words) {
            ids.add(word);
            texts.add(word);
        }
        LABEL_IDS = List.copyOf(ids);
        LABEL_TEXTS = List.copyOf(texts);
    }
    private MenuLayout() {}
    public static int textLine(int y) {
        int line = Math.round((y - TEXT_TOP) / (float) TEXT_STEP);
        if (line < 0 || line >= TEXT_LINES) throw new IllegalArgumentException("No text line at " + y);
        return line;
    }
    public static int textY(int line) { return TEXT_TOP + line * TEXT_STEP; }
    public static int mapSlot(int column, int row) { return row * 9 + column; }
    public static int slotX(int slot) { return LEFT + slot % 9 * COLUMN; }
}
