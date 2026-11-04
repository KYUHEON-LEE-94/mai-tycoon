"""Writes vectors/*.hex from PROTOCOL.md alone, independent of either Java codec."""
import struct, pathlib

def s(text):
    b = text.encode("utf-8")
    return struct.pack(">H", len(b)) + b

def strs(items):
    return struct.pack(">B", len(items)) + b"".join(s(i) for i in items)

out = pathlib.Path(__file__).with_name("vectors")
out.mkdir(exist_ok=True)
vectors = {
    "hello": struct.pack(">HH", 1, 2) + s("0.4.0") + strs(["full_map", "shop"]),
    "hello_ack": struct.pack(">BHH", 1, 2, 0) + s("클라이언트를 업데이트해 주세요") + strs([]),
    "map_request": struct.pack(">i", -7),
    "map": struct.pack(">iiiHHB", 42, -3, 5, 2, 2, 8)
        + struct.pack(">iiii", 0x7FB238, -1, 0x000000, 0xFFFFFF)
        + bytes([0, 1, 2, 3])
        + struct.pack(">ii", -40, 88)
        + struct.pack(">B", 2) + s("maizurzu") + struct.pack(">ii", 120, -5) + s("가나") + struct.pack(">ii", -2147483648, 2147483647),
}
def item(id, name, cat, mat, model, price, change, floor, ceil, owned, stack, notes):
    return s(id) + s(name) + s(cat) + s(mat) + s(model) + struct.pack(">qqqqiH", price, change, floor, ceil, owned, stack) + strs(notes)
vectors["shop"] = (struct.pack(">BB", 0, 0) + s("농산물 상점") + struct.pack(">qii", 5_000_000_000, 7, 125) + strs(["전체", "곡물"]) + struct.pack(">H", 2)
    + item("WHEAT", "밀", "곡물", "minecraft:wheat", "", 55, -5, 30, 90, 12, 64, ["시세 ▁▃▇"])
    + item("tomato", "토마토", "특산", "minecraft:paper", "tomato", 120, 0, 60, 180, 0, 64, []))
vectors["shop_request"] = struct.pack(">iBB", 9, 1, 0) + s("WHEAT_SEEDS") + struct.pack(">ii", 16, 3)
vectors["shop_result"] = struct.pack(">iB", 9, 2) + s("시세가 바뀌었습니다")
vectors["map_v1_0"] = vectors["map"][:16] + vectors["map"][17:]
for name, data in vectors.items():
    (out / (name + ".hex")).write_text(data.hex() + "\n")
    print(name, len(data))
