package kr.maitycoon;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/** Explains keys and features. Key names come from the client, so they match each player's own bindings. */
final class HelpMenu implements Listener {
    private static final class Holder implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }
    private static final int MAP_BUTTON = 47, DEX_BUTTON = 48, LAND_BUTTON = 49, WALLET_BUTTON = 50;
    private final MaiTycoonPlugin plugin;
    HelpMenu(MaiTycoonPlugin plugin) { this.plugin = plugin; }
    private static Component key(String keybind) { return Component.keybind(keybind, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false); }
    private static Component keyLine(String keybind, String text) { return key(keybind).append(Ui.line(" " + text, NamedTextColor.GRAY)); }
    private static ItemStack entry(Material icon, String title, List<Component> lines) { return Ui.icon(icon, Ui.line(title, NamedTextColor.GOLD), lines); }
    private static Component text(String line) { return Ui.line(line, NamedTextColor.GRAY); }
    void open(Player player) {
        Holder holder = new Holder();
        holder.inventory = plugin.getServer().createInventory(holder, Ui.SIZE, plugin.menuTitle(player, "도움말"));
        plugin.frame(holder.inventory, player);
        Inventory inventory = holder.inventory;
        inventory.setItem(10, entry(Material.COMPASS, "단축키", List.of(
            keyLine("key.swapOffhand", "도감 열기"),
            keyLine("key.quickActions", "전체 지도 (조작 설정에서 M으로 바꿀 수 있습니다)"),
            keyLine("key.swapOffhand", "구매할 수 있는 땅에서는 땅 구매"),
            keyLine("key.sneak", "를 누른 채 위 키: 손 바꾸기"))));
        inventory.setItem(12, entry(Material.WHEAT, "농사", List.of(
            text("공용 농장이나 내 땅에 씨앗을 심고 거둡니다."),
            text("씨앗 상인에게 씨앗을 사고, 작물 상인에게 팝니다."),
            text("특산 작물은 씨앗 상인의 특산 탭에 있습니다."))));
        inventory.setItem(14, entry(Material.EMERALD, "시세", List.of(
            text("작물값은 10분마다 바뀝니다."),
            text("많이 팔린 작물은 다음 시세에서 값이 내려갑니다."),
            text("/tycoon price 로 시세 게시판을 엽니다."))));
        inventory.setItem(16, entry(Material.WRITABLE_BOOK, "수집 도감", List.of(
            text("아이템은 1개를 제출해 등록합니다."),
            text("몬스터는 처치하면, 바이옴은 방문하면 자동 등록됩니다."),
            text("20개에 생활 램프, 30개에 땅 구매 이용권을 받습니다."))));
        inventory.setItem(28, entry(Material.GRASS_BLOCK, "땅", List.of(
            text("마을 밖 16×16 청크를 1인당 10개까지 삽니다."),
            text("살수록 비싸집니다. 골드나 구매 이용권 중 하나로 삽니다."),
            text("내 땅은 지정한 작업자만 쓸 수 있습니다."),
            text("작업자 권한은 아래 내 땅 버튼의 농장 관리에서 정합니다."))));
        inventory.setItem(30, entry(Material.GLOWSTONE_DUST, "생활 램프와 자동 심기", List.of(
            text("램프 상인에게 골드로 램프를 삽니다."),
            text("도구를 들고 램프 상점의 램프 사용을 누르면 능력이 붙습니다."),
            text("자동 심기는 아래 내 땅 버튼의 농장 관리에서 켭니다."))));
        inventory.setItem(32, entry(Material.CHEST, "상인", List.of(
            text("작물 상인, 씨앗 상인, 램프 상인은 광장 북쪽 상점에 함께 있습니다."),
            text("건축 자재 상인과 가구 상인은 남쪽 도로변 상점에 있습니다."),
            text("도감 사서와 안내원은 마을회관에 있습니다."),
            text("NPC를 우클릭하면 메뉴가 열립니다."))));
        inventory.setItem(34, entry(Material.PAPER, "명령어", List.of(
            text("/도움말  이 화면"),
            text("/tycoon info  보유 재화"),
            text("/tycoon wallet  지갑과 골드 보내기"),
            text("/tycoon price  시세 게시판"),
            text("/tycoon village  마을로 이동"),
            text("/tycoon starter  시작 보급품"),
            text("/tycoon land member add 이름  접속 중이 아닌 작업자 추가"))));
        inventory.setItem(MAP_BUTTON, Ui.icon(Material.FILLED_MAP, Ui.line("전체 지도 열기", NamedTextColor.YELLOW), List.of(text("유저 위치와 땅 소유를 넓게 봅니다."))));
        inventory.setItem(DEX_BUTTON, Ui.icon(Material.BOOK, Ui.line("수집 도감 열기", NamedTextColor.YELLOW), List.of()));
        inventory.setItem(LAND_BUTTON, Ui.icon(Material.GRASS_BLOCK, Ui.line("내 땅 열기", NamedTextColor.YELLOW), List.of(text("땅 지도와 농장 관리를 엽니다."), text("/tycoon land 와 같습니다."))));
        inventory.setItem(WALLET_BUTTON, Ui.icon(Material.GOLD_INGOT, Ui.line("지갑 열기", NamedTextColor.YELLOW), List.of(text("잔액과 최근 거래를 보고 골드를 보냅니다."), text("돈은 계정에 있어서 들고 다니지 않습니다."))));
        player.openInventory(inventory);
    }
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getRawSlot() == MAP_BUTTON) plugin.getServer().getScheduler().runTask(plugin, () -> player.performCommand("tycoon map"));
        else if (event.getRawSlot() == DEX_BUTTON) plugin.getServer().getScheduler().runTask(plugin, () -> plugin.openDex(player));
        else if (event.getRawSlot() == LAND_BUTTON) plugin.getServer().getScheduler().runTask(plugin, () -> plugin.openLand(player));
        else if (event.getRawSlot() == WALLET_BUTTON) plugin.getServer().getScheduler().runTask(plugin, () -> plugin.openWallet(player));
    }
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().sendMessage(Component.text("[마이 타이쿤] ", NamedTextColor.GREEN).append(Component.text("도움말은 ", NamedTextColor.WHITE)).append(Component.text("/도움말", NamedTextColor.YELLOW))
            .append(Component.text(", 도감은 ", NamedTextColor.WHITE)).append(Component.keybind("key.swapOffhand", NamedTextColor.YELLOW)).append(Component.text(" 키입니다.", NamedTextColor.WHITE)));
    }
}
