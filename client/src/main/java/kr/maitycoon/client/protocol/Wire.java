package kr.maitycoon.client.protocol;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The bytes of protocol/PROTOCOL.md. The server and the client each keep their own copy of this file on purpose:
 * neither build depends on the other, and both are checked against protocol/vectors.
 */
public final class Wire {
    public static final int MAJOR = 1, MINOR = 1, MAX_STRING = 256, MAX_LIST = 32, MAX_SIDE = 128;
    public static final String HELLO = "maitycoon:hello", HELLO_ACK = "maitycoon:hello_ack", MAP_REQUEST = "maitycoon:map_request", MAP = "maitycoon:map";
    public static final String FULL_MAP = "full_map";
    public static final int OK = 0, CLIENT_OLD = 1, SERVER_OLD = 2;
    public static final int NONE = 0, MINE = 1, TAKEN = 2, VILLAGE = 3, UNKNOWN = -1;
    public record Hello(int major, int minor, String modVersion, List<String> features) {}
    public record HelloAck(int status, int major, int minor, String message, List<String> features) {}
    public record MapRequest(int requestId) {}
    public record Mark(String name, int x, int z) {}
    /** Cells are cellBlocks wide; origin is the north-west cell in cell units. Protocol 1.0 had no cellBlocks and always meant 16. */
    public record MapData(int requestId, int originX, int originZ, int width, int height, int cellBlocks, int[] colors, byte[] overlays, int selfX, int selfZ, List<Mark> players) {}
    public static final int LEGACY_CELL = 16;
    private Wire() {}

    private static final class Out {
        private ByteBuffer buffer = ByteBuffer.allocate(256);
        private void room(int bytes) {
            if (buffer.remaining() >= bytes) return;
            ByteBuffer bigger = ByteBuffer.allocate(Math.max(buffer.capacity() * 2, buffer.position() + bytes));
            buffer.flip();
            bigger.put(buffer);
            buffer = bigger;
        }
        Out u8(int value) { range(value, 0xFF); room(1); buffer.put((byte) value); return this; }
        Out u16(int value) { range(value, 0xFFFF); room(2); buffer.putShort((short) value); return this; }
        Out i32(int value) { room(4); buffer.putInt(value); return this; }
        Out str(String value) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_STRING) throw new IllegalArgumentException("String too long");
            u16(bytes.length);
            room(bytes.length);
            buffer.put(bytes);
            return this;
        }
        Out strs(List<String> values) {
            if (values.size() > MAX_LIST) throw new IllegalArgumentException("List too long");
            u8(values.size());
            for (String value : values) str(value);
            return this;
        }
        byte[] bytes() { byte[] out = new byte[buffer.position()]; buffer.flip(); buffer.get(out); return out; }
        private static void range(int value, int max) { if (value < 0 || value > max) throw new IllegalArgumentException("Out of range " + value); }
    }
    private static final class In {
        private final ByteBuffer buffer;
        In(byte[] bytes) { buffer = ByteBuffer.wrap(bytes); }
        int u8() { return buffer.get() & 0xFF; }
        int u16() { return buffer.getShort() & 0xFFFF; }
        int i32() { return buffer.getInt(); }
        String str() {
            int length = u16();
            if (length > MAX_STRING) throw new IllegalArgumentException("String too long");
            byte[] bytes = new byte[length];
            buffer.get(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }
        List<String> strs() {
            int count = count();
            List<String> values = new ArrayList<>(count);
            for (int i = 0; i < count; i++) values.add(str());
            return List.copyOf(values);
        }
        int count() {
            int count = u8();
            if (count > MAX_LIST) throw new IllegalArgumentException("List too long");
            return count;
        }
        void end() { if (buffer.hasRemaining()) throw new IllegalArgumentException("Trailing bytes"); }
    }
    /** Every decoder fails the same way, so a caller only needs to catch one exception. */
    private interface Reader<T> { T read(In in); }
    private static <T> T decode(byte[] bytes, Reader<T> reader) {
        try {
            In in = new In(bytes);
            T value = reader.read(in);
            in.end();
            return value;
        } catch (BufferUnderflowException e) { throw new IllegalArgumentException("Message too short", e); }
    }

    public static byte[] encode(Hello hello) { return new Out().u16(hello.major()).u16(hello.minor()).str(hello.modVersion()).strs(hello.features()).bytes(); }
    public static Hello hello(byte[] bytes) { return decode(bytes, in -> new Hello(in.u16(), in.u16(), in.str(), in.strs())); }
    public static byte[] encode(HelloAck ack) { return new Out().u8(ack.status()).u16(ack.major()).u16(ack.minor()).str(ack.message()).strs(ack.features()).bytes(); }
    public static HelloAck helloAck(byte[] bytes) { return decode(bytes, in -> new HelloAck(in.u8(), in.u16(), in.u16(), in.str(), in.strs())); }
    public static byte[] encode(MapRequest request) { return new Out().i32(request.requestId()).bytes(); }
    public static MapRequest mapRequest(byte[] bytes) { return decode(bytes, in -> new MapRequest(in.i32())); }
    public static byte[] encode(MapData map) { return encode(map, false); }
    /** legacy writes the 1.0 layout for a client that only speaks 1.0; its cells must then be 16 blocks. */
    public static byte[] encode(MapData map, boolean legacy) {
        int cells = map.width() * map.height();
        if (map.width() < 1 || map.height() < 1 || map.width() > MAX_SIDE || map.height() > MAX_SIDE || map.colors().length != cells || map.overlays().length != cells || map.players().size() > MAX_LIST
            || map.cellBlocks() < 1 || map.cellBlocks() > 255 || (legacy && map.cellBlocks() != LEGACY_CELL)) {
            throw new IllegalArgumentException("Bad map");
        }
        Out out = new Out().i32(map.requestId()).i32(map.originX()).i32(map.originZ()).u16(map.width()).u16(map.height());
        if (!legacy) out.u8(map.cellBlocks());
        for (int color : map.colors()) out.i32(color);
        for (byte overlay : map.overlays()) out.u8(overlay & 0xFF);
        out.i32(map.selfX()).i32(map.selfZ()).u8(map.players().size());
        for (Mark mark : map.players()) out.str(mark.name()).i32(mark.x()).i32(mark.z());
        return out.bytes();
    }
    public static MapData map(byte[] bytes) { return map(bytes, false); }
    public static MapData map(byte[] bytes, boolean legacy) {
        return decode(bytes, in -> {
            int requestId = in.i32(), originX = in.i32(), originZ = in.i32(), width = in.u16(), height = in.u16(), cellBlocks = legacy ? LEGACY_CELL : in.u8();
            if (cellBlocks < 1) throw new IllegalArgumentException("Bad cell size");
            if (width < 1 || height < 1 || width > MAX_SIDE || height > MAX_SIDE) throw new IllegalArgumentException("Bad size");
            int[] colors = new int[width * height];
            for (int i = 0; i < colors.length; i++) colors[i] = in.i32();
            byte[] overlays = new byte[width * height];
            for (int i = 0; i < overlays.length; i++) {
                int overlay = in.u8();
                if (overlay > VILLAGE) throw new IllegalArgumentException("Bad overlay");
                overlays[i] = (byte) overlay;
            }
            int selfX = in.i32(), selfZ = in.i32(), count = in.count();
            List<Mark> players = new ArrayList<>(count);
            for (int i = 0; i < count; i++) players.add(new Mark(in.str(), in.i32(), in.i32()));
            return new MapData(requestId, originX, originZ, width, height, cellBlocks, colors, overlays, selfX, selfZ, List.copyOf(players));
        });
    }
}
