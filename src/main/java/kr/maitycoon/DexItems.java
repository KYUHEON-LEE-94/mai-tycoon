package kr.maitycoon;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.*;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.EntityType;

/** Fills the collection book with everything a survival player can obtain, kill or visit. */
final class DexItems {
    static final String MOB = "MOB:", BIOME = "BIOME:";
    private static final Pattern UNOBTAINABLE = Pattern.compile(
        "COMMAND_BLOCK.*|CHAIN_COMMAND_BLOCK|REPEATING_COMMAND_BLOCK|STRUCTURE_.*|JIGSAW|BARRIER|LIGHT|BEDROCK|DEBUG_STICK|KNOWLEDGE_BOOK|.*_SPAWN_EGG"
        + "|PETRIFIED_OAK_SLAB|SPAWNER|TRIAL_SPAWNER|VAULT|REINFORCED_DEEPSLATE|END_PORTAL_FRAME|BUDDING_AMETHYST|FARMLAND|DIRT_PATH|PLAYER_HEAD"
        + "|INFESTED_.*|FROGSPAWN|TEST_.*|BUNDLE|.*_BUNDLE|ENCHANTED_BOOK|POTION|SPLASH_POTION|LINGERING_POTION|TIPPED_ARROW|FILLED_MAP|WRITTEN_BOOK|FIREWORK_STAR");
    private static final Pattern GEAR = Pattern.compile(".*_(SWORD|HELMET|CHESTPLATE|LEGGINGS|BOOTS|SPEAR|HORSE_ARMOR)|BOW|CROSSBOW|TRIDENT|SHIELD|MACE|ELYTRA|ARROW|SPECTRAL_ARROW|WOLF_ARMOR|TURTLE_HELMET");
    private static final Pattern TOOLS = Pattern.compile(".*_(PICKAXE|SHOVEL|AXE|HOE)|FISHING_ROD|SHEARS|FLINT_AND_STEEL|BRUSH|COMPASS|RECOVERY_COMPASS|CLOCK|SPYGLASS|BUCKET|.*_BUCKET|LEAD|NAME_TAG|MAP");
    private static final Pattern COLORED = Pattern.compile(".*_(WOOL|CONCRETE|CONCRETE_POWDER|TERRACOTTA|GLAZED_TERRACOTTA|STAINED_GLASS|STAINED_GLASS_PANE|SHULKER_BOX|DYE)");
    private static final Pattern DECOR = Pattern.compile(".*_(BED|CARPET|BANNER|CANDLE|SIGN|HANGING_SIGN|POTTERY_SHERD|ARMOR_TRIM_SMITHING_TEMPLATE)|CANDLE|PAINTING|ITEM_FRAME|GLOW_ITEM_FRAME|FLOWER_POT|ARMOR_STAND|DECORATED_POT|LANTERN|SOUL_LANTERN|BELL");
    private static final Pattern PLANTS = Pattern.compile(".*_(SAPLING|LEAVES|PROPAGULE|MUSHROOM|ROOTS|FUNGUS|TULIP|VINES|VINE)|VINE|SHORT_GRASS|TALL_GRASS|FERN|LARGE_FERN|DEAD_BUSH|LILY_PAD|MOSS_BLOCK|MOSS_CARPET|AZALEA|FLOWERING_AZALEA|BIG_DRIPLEAF|SMALL_DRIPLEAF|SPORE_BLOSSOM|SEAGRASS|SEA_PICKLE|.*_SEEDS|PITCHER_POD");
    private static final Set<String> NOT_COLLECTED = Set.of("PLAYER", "ARMOR_STAND", "MANNEQUIN", "GIANT", "ILLUSIONER", "ZOMBIE_HORSE", "COPPER_GOLEM");
    private DexItems() {}
    static boolean obtainable(String id) { return !UNOBTAINABLE.matcher(id).matches(); }
    /** Which page an item belongs on. Order matters: the first match wins. */
    @SuppressWarnings("deprecation")
    static String pageOf(Material material) {
        String id = material.name();
        if (GEAR.matcher(id).matches()) return "장비";
        if (TOOLS.matcher(id).matches()) return "도구";
        if (material.isEdible()) return "음식";
        if (COLORED.matcher(id).matches()) return "색상";
        if (DECOR.matcher(id).matches()) return "장식";
        if (Tag.FLOWERS.isTagged(material) || PLANTS.matcher(id).matches()) return "식물";
        return material.isBlock() ? "건축" : "기타";
    }
    /** Pages for every item the existing pages do not list, plus the kill and visit collections. */
    @SuppressWarnings("deprecation")
    static Map<String, List<String>> remaining(Map<String, List<String>> pages) {
        Set<String> listed = new HashSet<>();
        pages.values().forEach(listed::addAll);
        Map<String, List<String>> added = new LinkedHashMap<>();
        for (String page : List.of("건축", "색상", "장식", "식물", "음식", "장비", "도구", "기타")) added.put(page, new ArrayList<>());
        for (Material material : Material.values()) {
            if (material.isLegacy() || !material.isItem() || material.isAir() || listed.contains(material.name()) || !obtainable(material.name())) continue;
            added.get(pageOf(material)).add(material.name());
        }
        List<String> mobs = new ArrayList<>(), biomes = new ArrayList<>();
        for (EntityType type : EntityType.values()) {
            if (type.isAlive() && type.isSpawnable() && !NOT_COLLECTED.contains(type.name())) mobs.add(MOB + type.name());
        }
        RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME).forEach(biome -> {
            var key = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME).getKey(biome);
            if (key != null && !key.value().equals("the_void")) biomes.add(BIOME + key.value());
        });
        Collections.sort(biomes);
        added.put("몬스터", mobs);
        added.put("바이옴", biomes);
        return added;
    }
    static Component name(String id) {
        if (id.startsWith(MOB)) return Component.translatable(EntityType.valueOf(id.substring(MOB.length())).translationKey());
        return Component.translatable("biome.minecraft." + id.substring(BIOME.length()));
    }
    static Material icon(String id) {
        if (id.startsWith(BIOME)) return Material.GRASS_BLOCK;
        Material egg = Material.matchMaterial(id.substring(MOB.length()) + "_SPAWN_EGG");
        return egg == null ? Material.SKELETON_SKULL : egg;
    }
}
