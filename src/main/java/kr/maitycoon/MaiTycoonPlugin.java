package kr.maitycoon;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class MaiTycoonPlugin extends JavaPlugin implements TabExecutor {
    private ProfileStore store;
    private Market market;
    private Shop shop;
    private Village village;
    private long marketInterval, nextMarketChange;
    private static final List<String> ACTIONS = List.of("help", "info", "starter", "price", "village");
    private static final List<String> ADMIN_ACTIONS = List.of("market");
    @Override public void onEnable() {
        saveDefaultConfig();
        try {
            store = new ProfileStore(getDataFolder().toPath().resolve("players"));
            market = loadMarket();
            restoreMarket();
            Map<Material, Long> seeds = loadSeeds();
            marketInterval = getConfig().getInt("market.interval-minutes") * 60_000L;
            if (marketInterval < 60_000) throw new IOException("Invalid market interval");
            shop = new Shop(this, market, store, seeds);
            village = new Village(this, shop);
            Objects.requireNonNull(getCommand("tycoon")).setExecutor(this);
            Objects.requireNonNull(getCommand("tycoon")).setTabCompleter(this);
            getServer().getPluginManager().registerEvents(shop, this);
            getServer().getPluginManager().registerEvents(village, this);
            nextMarketChange = System.currentTimeMillis() + marketInterval;
            long ticks = marketInterval / 50;
            getServer().getScheduler().runTaskTimer(this, this::refreshPrices, ticks, ticks);
            getServer().getScheduler().runTask(this, () -> {
                if (village.built()) { getServer().setSpawnRadius(0); village.ensureNpcs(); }
                else if (getConfig().getBoolean("village.auto-build")) village.build(() -> {});
            });
        } catch (IOException | IllegalArgumentException e) {
            getLogger().log(Level.SEVERE, "Cannot enable MaiTycoon", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    private Market loadMarket() throws IOException {
        var section = getConfig().getConfigurationSection("crops");
        if (section == null) throw new IOException("Missing crops");
        Map<String, Market.Rule> rules = new LinkedHashMap<>();
        for (String id : section.getKeys(false)) {
            Material crop = Material.matchMaterial(id);
            if (crop == null || !crop.isItem() || !crop.name().equals(id)) throw new IOException("Invalid crop " + id);
            rules.put(id, new Market.Rule(section.getLong(id + ".base", -1), section.getInt(id + ".swing", -1)));
        }
        return new Market(rules, getConfig().getInt("market.min-percent"), getConfig().getInt("market.max-percent"));
    }
    private Map<Material, Long> loadSeeds() throws IOException {
        var section = getConfig().getConfigurationSection("seeds");
        if (section == null) throw new IOException("Missing seeds");
        Map<Material, Long> seeds = new LinkedHashMap<>();
        for (String id : section.getKeys(false)) {
            Material item = Material.matchMaterial(id);
            long price = section.getLong(id, -1);
            if (item == null || !item.isItem() || price < 1 || price > 1_000_000) throw new IOException("Invalid seed price for " + id);
            // Buying must never undercut the best selling price, or YD could be minted by trading back and forth.
            if (market.has(id) && price <= market.ceiling(id)) throw new IOException("Seed price must exceed top market price: " + id);
            seeds.put(item, price);
        }
        return seeds;
    }
    private File marketFile() { return new File(getDataFolder(), "market.yml"); }
    private void restoreMarket() throws IOException {
        if (!marketFile().exists()) return;
        var yaml = new YamlConfiguration();
        try { yaml.load(marketFile()); }
        catch (InvalidConfigurationException e) { throw new IOException("Invalid market.yml", e); }
        for (String id : market.ids()) {
            if (yaml.isInt(id + ".percent")) market.restore(id, yaml.getInt(id + ".percent"), yaml.getInt(id + ".previous", yaml.getInt(id + ".percent")));
        }
    }
    private void saveMarket() throws IOException {
        var yaml = new YamlConfiguration();
        for (String id : market.ids()) {
            yaml.set(id + ".percent", market.percent(id));
            yaml.set(id + ".previous", market.previousPercent(id));
        }
        yaml.save(marketFile());
    }
    long secondsToNextMarket() { return Math.max(0, (nextMarketChange - System.currentTimeMillis()) / 1000); }
    private void refreshPrices() {
        List<Market.Move> moves = market.step(ThreadLocalRandom.current());
        nextMarketChange = System.currentTimeMillis() + marketInterval;
        try { saveMarket(); }
        catch (IOException e) { getLogger().log(Level.SEVERE, "Cannot save market.yml", e); }
        shop.refreshOpen();
        Market.Move up = moves.stream().max(Comparator.comparingDouble(m -> (double) m.change() / market.base(m.id()))).orElseThrow();
        Market.Move down = moves.stream().min(Comparator.comparingDouble(m -> (double) m.change() / market.base(m.id()))).orElseThrow();
        Component news = Component.text("시세 변동! ").append(moveText(up, NamedTextColor.RED)).append(Component.text(" / ")).append(moveText(down, NamedTextColor.BLUE));
        for (Player player : getServer().getOnlinePlayers()) tell(player, news);
    }
    private static Component moveText(Market.Move move, NamedTextColor color) {
        return Component.translatable(Material.valueOf(move.id())).color(color)
            .append(Component.text(" " + move.price() + " YD (" + (move.change() >= 0 ? "+" : "") + move.change() + ")", color));
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if (admin(sender, action, args)) return true;
        if (!(sender instanceof Player player)) { sender.sendMessage("게임 안에서 사용하세요."); return true; }
        if (!player.hasPermission("maitycoon.play")) return true;
        try {
            switch (action) {
                case "info" -> tell(player, "보유 YD: " + store.load(player.getUniqueId()).coins());
                case "price" -> prices(player);
                case "starter" -> starter(player);
                case "village" -> {
                    if (!village.built()) tell(player, "마을이 아직 지어지지 않았습니다.");
                    else { player.teleport(village.spawn()); tell(player, "마을 광장으로 이동했습니다."); }
                }
                default -> {
                    tell(player, "광장의 작물 상인에게 작물을 팔고, 씨앗 상인에게 씨앗과 도구를 살 수 있습니다.");
                    tell(player, "/tycoon starter — 테스트용 최초 1회 보급품 받기");
                    tell(player, "/tycoon price — 현재 작물 시세 보기");
                    tell(player, "/tycoon village — 마을 광장으로 이동");
                    tell(player, "/tycoon info — 보유 YD 확인");
                }
            }
        } catch (IOException | ArithmeticException e) {
            getLogger().log(Level.SEVERE, "Player transaction failed: " + player.getUniqueId(), e);
            tell(player, "데이터를 처리하지 못했습니다. 관리자에게 문의하세요.");
        }
        return true;
    }
    private boolean admin(CommandSender sender, String action, String[] args) {
        boolean build = action.equals("village") && args.length == 2 && args[1].equalsIgnoreCase("build");
        boolean step = action.equals("market") && args.length == 2 && args[1].equalsIgnoreCase("next");
        if (!build && !step) return false;
        if (!sender.hasPermission("maitycoon.admin")) { sender.sendMessage("권한이 없습니다."); return true; }
        if (step) { refreshPrices(); sender.sendMessage("시세를 갱신했습니다."); }
        else {
            sender.sendMessage("마을을 짓습니다. 스폰 주변 지형이 바뀝니다.");
            village.build(() -> sender.sendMessage("마을 작업이 끝났습니다."));
        }
        return true;
    }
    private void prices(Player player) {
        tell(player, "현재 시세 (다음 변동까지 약 " + secondsToNextMarket() + "초)");
        for (String id : market.ids()) {
            long change = market.change(id);
            String trend = change > 0 ? " ▲" + change : change < 0 ? " ▼" + -change : "";
            player.sendMessage(Component.text(" · ").append(Component.translatable(Material.valueOf(id))).append(Component.text(" " + market.price(id) + " YD" + trend)));
        }
    }
    boolean survival(Player player) {
        if (player.getGameMode() == GameMode.SURVIVAL) return true;
        tell(player, "농사 보급과 거래는 서바이벌 모드에서 사용할 수 있습니다.");
        return false;
    }
    private void starter(Player player) throws IOException {
        if (!survival(player)) return;
        Profile profile = store.load(player.getUniqueId());
        if (profile.starterClaimed()) { tell(player, "이미 시작 보급품을 받았습니다."); return; }
        long empty = Arrays.stream(player.getInventory().getStorageContents()).filter(i -> i == null || i.getType().isAir()).count();
        if (empty < 3) { tell(player, "인벤토리에 빈칸 3개가 필요합니다."); return; }
        store.save(player.getUniqueId(), new Profile(profile.coins(), profile.experience(), true));
        player.getInventory().addItem(new ItemStack(Material.STONE_HOE), new ItemStack(Material.WHEAT_SEEDS, 16), new ItemStack(Material.BREAD, 8));
        tell(player, "돌 괭이, 밀 씨앗 16개, 빵 8개를 받았습니다! 마을 공용 농장에서 농사를 시작하세요.");
    }
    void tell(Player player, String message) { tell(player, Component.text(message, NamedTextColor.WHITE)); }
    void tell(Player player, Component message) {
        player.sendMessage(Component.text("[마이 타이쿤] ", NamedTextColor.GREEN).append(message.colorIfAbsent(NamedTextColor.WHITE)));
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 2 && sender.hasPermission("maitycoon.admin")) {
            String next = args[0].equalsIgnoreCase("village") ? "build" : args[0].equalsIgnoreCase("market") ? "next" : "";
            return !next.isEmpty() && next.startsWith(typed) ? List.of(next) : List.of();
        }
        if (args.length != 1) return List.of();
        List<String> actions = new ArrayList<>(ACTIONS);
        if (sender.hasPermission("maitycoon.admin")) actions.addAll(ADMIN_ACTIONS);
        return actions.stream().filter(s -> s.startsWith(typed)).toList();
    }
}
