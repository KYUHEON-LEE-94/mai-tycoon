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
    private enum Screen { SHOP, USE, OPTIONS }
    private static final class Holder implements InventoryHolder {
        private final Screen screen;
        private final int slot;
        private final LifeLamp.Option previous;
        private final long readyAt;
        private boolean used, reopening;
        private Inventory inventory;
        private Holder(Screen screen, int slot, LifeLamp.Option previous, long readyAt) {
            this.screen = screen;
            this.slot = slot;
            this.previous = previous;
            this.readyAt = readyAt;
        }
        @Override public Inventory getInventory() { return inventory; }
    }
    private static final int BULK = 64, OPTIONS_START = 9, OPTIONS_SIZE = 36, OPTIONS_BACK = 45;
    private static final long RETRY_MILLIS = 400;
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
    /** The merchant's shop. Buying needs no tool; using a lamp is a screen of its own. */
    void open(Player player) throws IOException { show(player, Screen.SHOP, null, 0); }
    private void show(Player player, Screen screen, LifeLamp.Option previous, long readyAt) throws IOException {
        Holder holder = new Holder(screen, player.getInventory().getHeldItemSlot(), previous, readyAt);
        Profile profile = store.load(player.getUniqueId());
        boolean drawn = plugin.packDrawn(player);
        Component title = switch (screen) {
            case SHOP -> drawn ? shopTitle(profile) : Component.text("램프 상점");
            case USE -> drawn ? useTitle(player, profile, previous) : Component.text("생활 램프");
            case OPTIONS -> plugin.menuTitle(player, "가능한 능력");
        };
        holder.inventory = plugin.getServer().createInventory(holder, Ui.SIZE, title);
        render(holder, player);
        player.openInventory(holder.inventory);
    }
    private void showLater(Player player, Holder holder, LifeLamp.Option previous, long readyAt) {
        if (holder.reopening) return;
        holder.reopening = true;
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.guarded(player, () -> {
            if (player.getOpenInventory().getTopInventory().getHolder() == holder) show(player, holder.screen, previous, readyAt);
        }));
    }
    private void showNext(Player player, Screen screen) {
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.guarded(player, () -> show(player, screen, null, 0)));
    }
    private static String money(long value) { return String.format("%,d", value); }
    private Component shopTitle(Profile profile) {
        return new ShopTitle().screen(0xE500)
            .value(62, 18, money(profile.lamps())).label(62, -1, "개")
            .onBlue(true).value(62, 76, money(price)).label(62, -1, "골드").onBlue(false)
            .value(117, 24, money(profile.coins())).label(117, -1, "골드")
            .value(117, 82, "1/1").build();
    }
    private void describe(ShopTitle title, int y, LifeLamp.Option option) {
        title.label(y, 44, "KIND_" + option.kind());
        if (option.kind() == LifeLamp.Kind.AREA) title.back(40).value(y, -1, option.amount() == 2 ? "+2" : "+4");
        else if (option.percent() < 100) title.back(40).value(y, -1, option.percent() + "% +" + option.amount());
        else if (option.kind() == LifeLamp.Kind.TREE) title.back(40).value(y, -1, "+" + option.amount());
    }
    private Component useTitle(Player player, Profile profile, LifeLamp.Option previous) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        LifeLamp.Option current = option(hand);
        ShopTitle title = new ShopTitle().screen(0xE700);
        title.label(42, 30, tool(hand) == null ? "도구 없음" : hand.getType().name());
        if (current == null) title.label(62, 44, "없음");
        else describe(title, 62, current);
        if (previous != null) describe(title, 72, previous);
        title.value(97, 126, money(profile.lamps())).label(97, -1, "개");
        if (tool(hand) == null || profile.lamps() < 1) title.dimmed(MenuLayout.slotX(MenuLayout.USE_APPLY.get(0)) - 1, true);
        return title.build();
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
        switch (holder.screen) {
            case SHOP -> renderShop(holder, player, profile);
            case USE -> renderUse(holder, player, profile);
            case OPTIONS -> renderOptions(holder, player);
        }
    }
    private void renderShop(Holder holder, Player player, Profile profile) {
        boolean drawn = plugin.packDrawn(player);
        holder.inventory.setItem(MenuLayout.SHOP_ICON, Ui.icon(Material.GLOWSTONE_DUST, Ui.line("생활 도구 전용 램프", NamedTextColor.GOLD), List.of(
            Ui.line("보유 " + money(profile.lamps()) + "개", NamedTextColor.GRAY), Ui.line("괭이, 곡괭이, 삽, 도끼에 무작위 능력을 붙입니다.", NamedTextColor.GRAY))));
        Ui.button(holder.inventory, MenuLayout.SHOP_BUY, drawn, Ui.icon(Material.GOLD_INGOT, Ui.line("구매 · " + money(price) + " 골드", NamedTextColor.AQUA), List.of(
            Ui.line("좌클릭 시 1개 구매", NamedTextColor.GRAY),
            Ui.line("쉬프트+좌클릭 시 " + BULK + "개 구매 · " + money(price * BULK) + " 골드", NamedTextColor.GRAY),
            Ui.line("잔액 " + money(profile.coins()) + " 골드", NamedTextColor.YELLOW))));
        Ui.button(holder.inventory, MenuLayout.SHOP_USE, drawn, Ui.icon(Material.ENCHANTED_BOOK, Ui.line("램프 사용", NamedTextColor.GREEN), List.of(Ui.line("손에 든 도구에 램프를 씁니다.", NamedTextColor.GRAY))));
        Ui.button(holder.inventory, List.of(MenuLayout.SHOP_CLOSE), drawn, Ui.icon(Material.BARRIER, Ui.line("닫기", NamedTextColor.RED), List.of()));
    }
    private void renderUse(Holder holder, Player player, Profile profile) {
        boolean drawn = plugin.packDrawn(player);
        ItemStack hand = player.getInventory().getItemInMainHand();
        LifeLamp.Tool tool = tool(hand);
        LifeLamp.Option current = option(hand);
        if (tool == null) holder.inventory.setItem(MenuLayout.USE_TARGET, Ui.icon(Material.BARRIER, Ui.line("도구를 손에 들고 여세요", NamedTextColor.RED), List.of(Ui.line("괭이, 곡괭이, 삽, 도끼에 쓸 수 있습니다.", NamedTextColor.GRAY))));
        else holder.inventory.setItem(MenuLayout.USE_TARGET, Ui.decorate(hand.clone(), null, List.of(Ui.line(current == null ? "현재 능력 없음" : "현재: " + current.describe(), NamedTextColor.YELLOW))));
        List<Component> lore = new ArrayList<>();
        if (holder.previous != null) lore.add(Ui.line("이전: " + holder.previous.describe(), NamedTextColor.GRAY));
        if (current != null) lore.add(Ui.line("현재: " + current.describe(), NamedTextColor.YELLOW));
        lore.add(Ui.line("램프 1개를 씁니다. 보유 " + money(profile.lamps()) + "개", NamedTextColor.GRAY));
        lore.add(Ui.line(tool == null ? "손에 도구가 없어 쓸 수 없습니다." : profile.lamps() < 1 ? "램프가 없어 쓸 수 없습니다." : "기존 능력은 사라지고 무작위 능력이 붙습니다.", tool != null && profile.lamps() > 0 ? NamedTextColor.GRAY : NamedTextColor.RED));
        Ui.button(holder.inventory, MenuLayout.USE_APPLY, drawn, Ui.icon(Material.LIME_CONCRETE, Ui.line("1회 적용", NamedTextColor.GREEN), lore));
        Ui.button(holder.inventory, MenuLayout.USE_LIST, drawn, Ui.icon(Material.BOOK, Ui.line("가능한 능력 보기", NamedTextColor.YELLOW), List.of()));
        Ui.button(holder.inventory, List.of(MenuLayout.USE_BACK), drawn, Ui.icon(Material.ARROW, Ui.line("뒤로", NamedTextColor.WHITE), List.of()));
    }
    private void renderOptions(Holder holder, Player player) {
        LifeLamp.Tool tool = tool(player.getInventory().getItemInMainHand());
        int slot = OPTIONS_START;
        for (LifeLamp.Kind kind : lamp.kinds(tool)) {
            if (slot == OPTIONS_START + OPTIONS_SIZE) break;
            holder.inventory.setItem(slot++, Ui.icon(icon(kind), Ui.line(kind.label, NamedTextColor.WHITE), List.of(Ui.line(lamp.range(kind), NamedTextColor.GRAY), Ui.line(kind.tool == null ? "모든 도구 공통" : kind.tool.label + " 전용", NamedTextColor.AQUA))));
        }
        holder.inventory.setItem(OPTIONS_BACK, Ui.icon(Material.ARROW, Ui.line("뒤로", NamedTextColor.WHITE), List.of()));
    }
    private void buy(Player player, Holder holder, int amount) throws IOException {
        if (!plugin.survival(player)) return;
        if (plugin.tradeHeld(player)) { plugin.tell(player, "거래를 완료하지 못했습니다. 아이템과 잔액을 확인해 주세요."); return; }
        var id = player.getUniqueId();
        Profile profile = store.load(id);
        long cost = Math.multiplyExact(price, amount);
        if (profile.coins() < cost) { plugin.tell(player, "골드가 부족합니다. 필요 " + money(cost) + " / 보유 " + money(profile.coins())); return; }
        plugin.transact(player, "lamp-buy", amount + " -" + cost, () -> store.save(id, profile.spend(cost).addLamps(amount)));
        plugin.tell(player, "생활 램프 " + amount + "개를 구매했습니다. 보유 " + (profile.lamps() + amount) + "개");
        render(holder, player);
        showLater(player, holder, null, 0);
    }
    private void apply(Player player, Holder holder) throws IOException {
        if (holder.used || System.currentTimeMillis() < holder.readyAt) return;
        ItemStack hand = player.getInventory().getItemInMainHand();
        LifeLamp.Tool held = tool(hand);
        var id = player.getUniqueId();
        Profile profile = store.load(id);
        if (plugin.tradeHeld(player)) { plugin.tell(player, "거래를 완료하지 못했습니다. 아이템과 잔액을 확인해 주세요."); return; }
        if (player.getInventory().getHeldItemSlot() != holder.slot || held == null) { plugin.tell(player, "손에 든 도구가 없어 램프를 쓰지 않았습니다."); return; }
        if (profile.lamps() < 1) { plugin.tell(player, "램프가 없어 쓰지 못했습니다. 램프는 그대로입니다."); return; }
        holder.used = true;
        LifeLamp.Option before = option(hand), rolled = lamp.roll(held, ThreadLocalRandom.current());
        plugin.transact(player, "lamp", rolled.encode(), () -> {
            store.save(id, profile.useLamp());
            write(hand, rolled);
        });
        plugin.tell(player, "생활 램프를 적용했습니다. " + rolled.describe());
        // The result stays on the same screen; trying again takes a separate click on the refreshed window.
        render(holder, player);
        showLater(player, holder, before, System.currentTimeMillis() + RETRY_MILLIS);
    }
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ClickType click = event.getClick();
        int slot = event.getRawSlot();
        if (holder.screen == Screen.SHOP && MenuLayout.SHOP_BUY.contains(slot) && (click == ClickType.LEFT || click == ClickType.SHIFT_LEFT)) {
            plugin.guarded(player, () -> buy(player, holder, click == ClickType.LEFT ? 1 : BULK));
            return;
        }
        if (click != ClickType.LEFT) return;
        switch (holder.screen) {
            case SHOP -> {
                if (MenuLayout.SHOP_USE.contains(slot)) showNext(player, Screen.USE);
                else if (slot == MenuLayout.SHOP_CLOSE) plugin.getServer().getScheduler().runTask(plugin, () -> player.closeInventory());
            }
            case USE -> {
                if (MenuLayout.USE_APPLY.contains(slot)) plugin.guarded(player, () -> apply(player, holder));
                else if (MenuLayout.USE_LIST.contains(slot)) showNext(player, Screen.OPTIONS);
                else if (slot == MenuLayout.USE_BACK) showNext(player, Screen.SHOP);
            }
            case OPTIONS -> {
                if (slot == OPTIONS_BACK) showNext(player, Screen.USE);
            }
        }
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
