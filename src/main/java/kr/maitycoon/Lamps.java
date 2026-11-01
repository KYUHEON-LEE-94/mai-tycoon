package kr.maitycoon;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;

/** Applies life lamps to hoes and pays their bonus on direct harvests of fully grown crops. */
final class Lamps implements Listener {
    private static final class Holder implements InventoryHolder {
        private final int slot;
        private boolean used;
        private Inventory inventory;
        private Holder(int slot) { this.slot = slot; }
        @Override public Inventory getInventory() { return inventory; }
    }
    private static final int TARGET = 13, BUY = 15, RULE = 22;
    private static final List<Integer> YES = List.of(30, 31, 32), NO = List.of(39, 40, 41);
    private static final Map<Material, Material> CROPS = Map.of(Material.WHEAT, Material.WHEAT, Material.CARROTS, Material.CARROT, Material.POTATOES, Material.POTATO, Material.BEETROOTS, Material.BEETROOT);
    private static final Map<Material, Material> SEEDS = Map.of(Material.WHEAT, Material.WHEAT_SEEDS, Material.CARROTS, Material.CARROT, Material.POTATOES, Material.POTATO, Material.BEETROOTS, Material.BEETROOT_SEEDS);
    private final MaiTycoonPlugin plugin;
    private final ProfileStore store;
    private final LifeLamp lamp;
    private final long price;
    private final NamespacedKey key;
    Lamps(MaiTycoonPlugin plugin, ProfileStore store, LifeLamp lamp, long price) {
        this.plugin = plugin;
        this.store = store;
        this.lamp = lamp;
        this.price = price;
        this.key = new NamespacedKey(plugin, "lamp");
    }
    private static boolean hoe(ItemStack item) { return item != null && item.getType().name().endsWith("_HOE"); }
    LifeLamp.Option option(ItemStack item) {
        if (!hoe(item) || !item.hasItemMeta()) return null;
        return LifeLamp.Option.decode(item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING));
    }
    void write(ItemStack hoe, LifeLamp.Option option) {
        var meta = hoe.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, option.encode());
        meta.lore(List.of(Ui.line("생활 램프", NamedTextColor.GOLD), Ui.line(option.describe(), NamedTextColor.YELLOW)));
        hoe.setItemMeta(meta);
    }
    /** Opens with or without a hoe or lamps, because this is also where lamps are bought. */
    void open(Player player) throws IOException {
        Holder holder = new Holder(player.getInventory().getHeldItemSlot());
        holder.inventory = plugin.getServer().createInventory(holder, Ui.SIZE, plugin.menuTitle(player, "생활 램프"));
        render(holder, player);
        player.openInventory(holder.inventory);
    }
    private void render(Holder holder, Player player) throws IOException {
        plugin.frame(holder.inventory, player);
        ItemStack hand = player.getInventory().getItemInMainHand();
        Profile profile = store.load(player.getUniqueId());
        LifeLamp.Option current = option(hand);
        if (hoe(hand)) {
            ItemStack target = hand.clone();
            Ui.decorate(target, null, List.of(Ui.line(current == null ? "현재 옵션 없음" : "현재: " + current.describe(), NamedTextColor.GRAY)));
            holder.inventory.setItem(TARGET, target);
        } else holder.inventory.setItem(TARGET, Ui.icon(Material.BARRIER, Ui.line("괭이를 손에 들고 여세요", NamedTextColor.RED), List.of(Ui.line("램프 구매는 괭이 없이도 됩니다.", NamedTextColor.GRAY))));
        holder.inventory.setItem(BUY, Ui.icon(Material.GOLD_INGOT, Ui.line("램프 1개 구매", NamedTextColor.GOLD), List.of(
            Ui.line(price + " 골드", NamedTextColor.YELLOW), Ui.line("보유 " + profile.coins() + " 골드", NamedTextColor.GRAY))));
        holder.inventory.setItem(RULE, Ui.icon(Material.GLOWSTONE_DUST, Ui.line("옵션은 무작위로 정해집니다", NamedTextColor.YELLOW), List.of(
            Ui.line("램프 1개를 사용합니다. 남은 램프 " + profile.lamps() + "개", NamedTextColor.GRAY),
            Ui.line(current == null ? "밀, 당근, 감자, 비트를 다 자란 뒤 수확할 때 적용됩니다." : "기존 옵션은 새 옵션으로 바뀝니다.", current == null ? NamedTextColor.GRAY : NamedTextColor.RED))));
        ItemStack yes = Ui.icon(Material.LIME_CONCRETE, Ui.line("적용", NamedTextColor.GREEN), List.of());
        ItemStack no = Ui.icon(Material.RED_CONCRETE, Ui.line("취소", NamedTextColor.RED), List.of());
        for (int slot : YES) holder.inventory.setItem(slot, yes);
        for (int slot : NO) holder.inventory.setItem(slot, no);
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
        var id = player.getUniqueId();
        Profile profile = store.load(id);
        if (plugin.tradeHeld(player)) { plugin.tell(player, "거래를 완료하지 못했습니다. 아이템과 잔액을 확인해 주세요."); return; }
        if (player.getInventory().getHeldItemSlot() != holder.slot || !hoe(hand) || profile.lamps() < 1) { plugin.tell(player, "괭이나 램프가 바뀌어 적용하지 않았습니다."); return; }
        LifeLamp.Option rolled = lamp.roll(ThreadLocalRandom.current());
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
        if (slot == BUY) { plugin.guarded(player, () -> buy(player, holder)); return; }
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
        Player player = event.getPlayer();
        if (!CROPS.containsKey(block.getType()) || Lands.immature(block) || player.getGameMode() == GameMode.CREATIVE || plugin.tractorHarvesting()) return;
        LifeLamp.Option option = option(player.getInventory().getItemInMainHand());
        if (option == null) return;
        int bonus = option.bonus(ThreadLocalRandom.current());
        if (bonus == 0) return;
        Material drop = switch (option.kind()) {
            case CROP -> CROPS.get(block.getType());
            case SEED -> SEEDS.get(block.getType());
            case BONE_MEAL -> Material.BONE_MEAL;
        };
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), new ItemStack(drop, bonus));
    }
}
