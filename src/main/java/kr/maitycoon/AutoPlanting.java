package kr.maitycoon;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;

/**
 * Replants a hand-harvested crop. The seed is reserved before the break so new drops are never absorbed;
 * the plant goes in on the next tick, and a failure there returns the seed without spending a use.
 */
final class AutoPlanting implements Listener {
    private static final class Holder implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }
    /** What a harvest should do, decided from the player's state alone. */
    enum Plan {
        OFF, NO_USES, NO_SEED, REPLANT;
        static Plan of(boolean enabled, long uses, boolean hasSeed) {
            if (!enabled) return OFF;
            if (uses < 1) return NO_USES;
            return hasSeed ? REPLANT : NO_SEED;
        }
    }
    private static final int STATUS = 11, TOGGLE = 13, BUY = 15, TRACTOR = 31;
    private static final Map<Material, Material> SEEDS = Map.of(Material.WHEAT, Material.WHEAT_SEEDS, Material.CARROTS, Material.CARROT, Material.POTATOES, Material.POTATO, Material.BEETROOTS, Material.BEETROOT_SEEDS);
    private final MaiTycoonPlugin plugin;
    private final ProfileStore store;
    private final Village village;
    private final Lands lands;
    private final long price, uses, tractorPrice;
    /** tractorPrice 0 hides the experimental tractor. */
    AutoPlanting(MaiTycoonPlugin plugin, ProfileStore store, Village village, Lands lands, long price, long uses, long tractorPrice) {
        this.tractorPrice = tractorPrice;
        this.plugin = plugin;
        this.store = store;
        this.village = village;
        this.lands = lands;
        this.price = price;
        this.uses = uses;
    }
    void open(Player player) throws IOException {
        Holder holder = new Holder();
        holder.inventory = plugin.getServer().createInventory(holder, Ui.SIZE, plugin.menuTitle(player, "자동 심기"));
        render(holder, player);
        player.openInventory(holder.inventory);
    }
    private void render(Holder holder, Player player) throws IOException {
        Profile profile = store.load(player.getUniqueId());
        plugin.frame(holder.inventory, player);
        holder.inventory.setItem(STATUS, Ui.icon(Material.WHEAT_SEEDS, Ui.line("남은 횟수 " + profile.replants() + "회", NamedTextColor.YELLOW), List.of(
            Ui.line("밀, 당근, 감자, 비트를 다 자란 뒤 수확하면 다시 심습니다.", NamedTextColor.GRAY),
            Ui.line("심을 때마다 씨앗 1개와 1회를 사용합니다.", NamedTextColor.GRAY),
            Ui.line("내 땅과 작업자로 등록된 땅, 공용 농장에서 동작합니다.", NamedTextColor.GRAY))));
        holder.inventory.setItem(TOGGLE, Ui.icon(profile.autoPlant() ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE,
            Ui.line(profile.autoPlant() ? "켜짐" : "꺼짐", profile.autoPlant() ? NamedTextColor.GREEN : NamedTextColor.GRAY), List.of(Ui.line("클릭하면 바뀝니다.", NamedTextColor.GRAY))));
        holder.inventory.setItem(BUY, Ui.icon(Material.PAPER, Ui.line(uses + "회 구매", NamedTextColor.GOLD), List.of(
            Ui.line("즈코인 " + price + "개", NamedTextColor.AQUA), Ui.line("보유 즈코인 " + profile.zcoin() + "개", NamedTextColor.GRAY))));
        if (tractorPrice > 0) holder.inventory.setItem(TRACTOR, Ui.icon(Material.MINECART, Ui.line(profile.tractor() ? "트랙터 보유 중" : "트랙터 구매", NamedTextColor.GOLD), List.of(
            profile.tractor() ? Ui.line("괭이를 들고 웅크린 채 우클릭하면 주변을 수확합니다.", NamedTextColor.GRAY) : Ui.line("즈코인 " + tractorPrice + "개", NamedTextColor.AQUA),
            Ui.line("내 땅, 작업자 땅, 공용 농장에서 동작합니다.", NamedTextColor.GRAY))));
    }
    private void buy(Player player) throws IOException {
        var id = player.getUniqueId();
        Profile profile = store.load(id);
        if (plugin.tradeHeld(player)) { plugin.tell(player, "거래를 완료하지 못했습니다. 아이템과 잔액을 확인해 주세요."); return; }
        if (profile.zcoin() < price) { plugin.tell(player, "즈코인이 부족합니다. 필요 " + price + " / 보유 " + profile.zcoin()); return; }
        plugin.transact(player, "replant", uses + " uses -" + price + " zcoin", () -> store.save(id, profile.buyReplants(price, uses)));
        plugin.tell(player, "자동 심기 " + uses + "회를 구매했습니다.");
    }
    private void buyTractor(Player player) throws IOException {
        var id = player.getUniqueId();
        Profile profile = store.load(id);
        if (profile.tractor()) return;
        if (plugin.tradeHeld(player)) { plugin.tell(player, "거래를 완료하지 못했습니다. 아이템과 잔액을 확인해 주세요."); return; }
        if (profile.zcoin() < tractorPrice) { plugin.tell(player, "즈코인이 부족합니다. 필요 " + tractorPrice + " / 보유 " + profile.zcoin()); return; }
        plugin.transact(player, "tractor", "-" + tractorPrice + " zcoin", () -> store.save(id, profile.buyTractor(tractorPrice)));
        plugin.tell(player, "트랙터를 구매했습니다. 괭이를 들고 웅크린 채 우클릭하면 사용합니다.");
    }
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClick() != ClickType.LEFT) return;
        int slot = event.getRawSlot();
        if (slot != TOGGLE && slot != BUY && (slot != TRACTOR || tractorPrice == 0)) return;
        plugin.guarded(player, () -> {
            if (slot == BUY) buy(player);
            else if (slot == TRACTOR) buyTractor(player);
            else store.save(player.getUniqueId(), store.load(player.getUniqueId()).toggleAutoPlant());
            render(holder, player);
        });
    }
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }
    private static int seedSlot(Player player, Material seed) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (Ui.plain(contents[slot]) && contents[slot].getType() == seed) return slot;
        }
        return -1;
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHarvest(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        Material crop = block.getType(), seed = SEEDS.get(crop);
        if (seed == null || Lands.immature(block) || player.getGameMode() == GameMode.CREATIVE) return;
        if (!lands.mayReplant(player, block) || !village.mayReplant(block)) return;
        try {
            Profile profile = store.load(player.getUniqueId());
            int slot = seedSlot(player, seed);
            switch (Plan.of(profile.autoPlant(), profile.replants(), slot >= 0)) {
                case OFF, NO_USES -> {}
                case NO_SEED -> player.sendActionBar(Component.text("수확 완료 · 씨앗이 없어 다시 심지 못했습니다.", NamedTextColor.YELLOW));
                case REPLANT -> {
                    Ui.take(player, slot, 1);
                    plugin.getServer().getScheduler().runTask(plugin, () -> replant(player, block, crop, seed));
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Cannot read auto-plant state: " + player.getUniqueId(), e);
        }
    }
    private void replant(Player player, Block block, Material crop, Material seed) {
        boolean soil = block.getRelative(BlockFace.DOWN).getType() == Material.FARMLAND;
        if (!soil || !block.getType().isAir() || !player.isOnline()) { refund(player, block, seed); return; }
        try {
            var id = player.getUniqueId();
            Profile after = store.load(id).useReplant();
            store.save(id, after);
            Ageable sprout = (Ageable) crop.createBlockData();
            sprout.setAge(0);
            block.setBlockData(sprout);
            player.sendActionBar(Component.text("자동 심기 · 남은 횟수 " + after.replants() + "회", NamedTextColor.GREEN));
        } catch (IOException | IllegalArgumentException e) {
            plugin.getLogger().log(Level.SEVERE, "Replant failed: " + player.getUniqueId(), e);
            refund(player, block, seed);
        }
    }
    private void refund(Player player, Block block, Material seed) {
        ItemStack back = new ItemStack(seed);
        if (player.isOnline()) player.getInventory().addItem(back).values().forEach(left -> block.getWorld().dropItemNaturally(block.getLocation(), left));
        else block.getWorld().dropItemNaturally(block.getLocation(), back);
    }
}
