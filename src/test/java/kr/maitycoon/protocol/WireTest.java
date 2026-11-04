package kr.maitycoon.protocol;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The same file runs on both sides against protocol/vectors, which were written from the specification alone. */
class WireTest {
    private static byte[] vector(String name) throws Exception {
        return HexFormat.of().parseHex(Files.readString(Path.of(System.getProperty("protocol.vectors"), name + ".hex")).strip());
    }
    @Test void hello() throws Exception {
        var hello = Wire.hello(vector("hello"));
        assertEquals(new Wire.Hello(1, 2, "0.4.0", List.of("full_map", "shop")), hello);
        assertArrayEquals(vector("hello"), Wire.encode(hello));
    }
    @Test void helloAck() throws Exception {
        var ack = Wire.helloAck(vector("hello_ack"));
        assertEquals(new Wire.HelloAck(Wire.CLIENT_OLD, 2, 0, "클라이언트를 업데이트해 주세요", List.of()), ack);
        assertArrayEquals(vector("hello_ack"), Wire.encode(ack));
    }
    @Test void mapRequest() throws Exception {
        assertEquals(new Wire.MapRequest(-7), Wire.mapRequest(vector("map_request")));
        assertArrayEquals(vector("map_request"), Wire.encode(new Wire.MapRequest(-7)));
    }
    @Test void map() throws Exception {
        var map = Wire.map(vector("map"));
        assertEquals(42, map.requestId());
        assertEquals(-3, map.originX());
        assertEquals(5, map.originZ());
        assertEquals(8, map.cellBlocks());
        assertArrayEquals(new int[]{0x7FB238, Wire.UNKNOWN, 0, 0xFFFFFF}, map.colors());
        assertArrayEquals(new byte[]{Wire.NONE, Wire.MINE, Wire.TAKEN, Wire.VILLAGE}, map.overlays());
        assertEquals(List.of(new Wire.Mark("maizurzu", 120, -5), new Wire.Mark("가나", Integer.MIN_VALUE, Integer.MAX_VALUE)), map.players());
        assertArrayEquals(vector("map"), Wire.encode(map));
    }
    @Test void aProtocol10ClientGetsTheOldLayout() throws Exception {
        var old = Wire.map(vector("map_v1_0"), true);
        assertEquals(Wire.LEGACY_CELL, old.cellBlocks());
        assertEquals(42, old.requestId());
        assertArrayEquals(vector("map_v1_0"), Wire.encode(old, true));
        assertThrows(IllegalArgumentException.class, () -> Wire.map(vector("map_v1_0")));
        assertThrows(IllegalArgumentException.class, () -> Wire.encode(Wire.map(vector("map")), true));
    }
    @Test void shopMessages() throws Exception {
        var shop = Wire.shop(vector("shop"));
        assertEquals(Wire.OPEN, shop.reason());
        assertEquals(Wire.SHOP_SELL, shop.kind());
        assertEquals("농산물 상점", shop.title());
        assertEquals(5_000_000_000L, shop.coins());
        assertEquals(List.of("전체", "곡물"), shop.tabs());
        assertEquals(2, shop.items().size());
        var wheat = shop.items().get(0);
        assertEquals(new Wire.ShopItem("WHEAT", "밀", "곡물", "minecraft:wheat", "", 55, -5, 30, 90, 12, 64, List.of("시세 ▁▃▇")), wheat);
        assertEquals("tomato", shop.items().get(1).model());
        assertArrayEquals(vector("shop"), Wire.encode(shop));
        var request = Wire.shopRequest(vector("shop_request"));
        assertEquals(new Wire.ShopRequest(9, Wire.SHOP_BUY, Wire.ACT_BUY, "WHEAT_SEEDS", 16, 3), request);
        assertArrayEquals(vector("shop_request"), Wire.encode(request));
        var result = Wire.shopResult(vector("shop_result"));
        assertEquals(new Wire.ShopResult(9, Wire.RESULT_CHANGED, "시세가 바뀌었습니다"), result);
        assertArrayEquals(vector("shop_result"), Wire.encode(result));
    }
    @Test void brokenMessagesAreRefused() throws Exception {
        byte[] map = vector("map");
        assertThrows(IllegalArgumentException.class, () -> Wire.map(java.util.Arrays.copyOf(map, map.length - 1)));
        assertThrows(IllegalArgumentException.class, () -> Wire.map(java.util.Arrays.copyOf(map, map.length + 1)));
        byte[] huge = map.clone();
        huge[12] = (byte) 0x7F;
        byte[] zero = map.clone();
        zero[16] = 0;
        assertThrows(IllegalArgumentException.class, () -> Wire.map(zero));
        assertThrows(IllegalArgumentException.class, () -> Wire.map(huge));
        assertThrows(IllegalArgumentException.class, () -> Wire.hello(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> Wire.encode(new Wire.Hello(1, 0, "x".repeat(Wire.MAX_STRING + 1), List.of())));
    }
}
