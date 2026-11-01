package kr.maitycoon;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/** Atlas-safe tiled background and dynamically positioned product labels. */
final class ShopTitle {
    private static final Key GUI=Key.key("maitycoon","gui"),SPACE=Key.key("maitycoon","space");
    private static final TextColor GOLD=TextColor.color(0xF2DDA4);
    private final TextComponent.Builder title=Component.text().color(NamedTextColor.WHITE);
    private int cursor=8;
    private TextColor ink=null;
    private void moveTo(int x){
        int delta=x-cursor;cursor=x;StringBuilder spaces=new StringBuilder();
        int base=delta<0?0xE000:0xE100,left=Math.abs(delta);
        for(int bit=0;left!=0;bit++,left>>=1)if((left&1)!=0)spaces.append((char)(base+bit));
        if(!spaces.isEmpty())title.append(Component.text(spaces.toString()).font(SPACE));
    }
    private void put(Key font,String text,TextColor color,int advance){title.append(Component.text(text).font(font).color(color));cursor+=advance;}
    private void number(String font,int x,String value){moveTo(x);put(Key.key("maitycoon",font),value,GOLD,value.length()*4);}
    /** Shared dark panel for the other menus; the name is ordinary title text, so it stays in the client's own font. */
    ShopTitle panel(String name){
        tiles(0xE800);
        moveTo(8);
        title.append(Component.text(name,GOLD));
        return this;
    }
    ShopTitle background(Shop.Kind kind){
        tiles(switch(kind){case SELL->0xE300;case BUY->0xE400;case BUILD->0xE600;case FURNITURE->0xE900;});
        return this;
    }
    /** A full-size background with no shop layout. Its text comes from label() and value(). */
    ShopTitle screen(int base){tiles(base);return this;}
    /** A prepared label at a text height; x left at -1 continues after the previous text. */
    ShopTitle label(int y,int x,String id){
        int index=MenuLayout.LABEL_IDS.indexOf(id);
        if(index<0)throw new IllegalArgumentException("No label "+id);
        if(x>=0)moveTo(x);
        put(Key.key("maitycoon","t"+MenuLayout.textLine(y)),String.valueOf((char)(MenuLayout.LABEL_BASE+index)),ink==null?NamedTextColor.WHITE:ink,MenuLayout.LABEL_WIDTH+1);
        return this;
    }
    /** Digits never shorten an amount here, because these screens state what is about to be paid. */
    ShopTitle value(int y,int x,String text){
        if(x>=0)moveTo(x);
        put(Key.key("maitycoon","n"+MenuLayout.textLine(y)),text,ink==null?GOLD:ink,text.length()*4);
        return this;
    }
    /** Dark text for the blue buy button, where the usual light colours cannot be read. */
    ShopTitle onBlue(boolean blue){ink=blue?TextColor.color(0x142530):null;return this;}
    /** Pulls the cursor back over the empty right side of a label, so the next text can follow its last letter. */
    ShopTitle back(int width){moveTo(cursor-width);return this;}
    ShopTitle selected(int column,int row){moveTo(MenuLayout.LEFT+column*MenuLayout.COLUMN-1);put(GUI,String.valueOf((char)(0xE240+row)),NamedTextColor.WHITE,19);return this;}
    ShopTitle dimmed(int x,boolean wide){moveTo(x);put(GUI,wide?"\ue250":"\ue251",NamedTextColor.WHITE,wide?73:55);return this;}
    private void tiles(int base){
        for(int row=0;row<ShopLayout.TILE_ROWS;row++)for(int col=0;col<4;col++){
            moveTo(col*44);put(GUI,String.valueOf((char)(base+row*4+col)),NamedTextColor.WHITE,45);
        }
    }
    ShopTitle selectedTab(int tab){moveTo(7+tab*18);put(GUI,"\ue210",NamedTextColor.WHITE,19);return this;}
    private static String compact(long n){return n>=1_000_000_000L?n/1_000_000_000L+"B":n>=1_000_000L?n/1_000_000L+"M":n>=10_000?n/1000+"K":Long.toString(n);}
    ShopTitle coins(long n){number("footer",15,compact(n));put(Key.key("maitycoon","footer"),"\uE220",NamedTextColor.WHITE,14);return this;}
    ShopTitle amount(int n){number("controls",41,Integer.toString(n));return this;}
    ShopTitle page(int page,int total){number("controls",82,page+"/"+total);return this;}
    /** Clock time of the next price change: the title only redraws on a click, so a countdown would freeze. */
    ShopTitle timer(java.time.LocalTime at, boolean market){if (!market) return this; number("footer",139,String.format(java.util.Locale.ROOT,"%02d:%02d",at.getHour(),at.getMinute()));return this;}
    ShopTitle product(int index,String id,long price,long owned){
        int row=ShopLayout.row(index),x=ShopLayout.slotX(ShopLayout.itemColumn(index));
        int name=ShopLayout.ITEM_IDS.indexOf(id);
        if(name>=0){moveTo(x+20);put(Key.key("maitycoon","names"+row),String.valueOf((char)(ShopLayout.NAME_BASE+name)),NamedTextColor.WHITE,49);}
        number("row"+row,x+56,compact(price));number("owned"+row,x,compact(owned));return this;
    }
    ShopTitle spaceRight(int width){moveTo(cursor+width);return this;}
    Component build(){return title.build();}
}
