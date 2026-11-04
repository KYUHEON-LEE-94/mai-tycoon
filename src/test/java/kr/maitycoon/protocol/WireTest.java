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
        assertEquals(new Wire.Hello(1, 1, "0.3.0", List.of("full_map")), hello);
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
