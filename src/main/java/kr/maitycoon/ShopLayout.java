package kr.maitycoon;

import java.util.List;
import java.util.Set;

/** Two columns × four product cards; shared by click handlers and bitmap generation. */
public final class ShopLayout {
    public static final int SIZE=54, COLUMNS=9, ROWS=4, PAGE_SIZE=8, MAX_TABS=5, PREV=48, NEXT=50;
    public static final int GUI_WIDTH=176, GUI_HEIGHT=128, SLOT=18, LEFT=8, TOP=18, FOOTER_ROW=5;
    public static final int BACKGROUND_HEIGHT=222, TILE_WIDTH=44, TILE_HEIGHT=32, TILE_COLUMNS=4, TILE_ROWS=7;
    public static final Set<Integer> AMOUNT=Set.of(46,47), ACTION=Set.of(41,42,43,44);
    public static final List<String> ITEM_IDS=List.of("WHEAT","CARROT","POTATO","BEETROOT","PUMPKIN","MELON_SLICE","SWEET_BERRIES","GLOW_BERRIES","COCOA_BEANS","SUGAR_CANE","CACTUS","BAMBOO","KELP","BROWN_MUSHROOM","RED_MUSHROOM","NETHER_WART","TORCHFLOWER","PITCHER_PLANT","WHEAT_SEEDS","BEETROOT_SEEDS","MELON_SEEDS","PUMPKIN_SEEDS","TORCHFLOWER_SEEDS","PITCHER_POD","BONE_MEAL","STONE_HOE","IRON_HOE");
    public static final List<String> ITEM_NAMES=List.of("밀","당근","감자","비트","호박","수박 조각","달콤한 열매","발광 열매","코코아 콩","사탕수수","선인장","대나무","켈프","갈색 버섯","빨간색 버섯","네더 사마귀","횃불꽃","벌레잡이풀","밀 씨앗","비트 씨앗","수박씨","호박씨","횃불꽃 씨앗","벌레잡이풀 꼬투리","뼛가루","돌 괭이","철 괭이");
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
