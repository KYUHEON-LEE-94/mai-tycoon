package kr.maitycoon;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import kr.maitycoon.protocol.Wire;

/**
 * Life lamps are items: picked up in the inventory and put down on a tool, one is spent and the tool gets a random
 * option. The merchant only sells them and lists the options.
 */
final class Lamps implements Listener {
    private enum Screen { SHOP, OPTIONS }
    private static final class Holder implements InventoryHolder {
        private final Screen screen;
        private boolean reopening;
        private Inventory inventory;
        private Holder(Screen screen) { this.screen = screen; }
        @Override public Inventory getInventory() { return inventory; }
    }
    private static final int BULK = 64, OPTIONS_START = 9, OPTIONS_SIZE = 36, OPTIONS_BACK = 45, STACK = 64;
    private static final String MODEL = "life_lamp";
    private static final Map<Material, Material> CROPS = Map.of(Material.WHEAT, Material.WHEAT, Material.CARROTS, Material.CARROT, Material.POTATOES, Material.POTATO, Material.BEETROOTS, Material.BEETROOT);
    private static final Map<Material, Material> SEEDS = Map.of(Material.WHEAT, Material.WHEAT_SEEDS, Material.CARROTS, Material.CARROT, Material.POTATOES, Material.POTATO, Material.BEETROOTS, Material.BEETROOT_SEEDS);
    private static final Map<Material, Material> SMELTED = Map.of(Material.RAW_IRON, Material.IRON_INGOT, Material.RAW_GOLD, Material.GOLD_INGOT, Material.RAW_COPPER, Material.COPPER_INGOT, Material.ANCIENT_DEBRIS, Material.NETHERITE_SCRAP);
    private final MaiTycoonPlugin plugin;
    private final ProfileStore store;
    private final LifeLamp lamp;
    private final long price;
    private final NamespacedKey key, itemKey;
    private final Set<UUID> chaining = new HashSet<>();
    Lamps(MaiTycoonPlugin plugin, ProfileStore store, LifeLamp lamp, long price) {
        this.plugin = plugin;
        this.store = store;
        this.lamp = lamp;
        this.price = price;
        this.key = new NamespacedKey(plugin, "lamp");
        this.itemKey = new NamespacedKey(plugin, "life_lamp");
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
    ItemStack lampItem(int amount) {
        ItemStack item = new ItemStack(Catalog.ITEM_BASE, amount);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("생활 램프", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Ui.line("인벤토리에서 집어 괭이, 곡괭이, 삽, 도끼 위에 놓으면", NamedTextColor.GRAY),
            Ui.line("1개를 쓰고 무작위 능력을 붙입니다.", NamedTextColor.GRAY), Ui.line("이미 있던 능력은 새 능력으로 바뀝니다.", NamedTextColor.DARK_GRAY)));
        var model = meta.getCustomModelDataComponent();
        model.setStrings(List.of(MODEL));
        meta.setCustomModelDataComponent(model);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }
    boolean isLamp(ItemStack item) { return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE); }
    int carried(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) if (isLamp(item)) count += item.getAmount();
        return count;
    }
    private int room(Player player) {
        int free = 0;
        for (ItemStack slot : player.getInventory().getStorageContents()) {
            if (slot == null || slot.getType().isAir()) free += STACK;
            else if (isLamp(slot)) free += Math.max(0, STACK - slot.getAmount());
        }
        return free;
    }
    /** Hands over lamps the account still holds (older versions, dex rewards, admin gifts) as far as the inventory has room. */
    void deliver(Player player) throws IOException {
        var id = player.getUniqueId();
        Profile profile = store.load(id);
        int count = (int) Math.min(profile.lamps(), room(player));
        if (count < 1 || plugin.tradeHeld(player)) return;
        plugin.transact(player, "lamp-deliver", Integer.toString(count), () -> {
            store.save(id, profile.takeLamps(count));
            player.getInventory().addItem(lampItem(count));
        });
        plugin.tell(player, "생활 램프 " + count + "개를 인벤토리에 넣었습니다." + (profile.lamps() > count ? " 남은 " + (profile.lamps() - count) + "개는 인벤토리를 비우고 다시 접속하면 받습니다." : ""));
    }
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> { if (player.isOnline()) plugin.guarded(player, () -> deliver(player)); }, 20L);
    }
    void open(Player player) throws IOException { show(player, Screen.SHOP); }
    private void show(Player player, Screen screen) throws IOException {
        Holder holder = new Holder(screen);
        Profile profile = store.load(player.getUniqueId());
        boolean drawn = plugin.packDrawn(player);
        Component title = screen == Screen.SHOP ? drawn ? shopTitle(player, profile) : Component.text("램프 상점") : plugin.menuTitle(player, "가능한 능력");
        holder.inventory = plugin.getServer().createInventory(holder, Ui.SIZE, title);
        render(holder, player);
        player.openInventory(holder.inventory);
    }
    private void showLater(Player player, Holder holder) {
        if (holder.reopening) return;
        holder.reopening = true;
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.guarded(player, () -> {
            if (player.getOpenInventory().getTopInventory().getHolder() == holder) show(player, holder.screen);
        }));
    }
    private void showNext(Player player, Screen screen) {
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.guarded(player, () -> show(player, screen)));
    }
    private static String money(long value) { return String.format("%,d", value); }
    private Component shopTitle(Player player, Profile profile) {
        return new ShopTitle().screen(0xE500)
            .value(62, 70, money(carried(player))).label(62, -1, "개")
            .onBlue(true).value(82, 70, money(price)).label(82, -1, "골드").onBlue(false)
            .value(117, 24, money(profile.coins())).label(117, -1, "골드")
            .build();
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
    private void render(Holder holder, Player player) throws IOException {
        plugin.frame(holder.inventory, player);
        Profile profile = store.load(player.getUniqueId());
        if (holder.screen == Screen.SHOP) renderShop(holder, player, profile);
        else renderOptions(holder);
    }
    private void renderShop(Holder holder, Player player, Profile profile) {
        boolean drawn = plugin.packDrawn(player);
        Ui.button(holder.inventory, List.of(MenuLayout.SHOP_ICON), drawn, Ui.decorate(lampItem(1), Ui.line("생활 도구 전용 램프", NamedTextColor.GOLD), List.of(
            Ui.line("인벤토리에 " + money(carried(player)) + "개", NamedTextColor.GRAY), Ui.line("인벤토리에서 램프를 집어 도구 위에 놓으면 씁니다.", NamedTextColor.GRAY))));
        Ui.button(holder.inventory, MenuLayout.SHOP_BUY, drawn, Ui.icon(Material.GOLD_INGOT, Ui.line("구매 · " + money(price) + " 골드", NamedTextColor.AQUA), List.of(
            Ui.line("좌클릭 시 1개 구매", NamedTextColor.GRAY),
            Ui.line("쉬프트+좌클릭 시 " + BULK + "개 구매 · " + money(price * BULK) + " 골드", NamedTextColor.GRAY),
            Ui.line("잔액 " + money(profile.coins()) + " 골드", NamedTextColor.YELLOW))));
        Ui.button(holder.inventory, MenuLayout.SHOP_USE, drawn, Ui.icon(Material.BOOK, Ui.line("능력 목록", NamedTextColor.GREEN), List.of(Ui.line("도구별로 붙을 수 있는 능력을 봅니다.", NamedTextColor.GRAY))));
        Ui.button(holder.inventory, List.of(MenuLayout.SHOP_CLOSE), drawn, Ui.icon(Material.BARRIER, Ui.line("닫기", NamedTextColor.RED), List.of()));
    }
    private void renderOptions(Holder holder) {
        int slot = OPTIONS_START;
        for (LifeLamp.Kind kind : lamp.kinds(null)) {
            if (slot == OPTIONS_START + OPTIONS_SIZE) break;
            holder.inventory.setItem(slot++, Ui.icon(icon(kind), Ui.line(kind.label, NamedTextColor.WHITE), List.of(Ui.line(lamp.range(kind), NamedTextColor.GRAY), Ui.line(kind.tool == null ? "모든 도구 공통" : kind.tool.label + " 전용", NamedTextColor.AQUA))));
        }
        holder.inventory.setItem(OPTIONS_BACK, Ui.icon(Material.ARROW, Ui.line("뒤로", NamedTextColor.WHITE), List.of()));
    }
    static final String ITEM_ID = "LIFE_LAMP";
    /** The purchase itself, shared by the chest screen and the mod. */
    Shop.Outcome tryBuy(Player player, int amount) throws IOException {
        if (player.getGameMode() != GameMode.SURVIVAL && !player.hasPermission("maitycoon.admin")) return Shop.Outcome.refused("농사 보급과 거래는 서바이벌 모드에서 사용할 수 있습니다.");
        if (plugin.tradeHeld(player)) return Shop.Outcome.refused("거래를 완료하지 못했습니다. 아이템과 잔액을 확인해 주세요.");
        if (amount < 1 || amount > STACK) return Shop.Outcome.refused("한 번에 1~" + STACK + "개까지 살 수 있습니다.");
        var id = player.getUniqueId();
        Profile profile = store.load(id);
        long cost = Math.multiplyExact(price, amount);
        if (profile.coins() < cost) return Shop.Outcome.refused("골드가 부족합니다. 필요 " + money(cost) + " / 보유 " + money(profile.coins()));
        if (room(player) < amount) return Shop.Outcome.refused("생활 램프 " + amount + "개를 넣을 인벤토리 공간이 부족합니다.");
        plugin.transact(player, "lamp-buy", amount + " -" + cost, () -> {
            store.save(id, profile.spend(cost));
            player.getInventory().addItem(lampItem(amount));
        });
        plugin.tell(player, "생활 램프 " + amount + "개를 구매했습니다. 인벤토리에서 램프를 집어 도구 위에 놓으면 씁니다.");
        return Shop.Outcome.OK;
    }
    /** The lamp shop for the mod: one product and the list of what a lamp can do. */
    Wire.ShopData snapshot(Player player, int reason) throws IOException {
        List<String> notes = new ArrayList<>(List.of("인벤토리에서 램프를 집어 괭이, 곡괭이, 삽, 도끼 위에 놓으면 1개를 쓰고 무작위 능력이 붙습니다.", "이미 있던 능력은 새 능력으로 바뀝니다.", ""));
        for (LifeLamp.Kind kind : lamp.kinds(null)) notes.add((kind.tool == null ? "공통" : kind.tool.label) + " · " + kind.label + ": " + lamp.range(kind));
        var item = new Wire.ShopItem(ITEM_ID, "생활 램프", "램프", Catalog.ITEM_BASE.getKey().toString(), MODEL, price, 0, 0, 0, carried(player), STACK, List.copyOf(notes.subList(0, Math.min(notes.size(), Wire.MAX_LIST))));
        return new Wire.ShopData(reason, Wire.SHOP_LAMP, "램프 상점", store.load(player.getUniqueId()).coins(), 0, -1, List.of("램프"), List.of(item));
    }
    private void buy(Player player, Holder holder, int amount) throws IOException {
        Shop.Outcome outcome = tryBuy(player, amount);
        if (!outcome.ok()) { plugin.tell(player, outcome.message()); return; }
        render(holder, player);
        showLater(player, holder);
    }
    /** A lamp on the cursor put down on a tool in the player's own inventory. Anything else keeps the normal click. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUse(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack cursor = event.getCursor(), target = event.getCurrentItem();
        if (!isLamp(cursor) || tool(target) == null || event.getClickedInventory() != player.getInventory()) return;
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;
        event.setCancelled(true);
        InventoryView view = event.getView();
        Inventory inventory = event.getClickedInventory();
        int slot = event.getSlot();
        plugin.guarded(player, () -> apply(player, view, inventory, slot, target.clone(), cursor.clone()));
    }
    private void apply(Player player, InventoryView view, Inventory inventory, int slot, ItemStack target, ItemStack cursor) throws IOException {
        if (!plugin.survival(player)) return;
        if (plugin.tradeHeld(player)) { plugin.tell(player, "거래를 완료하지 못했습니다. 아이템과 잔액을 확인해 주세요."); return; }
        LifeLamp.Option before = option(target), rolled = lamp.roll(tool(target), ThreadLocalRandom.current());
        ItemStack tool = target.clone();
        write(tool, rolled);
        ItemStack rest = cursor.getAmount() > 1 ? cursor.asQuantity(cursor.getAmount() - 1) : null;
        plugin.transact(player, "lamp", rolled.encode(), () -> {
            inventory.setItem(slot, tool);
            view.setCursor(rest);
        });
        plugin.tell(player, "생활 램프를 썼습니다. " + (before == null ? "" : "이전: " + before.describe() + " → ") + "새 능력: " + rolled.describe());
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.2f);
    }
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        // The lamp itself may be picked up and used in the player's half of this window.
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            if (event.getClick().isShiftClick() || event.getClick() == ClickType.DOUBLE_CLICK) event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ClickType click = event.getClick();
        int slot = event.getRawSlot();
        if (holder.screen == Screen.SHOP && MenuLayout.SHOP_BUY.contains(slot) && (click == ClickType.LEFT || click == ClickType.SHIFT_LEFT)) {
            plugin.guarded(player, () -> buy(player, holder, click == ClickType.LEFT ? 1 : BULK));
            return;
        }
        if (click != ClickType.LEFT) return;
        if (holder.screen == Screen.SHOP) {
            if (MenuLayout.SHOP_USE.contains(slot)) showNext(player, Screen.OPTIONS);
            else if (slot == MenuLayout.SHOP_CLOSE) plugin.getServer().getScheduler().runTask(plugin, () -> player.closeInventory());
        } else if (slot == OPTIONS_BACK) showNext(player, Screen.SHOP);
    }
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) return;
        int top = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < top)) event.setCancelled(true);
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
