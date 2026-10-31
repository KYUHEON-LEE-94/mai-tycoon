package kr.maitycoon;

import java.util.List;
import java.util.Set;

/** Two columns × four product cards; shared by click handlers and bitmap generation. */
public final class ShopLayout {
    public static final int SIZE=54, COLUMNS=9, ROWS=4, PAGE_SIZE=8, MAX_TABS=5, PREV=48, NEXT=50;
    public static final int GUI_WIDTH=176, GUI_HEIGHT=128, SLOT=18, LEFT=8, TOP=18, FOOTER_ROW=5;
    public static final int BACKGROUND_HEIGHT=222, TILE_WIDTH=44, TILE_HEIGHT=32, TILE_COLUMNS=4, TILE_ROWS=7;
    public static final Set<Integer> AMOUNT=Set.of(46,47), ACTION=Set.of(41,42,43,44);
    public static final List<String> ITEM_IDS=List.of("WHEAT","CARROT","POTATO","BEETROOT","PUMPKIN","MELON_SLICE","SWEET_BERRIES","GLOW_BERRIES","COCOA_BEANS","SUGAR_CANE","CACTUS","BAMBOO","KELP","BROWN_MUSHROOM","RED_MUSHROOM","NETHER_WART","TORCHFLOWER","PITCHER_PLANT","WHEAT_SEEDS","BEETROOT_SEEDS","MELON_SEEDS","PUMPKIN_SEEDS","TORCHFLOWER_SEEDS","PITCHER_POD","BONE_MEAL","STONE_HOE","IRON_HOE","CABBAGE","PINEAPPLE","GRAPE","TOMATO","CORN","STRAWBERRY","CABBAGE_SEEDS","PINEAPPLE_SEEDS","GRAPE_SEEDS","TOMATO_SEEDS","CORN_SEEDS","STRAWBERRY_SEEDS","OAK_LOG","SPRUCE_LOG","BIRCH_LOG","CHERRY_LOG","OAK_PLANKS","SPRUCE_PLANKS","BIRCH_PLANKS","CHERRY_PLANKS","COBBLESTONE","STONE","STONE_BRICKS","DEEPSLATE_BRICKS","BRICKS","SMOOTH_QUARTZ","SANDSTONE","TERRACOTTA","GLASS","WHITE_STAINED_GLASS","WHITE_CONCRETE","WHITE_WOOL","LANTERN","IRON_CHAIN","OAK_LEAVES","SPRUCE_TRAPDOOR","ONION","ONION_SEEDS","RADISH","RADISH_SEEDS","EGGPLANT","EGGPLANT_SEEDS","CUCUMBER","CUCUMBER_SEEDS","BELL_PEPPER","BELL_PEPPER_SEEDS","BLUEBERRY","BLUEBERRY_SEEDS");
    public static final List<String> ITEM_NAMES=List.of("밀","당근","감자","비트","호박","수박 조각","달콤한 열매","발광 열매","코코아 콩","사탕수수","선인장","대나무","켈프","갈색 버섯","빨간색 버섯","네더 사마귀","횃불꽃","벌레잡이풀","밀 씨앗","비트 씨앗","수박씨","호박씨","횃불꽃 씨앗","벌레잡이풀 꼬투리","뼛가루","돌 괭이","철 괭이","배추","파인애플","포도","토마토","옥수수","딸기","배추 씨앗","파인애플 모종","포도 묘목","토마토 씨앗","옥수수 씨앗","딸기 모종","참나무 원목","가문비나무 원목","자작나무 원목","벚나무 원목","참나무 판자","가문비나무 판자","자작나무 판자","벚나무 판자","조약돌","돌","석재 벽돌","심층암 벽돌","벽돌","매끄러운 석영","사암","테라코타","유리","하얀색 유리","하얀색 콘크리트","하얀색 양털","랜턴","사슬","참나무 잎","가문비나무 다락문","양파","양파 씨앗","무","무 씨앗","가지","가지 씨앗","오이","오이 씨앗","파프리카","파프리카 씨앗","블루베리","블루베리 씨앗");
    private ShopLayout() {}
    public static int tabSlot(int tab){return 36+tab;}
    public static int tabAt(int slot){return slot>=36&&slot<=40?slot-36:-1;}
    public static int row(int index){return index/2;}
    public static int itemColumn(int index){return index%2*5;}
    public static int itemSlot(int index){return row(index)*9+itemColumn(index);}
    public static int priceSlot(int index){return itemSlot(index)+3;}
    public static int itemAt(int slot){
        if(slot<0||slot>=36||slot%9==4)return -1;
        return slot/9*2+(slot%9>=5?1:0);
    }
    public static int slotX(int column){return LEFT+column*SLOT;}
    public static int slotY(int row){return TOP+row*SLOT;}
}
