package kr.maitycoon;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;

/** Applies life lamps to tools and pays out their options. */
final class Lamps implements Listener {
    private static final class Holder implements InventoryHolder {
        private final int slot;
        private boolean used;
        private Inventory inventory;
        private Holder(int slot) { this.slot = slot; }
        @Override public Inventory getInventory() { return inventory; }
    }
    private static final int TARGET = 10, CURRENT = 13, STOCK = 16, LIST_START = 27, LIST_SIZE = 18;
    private static final List<Integer> BUY = List.of(45, 46, 47), YES = List.of(48, 49, 50), NO = List.of(51, 52, 53);
    private static final Map<Material, Material> CROPS = Map.of(Material.WHEAT, Material.WHEAT, Material.CARROTS, Material.CARROT, Material.POTATOES, Material.POTATO, Material.BEETROOTS, Material.BEETROOT);
    private static final Map<Material, Material> SEEDS = Map.of(Material.WHEAT, Material.WHEAT_SEEDS, Material.CARROTS, Material.CARROT, Material.POTATOES, Material.POTATO, Material.BEETROOTS, Material.BEETROOT_SEEDS);
    private static final Map<Material, Material> SMELTED = Map.of(Material.RAW_IRON, Material.IRON_INGOT, Material.RAW_GOLD, Material.GOLD_INGOT, Material.RAW_COPPER, Material.COPPER_INGOT, Material.ANCIENT_DEBRIS, Material.NETHERITE_SCRAP);
    private final MaiTycoonPlugin plugin;
    private final ProfileStore store;
    private final LifeLamp lamp;
    private final long price;
    private final NamespacedKey key;
    private final Set<UUID> chaining = new HashSet<>();
    Lamps(MaiTycoonPlugin plugin, ProfileStore store, LifeLamp lamp, long price) {
        this.plugin = plugin;
        this.store = store;
        this.lamp = lamp;
        this.price = price;
        this.key = new NamespacedKey(plugin, "lamp");
    }
    private static LifeLamp.Tool tool(ItemStack item) { return item == null ? null : LifeLamp.Tool.of(item.getType().name()); }
    LifeLamp.Option option(ItemStack item) {
        if (tool(item) == null || !item.hasItemMeta()) return null;
        return LifeLamp.Option.decode(item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING));
    }
    void write(ItemStack item, LifeLamp.Option option) {
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, option.encode());
        meta.lore(List.of(Ui.line("생활 램프", NamedTextColor.GOLD), Ui.line(option.describe(), NamedTextColor.YELLOW)));
        item.setItemMeta(meta);
    }
    /** Opens with or without a tool or lamps, because this is also where lamps are bought. */
    void open(Player player) throws IOException {
        Holder holder = new Holder(player.getInventory().getHeldItemSlot());
        holder.inventory = plugin.getServer().createInventory(holder, Ui.SIZE, plugin.packDrawn(player) ? new ShopTitle().lamp(price).build() : Component.text("생활 램프"));
        render(holder, player);
        player.openInventory(holder.inventory);
    }
    private static Material icon(LifeLamp.Kind kind) {
        return switch (kind) {
            case CROP -> Material.WHEAT;
            case SEED -> Material.WHEAT_SEEDS;
            case BONE_MEAL -> Material.BONE_MEAL;
            case EXP -> Material.EXPERIENCE_BOTTLE;
            case CHICKEN -> Material.FEATHER;
            case RABBIT -> Material.RABBIT_HIDE;
            case PIG -> Material.PORKCHOP;
            case SHEEP -> Material.WHITE_WOOL;
            case COW -> Material.LEATHER;
            case MONSTER -> Material.ROTTEN_FLESH;
            case SMELT -> Material.IRON_INGOT;
            case AREA -> Material.STONE;
            case ORE -> Material.RAW_IRON;
            case GLASS -> Material.GLASS;
            case TREE -> Material.OAK_LOG;
        };
    }
    private void button(Holder holder, boolean drawn, List<Integer> slots, ItemStack icon) {
        // The drawn button has its label beside the icon, so only the plain screen fills the whole width.
        for (int slot : drawn ? slots.subList(0, 1) : slots) holder.inventory.setItem(slot, icon);
    }
    private void render(Holder holder, Player player) throws IOException {
        plugin.frame(holder.inventory, player);
        boolean drawn = plugin.packDrawn(player);
        ItemStack hand = player.getInventory().getItemInMainHand();
        Profile profile = store.load(player.getUniqueId());
        LifeLamp.Tool tool = tool(hand);
        LifeLamp.Option current = option(hand);
        if (tool == null) holder.inventory.setItem(TARGET, Ui.icon(Material.BARRIER, Ui.line("도구를 손에 들고 여세요", NamedTextColor.RED), List.of(Ui.line("괭이, 곡괭이, 삽, 도끼에 쓸 수 있습니다.", NamedTextColor.GRAY), Ui.line("램프 구매는 도구 없이도 됩니다.", NamedTextColor.GRAY))));
        else holder.inventory.setItem(TARGET, Ui.decorate(hand.clone(), null, List.of(Ui.line("이 도구에 램프를 씁니다.", NamedTextColor.GRAY))));
        holder.inventory.setItem(CURRENT, Ui.icon(current == null ? Material.PAPER : Material.ENCHANTED_BOOK, Ui.line(current == null ? "옵션 없음" : current.kind().label, current == null ? NamedTextColor.GRAY : NamedTextColor.YELLOW), current == null ? List.of() : List.of(Ui.line(current.describe(), NamedTextColor.GRAY))));
        ItemStack stock = Ui.icon(Material.GLOWSTONE_DUST, Ui.line("보유 램프 " + profile.lamps() + "개", NamedTextColor.GOLD), List.of(Ui.line("적용할 때 1개를 씁니다.", NamedTextColor.GRAY)));
        stock.setAmount((int) Math.clamp(profile.lamps(), 1L, 64L));
        holder.inventory.setItem(STOCK, stock);
        int slot = LIST_START;
        for (LifeLamp.Kind kind : lamp.kinds(tool)) {
            if (slot == LIST_START + LIST_SIZE) break;
            holder.inventory.setItem(slot++, Ui.icon(icon(kind), Ui.line(kind.label, NamedTextColor.WHITE), List.of(Ui.line(lamp.range(kind), NamedTextColor.GRAY), Ui.line(kind.tool == null ? "모든 도구 공통" : kind.tool.label + " 전용", NamedTextColor.AQUA))));
        }
        button(holder, drawn, BUY, Ui.icon(Material.GOLD_INGOT, Ui.line("램프 1개 구매", NamedTextColor.GOLD), List.of(
            Ui.line(price + " 골드", NamedTextColor.YELLOW), Ui.line("보유 " + profile.coins() + " 골드", NamedTextColor.GRAY))));
        button(holder, drawn, YES, Ui.icon(Material.LIME_CONCRETE, Ui.line("적용", NamedTextColor.GREEN), List.of(
            Ui.line("능력은 이 도구가 받을 수 있는 것 중 무작위로 정해집니다.", NamedTextColor.GRAY),
            Ui.line(current == null ? "도구 하나에 능력 하나가 붙습니다." : "기존 능력은 새 능력으로 바뀝니다.", current == null ? NamedTextColor.GRAY : NamedTextColor.RED))));
        button(holder, drawn, NO, Ui.icon(Material.RED_CONCRETE, Ui.line("닫기", NamedTextColor.RED), List.of()));
    }
    private void buy(Player player, Holder holder) throws IOException {
        if (!plugin.survival(player)) return;
        if (plugin.tradeHeld(player)) { plugin.tell(player, "거래를 완료하지 못했습니다. 아이템과 잔액을 확인해 주세요."); return; }
        var id = player.getUniqueId();
        Profile profile = store.load(id);
        if (profile.coins() < price) { plugin.tell(player, "골드가 부족합니다. 필요 " + price + " / 보유 " + profile.coins()); return; }
        plugin.transact(player, "lamp-buy", "-" + price, () -> store.save(id, profile.spend(price).addLamps(1)));
        plugin.tell(player, "생활 램프를 구매했습니다. 보유 " + (profile.lamps() + 1) + "개");
        render(holder, player);
    }
    private void apply(Player player, Holder holder) throws IOException {
        if (holder.used) return;
        holder.used = true;
        ItemStack hand = player.getInventory().getItemInMainHand();
        LifeLamp.Tool held = tool(hand);
        var id = player.getUniqueId();
        Profile profile = store.load(id);
        if (plugin.tradeHeld(player)) { plugin.tell(player, "거래를 완료하지 못했습니다. 아이템과 잔액을 확인해 주세요."); return; }
        if (player.getInventory().getHeldItemSlot() != holder.slot || held == null || profile.lamps() < 1) { plugin.tell(player, "도구나 램프가 바뀌어 적용하지 않았습니다."); return; }
        LifeLamp.Option rolled = lamp.roll(held, ThreadLocalRandom.current());
        plugin.transact(player, "lamp", rolled.encode(), () -> {
            store.save(id, profile.useLamp());
            write(hand, rolled);
        });
        plugin.tell(player, "생활 램프를 적용했습니다. " + rolled.describe());
    }
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClick() != ClickType.LEFT) return;
        int slot = event.getRawSlot();
        if (BUY.contains(slot)) { plugin.guarded(player, () -> buy(player, holder)); return; }
        if (YES.contains(slot)) plugin.guarded(player, () -> apply(player, holder));
        else if (!NO.contains(slot)) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> player.closeInventory());
    }
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }
    /** Only a direct, uncancelled harvest of a grown crop pays out. Drops stay in the world like the normal ones. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHarvest(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!CROPS.containsKey(block.getType()) || Lands.immature(block)) return;
        harvest(event.getPlayer(), block, new ItemStack(CROPS.get(block.getType())), new ItemStack(SEEDS.get(block.getType())));
    }
    /** Called for any fully grown crop; crop and seed are what a CROP or SEED option adds. */
    void harvest(Player player, Block block, ItemStack crop, ItemStack seed) {
        if (player.getGameMode() == GameMode.CREATIVE || plugin.tractorHarvesting()) return;
        LifeLamp.Option option = option(player.getInventory().getItemInMainHand());
        if (option == null) return;
        ItemStack drop = switch (option.kind()) {
            case CROP -> crop;
            case SEED -> seed;
            case BONE_MEAL -> new ItemStack(Material.BONE_MEAL);
            default -> null;
        };
        int bonus = drop == null ? 0 : option.bonus(ThreadLocalRandom.current());
        if (bonus > 0) block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), drop.asQuantity(bonus));
    }
    /** Blocks an AREA or TREE option takes along. Protection still decides each one, because breakBlock fires its own event. */
    private List<Block> chain(Player player, Block block, LifeLamp.Option option, ItemStack tool) {
        List<Block> targets = new ArrayList<>();
        if (option.kind() == LifeLamp.Kind.AREA && block.isPreferredTool(tool)) {
            BlockFace facing = player.getFacing();
            for (int[] offset : LifeLamp.areaOffsets(option.amount(), facing.getModX(), facing.getModZ())) {
                Block next = block.getRelative(offset[0], offset[1], offset[2]);
                if (!next.isEmpty() && next.getType().getHardness() >= 0 && next.isPreferredTool(tool)) targets.add(next);
            }
        } else if (option.kind() == LifeLamp.Kind.TREE && Tag.LOGS.isTagged(block.getType())) {
            Block next = block.getRelative(BlockFace.UP);
            for (int i = 0; i < option.amount() && Tag.LOGS.isTagged(next.getType()); i++, next = next.getRelative(BlockFace.UP)) targets.add(next);
        }
        return targets.stream().filter(plugin::wild).toList();
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMine(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        LifeLamp.Option option = option(tool);
        if (option == null || player.getGameMode() == GameMode.CREATIVE || !chaining.add(player.getUniqueId())) return;
        try {
            for (Block next : chain(player, event.getBlock(), option, tool)) {
                if (player.getInventory().getItemInMainHand().getType() != tool.getType()) break;
                player.breakBlock(next);
            }
        } finally { chaining.remove(player.getUniqueId()); }
    }
    private static boolean ore(Material type) { return type.name().endsWith("_ORE") || type == Material.ANCIENT_DEBRIS; }
    @EventHandler(ignoreCancelled = true)
    public void onDrops(BlockDropItemEvent event) {
        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        LifeLamp.Option option = option(tool);
        if (option == null || event.getItems().isEmpty()) return;
        Material broken = event.getBlockState().getType();
        boolean silk = tool.containsEnchantment(Enchantment.SILK_TOUCH);
        switch (option.kind()) {
            case SMELT -> {
                if (!silk) event.getItems().forEach(item -> replace(item, SMELTED.get(item.getItemStack().getType())));
            }
            case GLASS -> {
                if (broken == Material.SAND || broken == Material.RED_SAND) event.getItems().forEach(item -> replace(item, Material.GLASS));
            }
            case ORE -> {
                int bonus = !silk && ore(broken) ? option.bonus(ThreadLocalRandom.current()) : 0;
                if (bonus > 0) event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation().add(0.5, 0.5, 0.5), event.getItems().get(0).getItemStack().asQuantity(bonus));
            }
            default -> {}
        }
    }
    private static void replace(Item item, Material type) {
        if (type != null) item.setItemStack(new ItemStack(type, item.getItemStack().getAmount()));
    }
    private static LifeLamp.Kind loot(Entity entity) {
        return switch (entity.getType()) {
            case CHICKEN -> LifeLamp.Kind.CHICKEN;
            case RABBIT -> LifeLamp.Kind.RABBIT;
            case PIG -> LifeLamp.Kind.PIG;
            case SHEEP -> LifeLamp.Kind.SHEEP;
            case COW -> LifeLamp.Kind.COW;
            default -> entity instanceof Enemy ? LifeLamp.Kind.MONSTER : null;
        };
    }
    @EventHandler(ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || event.getDrops().isEmpty()) return;
        LifeLamp.Option option = option(killer.getInventory().getItemInMainHand());
        if (option == null || option.kind() != loot(event.getEntity())) return;
        int bonus = option.bonus(ThreadLocalRandom.current());
        if (bonus > 0) event.getDrops().add(event.getDrops().get(ThreadLocalRandom.current().nextInt(event.getDrops().size())).asQuantity(bonus));
    }
    @EventHandler
    public void onExp(PlayerExpChangeEvent event) {
        LifeLamp.Option option = option(event.getPlayer().getInventory().getItemInMainHand());
        if (option != null && option.kind() == LifeLamp.Kind.EXP && event.getAmount() > 0) event.setAmount(event.getAmount() + option.bonus(ThreadLocalRandom.current()));
    }
}
