package kr.maitycoon;

import java.util.*;
import java.util.random.RandomGenerator;

/** Life lamp options for tools. Ranges come from config; the option itself is stored on the tool. */
public final class LifeLamp {
    public enum Tool {
        HOE("괭이"), PICKAXE("곡괭이"), SHOVEL("삽"), AXE("도끼");
        public final String label;
        Tool(String label) { this.label = label; }
        /** null for anything that is not a life tool. The underscore keeps PICKAXE from matching AXE. */
        public static Tool of(String material) {
            for (Tool tool : values()) if (material.endsWith("_" + tool.name())) return tool;
            return null;
        }
    }
    /** tool is null for options every life tool can hold. {p} and {a} stand for the percent and the amount. */
    public enum Kind {
        CROP(Tool.HOE, "추가 작물", "작물 수확 시 {p}% 확률로 작물 {a}개 추가"),
        SEED(Tool.HOE, "추가 씨앗", "작물 수확 시 {p}% 확률로 씨앗 {a}개 추가"),
        BONE_MEAL(null, "추가 뼛가루", "작물 수확 시 {p}% 확률로 뼛가루 {a}개 추가"),
        EXP(null, "추가 경험치", "경험치를 얻을 때 {p}% 확률로 경험치 {a} 증가"),
        CHICKEN(null, "닭 전리품", "닭 처치 시 {p}% 확률로 전리품 {a}개 추가"),
        RABBIT(null, "토끼 전리품", "토끼 처치 시 {p}% 확률로 전리품 {a}개 추가"),
        PIG(null, "돼지 전리품", "돼지 처치 시 {p}% 확률로 전리품 {a}개 추가"),
        SHEEP(null, "양 전리품", "양 처치 시 {p}% 확률로 전리품 {a}개 추가"),
        COW(null, "소 전리품", "소 처치 시 {p}% 확률로 전리품 {a}개 추가"),
        MONSTER(null, "몬스터 전리품", "몬스터 처치 시 {p}% 확률로 전리품 {a}개 추가"),
        SMELT(Tool.PICKAXE, "제련", "캔 광물이 구워진 주괴로 나옵니다. 섬세한 손길과 함께 쓸 수 없습니다."),
        AREA(Tool.PICKAXE, "범위 채굴", "{a} 1칸을 함께 캡니다. 야생 전용"),
        ORE(Tool.PICKAXE, "추가 광석", "광물 채굴 시 {p}% 확률로 광석 {a}개 추가. 섬세한 손길과 함께 쓸 수 없습니다."),
        GLASS(Tool.SHOVEL, "유리 모래", "모래를 캐면 유리 블록이 나옵니다."),
        TREE(Tool.AXE, "연속 벌목", "원목을 캐면 위로 쌓인 원목을 최대 {a}개까지 함께 캡니다. 야생 전용");
        public final Tool tool;
        public final String label;
        private final String text;
        Kind(Tool tool, String label, String text) {
            this.tool = tool;
            this.label = label;
            this.text = text;
        }
        public boolean fits(Tool held) { return tool == null || tool == held; }
        private String text(String percent, String amount) { return text.replace("{p}", percent).replace("{a}", amount); }
    }
    private static final int VERTICAL = 2;
    private static String directions(int amount) { return amount == VERTICAL ? "상·하" : "상·하·좌·우"; }
    public record Range(int minPercent, int maxPercent, int minAmount, int maxAmount) {
        public Range {
            if (minPercent < 0 || maxPercent > 100 || minPercent > maxPercent || minAmount < 1 || minAmount > maxAmount) throw new IllegalArgumentException("Invalid lamp range");
        }
    }
    public record Option(Kind kind, int percent, int amount) {
        private static final int SCHEMA = 1;
        public String describe() { return kind.text(Integer.toString(percent), kind == Kind.AREA ? directions(amount) : Integer.toString(amount)); }
        public String encode() { return SCHEMA + ":" + kind + ":" + percent + ":" + amount; }
        /** null for anything unreadable, so a damaged tag behaves like a tool without an option. */
        public static Option decode(String value) {
            if (value == null) return null;
            String[] parts = value.split(":");
            try {
                if (parts.length != 4 || Integer.parseInt(parts[0]) != SCHEMA) return null;
                return new Option(Kind.valueOf(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
            } catch (IllegalArgumentException e) { return null; }
        }
        /** Extra items for one harvest. */
        public int bonus(RandomGenerator random) { return random.nextInt(100) < percent ? amount : 0; }
    }
    /** Offsets {x, y, z} of the blocks mined with an AREA option; left and right follow the way the player faces. */
    public static List<int[]> areaOffsets(int amount, int facingX, int facingZ) {
        List<int[]> offsets = new ArrayList<>(List.of(new int[]{0, 1, 0}, new int[]{0, -1, 0}));
        if (amount != VERTICAL) {
            offsets.add(new int[]{facingZ, 0, -facingX});
            offsets.add(new int[]{-facingZ, 0, facingX});
        }
        return offsets;
    }
    private final Map<Kind, Range> ranges;
    public LifeLamp(Map<Kind, Range> ranges) {
        this.ranges = ranges.isEmpty() ? Map.of() : new EnumMap<>(ranges);
        for (Tool tool : Tool.values()) if (kinds(tool).isEmpty()) throw new IllegalArgumentException("No lamp options for " + tool);
    }
    /** Every configured option the tool can hold; a null tool lists them all. */
    public List<Kind> kinds(Tool tool) { return ranges.keySet().stream().filter(kind -> tool == null || kind.fits(tool)).toList(); }
    /** The option's sentence with its configured ranges in place of the numbers. */
    public String range(Kind kind) {
        Range range = ranges.get(kind);
        String amounts = kind == Kind.AREA ? directions(range.minAmount()) + " 또는 " + directions(range.maxAmount()) : span(range.minAmount(), range.maxAmount());
        return kind.text(span(range.minPercent(), range.maxPercent()), amounts);
    }
    private static String span(int min, int max) { return min == max ? Integer.toString(min) : min + "~" + max; }
    public Option roll(Tool tool, RandomGenerator random) {
        List<Kind> kinds = kinds(tool);
        Kind kind = kinds.get(random.nextInt(kinds.size()));
        Range range = ranges.get(kind);
        // Range mining comes in exactly two shapes, so anything between them has no meaning.
        int amount = kind == Kind.AREA ? (random.nextBoolean() ? range.minAmount() : range.maxAmount()) : random.nextInt(range.minAmount(), range.maxAmount() + 1);
        return new Option(kind, random.nextInt(range.minPercent(), range.maxPercent() + 1), amount);
    }
}
