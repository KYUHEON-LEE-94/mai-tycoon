package kr.maitycoon;

import java.util.*;
import java.util.regex.Pattern;
import org.bukkit.Material;

/** Fills the collection book with everything a survival player can actually obtain. */
final class DexItems {
    private static final Pattern UNOBTAINABLE = Pattern.compile(
        "COMMAND_BLOCK.*|CHAIN_COMMAND_BLOCK|REPEATING_COMMAND_BLOCK|STRUCTURE_.*|JIGSAW|BARRIER|LIGHT|BEDROCK|DEBUG_STICK|KNOWLEDGE_BOOK|.*_SPAWN_EGG"
        + "|PETRIFIED_OAK_SLAB|SPAWNER|TRIAL_SPAWNER|VAULT|REINFORCED_DEEPSLATE|END_PORTAL_FRAME|BUDDING_AMETHYST|FARMLAND|DIRT_PATH|PLAYER_HEAD"
        + "|INFESTED_.*|FROGSPAWN|TEST_.*|BUNDLE|.*_BUNDLE|ENCHANTED_BOOK|POTION|SPLASH_POTION|LINGERING_POTION|TIPPED_ARROW|FILLED_MAP|WRITTEN_BOOK|FIREWORK_STAR");
    private DexItems() {}
    static boolean obtainable(String id) { return !UNOBTAINABLE.matcher(id).matches(); }
    /** Pages for every item the existing pages do not list. */
    @SuppressWarnings("deprecation")
    static Map<String, List<String>> remaining(Map<String, List<String>> pages) {
        Set<String> listed = new HashSet<>();
        pages.values().forEach(listed::addAll);
        List<String> blocks = new ArrayList<>(), food = new ArrayList<>(), other = new ArrayList<>();
        for (Material material : Material.values()) {
            if (material.isLegacy() || !material.isItem() || material.isAir() || listed.contains(material.name()) || !obtainable(material.name())) continue;
            (material.isEdible() ? food : material.isBlock() ? blocks : other).add(material.name());
        }
        Map<String, List<String>> added = new LinkedHashMap<>();
        added.put("블록", blocks);
        added.put("음식", food);
        added.put("기타", other);
        return added;
    }
}
